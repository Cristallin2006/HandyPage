package dev.handypage.app

import dev.handypage.app.reader.ReaderSettings
import dev.handypage.app.reader.VocabHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the M8 vocab weak-highlight helpers (DESIGN.md §4.11).
 * The Readium plumbing is exercised on-device; here we pin down the pure
 * matching/normalization rules the plumbing relies on.
 */
class VocabHighlightTest {

    // --- normalizeTerms -----------------------------------------------------

    @Test
    fun `normalizeTerms trims lowercases and dedups`() {
        val terms = VocabHighlight.normalizeTerms(listOf(" Apple ", "apple", "BANANA"))
        assertEquals(listOf("apple", "banana"), terms)
    }

    @Test
    fun `normalizeTerms drops blanks short and non-letter tokens`() {
        val terms = VocabHighlight.normalizeTerms(listOf("", "  ", "a", "42", "ok"))
        assertEquals(listOf("ok"), terms)
    }

    @Test
    fun `normalizeTerms keeps apostrophes and hyphens`() {
        val terms = VocabHighlight.normalizeTerms(listOf("don't", "well-known"))
        assertEquals(listOf("don't", "well-known"), terms)
    }

    @Test
    fun `normalizeTerms caps the term count`() {
        val raw = (1..VocabHighlight.MAX_TERMS + 50).map { "word$it" }
        assertEquals(VocabHighlight.MAX_TERMS, VocabHighlight.normalizeTerms(raw).size)
    }

    // --- isWholeWordMatch ---------------------------------------------------

    @Test
    fun `whole word match accepts exact hit with punctuation boundaries`() {
        assertTrue(VocabHighlight.isWholeWordMatch("The ", "apple", " fell", "apple"))
        assertTrue(VocabHighlight.isWholeWordMatch("(", "Apple", ").", "apple"))
    }

    @Test
    fun `whole word match accepts text edges`() {
        assertTrue(VocabHighlight.isWholeWordMatch(null, "apple", null, "apple"))
        assertTrue(VocabHighlight.isWholeWordMatch("", "apple", "", "apple"))
    }

    @Test
    fun `whole word match rejects substring hits inside larger words`() {
        assertFalse(VocabHighlight.isWholeWordMatch("st", "art", "ed", "art"))
        assertFalse(VocabHighlight.isWholeWordMatch("", "art", "ist", "art"))
        assertFalse(VocabHighlight.isWholeWordMatch("p", "art", "", "art"))
    }

    @Test
    fun `whole word match rejects wrong or blank highlights`() {
        assertFalse(VocabHighlight.isWholeWordMatch("", "apples", "", "apple"))
        assertFalse(VocabHighlight.isWholeWordMatch("", " ", "", "apple"))
        assertFalse(VocabHighlight.isWholeWordMatch("", null, "", "apple"))
    }

    @Test
    fun `whole word match treats digits as word boundaries`() {
        // "42" was filtered out of terms already, but a digit next to a real
        // term (e.g. "covid19"-ish contexts) must not block a legit match.
        assertTrue(VocabHighlight.isWholeWordMatch("in 2", "apple", " pies", "apple"))
    }

    // --- tintForTheme -------------------------------------------------------

    @Test
    fun `tint is translucent and theme-specific`() {
        val light = VocabHighlight.tintForTheme(ReaderSettings.THEME_LIGHT)
        val dark = VocabHighlight.tintForTheme(ReaderSettings.THEME_DARK)
        val sepia = VocabHighlight.tintForTheme(ReaderSettings.THEME_SEPIA)
        assertNotEquals(light, dark)
        assertNotEquals(light, sepia)
        // Weak highlight = clearly translucent alpha, never opaque.
        for (tint in listOf(light, dark, sepia)) {
            val alpha = (tint ushr 24) and 0xFF
            assertTrue("alpha $alpha should be subtle", alpha in 0x20..0x80)
        }
    }

    @Test
    fun `tint falls back to light for unknown theme names`() {
        assertEquals(
            VocabHighlight.tintForTheme(ReaderSettings.THEME_LIGHT),
            VocabHighlight.tintForTheme("whatever"),
        )
    }

    // --- M16 highlight palette ----------------------------------------------

    @Test
    fun `every palette gives translucent tints in every theme`() {
        val themes = listOf(
            ReaderSettings.THEME_LIGHT, ReaderSettings.THEME_SEPIA, ReaderSettings.THEME_DARK,
        )
        for (name in ReaderSettings.HIGHLIGHT_NAMES) {
            for (theme in themes) {
                val alpha = (VocabHighlight.tintForTheme(theme, name) ushr 24) and 0xFF
                assertTrue(
                    "palette $name / theme $theme alpha $alpha should be subtle",
                    alpha in 0x20..0x80,
                )
            }
        }
    }

    @Test
    fun `palettes are visually distinct from each other`() {
        val tints = ReaderSettings.HIGHLIGHT_NAMES
            .map { VocabHighlight.tintForTheme(ReaderSettings.THEME_LIGHT, it) }
            .toSet()
        assertEquals(ReaderSettings.HIGHLIGHT_NAMES.size, tints.size)
    }

    @Test
    fun `unknown palette falls back to ink`() {
        assertEquals(
            VocabHighlight.tintForTheme(ReaderSettings.THEME_DARK, ReaderSettings.HIGHLIGHT_INK),
            VocabHighlight.tintForTheme(ReaderSettings.THEME_DARK, "whatever"),
        )
    }

    @Test
    fun `amber preset keeps the pre-M16 look`() {
        // The original M8 amber values stay available as a named preset.
        assertEquals(
            0x3DFFB300.toInt(),
            VocabHighlight.tintForTheme(ReaderSettings.THEME_LIGHT, ReaderSettings.HIGHLIGHT_AMBER),
        )
    }
}
