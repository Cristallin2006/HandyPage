package dev.handypage.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import dev.handypage.app.reader.ChatController
import dev.handypage.app.reader.ReaderSettings
import androidx.compose.runtime.collectAsState
import dev.handypage.app.ui.AppThemeController
import dev.handypage.app.ui.HandypageTheme
import dev.handypage.app.ui.ReaderShell
import dev.handypage.app.ui.applyHpAxisCloseTransition
import dev.handypage.app.ui.applyHpAxisOpenTransition

/**
 * Pure-Compose reader host (DESIGN.md §4.9): a Material 3 BottomSheetScaffold
 * whose body embeds the Readium [ReaderFragment] via `AndroidFragment`, with
 * the AI chat panel as the sheet. No View-side sheet/gesture hacks.
 *
 * The fragment owns the article-scoped [ChatController] (it has the book
 * metadata) and hands it up via [onChatControllerReady]; every "open the
 * drawer" gesture funnels through [requestChatOpen]. M16 adds the editorial
 * chrome: the fragment reports book metadata ([onBookOpened]), pushes the
 * persisted reader settings ([onReaderSettingsLoaded]), and forwards article
 * single-taps ([onReaderTap]) so the shell can toggle its top bar.
 */
class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EPUB_PATH = "dev.handypage.app.EPUB_PATH"
    }

    private val chatController = mutableStateOf<ChatController?>(null)
    private val chatOpenRequests = mutableIntStateOf(0)
    private val wordPanelVisible = mutableStateOf(false)
    private val bookTitle = mutableStateOf("")
    private val bookKicker = mutableStateOf("")
    private val readerSettingsState = mutableStateOf(ReaderSettings())
    private val tapSignals = mutableIntStateOf(0)

    /** M21 reader star: null hides it (demo book / arXiv reflow), true/false paints it. */
    private val starState = mutableStateOf<Boolean?>(null)
    private var readerFragment: ReaderFragment? = null

    /** Called by ReaderFragment once the article-scoped session controller exists. */
    fun onChatControllerReady(controller: ChatController) {
        chatController.value = controller
    }

    /** M16: book metadata for the Compose top bar (kicker = source). */
    fun onBookOpened(title: String, kicker: String) {
        bookTitle.value = title
        bookKicker.value = kicker
    }

    /** M16: seed the shell's mirror of the persisted reader settings. */
    fun onReaderSettingsLoaded(settings: ReaderSettings) {
        readerSettingsState.value = settings
    }

    /** M16: a clean single tap on the article toggles the top bar. */
    fun onReaderTap() {
        tapSignals.intValue += 1
    }

    /** M21: ReaderFragment publishes the star state for the top bar. */
    fun onStarState(starred: Boolean?) {
        starState.value = starred
    }

    /**
     * M8: while the word panel is open the bottom summon zone must step
     * aside — the invisible 48dp strip would otherwise swallow the taps
     * aimed at the panel's buttons (they sit in the same bottom band).
     */
    fun onWordPanelVisibilityChanged(visible: Boolean) {
        wordPanelVisible.value = visible
    }

    /** Selection actions, buttons, and the summon zone all open the sheet this way. */
    fun requestChatOpen() {
        chatOpenRequests.intValue += 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // M22: axis-Y open transition (design-system.md §9; close twin in finish()).
        applyHpAxisOpenTransition()
        val epubPath = intent.getStringExtra(EXTRA_EPUB_PATH)
        setContent {
            // M26: inside the reader the READING theme drives the chrome
            // (top bar / Aa panel / AI drawer / chat), not the system theme —
            // a light drawer over the night-mode page was the desync this
            // fixes. readerSettingsState is Compose state, so flipping the
            // theme in the Aa panel re-themes every surface instantly.
            val readerDark = readerSettingsState.value.normalizedThemeName ==
                ReaderSettings.THEME_DARK
            // M38: the chrome hue follows the app theme; light/dark still
            // comes from the READING theme (M26), which stays untouched.
            val appTheme = AppThemeController.theme.collectAsState().value
            HandypageTheme(appTheme = appTheme, darkTheme = readerDark) {
                ReaderShell(
                    controller = chatController.value,
                    openRequests = chatOpenRequests.intValue,
                    title = bookTitle.value,
                    kicker = bookKicker.value,
                    readerSettings = readerSettingsState.value,
                    tapSignals = tapSignals.intValue,
                    starred = starState.value,
                    onStar = { readerFragment?.onStarToggle() },
                    onSummon = { requestChatOpen() },
                    onGuide = { readerFragment?.onGuideClick() },
                    onReaderSettingsChange = { settings ->
                        readerSettingsState.value = settings
                        readerFragment?.applyReaderSettings(settings)
                    },
                    onOpenSettings = {
                        startActivity(
                            Intent(this, SettingsActivity::class.java)
                                .putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_AI),
                        )
                    },
                    summonZoneEnabled = !wordPanelVisible.value,
                ) {
                    AndroidFragment<ReaderFragment>(
                        modifier = Modifier.fillMaxSize(),
                        arguments = Bundle().apply {
                            putString(ReaderFragment.ARG_EPUB_PATH, epubPath)
                        },
                        onUpdate = { fragment -> readerFragment = fragment },
                    )
                }
            }
        }
    }

    override fun finish() {
        applyHpAxisCloseTransition()
        super.finish()
    }
}
