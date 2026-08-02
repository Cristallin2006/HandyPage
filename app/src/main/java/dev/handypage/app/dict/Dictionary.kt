package dev.handypage.app.dict

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One row of the ECDICT `words` table. When the looked-up form is an
 * inflection (either its own row names a `lemma`, or the `inflections`
 * reverse-map produced the base form), [lemmaEntry] carries the base form's
 * row and the UI should display [lemmaEntry]'s gloss next to the looked-up
 * [word] plus a "→ base" hint.
 */
data class DictEntry(
    val word: String,
    val phonetic: String?,
    val translation: String?,
    val definition: String?,
    val pos: String?,
    val tag: String?,
    val collins: Int,
    val frq: Int?,
    val lemma: String?,
    val lemmaEntry: DictEntry? = null,
)

/**
 * Extracts the bundled dictionary database from `assets/dict/` on first use.
 */
object DictManager {

    private const val ASSET_ZIP = "dict/handypage_dict.db.zip"
    private const val DB_NAME = "handypage_dict.db"
    private const val BUFFER_SIZE = 256 * 1024

    fun targetFile(context: Context): File =
        File(context.filesDir, "dict/$DB_NAME")

    /**
     * Decompresses `assets/dict/handypage_dict.db.zip` to
     * `filesDir/dict/handypage_dict.db` and returns the database file.
     *
     * The extraction is skipped when the target already exists with exactly
     * the uncompressed size recorded in the zip entry. The write goes to a
     * `.tmp` sibling which is atomically renamed over the target, so a
     * crashed first run cannot leave a truncated database behind.
     * [onProgress] is invoked on the IO dispatcher after every buffer flush.
     */
    suspend fun ensureExtracted(
        context: Context,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val target = targetFile(context)
        context.assets.open(ASSET_ZIP).use { assetStream ->
            val zip = ZipInputStream(assetStream.buffered(BUFFER_SIZE))
            val entry = zip.nextEntry
                ?: throw IllegalStateException("$ASSET_ZIP contains no entries")
            val total = entry.size
            if (target.isFile && target.length() == total) {
                return@withContext target
            }

            val dir = target.parentFile ?: throw IllegalStateException("No parent for $target")
            dir.mkdirs()
            val tmp = File(dir, "$DB_NAME.tmp")
            try {
                var written = 0L
                tmp.outputStream().buffered(BUFFER_SIZE).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                }
                if (total >= 0 && written != total) {
                    throw IllegalStateException(
                        "Short extraction of $ASSET_ZIP: wrote $written of $total bytes"
                    )
                }
                if (target.exists() && !target.delete()) {
                    throw IllegalStateException("Cannot replace stale $target")
                }
                if (!tmp.renameTo(target)) {
                    throw IllegalStateException("Cannot move $tmp into place as $target")
                }
                target
            } finally {
                tmp.delete()
            }
        }
    }
}

/**
 * Read-only access to the prebuilt ECDICT database.
 *
 * Plain [SQLiteDatabase], not Room: the file is a 770k-row read-only asset
 * whose schema is fixed at build time, so codegen would buy nothing.
 */
class Dictionary(private val dbFile: File) {

    private val db: SQLiteDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    /**
     * Looks up [raw] (any selected text) and returns the matching entry, or
     * null when no candidate form is in the dictionary.
     *
     * Chain per candidate from [WordForms.candidates] of [WordForms.clean]:
     * 1. direct `words` hit — if the row names a `lemma`, the lemma row is
     *    attached as [DictEntry.lemmaEntry];
     * 2. otherwise the `inflections` reverse map yields the base form, whose
     *    `words` row becomes [DictEntry.lemmaEntry] of a form-shaped entry.
     */
    fun lookup(raw: String): DictEntry? {
        val cleaned = WordForms.clean(raw)
        if (cleaned.isEmpty()) return null
        for (form in WordForms.candidates(cleaned)) {
            queryWords(form)?.let { hit ->
                val lemma = hit.lemma
                    ?.takeIf { it.isNotBlank() && !it.equals(hit.word, ignoreCase = true) }
                    ?: return hit
                val lemmaRow = queryWords(lemma) ?: return hit
                return hit.copy(lemmaEntry = lemmaRow)
            }
            val lemma = queryInflectionLemma(form) ?: continue
            val lemmaRow = queryWords(lemma) ?: continue
            return DictEntry(
                word = form,
                phonetic = null,
                translation = null,
                definition = null,
                pos = null,
                tag = null,
                collins = 0,
                frq = null,
                lemma = lemmaRow.word,
                lemmaEntry = lemmaRow,
            )
        }
        return null
    }

    fun close() {
        if (db.isOpen) db.close()
    }

    private fun queryWords(form: String): DictEntry? =
        db.rawQuery(
            "SELECT word, phonetic, definition, translation, pos, tag, collins, frq, lemma" +
                " FROM words WHERE word = ? LIMIT 1",
            arrayOf(form),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toEntry() else null }

    private fun queryInflectionLemma(form: String): String? =
        db.rawQuery(
            "SELECT lemma FROM inflections WHERE form = ? ORDER BY lemma LIMIT 1",
            arrayOf(form),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun Cursor.toEntry(): DictEntry = DictEntry(
        word = getString(0),
        phonetic = textOrNull(1),
        definition = textOrNull(2),
        translation = textOrNull(3),
        pos = textOrNull(4),
        tag = textOrNull(5),
        collins = if (isNull(6)) 0 else getInt(6),
        frq = if (isNull(7)) null else getInt(7),
        lemma = textOrNull(8),
    )

    private fun Cursor.textOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)?.takeIf { it.isNotBlank() }
}
