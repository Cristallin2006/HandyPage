package dev.handypage.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.handypage.app.R
import dev.handypage.app.reader.ChatController
import dev.handypage.app.reader.ReaderSettings
import java.util.Locale

/**
 * Reader shell (DESIGN.md §4.9, M16 restyle §4.18): the body IS the reader
 * (the Readium navigator Fragment via `AndroidFragment`, passed as [body]);
 * the AI chat panel is an [AgentDrawer] overlay. M16 wraps the body in
 * editorial chrome:
 *
 * - a newspaper-style top bar (source kicker + Fraunces title + Aa/guide/AI
 *   actions), shown on open and toggled by article single-taps ([tapSignals]);
 * - the "Aa" reader settings as a Compose bottom panel (no scrim — the
 *   article is the live preview);
 * - every bottom surface (chat drawer, settings panel, word panel) rises
 *   behind the same 2dp solid ink rule as the bottom nav.
 *
 * M17: the M3 BottomSheetScaffold is gone — its drag-to-dismiss kept eating
 * chat scrolls and page swipes. The drawer now opens/closes by animation
 * only ([openRequests] opens; the header × button or system back closes).
 */
@Composable
fun ReaderShell(
    controller: ChatController?,
    openRequests: Int,
    title: String,
    kicker: String,
    readerSettings: ReaderSettings,
    tapSignals: Int,
    starred: Boolean?,
    onStar: () -> Unit,
    onSummon: () -> Unit,
    onGuide: () -> Unit,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    onOpenSettings: () -> Unit,
    summonZoneEnabled: Boolean = true,
    body: @Composable () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var chatOpen by remember { mutableStateOf(false) }

    LaunchedEffect(openRequests) {
        if (openRequests > 0) {
            settingsOpen = false
            chatOpen = true
        }
    }
    LaunchedEffect(tapSignals) {
        if (tapSignals > 0) chromeVisible = !chromeVisible
    }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = chatOpen && !settingsOpen) { chatOpen = false }

    Box(modifier = Modifier.fillMaxSize()) {
        // The reader itself (Readium navigator via AndroidFragment).
        body()

        ReaderTopBar(
            visible = chromeVisible && title.isNotBlank(),
            kicker = kicker,
            title = title,
            starred = starred,
            onStar = onStar,
            onAa = { settingsOpen = true },
            onGuide = onGuide,
            onAi = onSummon,
        )

        // "Aa" settings overlay: scrim-free, so the article stays the
        // live preview; a full-screen tap catcher dismisses it.
        if (settingsOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { settingsOpen = false } },
            )
        }
        AnimatedVisibility(
            visible = settingsOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = HpMotion.sheetEnter(),
            exit = HpMotion.sheetExit(),
        ) {
            ReaderSettingsPanel(
                settings = readerSettings,
                onChange = onReaderSettingsChange,
                onClose = { settingsOpen = false },
            )
        }

        // Bottom summon zone: consumes the DOWN event (so the gesture
        // stream keeps arriving) and fires once the finger has travelled
        // far enough upward. Taps and horizontal page swipes are left
        // to the reader underneath… except within this strip, which is
        // the accepted trade-off for the summon gesture.
        //
        // M8: the zone is REMOVED while the word panel is open — its
        // invisible 48dp strip overlaps the panel's bottom button row
        // and would swallow those taps (the M8 save-button regression).
        // M16: also removed while the settings overlay eats all taps.
        // M17: and while the chat drawer is open.
        if (summonZoneEnabled && !settingsOpen && !chatOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var total = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                total += change.positionChange().y
                                if (total < -SUMMON_TRAVEL_PX) {
                                    onSummon()
                                    break
                                }
                            }
                        }
                    },
            )
        }

        AnimatedVisibility(
            visible = chatOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = HpMotion.sheetEnter(),
            exit = HpMotion.sheetExit(),
        ) {
            AgentDrawer(
                controller = controller,
                onQuickGuide = onGuide,
                onOpenSettings = onOpenSettings,
                onClose = { chatOpen = false },
            )
        }
    }
}

/**
 * Newspaper top bar (M16): source kicker (11sp uppercase meta) over a
 * single-line Fraunces article title, with Aa / guide / AI actions at the
 * trailing edge; a 1dp hairline separates it from the page. Slides up out
 * of view for immersive reading; an article single-tap brings it back.
 */
@Composable
private fun BoxScope.ReaderTopBar(
    visible: Boolean,
    kicker: String,
    title: String,
    starred: Boolean?,
    onStar: () -> Unit,
    onAa: () -> Unit,
    onGuide: () -> Unit,
    onAi: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = HpMotion.barEnter(),
        exit = HpMotion.barExit(),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(start = EditorialSpacing.lg, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = EditorialSpacing.sm),
                    ) {
                        Text(
                            text = kicker.uppercase(Locale.US),
                            style = EditorialType.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = title,
                            fontFamily = FrauncesFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    IconButton(onClick = onAa) {
                        Text(
                            text = "Aa",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onGuide) {
                        Icon(
                            imageVector = Icons.Filled.AutoStories,
                            contentDescription = stringResource(R.string.action_guide),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    // M21 article star: dual encoding (solid ink = starred,
                    // hollow grey = not); hidden when starred == null.
                    // M22: 120ms crossfade via the shared EditorialStarIcon.
                    if (starred != null) {
                        IconButton(onClick = onStar) {
                            EditorialStarIcon(
                                starred = starred,
                                contentDescription = stringResource(
                                    if (starred) R.string.star_remove else R.string.star_add,
                                ),
                            )
                        }
                    }
                    IconButton(onClick = onAi) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = stringResource(R.string.agent_panel_title),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                EditorialHairline()
            }
        }
    }
}

private const val SUMMON_TRAVEL_PX = 150f
