package dev.handypage.app.reader

import dev.handypage.app.vocab.SentenceText

/**
 * M20 saved-sentence weak-highlight constants and pure helpers (DESIGN.md
 * §4.13 follow-up).
 *
 * Sentences the learner saved from the open article are located with
 * Readium's search service (whitespace-normalized text, so the normalized
 * stored text matches) and painted as [org.readium.r2.navigator.Decoration.Style.Underline]
 * decorations — visually distinct from the vocab background tint, and the two
 * compose like pencil marginalia when a saved word sits inside a saved
 * sentence. Sentences are filtered by the book's dc:source article URL:
 * arXiv papers store the abs URL on both the pdf.js view and the reflow EPUB,
 * so saves from either view underline in the EPUB reader.
 *
 * Only the pure, JVM-testable logic lives here; the Readium plumbing is in
 * ReaderFragment.
 */
object SentenceHighlight {

    /** Decoration group id; re-applying the group replaces it (theme/save refresh). */
    const val GROUP = "sentences"

    /** Safety caps so a large sentence book can't stall article opening. */
    const val MAX_SENTENCES = 50
    const val MAX_LOCATORS_PER_SENTENCE = 20

    /**
     * Whole paragraphs can be saved; past a point a search query stops being
     * a "sentence" and starts being a DoS on the search service. Overlong
     * entries simply don't underline — the book row is untouched.
     */
    const val MAX_QUERY_CHARS = 600

    /**
     * Normalizes the raw DAO text list: folds whitespace (defensive — texts
     * are stored normalized, but older rows or future writers may differ),
     * drops blanks/overlong entries, dedups, and caps the count. No
     * lowercasing: the ICU search is case-insensitive already, and the
     * original casing feeds the [isMatch] guard.
     */
    fun normalizeQueries(raw: List<String>): List<String> =
        raw.map { SentenceText.normalize(it) }
            .filter { it.isNotEmpty() && it.length <= MAX_QUERY_CHARS }
            .distinct()
            .take(MAX_SENTENCES)

    /**
     * Underline tint per reader theme and palette name (M16). Same hue family
     * as the vocab background tint, but at ~55% alpha: a 1-2px underline is
     * invisible at background-highlight alpha, and the two marks are often
     * layered on the same line.
     */
    fun tintForTheme(
        themeName: String,
        highlightName: String = ReaderSettings.HIGHLIGHT_INK,
    ): Int = when (highlightName) {
        ReaderSettings.HIGHLIGHT_AMBER -> when (themeName) {
            ReaderSettings.THEME_DARK -> 0x8CFFD54F.toInt() // amber 200 @ 55%
            ReaderSettings.THEME_SEPIA -> 0x8C8F5A00.toInt() // browned amber @ 55%
            else -> 0x8CFFB300.toInt() // amber 600 @ 55%
        }
        ReaderSettings.HIGHLIGHT_TEAL -> when (themeName) {
            ReaderSettings.THEME_DARK -> 0x8C9CCFB8.toInt() // soft mint @ 55%
            ReaderSettings.THEME_SEPIA -> 0x8C556B48.toInt() // mossed teal @ 55%
            else -> 0x8C2E7D5B.toInt() // teal 700 @ 55%
        }
        ReaderSettings.HIGHLIGHT_BLUE -> when (themeName) {
            ReaderSettings.THEME_DARK -> 0x8C9AB8DE.toInt() // powder blue @ 55%
            ReaderSettings.THEME_SEPIA -> 0x8C506B8F.toInt() // greyed blue @ 55%
            else -> 0x8C3B6BA8.toInt() // slate blue @ 55%
        }
        ReaderSettings.HIGHLIGHT_RED -> when (themeName) {
            ReaderSettings.THEME_DARK -> 0x8CD4574A.toInt() // lifted editorial red @ 55%
            ReaderSettings.THEME_SEPIA -> 0x8CA8492F.toInt() // browned red @ 55%
            else -> 0x8CB3352B.toInt() // editorial red @ 55%
        }
        else -> when (themeName) { // 淡墨 ink (default)
            ReaderSettings.THEME_DARK -> 0x8CECE7DB.toInt() // warm white @ 55%
            ReaderSettings.THEME_SEPIA -> 0x8C2B2417.toInt() // sepia ink @ 55%
            else -> 0x8C141414.toInt() // ink @ 55%
        }
    }

    /**
     * Whole-sentence double-check for one search hit. The ICU search runs
     * case/diacritic-insensitively, which is what we want for recall; this
     * guard only rejects hits that aren't the same sentence at all after
     * whitespace normalization (e.g. a pathological collator folding two
     * different strings together).
     */
    fun isMatch(highlight: String?, query: String): Boolean {
        if (highlight.isNullOrBlank() || query.isBlank()) return false
        return SentenceText.normalize(highlight).equals(query, ignoreCase = true)
    }
}
