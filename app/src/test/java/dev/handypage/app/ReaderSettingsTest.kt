package dev.handypage.app

import dev.handypage.app.reader.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for the pure-Kotlin reader-preference values. The mapping to
 * Readium's EpubPreferences/Theme is not covered here on purpose: the Theme
 * enum initializer calls android.graphics.Color, which plain JVM tests
 * cannot load.
 */
class ReaderSettingsTest {

    @Test
    fun `defaults match the pre-M2b reader look`() {
        val settings = ReaderSettings()
        assertEquals(1.0, settings.clampedFontScale, 1e-6)
        assertEquals(1.4, settings.clampedPageMargins, 1e-6)
        assertEquals(ReaderSettings.THEME_LIGHT, settings.normalizedThemeName)
    }

    @Test
    fun `font scale is clamped to the slider range`() {
        assertEquals(0.8, ReaderSettings(fontScale = 0.1f).clampedFontScale, 1e-6)
        assertEquals(2.5, ReaderSettings(fontScale = 9.9f).clampedFontScale, 1e-6)
        assertEquals(1.25, ReaderSettings(fontScale = 1.25f).clampedFontScale, 1e-3)
    }

    @Test
    fun `page margins are clamped to the slider range`() {
        assertEquals(0.5, ReaderSettings(pageMargins = 0.0f).clampedPageMargins, 1e-6)
        assertEquals(2.0, ReaderSettings(pageMargins = 4.0f).clampedPageMargins, 1e-6)
    }

    @Test
    fun `known theme names survive normalization`() {
        assertEquals(
            ReaderSettings.THEME_DARK,
            ReaderSettings(themeName = ReaderSettings.THEME_DARK).normalizedThemeName
        )
        assertEquals(
            ReaderSettings.THEME_SEPIA,
            ReaderSettings(themeName = ReaderSettings.THEME_SEPIA).normalizedThemeName
        )
    }

    @Test
    fun `unknown theme names fall back to light`() {
        assertEquals(
            ReaderSettings.THEME_LIGHT,
            ReaderSettings(themeName = "purple").normalizedThemeName
        )
        assertEquals(
            ReaderSettings.THEME_LIGHT,
            ReaderSettings(themeName = "").normalizedThemeName
        )
    }

    @Test
    fun `justification is opt-in and defaults off`() {
        assertEquals(false, ReaderSettings().justified)
        assertEquals(true, ReaderSettings(justified = true).justified)
    }

    @Test
    fun `highlight palette defaults to ink`() {
        assertEquals(ReaderSettings.HIGHLIGHT_INK, ReaderSettings().normalizedHighlightName)
    }

    @Test
    fun `known highlight names survive normalization`() {
        for (name in ReaderSettings.HIGHLIGHT_NAMES) {
            assertEquals(name, ReaderSettings(highlightName = name).normalizedHighlightName)
        }
    }

    @Test
    fun `unknown highlight names fall back to ink`() {
        assertEquals(
            ReaderSettings.HIGHLIGHT_INK,
            ReaderSettings(highlightName = "neon").normalizedHighlightName,
        )
        assertEquals(
            ReaderSettings.HIGHLIGHT_INK,
            ReaderSettings(highlightName = "").normalizedHighlightName,
        )
    }
}
