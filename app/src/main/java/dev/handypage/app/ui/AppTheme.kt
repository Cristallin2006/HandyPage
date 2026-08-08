package dev.handypage.app.ui

import android.content.Context
import androidx.annotation.StringRes
import dev.handypage.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * M38 switchable app-wide colour themes (验收记录见 DESIGN.md §6 M38): six dual-tone
 * schemes for the main-UI Compose chrome (tabs, settings, agent, reader
 * chrome). Each theme pairs a deep accent with a same-family soft container,
 * over barely-hued paper/ink neutrals — the editorial layout stays neutral,
 * the hue lives in accent positions (selected tab, filled buttons, toggles,
 * badges).
 *
 * Boundaries: the READING surface keeps its own M2b/M27 palettes (reader
 * themes are untouched), and the semantic red (proxy badge, errors) never
 * changes with the theme.
 *
 * Colours are plain Long hex (not androidx Color) so the palette mapping is
 * JVM-testable; Theme.kt turns a palette into a Material3 ColorScheme.
 * Dark variants lift the accent one step so it stays readable on dark paper.
 */
data class AppThemePalette(
    val paper: Long,
    val paperAlt: Long,
    val ink: Long,
    val sub: Long,
    val faint: Long,
    val hairline: Long,
    val accent: Long,
    val onAccent: Long,
    val accentSoft: Long,
    val onAccentSoft: Long,
)

/** App UI themes. [labelEn] is decorative (English subscript), not localized. */
enum class AppTheme(
    val id: String,
    @StringRes val labelRes: Int,
    val labelEn: String,
    val light: AppThemePalette,
    val dark: AppThemePalette,
) {
    CLASSIC(
        id = "classic",
        labelRes = R.string.app_theme_classic,
        labelEn = "Classic Ink",
        light = AppThemePalette(
            paper = 0xFFFBFAF7, paperAlt = 0xFFF3F0E9,
            ink = 0xFF141414, sub = 0xFF5C5C58, faint = 0xFFA39E98,
            hairline = 0x1A000000,
            accent = 0xFF141414, onAccent = 0xFFFBFAF7,
            accentSoft = 0xFFF3F0E9, onAccentSoft = 0xFF141414,
        ),
        dark = AppThemePalette(
            paper = 0xFF171512, paperAlt = 0xFF26231E,
            ink = 0xFFECE7DB, sub = 0xFFA8A29A, faint = 0xFF6F6A62,
            hairline = 0x24FFFFFF,
            accent = 0xFFECE7DB, onAccent = 0xFF171512,
            accentSoft = 0xFF26231E, onAccentSoft = 0xFFECE7DB,
        ),
    ),
    FOREST(
        id = "forest",
        labelRes = R.string.app_theme_forest,
        labelEn = "Forest",
        light = AppThemePalette(
            paper = 0xFFF5F7F3, paperAlt = 0xFFE8EDE5,
            ink = 0xFF182420, sub = 0xFF54665A, faint = 0xFF9AA89C,
            hairline = 0x1A182420,
            accent = 0xFF2E6B4F, onAccent = 0xFFFBFDFB,
            accentSoft = 0xFFDCE7DD, onAccentSoft = 0xFF1E4634,
        ),
        dark = AppThemePalette(
            paper = 0xFF141B17, paperAlt = 0xFF212B24,
            ink = 0xFFDCE5DC, sub = 0xFF93A396, faint = 0xFF57685B,
            hairline = 0x26DCE5DC,
            accent = 0xFF8FC0A4, onAccent = 0xFF0F1713,
            accentSoft = 0xFF24352B, onAccentSoft = 0xFFA8CDB8,
        ),
    ),
    BURGUNDY(
        id = "burgundy",
        labelRes = R.string.app_theme_burgundy,
        labelEn = "Burgundy",
        light = AppThemePalette(
            paper = 0xFFF9F4F2, paperAlt = 0xFFF0E4E1,
            ink = 0xFF251A1C, sub = 0xFF6E5457, faint = 0xFFB4A09E,
            hairline = 0x1A251A1C,
            accent = 0xFF7E2F3A, onAccent = 0xFFFDF7F6,
            accentSoft = 0xFFF0DCDB, onAccentSoft = 0xFF5A1F28,
        ),
        dark = AppThemePalette(
            paper = 0xFF1C1415, paperAlt = 0xFF2C1F21,
            ink = 0xFFE5DCDC, sub = 0xFFA49393, faint = 0xFF6B5557,
            hairline = 0x26E5DCDC,
            accent = 0xFFD08D94, onAccent = 0xFF1C1113,
            accentSoft = 0xFF3A2226, onAccentSoft = 0xFFE0AAAF,
        ),
    ),
    PRUSSIAN(
        id = "prussian",
        labelRes = R.string.app_theme_prussian,
        labelEn = "Prussian",
        light = AppThemePalette(
            paper = 0xFFF3F6F9, paperAlt = 0xFFE2E9F0,
            ink = 0xFF161E26, sub = 0xFF4E6070, faint = 0xFF97A5B1,
            hairline = 0x1A161E26,
            accent = 0xFF2C547E, onAccent = 0xFFF7FAFD,
            accentSoft = 0xFFDCE5EE, onAccentSoft = 0xFF1E3D5E,
        ),
        dark = AppThemePalette(
            paper = 0xFF121A22, paperAlt = 0xFF1D2934,
            ink = 0xFFDCE4EC, sub = 0xFF8FA0B0, faint = 0xFF4E6070,
            hairline = 0x26DCE4EC,
            accent = 0xFF8FB3D4, onAccent = 0xFF0E151C,
            accentSoft = 0xFF1F3040, onAccentSoft = 0xFFA9C6E0,
        ),
    ),
    IRIS(
        id = "iris",
        labelRes = R.string.app_theme_iris,
        labelEn = "Iris",
        light = AppThemePalette(
            paper = 0xFFF7F4FA, paperAlt = 0xFFE9E3F0,
            ink = 0xFF1D1A26, sub = 0xFF605A72, faint = 0xFFA5A0B2,
            hairline = 0x1A1D1A26,
            accent = 0xFF6A5296, onAccent = 0xFFFBF9FD,
            accentSoft = 0xFFE7DFF0, onAccentSoft = 0xFF4A3870,
        ),
        dark = AppThemePalette(
            paper = 0xFF17141F, paperAlt = 0xFF242030,
            ink = 0xFFE2DDEA, sub = 0xFF9C95AC, faint = 0xFF5C5670,
            hairline = 0x26E2DDEA,
            accent = 0xFFB9A3D6, onAccent = 0xFF16121E,
            accentSoft = 0xFF2A2338, onAccentSoft = 0xFFC9B8E2,
        ),
    ),
    OAT(
        id = "oat",
        labelRes = R.string.app_theme_oat,
        labelEn = "Oat Latte",
        light = AppThemePalette(
            paper = 0xFFF8F5EF, paperAlt = 0xFFEFE7D9,
            ink = 0xFF211D15, sub = 0xFF665D48, faint = 0xFFADA48D,
            hairline = 0x1A211D15,
            accent = 0xFF8A6D3B, onAccent = 0xFFFDFAF3,
            accentSoft = 0xFFEDE3CF, onAccentSoft = 0xFF664E24,
        ),
        dark = AppThemePalette(
            paper = 0xFF1A1712, paperAlt = 0xFF2A251C,
            ink = 0xFFE6DFD2, sub = 0xFFA79E88, faint = 0xFF665D48,
            hairline = 0x26E6DFD2,
            accent = 0xFFC9A86C, onAccent = 0xFF191510,
            accentSoft = 0xFF352C1D, onAccentSoft = 0xFFD9BE8A,
        ),
    ),
    ;

    fun palette(dark: Boolean): AppThemePalette = if (dark) this.dark else this.light

    companion object {
        /** Stored id → theme; anything unrecognized falls back to [CLASSIC]. */
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/**
 * Holds the active app theme, persisted in SharedPreferences ("app_prefs").
 * Initialised once from the Application; Compose hosts collect [theme] and
 * pass it to HandypageTheme, so a switch in settings repaints every screen
 * live without an activity recreate.
 */
object AppThemeController {

    private const val PREFS = "app_prefs"
    private const val KEY_THEME = "app_theme"

    private var prefs: android.content.SharedPreferences? = null

    private val _theme = MutableStateFlow(AppTheme.CLASSIC)

    /** Observable active theme; CLASSIC until [init] loads the stored one. */
    val theme: StateFlow<AppTheme> = _theme

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _theme.value = AppTheme.fromId(p.getString(KEY_THEME, null))
    }

    fun set(theme: AppTheme) {
        prefs?.edit()?.putString(KEY_THEME, theme.id)?.apply()
        _theme.value = theme
    }
}
