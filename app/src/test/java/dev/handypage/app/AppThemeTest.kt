package dev.handypage.app

import dev.handypage.app.ui.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M38: app-wide colour themes. The palettes are plain Longs precisely so
 * this contract stays JVM-testable — Theme.kt only maps them to ColorScheme.
 */
class AppThemeTest {

    @Test
    fun `fromId resolves every stored id`() {
        AppTheme.entries.forEach { theme ->
            assertEquals(theme, AppTheme.fromId(theme.id))
        }
    }

    @Test
    fun `unknown or missing ids fall back to classic`() {
        assertEquals(AppTheme.CLASSIC, AppTheme.fromId(null))
        assertEquals(AppTheme.CLASSIC, AppTheme.fromId(""))
        assertEquals(AppTheme.CLASSIC, AppTheme.fromId("midnight-blue"))
    }

    @Test
    fun `classic reproduces the pre-M38 fixed scheme colours`() {
        val light = AppTheme.CLASSIC.palette(false)
        assertEquals(0xFFFBFAF7, light.paper)
        assertEquals(0xFF141414, light.ink)
        assertEquals(light.ink, light.accent) // monochrome: accent IS the ink
        assertEquals(0x1A000000, light.hairline)
        val dark = AppTheme.CLASSIC.palette(true)
        assertEquals(0xFF171512, dark.paper)
        assertEquals(0xFFECE7DB, dark.ink)
        assertEquals(dark.ink, dark.accent)
        assertEquals(0x24FFFFFF, dark.hairline)
    }

    @Test
    fun `six themes have six distinct light accents`() {
        val accents = AppTheme.entries.map { it.light.accent }.toSet()
        assertEquals(AppTheme.entries.size, accents.size)
    }

    @Test
    fun `dark variants lift the accent away from the light one`() {
        // Except Classic (monochrome: light accent is ink, dark accent is
        // dark-ink), every theme's dark accent differs from its light accent.
        AppTheme.entries.filter { it != AppTheme.CLASSIC }.forEach { theme ->
            assertNotEquals(theme.id, theme.light.accent, theme.dark.accent)
        }
    }

    @Test
    fun `solid palette colours are fully opaque`() {
        AppTheme.entries.forEach { theme ->
            listOf(theme.light, theme.dark).forEach { p ->
                listOf(
                    p.paper, p.paperAlt, p.ink, p.sub, p.faint,
                    p.accent, p.onAccent, p.accentSoft, p.onAccentSoft,
                ).forEach { colour ->
                    assertTrue(
                        "${theme.id} colour ${colour.toString(16)} must be opaque",
                        colour ushr 24 == 0xFFL,
                    )
                }
            }
        }
    }
}
