package dev.handypage.app

import dev.handypage.app.reader.ReaderSettings
import dev.handypage.app.reader.SentenceHighlight
import dev.handypage.app.reader.VocabHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the M20 saved-sentence weak-highlight helpers (DESIGN.md
 * §4.13 follow-up). The Readium plumbing is exercised on-device; here we pin
 * down the pure matching/normalization rules the plumbing relies on.
 */
class SentenceHighlightTest {

    // --- normalizeQueries ---------------------------------------------------

    @Test
    fun `normalizeQueries folds whitespace and dedups`() {
        val queries = SentenceHighlight.normalizeQueries(
            listOf(
                "The quick  brown\nfox.",
                "The quick brown fox.",
                "  A second sentence.  ",
            ),
        )
        assertEquals(listOf("The quick brown fox.", "A second sentence."), queries)
    }

    @Test
    fun `normalizeQueries drops blanks and overlong entries`() {
        val long = "x".repeat(SentenceHighlight.MAX_QUERY_CHARS + 1)
        val atLimit = "y".repeat(SentenceHighlight.MAX_QUERY_CHARS)
        val queries = SentenceHighlight.normalizeQueries(listOf("", "   ", long, atLimit))
        assertEquals(listOf(atLimit), queries)
    }

    @Test
    fun `normalizeQueries keeps original casing`() {
        val queries = SentenceHighlight.normalizeQueries(listOf("Apples are GREAT."))
        assertEquals(listOf("Apples are GREAT."), queries)
    }

    @Test
    fun `normalizeQueries caps the sentence count`() {
        val raw = (1..SentenceHighlight.MAX_SENTENCES + 25).map { "Sentence number $it." }
        assertEquals(SentenceHighlight.MAX_SENTENCES, SentenceHighlight.normalizeQueries(raw).size)
    }

    // --- isMatch ------------------------------------------------------------

    @Test
    fun `isMatch accepts the same sentence with different whitespace and case`() {
        assertTrue(
            SentenceHighlight.isMatch(
                "The quick\nbrown   fox.",
                "The quick brown fox.",
            ),
        )
        assertTrue(
            SentenceHighlight.isMatch(
                "the quick brown fox.",
                "The quick brown fox.",
            ),
        )
    }

    @Test
    fun `isMatch rejects different sentences and blanks`() {
        assertFalse(
            SentenceHighlight.isMatch(
                "The quick brown fox jumps.",
                "The quick brown fox.",
            ),
        )
        assertFalse(SentenceHighlight.isMatch(null, "query"))
        assertFalse(SentenceHighlight.isMatch("  ", "query"))
        assertFalse(SentenceHighlight.isMatch("highlight", "  "))
    }

    // --- tintForTheme -------------------------------------------------------

    @Test
    fun `every palette gives a visible underline tint in every theme`() {
        val themes = listOf(
            ReaderSettings.THEME_LIGHT, ReaderSettings.THEME_SEPIA, ReaderSettings.THEME_DARK,
        )
        for (name in ReaderSettings.HIGHLIGHT_NAMES) {
            for (theme in themes) {
                val alpha = (SentenceHighlight.tintForTheme(theme, name) ushr 24) and 0xFF
                assertTrue(
                    "palette $name / theme $theme alpha $alpha must suit a 1-2px line",
                    alpha in 0x60..0xB0,
                )
            }
        }
    }

    @Test
    fun `underline tints are stronger than the vocab background tints`() {
        // A line at background-highlight alpha would be invisible; the same
        // hue at underline alpha must stay clearly stronger in every palette.
        for (name in ReaderSettings.HIGHLIGHT_NAMES) {
            val underline = (SentenceHighlight.tintForTheme(ReaderSettings.THEME_LIGHT, name) ushr 24) and 0xFF
            val background = (VocabHighlight.tintForTheme(ReaderSettings.THEME_LIGHT, name) ushr 24) and 0xFF
            assertTrue("palette $name: $underline should exceed $background", underline > background)
        }
    }

    @Test
    fun `palettes are visually distinct from each other`() {
        val tints = ReaderSettings.HIGHLIGHT_NAMES
            .map { SentenceHighlight.tintForTheme(ReaderSettings.THEME_LIGHT, it) }
            .toSet()
        assertEquals(ReaderSettings.HIGHLIGHT_NAMES.size, tints.size)
    }

    @Test
    fun `underline tint is theme-specific and falls back to light`() {
        val light = SentenceHighlight.tintForTheme(ReaderSettings.THEME_LIGHT)
        assertNotEquals(light, SentenceHighlight.tintForTheme(ReaderSettings.THEME_DARK))
        assertNotEquals(light, SentenceHighlight.tintForTheme(ReaderSettings.THEME_SEPIA))
        assertEquals(light, SentenceHighlight.tintForTheme("whatever"))
    }

    @Test
    fun `unknown palette falls back to ink`() {
        assertEquals(
            SentenceHighlight.tintForTheme(ReaderSettings.THEME_DARK, ReaderSettings.HIGHLIGHT_INK),
            SentenceHighlight.tintForTheme(ReaderSettings.THEME_DARK, "whatever"),
        )
    }
}
