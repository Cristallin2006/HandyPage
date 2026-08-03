package dev.handypage.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * schemes — dynamic wallpaper colour is gone. Paper-white surface, off-black
 * ink, warm greys; the single editorial red is reserved for the proxy badge
 * and destructive actions (M3 error role). Light/dark follows the system.
 */
private val Paper = Color(0xFFFBFAF7)
private val PaperAlt = Color(0xFFF3F0E9)
private val Ink = Color(0xFF141414)
private val Sub = Color(0xFF5C5C58)
private val Faint = Color(0xFFA39E98)
private val Red = Color(0xFFB3352B)

private val DarkPaper = Color(0xFF171512)
private val DarkPaperAlt = Color(0xFF26231E)
private val DarkInk = Color(0xFFECE7DB)
private val DarkSub = Color(0xFFA8A29A)
private val DarkRed = Color(0xFFD4574A)

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = PaperAlt,
    onPrimaryContainer = Ink,
    secondary = Sub,
    onSecondary = Paper,
    secondaryContainer = PaperAlt,
    onSecondaryContainer = Sub,
    tertiary = Sub,
    onTertiary = Paper,
    tertiaryContainer = PaperAlt,
    onTertiaryContainer = Sub,
    error = Red,
    onError = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperAlt,
    onSurfaceVariant = Sub,
    surfaceTint = Ink,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = PaperAlt,
    outline = Faint,
    outlineVariant = Color(0x1A000000), // hairline: black 10%
    surfaceBright = Paper,
    surfaceDim = PaperAlt,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Paper,
    surfaceContainerHigh = PaperAlt,
    surfaceContainerHighest = PaperAlt,
)

private val DarkScheme = darkColorScheme(
    primary = DarkInk,
    onPrimary = DarkPaper,
    primaryContainer = DarkPaperAlt,
    onPrimaryContainer = DarkInk,
    secondary = DarkSub,
    onSecondary = DarkPaper,
    secondaryContainer = DarkPaperAlt,
    onSecondaryContainer = DarkSub,
    tertiary = DarkSub,
    onTertiary = DarkPaper,
    tertiaryContainer = DarkPaperAlt,
    onTertiaryContainer = DarkSub,
    error = DarkRed,
    onError = DarkPaper,
    background = DarkPaper,
    onBackground = DarkInk,
    surface = DarkPaper,
    onSurface = DarkInk,
    surfaceVariant = DarkPaperAlt,
    onSurfaceVariant = DarkSub,
    surfaceTint = DarkInk,
    inverseSurface = DarkInk,
    inverseOnSurface = DarkPaper,
    inversePrimary = Ink,
    outline = DarkSub,
    outlineVariant = Color(0x24FFFFFF), // hairline: white 14%
    surfaceBright = DarkPaperAlt,
    surfaceDim = DarkPaper,
    surfaceContainerLowest = DarkPaper,
    surfaceContainerLow = DarkPaper,
    surfaceContainer = DarkPaper,
    surfaceContainerHigh = DarkPaperAlt,
    surfaceContainerHighest = DarkPaperAlt,
)

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        shapes = EditorialShapes,
        content = content,
    )
}
