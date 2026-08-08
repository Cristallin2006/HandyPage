package dev.handypage.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.handypage.app.R

/**
 * Editorial print theme (docs/design-system.md §2/§3): fixed light/dark
 * schemes per [AppTheme] (M38) — dynamic wallpaper colour is gone. Paper
 * surface, ink text, warm greys; the theme hue lives in the accent roles
 * (primary / primaryContainer). The single editorial red is reserved for the
 * proxy badge and destructive actions (M3 error role) in EVERY theme.
 * Light/dark follows the system (or the reader theme in ReaderActivity).
 */
private val Red = Color(0xFFB3352B)
private val DarkRed = Color(0xFFD4574A)

/**
 * Maps an [AppThemePalette] (plain Longs) to a Material3 scheme. Neutral
 * roles come from paper/ink/sub/faint; the hue enters through the primary
 * roles. Classic reproduces the pre-M38 fixed schemes exactly, except
 * inversePrimary (an unused-in-app role) which now tracks accentSoft.
 */
private fun schemeFor(p: AppThemePalette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(p.accent),
        onPrimary = Color(p.onAccent),
        primaryContainer = Color(p.accentSoft),
        onPrimaryContainer = Color(p.onAccentSoft),
        secondary = Color(p.sub),
        onSecondary = Color(p.paper),
        secondaryContainer = Color(p.paperAlt),
        onSecondaryContainer = Color(p.sub),
        tertiary = Color(p.sub),
        onTertiary = Color(p.paper),
        tertiaryContainer = Color(p.paperAlt),
        onTertiaryContainer = Color(p.sub),
        error = if (dark) DarkRed else Red,
        onError = Color(p.paper),
        background = Color(p.paper),
        onBackground = Color(p.ink),
        surface = Color(p.paper),
        onSurface = Color(p.ink),
        surfaceVariant = Color(p.paperAlt),
        onSurfaceVariant = Color(p.sub),
        surfaceTint = Color(p.accent),
        inverseSurface = Color(p.ink),
        inverseOnSurface = Color(p.paper),
        inversePrimary = Color(p.accentSoft),
        outline = Color(p.faint),
        outlineVariant = Color(p.hairline),
        surfaceBright = if (dark) Color(p.paperAlt) else Color(p.paper),
        surfaceDim = if (dark) Color(p.paper) else Color(p.paperAlt),
        surfaceContainerLowest = Color(p.paper),
        surfaceContainerLow = Color(p.paper),
        surfaceContainer = Color(p.paper),
        surfaceContainerHigh = Color(p.paperAlt),
        surfaceContainerHighest = Color(p.paperAlt),
    )
}

/**
 * Fraunces serif display face (SIL OFL 1.1, see res/font/OFL.txt). CJK
 * glyphs fall back to the system stack automatically; Latin/digits render
 * in the serif.
 */
val FrauncesFamily = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_bold, FontWeight.Bold),
)

/**
 * Pinyon Script copperplate hand (SIL OFL 1.1, see
 * res/font/pinyon_script_OFL.txt) — a ~9KB subset (R/L/A/S) used ONLY for
 * the bottom-nav glyph letters (design-system.md §4.5, B · 花体手书).
 */
val PinyonFamily = FontFamily(
    Font(R.font.pinyon_script, FontWeight.Normal),
)

/**
 * Named editorial text styles (design-system.md §3/§4). No colour is baked
 * in — callers apply scheme colours. Body/UI text keeps the M3 default
 * system stack; these styles cover only the serif display and label roles.
 */
object EditorialType {
    /** Masthead title 34sp/700. */
    val masthead = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    )

    /** Section header 中文 18sp/600. */
    val section = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )

    /** Source-row serif monogram 20sp/600 (§3; §4.3's 22sp is superseded). */
    val monogram = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    )

    /** Vocab headword 18sp/600. */
    val headword = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )

    /** Empty-state title 20sp/600. */
    val emptyTitle = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )

    /** Kicker/meta/labels: 11sp / 500 / .12em tracking (caller uppercases). */
    val meta = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.12f.em,
    )

    /** Section-header trailing count: 12sp tabular figures. */
    val count = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        fontFeatureSettings = "tnum",
    )

    /** Proxy badge label: 11sp/600 with Notion-style micro tracking. */
    val badge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.02f.em,
    )

    /** Empty-state guide copy 13sp, CJK line height ≥1.5. */
    val guide = TextStyle(
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}

/** Corner radius discipline (§1): small=2, medium=4, large=8. */
private val EditorialShapes = Shapes(
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(8.dp),
)

@Composable
fun HandypageTheme(
    appTheme: AppTheme = AppTheme.CLASSIC,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = schemeFor(appTheme.palette(darkTheme), darkTheme),
        shapes = EditorialShapes,
        content = content,
    )
}
