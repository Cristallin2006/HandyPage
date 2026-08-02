package dev.handypage.app.reader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import dev.handypage.app.R
import dev.handypage.app.dict.DictEntry

/**
 * M8 persistent bottom word panel (DESIGN.md §4.11) — replaces the M2a modal
 * word dialog (WordCardDialog, removed in M8).
 *
 * The panel is a plain view pinned to the bottom of the reader layout, NOT a
 * dialog: no dim, no focus steal, no touch interception outside its own
 * bounds. The article keeps scrolling while the gloss is open, and looking up
 * another word rebinds the same panel in place. Dismissal is explicit (the ✕
 * button or a Back press) so the panel never fights the article for gestures.
 *
 * The body scrolls inside a [NestedScrollView] capped at ~45% of the screen
 * height, keeping long translations readable without covering the article.
 */
class WordCardPanel(
    private val container: FrameLayout,
) {

    /** Notified when the panel opens/closes so the fragment can arm Back handling. */
    var onVisibilityChanged: ((Boolean) -> Unit)? = null

    private val content: View = LayoutInflater.from(container.context)
        .inflate(R.layout.word_card_panel, container, false)
    private val scroll: NestedScrollView = content.findViewById(R.id.word_card_scroll)
    private val addButton: Button = content.findViewById(R.id.word_card_add)

    private var onAddToVocab: (() -> Unit)? = null
    private var onAskAI: (() -> Unit)? = null

    /** Fraction of the screen height the gloss body may occupy at most. */
    private val maxBodyHeight: Int
        get() = (container.resources.displayMetrics.heightPixels * 0.45f).toInt()

    val isVisible: Boolean
        get() = container.visibility == View.VISIBLE

    init {
        container.addView(content)
        container.visibility = View.GONE
        content.findViewById<View>(R.id.word_card_close).setOnClickListener { hide() }
        addButton.setOnClickListener { onAddToVocab?.invoke() }
        content.findViewById<Button>(R.id.word_card_ai).setOnClickListener { onAskAI?.invoke() }
    }

    /**
     * Binds a fresh lookup result and slides the panel up if it was hidden.
     * [alreadySaved] renders the vocab button in its disabled "已在生词本中"
     * state; the callbacks capture the current word and are replaced on every
     * show.
     */
    fun show(
        rawWord: String,
        entry: DictEntry?,
        alreadySaved: Boolean,
        onAddToVocab: () -> Unit,
        onAskAI: () -> Unit,
    ) {
        this.onAddToVocab = onAddToVocab
        this.onAskAI = onAskAI
        bind(rawWord, entry)
        setSaved(alreadySaved)

        // Reset any previous height cap, then re-cap once the new content is
        // measured (a short gloss stays compact, a long one gets the scroll).
        scroll.layoutParams = scroll.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        content.post {
            if (scroll.height > maxBodyHeight) {
                scroll.layoutParams = scroll.layoutParams.apply { height = maxBodyHeight }
            }
        }

        if (!isVisible) {
            container.visibility = View.VISIBLE
            container.post {
                container.translationY = container.height.toFloat()
                container.animate().translationY(0f).setDuration(180).start()
            }
            onVisibilityChanged?.invoke(true)
        }
    }

    /** Marks the vocab button saved after a successful insert (the panel stays open). */
    fun markSaved() {
        setSaved(true)
    }

    fun hide() {
        if (!isVisible) return
        container.animate().cancel()
        container.animate()
            .translationY(container.height.toFloat())
            .setDuration(150)
            .withEndAction {
                container.visibility = View.GONE
                container.translationY = 0f
            }
            .start()
        onVisibilityChanged?.invoke(false)
    }

    private fun setSaved(saved: Boolean) {
        addButton.isEnabled = !saved
        addButton.setText(if (saved) R.string.vocab_already_saved else R.string.add_to_vocab)
    }

    private fun bind(rawWord: String, entry: DictEntry?) {
        // For inflection hits the gloss lives on the lemma row.
        val shown = entry?.lemmaEntry ?: entry

        bindText(R.id.word_card_word, entry?.word ?: rawWord)
        bindText(R.id.word_card_phonetic, shown?.phonetic?.let { "/$it/" })

        val posTag = listOfNotNull(shown?.pos, shown?.tag)
            .filter { it.isNotBlank() }
            .joinToString("  ")
        bindText(R.id.word_card_pos_tag, posTag)

        content.findViewById<TextView>(R.id.word_card_lemma).apply {
            val lemmaWord = entry?.lemmaEntry?.word
            if (lemmaWord.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = context.getString(R.string.lemma_arrow, lemmaWord)
            }
        }

        bindText(R.id.word_card_translation, shown?.translation)
        bindText(R.id.word_card_definition, shown?.definition)

        content.findViewById<TextView>(R.id.word_card_collins).apply {
            val stars = (shown?.collins ?: 0).coerceIn(0, 5)
            if (stars == 0) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = "★".repeat(stars) + "☆".repeat(5 - stars)
            }
        }

        content.findViewById<TextView>(R.id.word_card_miss).visibility =
            if (entry == null) View.VISIBLE else View.GONE
    }

    private fun bindText(viewId: Int, value: String?) {
        content.findViewById<TextView>(viewId).apply {
            if (value.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = value
            }
        }
    }
}
