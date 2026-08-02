package dev.handypage.app.reader

/**
 * M8 vocab weak-highlight constants and pure helpers (DESIGN.md §4.11).
 *
 * Saved vocab words (and their lemmas) are located in the open publication
 * with Readium's search service and painted as low-alpha
 * [org.readium.r2.navigator.Decoration.Style.Highlight] decorations, so a
 * word the learner has already looked up is visibly "known" when it reappears
 * — without any DOM rewriting or layout shift.
 *
 * Only the pure, JVM-testable logic lives here; the Readium plumbing is in
 * ReaderFragment.
 */
object VocabHighlight {

    /** Decoration group id; re-applying the group replaces it (theme/save refresh). */
    const val GROUP = "vocab"

    /** Safety caps so a huge vocab book can't stall article opening. */
    const val MAX_TERMS = 400
    const val MAX_LOCATORS_PER_TERM = 100

    /**
     * Normalizes the raw DAO term list (saved words UNION lemmas): trims,
     * lowercases, drops blanks/1-char tokens and tokens without any letter,
     * dedups, and caps the count. Case-fold here because the search runs
     * case-insensitively.
     */
    fun normalizeTerms(raw: List<String>): List<String> =
        raw.map { it.trim().lowercase() }
            .filter { it.length >= 2 && it.any(Char::isLetter) }
            .distinct()
            .take(MAX_TERMS)

    /**
     * Weak-highlight tint per reader theme and palette name (M16). Every
     * preset stays translucent and readable against light/sepia/dark
     * backgrounds, clearly weaker than a real text selection. `ink` is the
     * editorial default (淡墨, like a soft pencil mark); the amber preset
     * keeps the pre-M16 look for users who prefer it.
     */
    fun tintForTheme(
        themeName: String,
        highlightName: String = ReaderSettings.HIGHLIGHT_INK,
    ): Int = when (highlightName) {
        ReaderSettings.HIGHLIGHT_AMBER -> when (themeName) {
            ReaderSettings.THEME_DARK -> 0x52FFD54F.toInt() // amber 200 @ 32%
            ReaderSettings.THEME_SEPIA -> 0x3D8F5A00 // browned amber @ 24%
            else -> 0x3DFFB300 // amber 600 @ 24%
        }
        ReaderSettings.HIGHLIGHT_TEAL -> when (themeName) {
            ReaderSettings.THEME_DARK -> 0x3D9CCFB8.toInt() // soft mint @ 24%
            ReaderSettings.THEME_SEPIA -> 0x33556B48 // mossed teal @ 20%
            else -> 0x332E7D5B // teal 700 @ 20%
        }
        ReaderSettings.HIGHLIGHT_BLUE -> when (themeName) {
            ReaderSettings.THEME_DARK -> 0x3D9AB8DE.toInt() // powder blue @ 24%
            ReaderSettings.THEME_SEPIA -> 0x33506B8F // greyed blue @ 20%
            else -> 0x333B6BA8 // slate blue @ 20%
        }
        ReaderSettings.HIGHLIGHT_RED -> when (themeName) {
            ReaderSettings.THEME_DARK -> 0x3DD4574A.toInt() // lifted editorial red @ 24%
            ReaderSettings.THEME_SEPIA -> 0x33A8492F // browned red @ 20%
            else -> 0x33B3352B // editorial red @ 20%
        }
        else -> when (themeName) { // 淡墨 ink (default)
            ReaderSettings.THEME_DARK -> 0x2BECE7DB.toInt() // warm white @ 17%
            ReaderSettings.THEME_SEPIA -> 0x242B2417 // sepia ink @ 14%
            else -> 0x24141414 // ink @ 14%
        }
    }

    /**
     * Whole-word double-check for one search hit. `wholeWord = true` is
     * requested from the search service, but this guard keeps the highlight
     * correct even if a search backend falls back to substring matching
     * (e.g. "art" must not light up inside "start"). Word characters for the
     * boundary test are letters only, so "don't"-style terms still match.
     */
    fun isWholeWordMatch(
        before: String?,
        highlight: String?,
        after: String?,
        term: String,
    ): Boolean {
        if (highlight.isNullOrBlank() || !highlight.trim().equals(term, ignoreCase = true)) {
            return false
        }
        val left = before?.lastOrNull()
        val right = after?.firstOrNull()
        return (left == null || !left.isLetter()) && (right == null || !right.isLetter())
    }
}
