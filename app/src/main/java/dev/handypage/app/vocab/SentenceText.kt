package dev.handypage.app.vocab

/**
 * Text normalization for saved sentences (DESIGN.md §4.13). Pure Kotlin, no
 * android imports, so it is JVM-testable.
 */
object SentenceText {

    /**
     * Collapses every run of whitespace (including the newlines an EPUB
     * selection picks up across elements) into one space and trims the ends,
     * so the same sentence selected from different renderings dedups against
     * the (text, articleUrl) unique index.
     */
    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")
}
