package dev.handypage.app.paper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dev.handypage.app.HandypageApp
import dev.handypage.app.R
import dev.handypage.app.ReaderActivity
import dev.handypage.app.SettingsActivity
import dev.handypage.app.ai.Prompts
import dev.handypage.app.dict.DictEntry
import dev.handypage.app.dict.WordForms
import dev.handypage.app.reader.ChatController
import dev.handypage.app.reader.VocabHighlight
import dev.handypage.app.reader.WordCardPanel
import dev.handypage.app.ui.AgentDrawer
import dev.handypage.app.ui.EditorialHairline
import dev.handypage.app.ui.EditorialSpacing
import dev.handypage.app.ui.EditorialStarIcon
import dev.handypage.app.ui.EditorialType
import dev.handypage.app.ui.FrauncesFamily
import dev.handypage.app.ui.HandypageTheme
import dev.handypage.app.ui.HpMotion
import dev.handypage.app.ui.applyHpAxisCloseTransition
import dev.handypage.app.ui.applyHpAxisOpenTransition
import dev.handypage.app.vocab.PaperStar
import dev.handypage.app.vocab.SavedSentence
import dev.handypage.app.vocab.SentenceText
import dev.handypage.app.vocab.VocabWord
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.jsoup.Jsoup

/**
 * M12-A original-layout paper reader: the downloaded arXiv PDF rendered by
 * the bundled pdf.js (assets/pdfjs/viewer.html) inside a WebView.
 *
 * The page and every asset are served from the virtual origin
 * `https://paper.local/` by [WebViewClient.shouldInterceptRequest] — no
 * network, no file:// URLs, `allowFileAccess` stays off:
 *  - `/assets/<p>`  → `assets/pdfjs/<p>` (viewer.html, pdf.mjs, cmaps, fonts)
 *  - `/papers/<n>`  → `filesDir/papers/<n>` (the downloaded PDF)
 *
 * Selections travel JS → Kotlin through the `Handypage` bridge, which keeps
 * the latest selection text in [selectionText]; the long-press system menu
 * is replaced by [PaperWebView]'s ActionMode with the same four actions as
 * the EPUB reader (查词 / 讲句子 / 收藏句子 / 复制). The word card, chat
 * sheet, vocab book and sentence book are all the shared components.
 *
 * M17 brings the M16 editorial chrome over from ReaderShell: the M3 TopAppBar
 * is gone in favour of an overlay newspaper top bar (arXiv/category/year
 * kicker + Fraunces title + reflow/star/AI actions over a 1dp hairline),
 * shown on open and toggled by clean single taps on the paper (the JS
 * `Handypage.onTap` bridge); the chat sheet rises behind the same 2dp solid
 * M17 brings the M16 editorial chrome over from ReaderShell: the M3 TopAppBar
 * is gone in favour of an overlay newspaper top bar (arXiv/category/year
 * kicker + Fraunces title + reflow/star/AI actions over a 1dp hairline),
 * shown on open and toggled by clean single taps on the paper (the JS
 * `Handypage.onTap` bridge); the chat sheet became the shared pure-animation
 * [AgentDrawer] overlay (no drag-to-dismiss; header × button or back closes).
 */
class PaperReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PDF_PATH = "dev.handypage.app.PDF_PATH"
        const val EXTRA_ABS_URL = "dev.handypage.app.ABS_URL"
        const val EXTRA_TITLE = "dev.handypage.app.PAPER_TITLE"
        /** M13 star bookkeeping: flat ", "-joined authors, category, date. */
        const val EXTRA_AUTHORS = "dev.handypage.app.PAPER_AUTHORS"
        const val EXTRA_PRIMARY_CATEGORY = "dev.handypage.app.PAPER_PRIMARY_CATEGORY"
        const val EXTRA_PUBLISHED = "dev.handypage.app.PAPER_PUBLISHED"

        private const val VIRTUAL_HOST = "paper.local"
        private const val ASSETS_PREFIX = "/assets/"
        private const val PAPERS_PREFIX = "/papers/"
    }

    private val app: HandypageApp
        get() = application as HandypageApp

    private var pdfPath = ""
    private var absUrl = ""
    private var paperTitle = ""
    private var paperAuthors = ""
    private var paperCategory = ""
    private var paperPublished = ""
    private var epubPath = ""

    private var webView: WebView? = null
    private var wordCardPanel: WordCardPanel? = null

    private val selectionText = mutableStateOf<String?>(null)
    private val chatController = mutableStateOf<ChatController?>(null)
    private val chatOpenRequests = mutableIntStateOf(0)
    private val epubReady = mutableStateOf(false)
    /** M17: bumped by the JS tap bridge; the shell toggles its top bar. */
    private val tapSignals = mutableIntStateOf(0)

    /** Last vocab-term JS pushed to the page; re-sent from onPageFinished. */
    private var lastVocabTermsJs: String? = null

    /** Back closes an open word panel before it can finish the activity. */
    private val wordCardBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            wordCardPanel?.hide()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // M22: axis-Y open transition (design-system.md §9; close twin in finish()).
        applyHpAxisOpenTransition()
        pdfPath = intent.getStringExtra(EXTRA_PDF_PATH).orEmpty()
        absUrl = intent.getStringExtra(EXTRA_ABS_URL).orEmpty()
        paperTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        paperAuthors = intent.getStringExtra(EXTRA_AUTHORS).orEmpty()
        paperCategory = intent.getStringExtra(EXTRA_PRIMARY_CATEGORY).orEmpty()
        paperPublished = intent.getStringExtra(EXTRA_PUBLISHED).orEmpty()
        epubPath = intent.getStringExtra(ReaderActivity.EXTRA_EPUB_PATH).orEmpty()
        if (!File(pdfPath).isFile) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this, wordCardBackCallback)
        setupChat()
        collectVocabTerms()
        setContent {
            HandypageTheme { PaperReaderScreen() }
        }
    }

    override fun finish() {
        applyHpAxisCloseTransition()
        super.finish()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ setup

    /**
     * Article-scoped chat session, same wiring as ReaderFragment — except the
     * article text comes from the reflow EPUB, which the background conversion
     * may not have produced yet; the ArticleTextTool appears once it exists.
     */
    private fun setupChat() {
        val controller = ChatController(
            app = app,
            context = this,
            articleKey = absUrl.ifBlank { File(pdfPath).name },
            articleTitle = paperTitle.ifBlank { File(pdfPath).nameWithoutExtension },
            articleUrl = absUrl,
            sourceName = "arXiv",
            articleTextProvider = { extractArticleText() },
            articleTextAvailable = { File(epubPath).isFile },
            scope = lifecycleScope,
        )
        controller.start()
        chatController.value = controller
    }

    /** Pushes the vocab-book term set into the page whenever it changes. */
    private fun collectVocabTerms() {
        lifecycleScope.launch {
            app.vocabDb.vocabWordDao().observeAll()
                .map { words ->
                    VocabHighlight.normalizeTerms(
                        words.flatMap { listOfNotNull(it.word, it.lemma) },
                    )
                }
                .distinctUntilChanged()
                .collect { terms ->
                    val js = "window.__setVocabTerms(${JSONArray(terms)})"
                    lastVocabTermsJs = js
                    webView?.evaluateJavascript(js, null)
                }
        }
    }

    // ------------------------------------------------------------------ UI

    @Composable
    private fun PaperReaderScreen() {
        var chromeVisible by remember { mutableStateOf(true) }
        var chatOpen by remember { mutableStateOf(false) }

        LaunchedEffect(chatOpenRequests.intValue) {
            if (chatOpenRequests.intValue > 0) chatOpen = true
        }
        LaunchedEffect(tapSignals.intValue) {
            if (tapSignals.intValue > 0) chromeVisible = !chromeVisible
        }
        BackHandler(enabled = chatOpen) { chatOpen = false }
        // The reflow EPUB is converted in the background while the PDF is
        // being read; poll and flip the 重排 button on once it lands.
        LaunchedEffect(Unit) {
            while (!File(epubPath).isFile) {
                delay(1500)
            }
            epubReady.value = true
        }

        val starred by remember(absUrl) {
            app.vocabDb.paperStarDao().observeUrls().map { absUrl in it }
        }.collectAsState(initial = false)

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context -> createWebView(context) },
                modifier = Modifier.fillMaxSize(),
            )
            // M8 word panel host: stays GONE until a lookup slides it up.
            AndroidView(
                factory = { context -> createWordPanelHost(context) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
            PaperTopBar(
                visible = chromeVisible,
                kicker = paperKicker(paperCategory, paperPublished),
                title = paperTitle.ifBlank { File(pdfPath).nameWithoutExtension },
                reflowReady = epubReady.value,
                starred = starred,
                showStar = absUrl.isNotBlank(),
                onReflow = { openReflow() },
                onToggleStar = { toggleStar() },
                onAi = { chatOpenRequests.intValue += 1 },
            )
            // M17: pure-animation overlay drawer — no drag gestures, the
            // header × button or system back closes it (see AgentDrawer).
            // M22: spec from HpMotion (§9 sheet 250/180 asymmetric).
            AnimatedVisibility(
                visible = chatOpen,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = HpMotion.sheetEnter(),
                exit = HpMotion.sheetExit(),
            ) {
                AgentDrawer(
                    controller = chatController.value,
                    onQuickGuide = null,
                    onOpenSettings = {
                        startActivity(
                            Intent(this@PaperReaderActivity, SettingsActivity::class.java)
                                .putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_AI),
                        )
                    },
                    onClose = { chatOpen = false },
                )
            }
        }
    }

    /**
     * Newspaper top bar (M17, mirrors ReaderShell's M16 bar): kicker (11sp
     * uppercase meta) over a single-line Fraunces paper title, with
     * reflow / star / AI actions at the trailing edge and a 1dp hairline
     * below. Slides up out of view for immersive reading; a clean single
     * tap on the paper (JS bridge) brings it back. No back arrow — the
     * system back gesture closes the activity, same as the EPUB reader.
     *
     * The reflow action shares its AutoStories glyph with the EPUB reader's
     * 导读: in both shells that icon means "read this as an article".
     */
    @Composable
    private fun BoxScope.PaperTopBar(
        visible: Boolean,
        kicker: String,
        title: String,
        reflowReady: Boolean,
        starred: Boolean,
        showStar: Boolean,
        onReflow: () -> Unit,
        onToggleStar: () -> Unit,
        onAi: () -> Unit,
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = HpMotion.barEnter(),
            exit = HpMotion.barExit(),
        ) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        modifier = Modifier
                            .statusBarsPadding()
                            .fillMaxWidth()
                            .padding(start = EditorialSpacing.lg, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = EditorialSpacing.sm),
                        ) {
                            Text(
                                text = kicker.uppercase(Locale.US),
                                style = EditorialType.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = title,
                                fontFamily = FrauncesFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        IconButton(onClick = onReflow, enabled = reflowReady) {
                            Icon(
                                imageVector = Icons.Filled.AutoStories,
                                contentDescription = stringResource(R.string.paper_reflow),
                                tint = if (reflowReady) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            )
                        }
                        if (showStar) {
                            IconButton(onClick = onToggleStar) {
                                // M17: double coding — filled ink star =
                                // starred, hollow grey star = not.
                                // M22: 120ms crossfade via EditorialStarIcon.
                                EditorialStarIcon(
                                    starred = starred,
                                    contentDescription = stringResource(R.string.local_stars),
                                )
                            }
                        }
                        IconButton(onClick = onAi) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = stringResource(R.string.agent_panel_title),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    EditorialHairline()
                }
            }
        }
    }

    // ------------------------------------------------------------------ WebView

    private fun createWebView(context: Context): WebView =
        PaperWebView(context).apply {
            // The selection action mode's four menu entries run the same
            // handlers the old Compose floating bar did.
            actions = object : PaperWebView.SelectionActions {
                override fun onLookup() = lookupSelection()
                override fun onExplain() = explainSelection()
                override fun onSaveSentence() = saveSelection()
                override fun onCopy() = copySelection()
            }
            settings.javaScriptEnabled = true
            // Pinch zoom for dense two-column papers; no on-screen +/- buttons.
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // Everything arrives via the virtual origin; no file/content access.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val uri = request.url
                    if (uri.host != VIRTUAL_HOST) return null
                    val path = uri.path ?: return null
                    return when {
                        path.startsWith(ASSETS_PREFIX) ->
                            assetResponse(path.removePrefix(ASSETS_PREFIX))
                        path.startsWith(PAPERS_PREFIX) ->
                            paperResponse(path.removePrefix(PAPERS_PREFIX))
                        else -> null
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    // A (re)loaded page lost its in-page term list: push again.
                    lastVocabTermsJs?.let { view.evaluateJavascript(it, null) }
                }
            }
            addJavascriptInterface(PaperBridge(), "Handypage")
            val fileUrl = "https://$VIRTUAL_HOST$PAPERS_PREFIX${File(pdfPath).name}"
            loadUrl(
                "https://$VIRTUAL_HOST${ASSETS_PREFIX}viewer.html?file=" +
                    URLEncoder.encode(fileUrl, "UTF-8"),
            )
            webView = this
        }

    /** Serves `assets/pdfjs/<path>`; rejects traversal. */
    private fun assetResponse(path: String): WebResourceResponse? {
        if (path.isBlank() || path.contains("..")) return null
        return try {
            WebResourceResponse(mimeFor(path), encodingFor(path), assets.open("pdfjs/$path"))
        } catch (e: Exception) {
            null
        }
    }

    /** Serves `filesDir/papers/<name>`; the name must be a flat file name. */
    private fun paperResponse(name: String): WebResourceResponse? {
        if (name.isBlank() || name.contains('/') || name.contains("..")) return null
        val file = File(filesDir, "papers/$name")
        if (!file.isFile) return null
        return WebResourceResponse("application/pdf", null, file.inputStream())
    }

    private fun mimeFor(path: String): String =
        when (path.substringAfterLast('.', "").lowercase()) {
            "mjs", "js" -> "text/javascript"
            "html" -> "text/html"
            "css" -> "text/css"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "woff", "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }

    private fun encodingFor(path: String): String? =
        if (mimeFor(path).startsWith("text/")) "UTF-8" else null

    /** JS → Kotlin bridge (`window.Handypage`); calls arrive on a binder thread. */
    inner class PaperBridge {
        @JavascriptInterface
        fun onSelection(text: String) {
            runOnUiThread {
                val trimmed = text.trim()
                if (trimmed.isNotEmpty()) selectionText.value = trimmed
            }
        }

        @JavascriptInterface
        fun onCleared() {
            runOnUiThread { selectionText.value = null }
        }

        /** M17: a clean single tap on the paper toggles the top bar. */
        @JavascriptInterface
        fun onTap() {
            runOnUiThread { tapSignals.intValue += 1 }
        }

        @JavascriptInterface
        fun onError(message: String) {
            runOnUiThread {
                toast(getString(R.string.paper_load_error) + ": " + message)
            }
        }
    }

    private fun createWordPanelHost(context: Context): FrameLayout {
        val frame = FrameLayout(context)
        // Same backdrop as the EPUB reader's word_card_container (M16: the
        // drawable carries the 2dp ink top rule; elevation stays low).
        frame.setBackgroundResource(R.drawable.word_card_panel_bg)
        frame.elevation = 4f * resources.displayMetrics.density
        val panel = WordCardPanel(frame)
        panel.onVisibilityChanged = { visible ->
            wordCardBackCallback.isEnabled = visible
        }
        wordCardPanel = panel
        return frame
    }

    // ------------------------------------------------------- selection actions

    /** Drops the in-page selection and returns its latest JS-reported text. */
    private fun consumeSelection(): String? {
        val text = selectionText.value ?: return null
        selectionText.value = null
        webView?.evaluateJavascript("window.__clearSelection()", null)
        return text
    }

    private fun lookupSelection() {
        val text = consumeSelection() ?: return
        lifecycleScope.launch {
            val dictionary = try {
                // First lookup ever extracts the ~86 MB database from assets.
                app.requireDictionary()
            } catch (e: Exception) {
                toast(getString(R.string.dict_init_failed))
                return@launch
            }
            val entry = withContext(Dispatchers.IO) { dictionary.lookup(text) }
            // The whole selection doubles as the word's sentence context.
            showWordCard(text, entry, SentenceText.normalize(text))
        }
    }

    /** M8 word card, mirroring ReaderFragment.showWordCard. */
    private fun showWordCard(raw: String, entry: DictEntry?, sentence: String) {
        val panel = wordCardPanel ?: return
        val displayWord = entry?.word ?: WordForms.clean(raw)
        lifecycleScope.launch {
            val alreadySaved = withContext(Dispatchers.IO) {
                app.vocabDb.vocabWordDao().exists(displayWord, absUrl)
            }
            panel.show(
                rawWord = displayWord,
                entry = entry,
                alreadySaved = alreadySaved,
                onAddToVocab = { saveToVocab(raw, entry, sentence) },
                onAskAI = {
                    chatController.value?.let { controller ->
                        controller.send(
                            prompt = Prompts.explainWord(displayWord, sentence).last().content,
                            display = getString(R.string.ai_word_title, displayWord),
                        )
                        requestChatOpen()
                    }
                },
            )
        }
    }

    private fun saveToVocab(raw: String, entry: DictEntry?, sentence: String) {
        val shown = entry?.lemmaEntry ?: entry
        val word = VocabWord(
            word = entry?.word ?: WordForms.clean(raw),
            lemma = entry?.lemmaEntry?.word ?: entry?.lemma,
            phonetic = shown?.phonetic,
            translation = shown?.translation,
            definition = shown?.definition,
            sentence = sentence.ifBlank { null },
            articleUrl = absUrl,
            sourceName = "arXiv",
            addedAt = System.currentTimeMillis(),
        )
        lifecycleScope.launch {
            val rowId = withContext(Dispatchers.IO) { app.vocabDb.vocabWordDao().insert(word) }
            toast(getString(if (rowId >= 0) R.string.vocab_added else R.string.vocab_already_saved))
            // The panel stays open; the new term joins the in-page weak
            // highlights via the vocab-terms Flow automatically.
            wordCardPanel?.markSaved()
        }
    }

    private fun explainSelection() {
        val text = consumeSelection() ?: return
        val controller = chatController.value ?: return
        controller.send(
            prompt = Prompts.explainSentence(text, "").last().content,
            display = getString(R.string.action_explain_sentence) + ": " + text.take(80),
        )
        requestChatOpen()
    }

    private fun saveSelection() {
        val text = SentenceText.normalize(consumeSelection() ?: return)
        if (text.isEmpty()) return
        lifecycleScope.launch {
            val rowId = withContext(Dispatchers.IO) {
                app.vocabDb.savedSentenceDao().insert(
                    SavedSentence(
                        text = text,
                        articleUrl = absUrl,
                        articleTitle = paperTitle,
                        sourceName = "arXiv",
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            }
            toast(
                getString(
                    if (rowId >= 0) R.string.sentence_saved else R.string.sentence_already_saved,
                ),
            )
        }
    }

    private fun copySelection() {
        val text = consumeSelection() ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("handypage", text))
        toast(getString(R.string.copied_to_clipboard))
    }

    // ------------------------------------------------------------------ misc

    /** 重排: hand the (now generated) reflow EPUB to the Readium reader. */
    private fun openReflow() {
        if (!File(epubPath).isFile) return
        startActivity(
            Intent(this, ReaderActivity::class.java)
                .putExtra(ReaderActivity.EXTRA_EPUB_PATH, epubPath),
        )
    }

    /** M13: toggles this paper in the 收藏 book (paper_stars table). */
    private fun toggleStar() {
        if (absUrl.isBlank()) return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = app.vocabDb.paperStarDao()
                if (dao.exists(absUrl)) {
                    dao.deleteByUrl(absUrl)
                } else {
                    dao.insert(
                        PaperStar(
                            url = absUrl,
                            title = paperTitle,
                            authors = paperAuthors,
                            primaryCategory = paperCategory,
                            published = paperPublished,
                            starredAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    private fun requestChatOpen() {
        chatOpenRequests.intValue += 1
    }

    /**
     * Article text for the agent's ArticleTextTool, pulled from the reflow
     * EPUB (same zip+Jsoup recipe as ReaderFragment.extractArticleText);
     * "" while the background conversion has not produced it yet.
     */
    private suspend fun extractArticleText(): String {
        val file = File(epubPath)
        if (!file.isFile) return ""
        return withContext(Dispatchers.IO) {
            ZipFile(file).use { zip ->
                zip.entries().toList()
                    .filter {
                        it.name.endsWith(".xhtml") || it.name.endsWith(".html") ||
                            it.name.endsWith(".htm")
                    }
                    .sortedBy { it.name }
                    .joinToString("\n\n") { entry ->
                        zip.getInputStream(entry).use { input ->
                            Jsoup.parse(input, null, "").text()
                        }
                    }
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
