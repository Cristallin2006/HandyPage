package dev.handypage.app.reader

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.widget.NestedScrollView
import dev.handypage.app.R
import dev.handypage.app.dict.DictEntry
import kotlin.math.roundToInt

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
    private val aiButton: Button = content.findViewById(R.id.word_card_ai)
    private val wordText: TextView = content.findViewById(R.id.word_card_word)
    private val closeButton: ImageButton = content.findViewById(R.id.word_card_close)
    private val phoneticText: TextView = content.findViewById(R.id.word_card_phonetic)
    private val posTagText: TextView = content.findViewById(R.id.word_card_pos_tag)
    private val lemmaText: TextView = content.findViewById(R.id.word_card_lemma)
    private val collinsText: TextView = content.findViewById(R.id.word_card_collins)
    private val divider: View = content.findViewById(R.id.word_card_divider)
    private val missText: TextView = content.findViewById(R.id.word_card_miss)
    private val translationText: TextView = content.findViewById(R.id.word_card_translation)
    private val definitionText: TextView = content.findViewById(R.id.word_card_definition)

    /** M37 active palette; the XML ships the light theme, applyTheme overrides. */
    private var palette = WordCardPalette.forTheme(ReaderSettings.THEME_LIGHT)

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

    /**
     * M37: re-skins the panel for the in-reader theme (light/sepia/dark).
     * The XML colors only flip with the SYSTEM night mode (values-night),
     * so a reader set to night while the system is light used to render a
     * blinding paper panel over the dark page. Every color the panel uses
     * is re-applied here from [WordCardPalette]; called once at creation
     * and again on every reader-theme flip (also while the panel is open).
     */
    fun applyTheme(themeName: String) {
        val p = WordCardPalette.forTheme(themeName)
        palette = p
        val density = container.resources.displayMetrics.density

        // Same structure as word_card_panel_bg: flat paper + 2dp ink top rule.
        container.background = LayerDrawable(
            arrayOf(
                GradientDrawable().apply { setColor(p.paper) },
                GradientDrawable().apply { setColor(p.ink) },
            )
        ).apply {
            setLayerGravity(1, Gravity.TOP)
            setLayerHeight(1, (density * 2).roundToInt().coerceAtLeast(1))
        }

        wordText.setTextColor(p.ink)
        closeButton.imageTintList = ColorStateList.valueOf(p.sub)
        phoneticText.setTextColor(p.sub)
        posTagText.setTextColor(p.sub)
        lemmaText.setTextColor(p.sub)
        collinsText.setTextColor(p.ink)
        divider.setBackgroundColor(p.hairline)
        missText.setTextColor(p.faint)
        translationText.setTextColor(p.ink)
        definitionText.setTextColor(p.sub)

        updateAddButton()
        aiButton.background = GradientDrawable().apply {
            cornerRadius = density * 4
            setColor(Color.TRANSPARENT)
            setStroke((density * 1).roundToInt().coerceAtLeast(1), p.ink)
        }
        aiButton.setTextColor(p.ink)
    }

    /** Fill-button background follows the palette AND the enabled state. */
    private fun updateAddButton() {
        addButton.background = GradientDrawable().apply {
            cornerRadius = addButton.resources.displayMetrics.density * 4
            setColor(if (addButton.isEnabled) palette.ink else palette.faint)
        }
        addButton.setTextColor(palette.onInk)
    }

    private fun setSaved(saved: Boolean) {
        addButton.isEnabled = !saved
        addButton.setText(if (saved) R.string.vocab_already_saved else R.string.add_to_vocab)
        updateAddButton()
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

/**
 * M37 word-panel colors per IN-READER theme (DESIGN.md §4.11 follow-up).
 *
 * Dark reuses the design-system night values (values-night/colors.xml:
 * #171512 paper = the M27 night page itself, so the flat panel merges with
 * the page and the 2dp ink rule marks the edge, per M16's "no floating
 * shadow" language). Sepia derives from Readium's sepia page (#FAF4E8 /
 * #5F4B32). Light mirrors the day hp_* resources the XML ships with.
 * Pure Int literals — no android.graphics dependency, JVM-testable.
 */
internal data class WordCardPalette(
    @ColorInt val paper: Int,
    @ColorInt val ink: Int,
    @ColorInt val sub: Int,
    @ColorInt val faint: Int,
    @ColorInt val hairline: Int,
    /** Text/icon color on top of an ink-filled surface (the add button). */
    @ColorInt val onInk: Int,
) {
    companion object {
        fun forTheme(themeName: String): WordCardPalette = when (themeName) {
            ReaderSettings.THEME_DARK -> WordCardPalette(
                paper = 0xFF171512.toInt(),
                ink = 0xFFECE7DB.toInt(),
                sub = 0xFFA8A29A.toInt(),
                faint = 0xFF6F6A62.toInt(),
                hairline = 0x24FFFFFF,
                onInk = 0xFF171512.toInt(),
            )
            ReaderSettings.THEME_SEPIA -> WordCardPalette(
                paper = 0xFFFAF4E8.toInt(),
                ink = 0xFF5F4B32.toInt(),
                sub = 0xFF8A7560.toInt(),
                faint = 0xFFB4A48F.toInt(),
                hairline = 0x245F4B32,
                onInk = 0xFFFAF4E8.toInt(),
            )
            else -> WordCardPalette( // THEME_LIGHT (and anything unknown)
                paper = 0xFFFBFAF7.toInt(),
                ink = 0xFF141414.toInt(),
                sub = 0xFF5C5C58.toInt(),
                faint = 0xFFA39E98.toInt(),
                hairline = 0x1A000000,
                onInk = 0xFFFBFAF7.toInt(),
            )
        }
    }
}
