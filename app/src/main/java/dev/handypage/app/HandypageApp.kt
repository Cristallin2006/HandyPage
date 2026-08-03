package dev.handypage.app

import android.app.Application
import androidx.room.Room
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dev.handypage.app.dict.DictManager
import dev.handypage.app.dict.Dictionary
import dev.handypage.app.engine.ImageEmbedder
import dev.handypage.app.engine.SourceEngine
import dev.handypage.app.vocab.VocabDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.Asset

/**
 * Application class. Holds the shared [SourceEngine] and the currently open
 * book in a plain field so it survives Activity configuration changes without
 * re-parsing the EPUB.
 */
class HandypageApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // pdfbox-android needs its resource loader bound to the app context
        // before any PDDocument load (pdf/PdfToArticle).
        PDFBoxResourceLoader.init(this)
    }

    val engine: SourceEngine by lazy {
        // M30: one shared client; the embedder gets the BitmapFactory
        // transcoder so webp/avif payloads are normalized for the reader.
        val client = handypageHttpClient()
        SourceEngine(
            client,
            imageEmbedder = ImageEmbedder(client, transcoder = AndroidImageTranscoder),
        )
    }

    /**
     * Application-level scope for work that must outlive any screen (M12-A:
     * the arXiv reflow EPUB conversion keeps running after the paper reader
     * opens). SupervisorJob: one failed job doesn't cancel the others.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Room vocab book; built lazily on first access. */
    val vocabDb: VocabDatabase by lazy {
        Room.databaseBuilder(this, VocabDatabase::class.java, "handypage.db")
            .addMigrations(
                VocabDatabase.MIGRATION_1_2,
                VocabDatabase.MIGRATION_2_3,
                VocabDatabase.MIGRATION_3_4,
                VocabDatabase.MIGRATION_4_5,
                VocabDatabase.MIGRATION_5_6,
            )
            .build()
    }

    @Volatile
    private var dictionary: Dictionary? = null
    private val dictionaryMutex = Mutex()

    /**
     * Opens the bundled ECDICT database, extracting it from assets on the
     * very first call (a one-time ~86 MB write to filesDir). Safe to call
     * concurrently; the extraction runs at most once.
     */
    suspend fun requireDictionary(): Dictionary {
        dictionary?.let { return it }
        return dictionaryMutex.withLock {
            dictionary ?: run {
                val file = DictManager.ensureExtracted(this)
                Dictionary(file).also { dictionary = it }
            }
        }
    }

    var openBook: OpenBook? = null

    /** M17: the currently active global Agent session key (survives config changes). */
    var currentGlobalSessionKey: String = "global"

    class OpenBook(
        /** Absolute path of the EPUB file this book was opened from. */
        val path: String,
        val publication: Publication,
        val asset: Asset,
    )
}
