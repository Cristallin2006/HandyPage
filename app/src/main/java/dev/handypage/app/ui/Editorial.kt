package dev.handypage.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared editorial components (docs/design-system.md §4): masthead, section
 * header, proxy badge, self-drawn bottom nav and segment tabs, plus the
 * spacing tokens and hairline/ink-rule primitives. Rules do all separation —
 * no cards, no selection colour blocks, no list shadows anywhere.
 */

/** Spacing tokens (§5): xs4 / sm8 / md12 / lg16 / xl24 / xxl32. */
object EditorialSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Notion whisper border (§2): black 10% light, white 14% dark. */
@Composable
fun editorialHairlineColor(): Color =
    if (isSystemInDarkTheme()) Color(0x24FFFFFF) else Color(0x1A000000)

/** 1dp whisper line, full width of its [modifier]. */
@Composable
fun EditorialHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(editorialHairlineColor()))
}

/** Solid ink rule; `primary` maps to ink in light, warm white in dark (§2). */
@Composable
fun EditorialInkRule(height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.primary))
}

/**
 * Masthead (§4.1): HANDYPAGE kicker row (right side is an optional tool
 * slot) → bilingual title (34sp Fraunces 700 + small uppercase English) →
 * meta line (real counts + date) → 2dp/1dp double ink rule.
 */
@Composable
fun EditorialMasthead(
    title: String,
    titleEn: String?,
    meta: String,
    modifier: Modifier = Modifier,
    kickerEnd: @Composable BoxScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EditorialSpacing.lg)
            .padding(top = 10.dp), // M15-R2: 刊头上 padding 收紧 20→10
    ) {
        // Kicker line height comes from the HANDYPAGE text alone: the tool
        // slot hangs on a zero-height centered anchor, so a 36dp IconButton
        // (Agent history, settings sub-page back) can't push the title row
        // down and break title alignment across tabs.
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = "HANDYPAGE",
                style = EditorialType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .height(0.dp)
                    .wrapContentHeight(Alignment.CenterVertically, unbounded = true),
            ) {
                kickerEnd()
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = EditorialSpacing.xs),
        ) {
            Text(
                text = title,
                style = EditorialType.masthead,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, // 刊头标题一行不换 (§8)
            )
            if (titleEn != null) {
                Text(
                    text = titleEn.uppercase(Locale.US),
                    style = EditorialType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = EditorialSpacing.md,
                        bottom = 6.dp,
                    ),
                )
            }
        }
        Text(
            text = meta,
            style = EditorialType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = EditorialSpacing.sm),
        )
        Spacer(Modifier.height(EditorialSpacing.md)) // meta 距双细线 12 (§5)
        EditorialInkRule(2.dp)
        Spacer(Modifier.height(3.dp)) // 双细线间距 3dp (§4.1)
        EditorialInkRule(1.dp)
    }
}

/**
 * Section header (§4.2): 中文衬线 18sp/600 + uppercase English 11sp on the
 * same baseline + trailing tabular count, over a 1dp solid ink full-width
 * rule (not a hairline).
 */
@Composable
fun EditorialSectionHeader(
    title: String,
    titleEn: String?,
    count: Int?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = EditorialType.section,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (titleEn != null) {
                Text(
                    text = titleEn.uppercase(Locale.US),
                    style = EditorialType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = EditorialSpacing.sm,
                        bottom = 2.dp,
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = EditorialType.count,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(EditorialSpacing.xs))
        EditorialInkRule(1.dp)
    }
}

/** Proxy badge (§4.4): pill, red label on red 12% fill with red 35% stroke. */
@Composable
fun ProxyBadge(text: String, modifier: Modifier = Modifier) {
    val red = MaterialTheme.colorScheme.error
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = red.copy(alpha = 0.12f),
        contentColor = red,
        border = BorderStroke(1.dp, red.copy(alpha = 0.35f)),
    ) {
        Text(
            text = text,
            style = EditorialType.badge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** One bottom-nav destination: [glyph] is a Pinyon Script letter (§4.5 B). */
data class EditorialNavItem(val route: String, val label: String, val glyph: String)

/**
 * Bottom nav (§4.5, replaces M3 NavigationBar): 2dp solid ink top rule, four
 * equal icon+label items. Selected = 600 weight + 2dp dash under the label,
 * both in the theme accent (M38: colorScheme.primary — ink in Classic);
 * unselected = sub colour. No pill, no colour block, no elevation.
 *
 * M22 (§9): the dash is ONE indicator sliding between items (200ms
 * standard), not a per-item box popping in and out; each item keeps a
 * transparent layout twin so geometry is unchanged. Label/icon colour
 * crossfades in 120ms.
 */
@Composable
fun EditorialNavBar(
    items: List<EditorialNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val dashSpots = remember { mutableStateMapOf<Int, Offset>() }
    var rowRoot by remember { mutableStateOf(Offset.Zero) }
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        EditorialInkRule(2.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { rowRoot = it.boundsInRoot().topLeft },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) {
                            // M38: selected state tracks the theme accent
                            // (primary == ink in Classic, so no visual delta).
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(HpMotion.State, easing = HpMotion.Standard),
                        label = "navContent",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 56.dp) // ≥48dp touch target (§8)
                            .clickable { onSelect(item.route) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // M15-R2: 图标+文字+线整体垂直居中,线不贴 bar 底缘(真机被裁)
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // M23: Pinyon Script letter in a fixed 28dp box —
                        // script advance heights vary, the box keeps the bar
                        // geometry identical across glyphs and states.
                        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = item.glyph,
                                fontFamily = PinyonFamily,
                                fontSize = 22.sp,
                                color = contentColor,
                            )
                        }
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = contentColor,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        // M15-R2: 文字正下方 2dp; transparent twin of the dash.
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .width(24.dp)
                                .height(2.dp)
                                .onGloballyPositioned { dashSpots[index] = it.boundsInRoot().topLeft },
                        )
                    }
                }
            }
            val spot = dashSpots[selectedIndex]
            if (spot != null) {
                val target = spot - rowRoot
                val dashX = remember { Animatable(target.x) }
                LaunchedEffect(target) {
                    dashX.animateTo(
                        target.x,
                        tween(HpMotion.Indicator, easing = HpMotion.Standard),
                    )
                }
                Box(
                    Modifier
                        .offset { IntOffset(dashX.value.roundToInt(), target.y.roundToInt()) }
                        .width(24.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * Segment tabs (§4.6, 本机四段): 15sp labels, selected = 600 + 2dp underline,
 * both in the theme accent (M38), full-width hairline baseline, horizontally
 * scrollable.
 *
 * M22 (§9): same single-sliding-indicator treatment as the bottom nav —
 * the underline glides between segments (200ms standard); each label keeps
 * a transparent layout twin so geometry is unchanged.
 */
@Composable
fun EditorialTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val dashSpots = remember { mutableStateMapOf<Int, Offset>() }
        var rowRoot by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { rowRoot = it.boundsInRoot().topLeft },
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = EditorialSpacing.xs),
            ) {
                tabs.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) {
                            // M38: selected state tracks the theme accent
                            // (primary == ink in Classic, so no visual delta).
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(HpMotion.State, easing = HpMotion.Standard),
                        label = "tabContent",
                    )
                    Column(
                        modifier = Modifier
                            .clickable { onSelect(index) }
                            .padding(horizontal = EditorialSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = contentColor,
                            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                        )
                        Box(
                            Modifier
                                .width(24.dp)
                                .height(2.dp)
                                .onGloballyPositioned { dashSpots[index] = it.boundsInRoot().topLeft },
                        )
                    }
                }
            }
            val spot = dashSpots[selectedIndex]
            if (spot != null) {
                val target = spot - rowRoot
                val dashX = remember { Animatable(target.x) }
                LaunchedEffect(target) {
                    dashX.animateTo(
                        target.x,
                        tween(HpMotion.Indicator, easing = HpMotion.Standard),
                    )
                }
                Box(
                    Modifier
                        .offset { IntOffset(dashX.value.roundToInt(), target.y.roundToInt()) }
                        .width(24.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        EditorialHairline()
    }
}

/**
 * Masthead portal host (M22-R2): the four tab screens register their
 * masthead content here instead of drawing it inline; MainActivity renders
 * the active route's slot in the shell above the NavHost. The page header
 * therefore never participates in tab transitions (the R1 "masthead jumps"
 * complaint) — the shell crossfades its text in 120ms while only the body
 * below animates. Hosts that don't provide [LocalMastheadHost]
 * (SettingsActivity) leave screens rendering their own masthead inline.
 */
class MastheadHost {
    val slots = mutableStateMapOf<String, @Composable () -> Unit>()
}

val LocalMastheadHost = staticCompositionLocalOf<MastheadHost?> { null }

/**
 * Register [content] as [key]'s shell masthead when a host is present,
 * otherwise render it inline. The slot is cleared on dispose unless a newer
 * registration already replaced it (tab switches re-register immediately).
 */
@Composable
fun EditorialMastheadSlot(key: String, content: @Composable () -> Unit) {
    val host = LocalMastheadHost.current
    if (host == null) {
        content()
        return
    }
    val latest = rememberUpdatedState(content)
    SideEffect { host.slots[key] = latest.value }
    DisposableEffect(host, key) {
        onDispose {
            if (host.slots[key] === latest.value) host.slots.remove(key)
        }
    }
}

/**
 * Star glyph with M22 state feedback (§9): a 120ms crossfade between the
 * dual-coded pair (solid ink = starred, grey = not, §4.12). Shared by every
 * star toggle — reader top bars, article rows, paper rows.
 */@Composable
fun EditorialStarIcon(
    starred: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = starred,
        animationSpec = tween(HpMotion.State, easing = HpMotion.Standard),
        modifier = modifier,
        label = "star",
    ) { on ->
        Icon(
            imageVector = if (on) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = contentDescription,
            tint = if (on) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
