package dev.handypage.app

import dev.handypage.app.reader.ReaderSettings
import dev.handypage.app.reader.WordCardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * M37: the word panel skins per IN-READER theme, not the system night mode.
 * The palette mapping is the whole contract — the view rebinding in
 * WordCardPanel.applyTheme is covered by on-device verification.
 */
class WordCardPaletteTest {

    @Test
    fun `dark theme reuses the design-system night values`() {
        val p = WordCardPalette.forTheme(ReaderSettings.THEME_DARK)
        assertEquals(0xFF171512.toInt(), p.paper) // M27 night page
        assertEquals(0xFFECE7DB.toInt(), p.ink) // values-night hp_ink
        assertEquals(0xFFA8A29A.toInt(), p.sub) // values-night hp_sub
        assertEquals(0xFF6F6A62.toInt(), p.faint) // values-night hp_faint
        assertEquals(0x24FFFFFF, p.hairline) // values-night hp_hairline
        assertEquals(0xFF171512.toInt(), p.onInk)
    }

    @Test
    fun `sepia theme derives from the Readium sepia page`() {
        val p = WordCardPalette.forTheme(ReaderSettings.THEME_SEPIA)
        assertEquals(0xFFFAF4E8.toInt(), p.paper)
        assertEquals(0xFF5F4B32.toInt(), p.ink)
        assertEquals(0xFFFAF4E8.toInt(), p.onInk)
    }

    @Test
    fun `light theme mirrors the day hp_ resources`() {
        val p = WordCardPalette.forTheme(ReaderSettings.THEME_LIGHT)
        assertEquals(0xFFFBFAF7.toInt(), p.paper)
        assertEquals(0xFF141414.toInt(), p.ink)
        assertEquals(0xFF5C5C58.toInt(), p.sub)
        assertEquals(0xFFA39E98.toInt(), p.faint)
        assertEquals(0x1A000000, p.hairline)
        assertEquals(0xFFFBFAF7.toInt(), p.onInk)
    }

    @Test
    fun `unknown theme names fall back to light`() {
        assertEquals(
            WordCardPalette.forTheme(ReaderSettings.THEME_LIGHT),
            WordCardPalette.forTheme("midnight-blue"),
        )
    }

    @Test
    fun `the three themes are visually distinct`() {
        val light = WordCardPalette.forTheme(ReaderSettings.THEME_LIGHT)
        val dark = WordCardPalette.forTheme(ReaderSettings.THEME_DARK)
        val sepia = WordCardPalette.forTheme(ReaderSettings.THEME_SEPIA)
        assertNotEquals(light.paper, dark.paper)
        assertNotEquals(light.paper, sepia.paper)
        assertNotEquals(dark.paper, sepia.paper)
        assertNotEquals(light.ink, dark.ink)
    }
}
