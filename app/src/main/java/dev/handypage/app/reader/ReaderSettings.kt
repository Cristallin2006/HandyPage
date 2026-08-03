package dev.handypage.app.reader

import android.content.Context
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Reader display preferences (M2b): font scale, theme, page margins.
 *
 * Deliberately holds the theme as a plain String instead of Readium's
 * [Theme] enum: the enum's initializer calls `android.graphics.Color`, so
 * keeping it out of this data class leaves the value object (and its
 * clamping/normalization logic) testable in plain JVM unit tests.
 */
data class ReaderSettings(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val themeName: String = THEME_LIGHT,
    val pageMargins: Float = DEFAULT_PAGE_MARGINS,
    val justified: Boolean = DEFAULT_JUSTIFIED,
    val highlightName: String = HIGHLIGHT_INK,
) {

    /** Font multiplier within the slider range ([FONT_SCALE_MIN]..[FONT_SCALE_MAX]). */
    val clampedFontScale: Double
        get() = fontScale.toDouble().coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)

    /** Margin factor within the slider range ([PAGE_MARGINS_MIN]..[PAGE_MARGINS_MAX]). */
    val clampedPageMargins: Double
        get() = pageMargins.toDouble().coerceIn(PAGE_MARGINS_MIN, PAGE_MARGINS_MAX)

    /** Stored theme name, or [THEME_LIGHT] for anything unrecognized. */
    val normalizedThemeName: String
        get() = when (themeName) {
            THEME_DARK, THEME_SEPIA -> themeName
            else -> THEME_LIGHT
        }

    /** Stored highlight palette name, or [HIGHLIGHT_INK] for anything unknown. */
    val normalizedHighlightName: String
        get() = when (highlightName) {
            HIGHLIGHT_AMBER, HIGHLIGHT_TEAL, HIGHLIGHT_BLUE, HIGHLIGHT_RED -> highlightName
            else -> HIGHLIGHT_INK
        }

    companion object {
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SEPIA = "sepia"

        /** M16 vocab weak-highlight palette names (selectable in reader settings). */
        const val HIGHLIGHT_INK = "ink"
        const val HIGHLIGHT_AMBER = "amber"
        const val HIGHLIGHT_TEAL = "teal"
        const val HIGHLIGHT_BLUE = "blue"
        const val HIGHLIGHT_RED = "red"

        val HIGHLIGHT_NAMES = listOf(
            HIGHLIGHT_INK, HIGHLIGHT_AMBER, HIGHLIGHT_TEAL, HIGHLIGHT_BLUE, HIGHLIGHT_RED,
        )

        const val DEFAULT_FONT_SCALE = 1.0f

        /** Matches the EpubDefaults margin factor used before M2b. */
        const val DEFAULT_PAGE_MARGINS = 1.4f

        /** Left-aligned (ragged right) by default; justify is opt-in (M9). */
        const val DEFAULT_JUSTIFIED = false

        const val FONT_SCALE_MIN = 0.8
        const val FONT_SCALE_MAX = 2.5
        const val PAGE_MARGINS_MIN = 0.5
        const val PAGE_MARGINS_MAX = 2.0
    }
}

/** Maps to Readium [EpubPreferences] for `submitPreferences` / `initialPreferences`. */
@OptIn(ExperimentalReadiumApi::class)
fun ReaderSettings.toEpubPreferences(): EpubPreferences {
    val dark = normalizedThemeName == ReaderSettings.THEME_DARK
    return EpubPreferences(
        fontSize = clampedFontScale,
        // M27: night mode no longer uses Readium's Theme.DARK — its stock
        // #FEFEFE-on-#000000 is maximum-glare pure contrast (and the pure
        // black breaks our own off-black rule). Neutral LIGHT base plus
        // explicit user colours instead: warm DarkPaper background with a
        // ~72% warm-grey ink (≈7.8:1, no halation at night). The --USER__
        // colour rules propagate through the book via ReadiumCSS's own
        // inherit selectors, so no publisherStyles flip is needed.
        theme = when (normalizedThemeName) {
            ReaderSettings.THEME_SEPIA -> Theme.SEPIA
            else -> Theme.LIGHT
        },
        backgroundColor = if (dark) Color(NIGHT_BACKGROUND) else null,
        textColor = if (dark) Color(NIGHT_TEXT) else null,
        pageMargins = clampedPageMargins,
    // M9: justify only makes sense for English with hyphenation — without
    // `hyphens: auto` the justified columns get ugly word gaps. Readium
    // injects the CSS; the EPUB chapters carry lang="en" (EpubPackager).
    //
    // Readium gotcha (3.3.0): every textAlign rule in ReadiumCSS is gated
    // behind `readium-advanced-on`, which only turns on when
    // publisherStyles == false (EpubSettingsKt maps advancedSettings =
    // !publisherStyles). With the default (publisher styles respected) a
    // JUSTIFY preference silently renders as left — so justify must disable
    // publisher styles. (advanced mode also auto-enables body hyphens for
    // justify, the explicit hyphens flag is belt and braces.)
        textAlign = if (justified) TextAlign.JUSTIFY else null,
        hyphens = if (justified) true else null,
        publisherStyles = if (justified) false else null,
    )
}

/** M27 night-reading palette (design-system.md §2): warm off-black page + ~72% warm-grey ink. */
private const val NIGHT_BACKGROUND = 0xFF171512.toInt()
private const val NIGHT_TEXT = 0xFFB8B3A8.toInt()

/**
 * SharedPreferences-backed store ("reader_prefs"): `fontScale` float,
 * `theme` string, `pageMargins` float, `justified` boolean (M9).
 */
class ReaderSettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ReaderSettings = ReaderSettings(
        fontScale = prefs.getFloat(KEY_FONT_SCALE, ReaderSettings.DEFAULT_FONT_SCALE),
        themeName = prefs.getString(KEY_THEME, ReaderSettings.THEME_LIGHT)
            ?: ReaderSettings.THEME_LIGHT,
        pageMargins = prefs.getFloat(KEY_PAGE_MARGINS, ReaderSettings.DEFAULT_PAGE_MARGINS),
        justified = prefs.getBoolean(KEY_JUSTIFIED, ReaderSettings.DEFAULT_JUSTIFIED),
        highlightName = prefs.getString(KEY_HIGHLIGHT, ReaderSettings.HIGHLIGHT_INK)
            ?: ReaderSettings.HIGHLIGHT_INK,
    )

    fun save(settings: ReaderSettings) {
        prefs.edit()
            .putFloat(KEY_FONT_SCALE, settings.fontScale)
            .putString(KEY_THEME, settings.normalizedThemeName)
            .putFloat(KEY_PAGE_MARGINS, settings.pageMargins)
            .putBoolean(KEY_JUSTIFIED, settings.justified)
            .putString(KEY_HIGHLIGHT, settings.normalizedHighlightName)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "reader_prefs"
        const val KEY_FONT_SCALE = "fontScale"
        const val KEY_THEME = "theme"
        const val KEY_PAGE_MARGINS = "pageMargins"
        const val KEY_JUSTIFIED = "justified"
        const val KEY_HIGHLIGHT = "highlight"
    }
}
