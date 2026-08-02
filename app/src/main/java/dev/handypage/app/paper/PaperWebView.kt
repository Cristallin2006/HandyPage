package dev.handypage.app.paper

import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import dev.handypage.app.R

/**
 * A WebView whose system text-selection action mode is fully replaced by the
 * app's own four actions — the same trick as Readium substituting
 * `selectionActionModeCallback` in the EPUB reader.
 *
 * Long-press selection makes the WebView call [startActionMode]; both
 * overloads wrap the system callback in [SelectionActionModeCallback],
 * so the platform still creates and owns the ActionMode (correct lifecycle
 * and chrome) but the menu contains only 查词 / 讲句子 / 收藏句子 / 复制.
 * The mode is always forced to [ActionMode.TYPE_FLOATING] so the menu floats
 * next to the selection instead of docking as a top contextual bar; the
 * selection's anchor rect is delegated to Chromium's original callback
 * ([ActionMode.Callback2.onGetContentRect]). The handlers run against the
 * latest JS-reported selection text via [actions], then `mode.finish()`
 * closes it.
 */
class PaperWebView(context: Context) : WebView(context) {

    /** Selection action handlers, wired by PaperReaderActivity. */
    var actions: SelectionActions? = null

    interface SelectionActions {
        fun onLookup()
        fun onExplain()
        fun onSaveSentence()
        fun onCopy()
    }

    companion object {
        // Same ids/semantics as reader/WordActionModeCallback.
        const val ITEM_LOOKUP = 1
        const val ITEM_EXPLAIN = 2
        const val ITEM_SAVE_SENTENCE = 3
        const val ITEM_COPY = 4
    }

    // Always force TYPE_FLOATING: the no-arg overload defaults to TYPE_PRIMARY,
    // which renders as a top contextual bar far away from the selection —
    // floating mode anchors the menu next to the selected text instead.
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? =
        super.startActionMode(SelectionActionModeCallback(callback), ActionMode.TYPE_FLOATING)

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? =
        super.startActionMode(SelectionActionModeCallback(callback), ActionMode.TYPE_FLOATING)

    /**
     * Wraps (not discards) the system callback: Chromium supplies the
     * selection's on-screen rect through [ActionMode.Callback2.onGetContentRect]
     * on ITS callback — dropping it leaves the floating toolbar with no anchor
     * and it falls back to the top of the view. Only the menu content and item
     * handling are replaced; lifecycle and content-rect queries delegate.
     */
    private inner class SelectionActionModeCallback(
        private val original: ActionMode.Callback,
    ) : ActionMode.Callback2() {

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (original is ActionMode.Callback2) {
                original.onGetContentRect(mode, view, outRect)
            } else {
                super.onGetContentRect(mode, view, outRect)
            }
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(0, ITEM_LOOKUP, 0, R.string.action_lookup_word)
            menu.add(0, ITEM_EXPLAIN, 1, R.string.action_explain_sentence)
            menu.add(0, ITEM_SAVE_SENTENCE, 2, R.string.action_save_sentence)
            menu.add(0, ITEM_COPY, 3, R.string.action_copy)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val handler = when (item.itemId) {
                ITEM_LOOKUP -> actions?.let { it::onLookup }
                ITEM_EXPLAIN -> actions?.let { it::onExplain }
                ITEM_SAVE_SENTENCE -> actions?.let { it::onSaveSentence }
                ITEM_COPY -> actions?.let { it::onCopy }
                else -> null
            } ?: return false
            // The handler consumes the latest JS-reported selection and
            // clears it in-page (__clearSelection); finish closes the mode.
            handler()
            mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            original.onDestroyActionMode(mode)
        }
    }
}
