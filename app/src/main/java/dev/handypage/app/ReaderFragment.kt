package dev.handypage.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ActionMode
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import dev.handypage.app.ai.Prompts
import dev.handypage.app.dict.DictEntry
import dev.handypage.app.dict.WordForms
import dev.handypage.app.local.ArticleRecord
import dev.handypage.app.reader.ChatController
import dev.handypage.app.reader.ReaderSettings
import dev.handypage.app.reader.ReaderSettingsStore
import dev.handypage.app.reader.SentenceHighlight
import dev.handypage.app.reader.VocabHighlight
import dev.handypage.app.reader.WordActionModeCallback
import dev.handypage.app.reader.WordCardPanel
import dev.handypage.app.reader.toEpubPreferences
import dev.handypage.app.vocab.ArticleStar
import dev.handypage.app.vocab.SavedSentence
import dev.handypage.app.vocab.SentenceText
import dev.handypage.app.vocab.VocabWord
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUri
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Opens an EPUB file and displays it in an [EpubNavigatorFragment].
 *
 * The file to open comes from the [ARG_EPUB_PATH] argument; when absent, the
 * M0 default (bundled `assets/demo.epub` copied to filesDir) is used, so the
 * original demo behavior is preserved.
 *
 * Progress text is shown while loading and an error text on failure, so a
 * runtime failure is diagnosable from a screenshot.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderFragment : Fragment(R.layout.fragment_reader), EpubNavigatorFragment.Listener {

    companion object {
        const val ARG_EPUB_PATH = "dev.handypage.app.EPUB_PATH"
        private const val NAVIGATOR_TAG = "EpubNavigatorFragment"

        fun newInstance(epubPath: String?): ReaderFragment =
            ReaderFragment().apply {
                if (epubPath != null) {
                    arguments = Bundle().apply { putString(ARG_EPUB_PATH, epubPath) }
                }
            }
    }

    private val app: HandypageApp
        get() = requireActivity().application as HandypageApp

    private var statusText: TextView? = null

    /** M5 chat panel controller; created once the book (and its metadata) is open. */
    private var chatController: ChatController? = null

    /** M8 persistent bottom word panel; replaces the M2a modal word dialog. */
    private var wordCardPanel: WordCardPanel? = null

    /** Running M8 vocab-highlight refresh, cancelled on re-trigger/destroy. */
    private var vocabHighlightJob: Job? = null

    /** Running M20 sentence-highlight refresh, cancelled on re-trigger/destroy. */
    private var sentenceHighlightJob: Job? = null

    /**
     * Article identity for the open book, resolved once and cached. Our EPUBs
     * carry the article URL as dc:source, but Readium does not surface it
     * (not a first-class RWPM property — the OPF adapter drops it), so the
     * canonical lookup is the reading-record table keyed by the EPUB path —
     * the same URL that produced the file's sha256 name. The metadata read
     * stays as a fast path for books whose source does survive.
     */
    private var currentArticleUrl: String? = null

    /** M21: the open book's reading record (canonical identity), resolved once. */
    private var currentRecord: ArticleRecord? = null
    private var recordResolved = false

    private suspend fun articleRecord(): ArticleRecord? {
        if (recordResolved) return currentRecord
        val book = app.openBook ?: return null
        currentRecord = withContext(Dispatchers.IO) {
            app.vocabDb.articleRecordDao().recordForEpubPath(book.path)
        }
        recordResolved = true
        return currentRecord
    }

    private suspend fun articleUrl(): String {
        currentArticleUrl?.let { return it }
        val book = app.openBook ?: return ""
        val fromMetadata = book.publication.metadata["source"] as? String
        val resolved = if (!fromMetadata.isNullOrEmpty()) {
            fromMetadata
        } else {
            articleRecord()?.url.orEmpty()
        }
        currentArticleUrl = resolved
        return resolved
    }

    /** Back closes an open word panel before it can finish the activity. */
    private val wordCardBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            wordCardPanel?.hide()
        }
    }

    private val settingsStore: ReaderSettingsStore by lazy {
        ReaderSettingsStore(requireContext())
    }

    /** Last applied reader display settings; seeded from SharedPreferences. */
    private var readerSettings = ReaderSettings()

    private val demoPath: String
        get() = File(requireContext().filesDir, "demo.epub").absolutePath

    private val epubPath: String
        get() = arguments?.getString(ARG_EPUB_PATH)?.takeIf { it.isNotBlank() } ?: demoPath

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore path (e.g. configuration change): the book is already open in
        // the Application holder, so the fragment factory must be installed
        // BEFORE super.onCreate for the FragmentManager to be able to
        // re-instantiate the child EpubNavigatorFragment from its saved state.
        app.openBook?.takeIf { it.path == epubPath }?.let { installNavigatorFactory(it.publication) }
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusText = view.findViewById(R.id.status_text)
        // Re-resolve article identity for this view (book may have changed).
        currentArticleUrl = null
        currentRecord = null
        recordResolved = false

        val panel = WordCardPanel(view.findViewById<FrameLayout>(R.id.word_card_container))
        panel.onVisibilityChanged = { visible ->
            wordCardBackCallback.isEnabled = visible
            // While the panel is open its buttons live in the same bottom
            // band as ReaderShell's summon zone; the activity hides that
            // zone for as long as the panel is up.
            (activity as? ReaderActivity)?.onWordPanelVisibilityChanged(visible)
        }
        wordCardPanel = panel
        requireActivity().onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, wordCardBackCallback)

        app.openBook?.takeIf { it.path == epubPath }?.let {
            // This book is already open: the navigator fragment is either
            // restored by the FragmentManager or needs to be added once.
            showNavigator()
            setupChatPanel()
            refreshVocabHighlights()
            refreshSentenceHighlights()
            publishStarState()
            installDoubleTapLookup()
            return
        }

        showStatus("Opening book…")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val book = openEpub(epubPath)
                // Close the previously open book (Asset is Closeable in
                // Readium 3.x; Publication is not) before replacing it.
                app.openBook?.let { old ->
                    try {
                        old.asset.close()
                    } catch (_: Exception) {
                        // Best effort; a leaked zip handle is not fatal here.
                    }
                }
                app.openBook = book
                installNavigatorFactory(book.publication)
                showNavigator(replaceExisting = true)
                setupChatPanel()
                refreshVocabHighlights()
                refreshSentenceHighlights()
                publishStarState()
                installDoubleTapLookup()
            } catch (e: Exception) {
                showStatus("Failed to open book:\n\n${e.stackTraceToString()}")
            }
        }
    }

    override fun onDestroyView() {
        statusText = null
        wordCardPanel = null
        vocabHighlightJob?.cancel()
        vocabHighlightJob = null
        sentenceHighlightJob?.cancel()
        sentenceHighlightJob = null
        (activity as? ReaderActivity)?.onWordPanelVisibilityChanged(false)
        super.onDestroyView()
    }

    private fun installNavigatorFactory(publication: Publication) {
        readerSettings = settingsStore.load()
        // M16: keep the Compose settings panel's mirror of the settings fresh.
        (activity as? ReaderActivity)?.onReaderSettingsLoaded(readerSettings)
        val navigatorFactory = EpubNavigatorFactory(
            publication = publication,
            configuration = EpubNavigatorFactory.Configuration(
                defaults = EpubDefaults(pageMargins = 1.4)
            )
        )
        childFragmentManager.fragmentFactory =
            navigatorFactory.createFragmentFactory(
                initialLocator = null,
                // EpubDefaults has no theme field, so the whole restored set
                // goes through initialPreferences.
                initialPreferences = readerSettings.toEpubPreferences(),
                listener = this,
                configuration = EpubNavigatorFragment.Configuration(
                    selectionActionModeCallback = WordActionModeCallback(
                        lookupLabel = getString(R.string.action_lookup_word),
                        explainLabel = getString(R.string.action_explain_sentence),
                        saveLabel = getString(R.string.action_save_sentence),
                        copyLabel = getString(R.string.action_copy),
                        onLookup = { mode -> onSelectionAction(mode, copyOnly = false) },
                        onExplain = { mode -> onExplainAction(mode) },
                        onSaveSentence = { mode -> onSaveSentenceAction(mode) },
                        onCopy = { mode -> onSelectionAction(mode, copyOnly = true) },
                    ),
                ),
            )
    }

    /**
     * Handles the floating selection menu: grabs the current selection from
     * the navigator, clears it, closes the action mode, then either copies
     * the highlight or runs the dictionary lookup and shows the word card.
     */
    private fun onSelectionAction(mode: ActionMode, copyOnly: Boolean) {
        val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)
            as? SelectableNavigator ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val selection = try {
                navigator.currentSelection()
            } catch (e: Exception) {
                null
            }
            val text = selection?.locator?.text
            val highlight = text?.highlight?.trim().orEmpty()
            navigator.clearSelection()
            mode.finish()
            if (highlight.isEmpty()) return@launch

            if (copyOnly) {
                val clipboard = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("handypage", highlight))
                toast(getString(R.string.copied_to_clipboard))
            } else {
                lookupAndShowCard(highlight, text)
            }
        }
    }

    private suspend fun lookupAndShowCard(raw: String, text: Locator.Text?) {
        val dictionary = try {
            // First lookup ever extracts the ~86 MB database from assets.
            app.requireDictionary()
        } catch (e: Exception) {
            toast(getString(R.string.dict_init_failed))
            return
        }
        val entry = withContext(Dispatchers.IO) { dictionary.lookup(raw) }
        val sentence = WordForms.sentence(text?.before, text?.highlight, text?.after)
        showWordCard(raw, entry, sentence)
    }

    /**
     * M3 "讲句子": captures the current selection (the highlighted sentence/
     * phrase plus its surrounding context), clears it, then streams an
     * [Prompts.explainSentence] answer into the AI panel.
     */
    private fun onExplainAction(mode: ActionMode) {
        val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)
            as? SelectableNavigator ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val selection = try {
                navigator.currentSelection()
            } catch (e: Exception) {
                null
            }
            val text = selection?.locator?.text
            val highlight = text?.highlight?.trim().orEmpty()
            navigator.clearSelection()
            mode.finish()
            if (highlight.isEmpty()) return@launch

            // Context keeps the highlight inline so the model sees the
            // sentence verbatim inside its surroundings (avoids "the word
            // does not appear in the context" confusion).
            val context = WordForms.sentence(text?.before, text?.highlight, text?.after)
            val controller = chatController ?: return@launch
            controller.send(
                prompt = Prompts.explainSentence(highlight, context).last().content,
                display = getString(R.string.action_explain_sentence) + ": " +
                    highlight.take(80),
            )
            requestChatOpen()
        }
    }

    /**
     * M10 "收藏句子": captures the current selection, normalizes it with
     * [SentenceText], and inserts it into the sentence book — no panel, no
     * interruption, just a toast. Annotations (manual/AI) are edited later
     * from the 本机 tab's 好句 section.
     */
    private fun onSaveSentenceAction(mode: ActionMode) {
        val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)
            as? SelectableNavigator ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val selection = try {
                navigator.currentSelection()
            } catch (e: Exception) {
                null
            }
            val text = SentenceText.normalize(selection?.locator?.text?.highlight.orEmpty())
            navigator.clearSelection()
            mode.finish()
            if (text.isEmpty()) return@launch

            // Title/creator come from the OPF metadata the EpubPackager
            // embeds; the article URL comes from the reading record, because
            // Readium drops dc:source in the RWPM conversion (see articleUrl()).
            val metadata = app.openBook?.publication?.metadata
            val sentence = SavedSentence(
                text = text,
                articleUrl = articleUrl(),
                articleTitle = metadata?.title ?: "",
                sourceName = metadata?.authors?.firstOrNull()?.name ?: "",
                addedAt = System.currentTimeMillis(),
            )
            val rowId = withContext(Dispatchers.IO) {
                app.vocabDb.savedSentenceDao().insert(sentence)
            }
            toast(
                getString(
                    if (rowId >= 0) R.string.sentence_saved else R.string.sentence_already_saved,
                ),
            )
            // A fresh save underlines itself right away (M20).
            if (rowId >= 0) refreshSentenceHighlights()
        }
    }

    // --- M21 article star ---------------------------------------------------

    /**
     * Publishes the reader star state to the activity: null hides the star
     * (demo book without a reading record; arXiv reflow EPUBs — the paper
     * star lives in the PDF view), true/false paints it filled/outlined.
     */
    private fun publishStarState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val record = articleRecord()
            val state = when {
                record == null -> null
                record.sourceId == "arxiv" -> null
                else -> withContext(Dispatchers.IO) {
                    app.vocabDb.articleStarDao().exists(record.url)
                }
            }
            (activity as? ReaderActivity)?.onStarState(state)
        }
    }

    /** Reader top-bar star: toggles the article star for the open book. */
    fun onStarToggle() {
        viewLifecycleOwner.lifecycleScope.launch {
            val record = articleRecord() ?: return@launch
            if (record.sourceId == "arxiv") return@launch
            val dao = app.vocabDb.articleStarDao()
            val starred = withContext(Dispatchers.IO) {
                if (dao.exists(record.url)) {
                    dao.deleteByUrl(record.url)
                    false
                } else {
                    dao.insert(
                        ArticleStar(
                            url = record.url,
                            title = record.title,
                            sourceId = record.sourceId,
                            sourceName = record.sourceName,
                            starredAt = System.currentTimeMillis(),
                        ),
                    )
                    true
                }
            }
            (activity as? ReaderActivity)?.onStarState(starred)
        }
    }

    /**
     * "导读": extracts the open book's chapter text (xhtml straight from the
     * EPUB zip on filesDir, tags stripped with Jsoup) and sends the
     * [Prompts.articleGuide] template into the article's chat session.
     */
    fun onGuideClick() {
        val book = app.openBook
        if (book == null) {
            toast(getString(R.string.ai_no_book))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val articleText = try {
                withContext(Dispatchers.IO) { extractArticleText(book.path) }
            } catch (e: Exception) {
                toast(getString(R.string.ai_guide_failed))
                return@launch
            }
            if (articleText.isBlank()) {
                toast(getString(R.string.ai_guide_failed))
                return@launch
            }
            val controller = chatController ?: return@launch
            controller.send(
                prompt = Prompts.articleGuide(articleText).last().content,
                display = getString(R.string.agent_display_guide),
            )
            requestChatOpen()
        }
    }

    /**
     * Creates the article-scoped chat session controller and hands it to the
     * activity, which mounts the Compose chat sheet. Called once the book
     * (and thus its OPF metadata) is open.
     */
    private fun setupChatPanel() {
        if (chatController != null) return
        val book = app.openBook ?: return
        // articleUrl() is suspend (records-table lookup), so the controller
        // is built in a coroutine; a second call simply re-checks the guard.
        viewLifecycleOwner.lifecycleScope.launch {
            if (chatController != null) return@launch
            val metadata = book.publication.metadata
            val articleUrl = articleUrl()
            val controller = ChatController(
                app = app,
                context = requireContext(),
                articleKey = articleUrl.ifBlank { File(book.path).name },
                articleTitle = metadata.title ?: File(book.path).nameWithoutExtension,
                articleUrl = articleUrl,
                sourceName = metadata.authors.firstOrNull()?.name ?: "",
                articleTextProvider = {
                    val path = app.openBook?.path.orEmpty()
                    if (path.isBlank()) {
                        ""
                    } else {
                        withContext(Dispatchers.IO) { extractArticleText(path) }
                    }
                },
                scope = viewLifecycleOwner.lifecycleScope,
            )
            chatController = controller
            controller.start()
            (activity as? ReaderActivity)?.onChatControllerReady(controller)
            // M16: feed the Compose top bar (kicker = source, title = article).
            (activity as? ReaderActivity)?.onBookOpened(
                title = metadata.title ?: File(book.path).nameWithoutExtension,
                kicker = metadata.authors.firstOrNull()?.name
                    ?: File(book.path).nameWithoutExtension,
            )
        }
    }

    /** Asks the activity to expand the chat sheet (selection actions, 导读, 问 AI). */
    private fun requestChatOpen() {
        // The sheet covers the screen; close the word panel so Back handling
        // and screen real estate stay unambiguous underneath it.
        wordCardPanel?.hide()
        (activity as? ReaderActivity)?.requestChatOpen()
    }

    private fun extractArticleText(epubPath: String): String =
        ZipFile(File(epubPath)).use { zip ->
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

    /**
     * M8: shows the lookup result in the persistent bottom [WordCardPanel]
     * instead of a dialog — reading continues underneath. The vocab button
     * pre-renders its saved state when the (word, article) pair is already in
     * the book, so the panel doubles as a "known word" indicator.
     */
    private fun showWordCard(raw: String, entry: DictEntry?, sentence: String) {
        if (view == null) return
        val panel = wordCardPanel ?: return
        val displayWord = entry?.word ?: WordForms.clean(raw)
        viewLifecycleOwner.lifecycleScope.launch {
            val articleUrl = articleUrl()
            val alreadySaved = withContext(Dispatchers.IO) {
                app.vocabDb.vocabWordDao().exists(displayWord, articleUrl)
            }
            panel.show(
                rawWord = displayWord,
                entry = entry,
                alreadySaved = alreadySaved,
                onAddToVocab = { saveToVocab(raw, entry, sentence) },
                onAskAI = {
                    chatController?.let { controller ->
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
        // Source name comes from OPF dc:creator; the article URL comes from
        // the reading record, because Readium drops dc:source (see articleUrl()).
        val metadata = app.openBook?.publication?.metadata
        val sourceName = metadata?.authors?.firstOrNull()?.name ?: ""
        viewLifecycleOwner.lifecycleScope.launch {
            val word = VocabWord(
                word = entry?.word ?: WordForms.clean(raw),
                lemma = entry?.lemmaEntry?.word ?: entry?.lemma,
                phonetic = shown?.phonetic,
                translation = shown?.translation,
                definition = shown?.definition,
                sentence = sentence.ifBlank { null },
                articleUrl = articleUrl(),
                sourceName = sourceName,
                addedAt = System.currentTimeMillis(),
            )
            val rowId = withContext(Dispatchers.IO) { app.vocabDb.vocabWordDao().insert(word) }
            toast(getString(if (rowId >= 0) R.string.vocab_added else R.string.vocab_already_saved))
            // The panel stays open (reading is not interrupted); the button
            // flips to its saved state and the new term joins the in-article
            // weak highlights right away.
            wordCardPanel?.markSaved()
            refreshVocabHighlights()
        }
    }

    /**
     * M8 vocab weak highlight (DESIGN.md §4.11): finds every saved vocab
     * term (words + lemmas) in the open publication via Readium whole-word
     * search and paints low-alpha highlight decorations. Re-applying the
     * [VocabHighlight.GROUP] group replaces it, so saves and theme changes
     * just re-run this. No-ops when the book or navigator isn't ready.
     */
    private fun refreshVocabHighlights() {
        vocabHighlightJob?.cancel()
        val book = app.openBook ?: return
        vocabHighlightJob = viewLifecycleOwner.lifecycleScope.launch {
            val terms = try {
                VocabHighlight.normalizeTerms(
                    withContext(Dispatchers.IO) { app.vocabDb.vocabWordDao().highlightTerms() },
                )
            } catch (e: Exception) {
                return@launch
            }
            val tint = VocabHighlight.tintForTheme(
                readerSettings.normalizedThemeName,
                readerSettings.normalizedHighlightName,
            )
            val decorations = withContext(Dispatchers.Default) {
                buildVocabDecorations(book.publication, terms, tint)
            }
            val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)
                as? DecorableNavigator ?: return@launch
            try {
                navigator.applyDecorations(decorations, VocabHighlight.GROUP)
            } catch (e: Exception) {
                // Navigator mid-teardown; highlights are best-effort.
            }
        }
    }

    private suspend fun buildVocabDecorations(
        publication: Publication,
        terms: List<String>,
        tint: Int,
    ): List<Decoration> {
        val decorations = ArrayList<Decoration>()
        for (term in terms) {
            // search() returns a nullable iterator (throws on hard failure)
            // in Readium 3.3, while next() pages Try<LocatorCollection>.
            val iterator = try {
                publication.search(term, SearchService.Options(wholeWord = true))
            } catch (e: Exception) {
                null
            } ?: continue
            try {
                var found = 0
                while (found < VocabHighlight.MAX_LOCATORS_PER_TERM) {
                    val collection = iterator.next()?.getOrNull() ?: break
                    for (locator in collection.locators) {
                        val text = locator.text
                        // wholeWord is requested but re-checked: a backend
                        // falling back to substring search must not light up
                        // "art" inside "start".
                        if (!VocabHighlight.isWholeWordMatch(
                                text.before, text.highlight, text.after, term,
                            )
                        ) {
                            continue
                        }
                        decorations += Decoration(
                            id = "vocab:${decorations.size}",
                            locator = locator,
                            style = Decoration.Style.Highlight(tint = tint),
                            extras = emptyMap(),
                        )
                        found++
                        if (found >= VocabHighlight.MAX_LOCATORS_PER_TERM) break
                    }
                }
            } finally {
                iterator.close()
            }
        }
        return decorations
    }

    /**
     * M20 saved-sentence weak highlight (DESIGN.md §4.13 follow-up): finds
     * every sentence saved from the open article ([articleUrl] match) via
     * Readium case-insensitive search and paints underline decorations in the
     * current palette hue — distinct from the vocab background tint, so the
     * two layer cleanly. Re-applying the [SentenceHighlight.GROUP] group
     * replaces it. No-ops when the book or navigator isn't ready, or when the
     * book carries no source URL.
     */
    private fun refreshSentenceHighlights() {
        sentenceHighlightJob?.cancel()
        val book = app.openBook ?: return
        sentenceHighlightJob = viewLifecycleOwner.lifecycleScope.launch {
            val url = articleUrl()
            if (url.isEmpty()) return@launch
            val queries = try {
                SentenceHighlight.normalizeQueries(
                    withContext(Dispatchers.IO) {
                        app.vocabDb.savedSentenceDao().textsForArticle(url)
                    },
                )
            } catch (e: Exception) {
                return@launch
            }
            val tint = SentenceHighlight.tintForTheme(
                readerSettings.normalizedThemeName,
                readerSettings.normalizedHighlightName,
            )
            val decorations = withContext(Dispatchers.Default) {
                buildSentenceDecorations(book.publication, queries, tint)
            }
            val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)
                as? DecorableNavigator ?: return@launch
            try {
                navigator.applyDecorations(decorations, SentenceHighlight.GROUP)
            } catch (e: Exception) {
                // Navigator mid-teardown; highlights are best-effort.
            }
        }
    }

    private suspend fun buildSentenceDecorations(
        publication: Publication,
        queries: List<String>,
        tint: Int,
    ): List<Decoration> {
        val decorations = ArrayList<Decoration>()
        for (query in queries) {
            // Default options: the ICU backend is case/diacritic-insensitive,
            // wholeWord stays off — a sentence is its own anchor.
            val iterator = try {
                publication.search(query, SearchService.Options())
            } catch (e: Exception) {
                null
            } ?: continue
            try {
                var found = 0
                while (found < SentenceHighlight.MAX_LOCATORS_PER_SENTENCE) {
                    val collection = iterator.next()?.getOrNull() ?: break
                    for (locator in collection.locators) {
                        // Guard against collator folding surprises: the hit
                        // must be the same sentence after normalization.
                        if (!SentenceHighlight.isMatch(locator.text.highlight, query)) {
                            continue
                        }
                        decorations += Decoration(
                            id = "sentences:${decorations.size}",
                            locator = locator,
                            style = Decoration.Style.Underline(tint = tint),
                            extras = emptyMap(),
                        )
                        found++
                        if (found >= SentenceHighlight.MAX_LOCATORS_PER_SENTENCE) break
                    }
                }
            } finally {
                iterator.close()
            }
        }
        return decorations
    }

    /**
     * Persists [settings] and live-applies them to the navigator, when it is
     * already attached. Called on every settings-panel change (M16: the
     * Compose panel in ReaderShell replaces the old AppCompat dialog).
     */
    fun applyReaderSettings(settings: ReaderSettings) {
        val themeChanged = settings.normalizedThemeName != readerSettings.normalizedThemeName
        // M9: flipping justify also flips publisherStyles (Readium advanced
        // mode), which reloads the resource and drops applied decorations.
        val layoutChanged = settings.justified != readerSettings.justified
        // M16: a new highlight palette must be re-painted, like a theme flip.
        val highlightChanged =
            settings.normalizedHighlightName != readerSettings.normalizedHighlightName
        readerSettings = settings
        settingsStore.save(settings)
        (childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment)
            ?.submitPreferences(settings.toEpubPreferences())
        // Decoration tints are fixed at apply time (theme/palette), and some
        // preference flips reload the resource — re-paint in both cases.
        if (themeChanged || layoutChanged || highlightChanged) {
            refreshVocabHighlights()
            refreshSentenceHighlights()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showNavigator(replaceExisting: Boolean = false) {
        statusText?.visibility = View.GONE
        val existing = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)
        if (existing != null) {
            if (!replaceExisting) return
            childFragmentManager.commitNow { remove(existing) }
        }
        childFragmentManager.commitNow {
            add(
                R.id.navigator_container,
                EpubNavigatorFragment::class.java,
                Bundle(),
                NAVIGATOR_TAG
            )
        }
    }

    private fun showStatus(message: String) {
        statusText?.apply {
            visibility = View.VISIBLE
            text = message
        }
    }

    // --- M18: double-tap word lookup ---

    /**
     * Installs a non-consuming double-tap detector on the Readium WebView.
     * The WebView is created asynchronously by the navigator fragment, so we
     * poll for it briefly. Once found, a double-tap extracts the word under
     * the finger via JS and reuses the dictionary-lookup path.
     */
    private fun installDoubleTapLookup() {
        viewLifecycleOwner.lifecycleScope.launch {
            val webView = findWebView() ?: return@launch
            attachDoubleTapDetector(webView)
        }
    }

    /** Traverses the navigator container's subtree for the first WebView. */
    private suspend fun findWebView(): WebView? {
        val container = view?.findViewById<ViewGroup>(R.id.navigator_container) ?: return null
        repeat(20) {
            val wv = findWebViewRecursive(container)
            if (wv != null) return wv
            kotlinx.coroutines.delay(100)
        }
        return null
    }

    private fun findWebViewRecursive(group: ViewGroup): WebView? {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is WebView -> return child
                is ViewGroup -> findWebViewRecursive(child)?.let { return it }
            }
        }
        return null
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun attachDoubleTapDetector(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        val detector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    extractWordAt(webView, e.x, e.y)
                    return false // let the WebView keep its own handling
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    // M16: a clean single tap toggles the Compose top bar
                    // (point-press show/hide). Swipes still turn pages and
                    // double-tap still looks up words — nothing is consumed.
                    (activity as? ReaderActivity)?.onReaderTap()
                    return false
                }
            },
        )
        webView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false // never consume; the WebView still scrolls/selects normally
        }
    }

    /**
     * Runs a JS snippet that locates the word under (x, y) using
     * caretRangeFromPoint, expands to whole-word boundaries, and returns the
     * word plus surrounding sentence context. The result feeds the same
     * [lookupAndShowCard] path as the long-press "查词" action.
     */
    private fun extractWordAt(webView: WebView, x: Float, y: Float) {
        val js = """
            (function() {
              var scale = window.devicePixelRatio || 1;
              var cx = ${x} / scale;
              var cy = ${y} / scale;
              var range = document.caretRangeFromPoint(cx, cy);
              if (!range || !range.startContainer || range.startContainer.nodeType !== 3) return null;
              var node = range.startContainer;
              var text = node.textContent;
              var i = range.startOffset;
              var isWord = function(ch) { return /[A-Za-z\u00C0-\u024F\u2019\u0027-]/.test(ch); };
              if (i >= text.length || !isWord(text[i])) {
                if (i > 0 && isWord(text[i-1])) { i = i - 1; } else { return null; }
              }
              var s = i, e2 = i;
              while (s > 0 && isWord(text[s-1])) s--;
              while (e2 < text.length - 1 && isWord(text[e2+1])) e2++;
              var word = text.substring(s, e2 + 1);
              if (!word) return null;
              var before = text.substring(Math.max(0, s - 120), s);
              var after = text.substring(e2 + 1, Math.min(text.length, e2 + 121));
              return JSON.stringify({ word: word, before: before, after: after });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            if (result == null || result == "null") return@evaluateJavascript
            val json = result.trim().removeSurrounding("\"")
                .replace("\\\"", "\"").replace("\\\\", "\\")
            try {
                val obj = org.json.JSONObject(json)
                val word = obj.optString("word").trim()
                if (word.isEmpty()) return@evaluateJavascript
                val locText = Locator.Text(
                    before = obj.optString("before"),
                    highlight = word,
                    after = obj.optString("after"),
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    lookupAndShowCard(word, locText)
                }
            } catch (_: Exception) {
                // Malformed JS result; ignore.
            }
        }
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        // Hand external links to the browser, if any is installed.
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            // No handler installed; ignore.
        }
    }

    private suspend fun openEpub(path: String): HandypageApp.OpenBook {
        val context = requireContext().applicationContext
        val file = File(path)
        if (path == demoPath && !file.exists()) {
            withContext(Dispatchers.IO) {
                context.assets.open("demo.epub").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        val assetRetriever = AssetRetriever(
            context.contentResolver,
            DefaultHttpClient()
        )
        val asset: Asset = assetRetriever.retrieve(file)
            .getOrElse { error -> throw Exception("Cannot retrieve asset: ${error.message}") }

        val publicationParser = DefaultPublicationParser(
            context,
            httpClient = DefaultHttpClient(),
            assetRetriever = assetRetriever,
            // EPUB-only; no PDF support needed.
            pdfFactory = null
        )
        val publication: Publication = PublicationOpener(publicationParser)
            .open(asset, allowUserInteraction = true)
            .getOrElse { error -> throw Exception("Cannot open publication: ${error.message}") }

        return HandypageApp.OpenBook(path, publication, asset)
    }
}
