package dev.handypage.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.handypage.app.reader.ChatController

/**
 * M17 shared AI drawer surface (DESIGN.md §4.9): a full-height overlay that
 * slides up from the bottom — open/close is a pure animation; there is NO
 * drag gesture (drag-to-dismiss kept eating chat-scroll gestures and page
 * swipes). Closing goes through the header's close button or the system
 * back gesture; opening through the bottom summon swipe or the AI action.
 *
 * Both readers host their [ChatPanel] through this: the 8dp top corners and
 * the 2dp solid ink rule are the M16 bottom-surface signature.
 */
@Composable
fun AgentDrawer(
    controller: ChatController?,
    onQuickGuide: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            // Being a pointer-input target at all is what keeps taps and
            // scrolls from falling through to the reader underneath while
            // the drawer is open; the events themselves are left for the
            // drawer's children.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            EditorialInkRule(2.dp)
            if (controller == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                ChatPanel(
                    controller = controller,
                    onQuickGuide = onQuickGuide,
                    onOpenSettings = onOpenSettings,
                    onClose = onClose,
                )
            }
        }
    }
}
