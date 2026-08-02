package dev.handypage.app.dict

/**
 * Pure-Kotlin word-form helpers for the dictionary lookup chain.
 *
 * Deliberately free of any `android.*` imports so the whole lookup-key logic
 * is unit-testable on the JVM (android.database.sqlite is not).
 */
object WordForms {

    /** Characters that are neither letters nor digits, anchored at a string edge. */
    private val EDGE_JUNK = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")

    private val WHITESPACE = Regex("\\s+")

    /**
     * Normalizes a raw text selection into a lookup key: trims, collapses
     * inner whitespace runs to single spaces, lowercases, and strips
     * leading/trailing punctuation (quotes, brackets, full-width variants).
     * Inner apostrophes are kept (`rock'n'roll`); a trailing possessive `'s`
     * is kept here and handled as a fallback in [candidates].
     */
    fun clean(raw: String): String =
        raw.trim()
            .replace(WHITESPACE, " ")
            .lowercase()
            .replace(EDGE_JUNK, "")

    /**
     * Lookup candidates in priority order: the cleaned form itself first; a
     * trailing English possessive (`'s`) adds the base form as fallback.
     */
    fun candidates(cleaned: String): List<String> =
        if (cleaned.endsWith("'s") && cleaned.length > 2) {
            listOf(cleaned, cleaned.dropLast(2))
        } else {
            listOf(cleaned)
        }

    /**
     * Builds the sentence context for a vocab entry from the selection's
     * before/highlight/after parts: concatenated verbatim (Readium keeps the
     * original spacing at the boundaries), then every whitespace run
     * (including newlines) collapses to a single space.
     */
    fun sentence(before: String?, highlight: String?, after: String?): String =
        listOfNotNull(before, highlight, after)
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()
}
