package dev.handypage.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.handypage.app.HandypageApp
import dev.handypage.app.R
import dev.handypage.app.ReaderActivity
import dev.handypage.app.arxiv.ArxivApi
import dev.handypage.app.handypageHttpClient
import dev.handypage.app.arxiv.ArxivEntry
import dev.handypage.app.arxiv.ArxivHtml
import dev.handypage.app.epub.EpubPackager
import dev.handypage.app.paper.PaperReaderActivity
import dev.handypage.app.pdf.PdfToArticle
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * M13: the arXiv paper "download → open" flow, extracted from ArxivScreen so
 * the 本机 tab's 收藏 section can re-run it for starred papers whose PDF was
 * evicted. Behaviour matches the original exactly: modal progress dialog with
 * cancel, ".part" cleanup, reading-record write, background reflow-EPUB
 * conversion, and a failure snackbar offering 「浏览器打开」.
 *
 * Also owns the [ArxivApi] instance plus the serialising [apiMutex]/[throttle]
 * pair (arXiv etiquette: one request per [delaySeconds]), shared with the
 * list screen's fetch path.
 */
class PaperOpener(
    private val context: Context,
    private val app: HandypageApp,
    private val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    private val delaySeconds: Double,
    private val sourceId: String,
    private val sourceName: String,
) {
    val api: ArxivApi = ArxivApi(handypageHttpClient())
    val apiMutex = Mutex()
    private var lastApiCallAt = 0L

    /** Non-null while a paper download+parse is in flight; shows the dialog. */
    var openProgress by mutableStateOf<OpenProgress?>(null)
        private set

    /** absUrl of the paper being opened, for cancel-time ".part" cleanup. */
    private var openingUrl: String? = null
    private var openJob: Job? = null

    suspend fun throttle() {
        val waitMs = lastApiCallAt + (delaySeconds * 1000).toLong() -
            System.currentTimeMillis()
        if (waitMs > 0) delay(waitMs)
        lastApiCallAt = System.currentTimeMillis()
    }

    fun open(entry: ArxivEntry) {
        openJob?.cancel()
        openJob = scope.launch {
            val epubFile = epubFileFor(context, entry.absUrl)
            val paperFile = paperFileFor(context, entry.absUrl)
            openingUrl = entry.absUrl
            try {
                if (!paperFile.isFile) {
                    openProgress = OpenProgress(OpenStage.DOWNLOADING, 0f)
                    apiMutex.withLock {
                        throttle()
                        withContext(Dispatchers.IO) {
                            api.downloadPdf(entry.pdfUrl, paperFile) { p ->
                                openProgress = OpenProgress(OpenStage.DOWNLOADING, p)
                            }
                        }
                    }
                    ensureActive()
                }
                withContext(Dispatchers.IO) {
                    app.vocabDb.articleRecordDao().recordOpen(
                        url = entry.absUrl,
                        title = entry.title,
                        sourceId = sourceId,
                        sourceName = sourceName,
                        epubPath = epubFile.absolutePath,
                        now = System.currentTimeMillis(),
                    )
                }
                openProgress = null
                openingUrl = null
                // M12-A: the original PDF opens right after the download; the
                // reflow conversion catches up in the background (skipped
                // entirely when the EPUB already exists).
                startReflowConversionIfNeeded(entry, epubFile, paperFile)
                context.startActivity(
                    Intent(context, PaperReaderActivity::class.java)
                        .putExtra(PaperReaderActivity.EXTRA_PDF_PATH, paperFile.absolutePath)
                        .putExtra(PaperReaderActivity.EXTRA_ABS_URL, entry.absUrl)
                        .putExtra(PaperReaderActivity.EXTRA_TITLE, entry.title)
                        .putExtra(PaperReaderActivity.EXTRA_AUTHORS, entry.authors.joinToString(", "))
                        .putExtra(PaperReaderActivity.EXTRA_PRIMARY_CATEGORY, entry.primaryCategory)
                        .putExtra(PaperReaderActivity.EXTRA_PUBLISHED, entry.published)
                        .putExtra(ReaderActivity.EXTRA_EPUB_PATH, epubFile.absolutePath),
                )
            } catch (e: Exception) {
                // The cancel button nulled openProgress before cancelling the
                // job; anything arriving after that is the unwinding of a
                // user-cancelled open and stays silent.
                val cancelledByUser = openProgress == null
                cleanupPartialPaper(entry.absUrl)
                openProgress = null
                openingUrl = null
                if (e is CancellationException) throw e
                if (!cancelledByUser) {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.arxiv_open_failed),
                        actionLabel = context.getString(R.string.open_in_browser),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.absUrl)))
                    }
                }
            }
        }
    }

    /** Cancels the in-flight open and drops its ".part" right away. */
    fun cancelOpen() {
        openProgress = null
        openJob?.cancel()
        // Drop the in-flight ".part" right away; the unwinding download job
        // cleans up anything else via cleanupPartialPaper.
        val url = openingUrl
        openingUrl = null
        if (url != null) {
            scope.launch(Dispatchers.IO) {
                val paper = paperFileFor(context, url)
                File(paper.parentFile, paper.name + ".part").delete()
            }
        }
    }

    /** Removes a half-finished download for [url] after a failed/cancelled open. */
    private suspend fun cleanupPartialPaper(url: String) {
        withContext(NonCancellable + Dispatchers.IO) {
            val paper = paperFileFor(context, url)
            File(paper.parentFile, paper.name + ".part").delete()
            // The open path only downloads: reaching the catch block means the
            // PDF never completed (or the record write failed), so drop it.
            paper.delete()
        }
    }

    /**
     * M12-A: the reflow EPUB is no longer on the open path. When missing it
     * is converted on the application scope (survives the calling screen),
     * packed to a ".tmp" sibling renamed into place so a crash never leaves a
     * truncated EPUB masquerading as a cache hit. Failures stay silent (log
     * only) — the reader's 重排 button simply stays disabled.
     *
     * M12-B: the article source is arXiv's official HTML version (ar5iv, keeps
     * figures/MathML) when available, falling back to PDF text extraction.
     */
    private fun startReflowConversionIfNeeded(entry: ArxivEntry, epubFile: File, paperFile: File) {
        if (epubFile.isFile) return
        app.appScope.launch {
            try {
                val html = api.fetchHtmlVersion(entry.id)
                val article = if (html != null) {
                    ArxivHtml.toArticle(html, entry.title, entry.authors, entry.absUrl)
                } else {
                    PdfToArticle.convert(paperFile, entry.title, entry.authors, entry.absUrl)
                }
                val tmp = File(epubFile.parentFile, epubFile.name + ".tmp")
                try {
                    EpubPackager.pack(article, sourceName, tmp)
                    if (!tmp.renameTo(epubFile)) {
                        throw IllegalStateException("Cannot move $tmp into place as $epubFile")
                    }
                } finally {
                    tmp.delete()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.paper_reflow_ready, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.w("PaperOpener", "reflow conversion failed for ${entry.absUrl}", e)
            }
        }
    }
}

/** Creates a [PaperOpener] bound to the current composition's scope/context. */
@Composable
fun rememberPaperOpener(
    delaySeconds: Double,
    sourceId: String,
    sourceName: String,
): PaperOpener {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // The Activity context is required: open() calls startActivity without
    // FLAG_ACTIVITY_NEW_TASK. remember(context) rebinds after config changes.
    return remember(context) {
        PaperOpener(
            context = context,
            app = app,
            scope = scope,
            snackbarHostState = snackbarHostState,
            delaySeconds = delaySeconds,
            sourceId = sourceId,
            sourceName = sourceName,
        )
    }
}

/** Hosts the modal, non-dismissible download/parse progress dialog of [opener]. */
@Composable
fun PaperOpenDialog(opener: PaperOpener) {
    opener.openProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            text = {
                Column {
                    Text(
                        stringResource(
                            when (progress.stage) {
                                OpenStage.DOWNLOADING -> R.string.arxiv_downloading
                                OpenStage.PARSING -> R.string.arxiv_parsing
                            },
                        ),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (progress.stage == OpenStage.DOWNLOADING && progress.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = opener::cancelOpen) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

enum class OpenStage { DOWNLOADING, PARSING }

data class OpenProgress(val stage: OpenStage, val progress: Float)
