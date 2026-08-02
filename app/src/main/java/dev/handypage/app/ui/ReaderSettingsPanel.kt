package dev.handypage.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.handypage.app.R
import dev.handypage.app.reader.ReaderSettings
import dev.handypage.app.reader.VocabHighlight
import java.util.Locale
import kotlin.math.roundToInt

/**
 * M16 "Aa" reader settings panel (DESIGN.md §4.18): a Compose bottom panel
 * in the editorial language — segmented controls for theme/alignment, a
 * swatch row for the vocab-highlight palette, and sliders for font scale and
 * page margins. Every change applies immediately, so the article behind the
 * (scrim-free) panel is the live preview. Replaces the M2b AppCompat dialog.
 */
@Composable
fun ReaderSettingsPanel(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column {
            // Signature move (design-system.md §4.18): every bottom surface
            // rises behind a 2dp solid ink rule, like the bottom nav.
            EditorialInkRule(2.dp)
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = EditorialSpacing.lg, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.reader_settings_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            EditorialHairline()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EditorialSpacing.lg)
                    .padding(top = EditorialSpacing.lg, bottom = EditorialSpacing.xl)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SettingsGroup(label = stringResource(R.string.settings_theme)) {
                    EditorialSegmented(
                        options = listOf(
                            stringResource(R.string.settings_theme_light),
                            stringResource(R.string.settings_theme_sepia),
                            stringResource(R.string.settings_theme_dark),
                        ),
                        selectedIndex = when (settings.normalizedThemeName) {
                            ReaderSettings.THEME_SEPIA -> 1
                            ReaderSettings.THEME_DARK -> 2
                            else -> 0
                        },
                        onSelect = { index ->
                            val name = when (index) {
                                1 -> ReaderSettings.THEME_SEPIA
                                2 -> ReaderSettings.THEME_DARK
                                else -> ReaderSettings.THEME_LIGHT
                            }
                            onChange(settings.copy(themeName = name))
                        },
                    )
                }
                SettingsGroup(label = stringResource(R.string.settings_align)) {
                    EditorialSegmented(
                        options = listOf(
                            stringResource(R.string.settings_align_left),
                            stringResource(R.string.settings_align_justify),
                        ),
                        selectedIndex = if (settings.justified) 1 else 0,
                        onSelect = { index ->
                            onChange(settings.copy(justified = index == 1))
                        },
                    )
                }
                SettingsGroup(label = stringResource(R.string.settings_highlight_color)) {
                    HighlightSwatchRow(
                        settings = settings,
                        onSelect = { name -> onChange(settings.copy(highlightName = name)) },
                    )
                }
                SettingsGroup(
                    label = stringResource(R.string.settings_font_size),
                    output = String.format(
                        Locale.US, "%d%%", (settings.clampedFontScale * 100).roundToInt(),
                    ),
                ) {
                    EditorialSlider(
                        value = settings.clampedFontScale.toFloat(),
                        range = ReaderSettings.FONT_SCALE_MIN.toFloat()..
                            ReaderSettings.FONT_SCALE_MAX.toFloat(),
                        onValueChange = { onChange(settings.copy(fontScale = it)) },
                    )
                }
                SettingsGroup(
                    label = stringResource(R.string.settings_page_margins),
                    output = String.format(Locale.US, "%.2f", settings.clampedPageMargins),
                ) {
                    EditorialSlider(
                        value = settings.clampedPageMargins.toFloat(),
                        range = ReaderSettings.PAGE_MARGINS_MIN.toFloat()..
                            ReaderSettings.PAGE_MARGINS_MAX.toFloat(),
                        onValueChange = { onChange(settings.copy(pageMargins = it)) },
                    )
                }
            }
        }
    }
}

/** Label row (meta style, optional trailing tabular output) + control below. */
@Composable
private fun SettingsGroup(
    label: String,
    output: String? = null,
    content: @Composable () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = label,
                style = EditorialType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (output != null) {
                Text(
                    text = output,
                    style = EditorialType.count,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(EditorialSpacing.sm))
        content()
    }
}

/**
 * Editorial segmented control (design-system.md §4.10): 1dp hairline frame,
 * 6dp corners, selected segment = solid ink with paper text. No pill, no
 * elevation.
 */
@Composable
fun EditorialSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .border(1.dp, editorialHairlineColor(), RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp)),
    ) {
        options.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(editorialHairlineColor()),
                )
            }
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Vocab-highlight palette: five translucent swatches rendered over paper. */
@Composable
fun HighlightSwatchRow(
    settings: ReaderSettings,
    onSelect: (String) -> Unit,
) {
    val names = ReaderSettings.HIGHLIGHT_NAMES
    val labels = listOf(
        stringResource(R.string.highlight_ink),
        stringResource(R.string.highlight_amber),
        stringResource(R.string.highlight_teal),
        stringResource(R.string.highlight_blue),
        stringResource(R.string.highlight_red),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(EditorialSpacing.md)) {
        names.forEachIndexed { index, name ->
            val tint = VocabHighlight.tintForTheme(settings.normalizedThemeName, name)
            val selected = name == settings.normalizedHighlightName
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(tint))
                    .then(
                        if (selected) {
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(4.dp),
                            )
                        } else {
                            Modifier.border(
                                1.dp,
                                editorialHairlineColor(),
                                RoundedCornerShape(4.dp),
                            )
                        },
                    )
                    .semantics { contentDescription = labels[index] }
                    .clickable { onSelect(name) },
            )
        }
    }
}

/** Ink-track slider matching design-system.md §4.10. */
@Composable
private fun EditorialSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = editorialHairlineColor(),
        ),
    )
}
