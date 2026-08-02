package dev.handypage.app.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem

/**
 * Floating selection menu for the EPUB WebView.
 *
 * Readium's `R2BasicWebView` wraps whatever callback we hand it via
 * `EpubNavigatorFragment.Configuration.selectionActionModeCallback`, so this
 * REPLACES the default Copy/Share menu rather than appending to it: the menu
 * contains only "查词", "讲句子" (M3), "收藏句子" (M10), and our own "复制"
 * (all resolved against `SelectableNavigator.currentSelection()` by the
 * fragment).
 */
class WordActionModeCallback(
    private val lookupLabel: String,
    private val explainLabel: String,
    private val saveLabel: String,
    private val copyLabel: String,
    private val onLookup: (mode: ActionMode) -> Unit,
    private val onExplain: (mode: ActionMode) -> Unit,
    private val onSaveSentence: (mode: ActionMode) -> Unit,
    private val onCopy: (mode: ActionMode) -> Unit,
) : ActionMode.Callback {

    companion object {
        const val ITEM_LOOKUP = 1
        const val ITEM_EXPLAIN = 2
        const val ITEM_SAVE_SENTENCE = 3
        const val ITEM_COPY = 4
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(0, ITEM_LOOKUP, 0, lookupLabel)
        menu.add(0, ITEM_EXPLAIN, 1, explainLabel)
        menu.add(0, ITEM_SAVE_SENTENCE, 2, saveLabel)
        menu.add(0, ITEM_COPY, 3, copyLabel)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
        when (item.itemId) {
            ITEM_LOOKUP -> {
                onLookup(mode)
                true
            }
            ITEM_EXPLAIN -> {
                onExplain(mode)
                true
            }
            ITEM_SAVE_SENTENCE -> {
                onSaveSentence(mode)
                true
            }
            ITEM_COPY -> {
                onCopy(mode)
                true
            }
            else -> false
        }

    override fun onDestroyActionMode(mode: ActionMode) {}
}
