package dev.handypage.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.handypage.app.R
import dev.handypage.app.reader.ChatController

/**
 * The AI chat panel (DESIGN.md §4.9). Two hosts use it: the reader's M17
 * overlay drawer ([AgentDrawer], no drag gestures — closing goes through
 * [onClose]) and the Agent tab (full-screen). [onQuickGuide] is
 * EPUB-reader-only — null hides the 导读 chip. The header row renders when
 * either [onQuickGuide] or [onClose] is present. [applyInsets] adds
 * status/navigation-bar and IME padding for the drawer; the Agent tab
 * leaves it off because its Scaffold already handles insets.
 * [emptyState] (M15, §4.9) replaces the message list when the session is
 * empty; the reader sheet passes null and renders exactly as before.
 *
 * Assistant answers render Markdown (Markwon on a wrapped TextView) once
 * finished; the in-flight stream shows as plain text to avoid re-parsing
 * on every token.
 */
@Composable
fun ChatPanel(
    controller: ChatController,
    onQuickGuide: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    applyInsets: Boolean = true,
    inputHintRes: Int = R.string.agent_input_hint,
    emptyState: (@Composable () -> Unit)? = null,
    onOpenArticle: ((url: String, title: String, sourceName: String) -> Unit)? = null,
    onOpenPaper: ((absUrl: String, title: String) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val state by controller.state.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the latest message visible as content grows/streams.
    LaunchedEffect(state.messages.size, state.streamingText.length) {
        val count = state.messages.size + if (state.streamingText.isNotEmpty()) 1 else 0
        if (count > 0) listState.scrollToItem(count - 1)
    }

    val insetsModifier = if (applyInsets) {
        Modifier.statusBarsPadding().navigationBarsPadding().imePadding()
    } else {
        Modifier.imePadding()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(insetsModifier),
    ) {
        if (onQuickGuide != null || onClose != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.agent_panel_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onQuickGuide != null) {
                    AssistChip(
                        onClick = onQuickGuide,
                        enabled = state.ready && !state.busy,
                        label = { Text(stringResource(R.string.agent_quick_guide)) },
                    )
                }
                if (onClose != null) {
                    // M17: the drawer has no drag-to-dismiss (drags kept
                    // eating scroll gestures); this hairline-circle × is the
                    // explicit close affordance, next to the system back key.
                    IconButton(onClick = onClose) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, editorialHairlineColor()),
                            modifier = Modifier.size(26.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // M15: the Agent tab passes an editorial empty state (§4.9); the
        // reader sheet passes null and is unchanged.
        if (emptyState != null && state.messages.isEmpty() &&
            state.streamingText.isEmpty() && !state.busy
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { emptyState() }
        } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(
                    role = message.role,
                    text = message.text,
                    onOpenArticle = onOpenArticle,
                    onOpenPaper = onOpenPaper,
                )
            }
            if (state.streamingText.isNotEmpty()) {
                item(key = "streaming") {
                    MessageBubble(role = "assistant", text = state.streamingText, streaming = true)
                }
            }
            if (state.busy) {
                item(key = "status") { BusyStatus(state) }
            }
            if (state.toolLimitNotice) {
                item(key = "tool_limit") {
                    Text(
                        text = stringResource(R.string.agent_tool_limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }

        state.error?.let { error ->
            ErrorRow(
                error = error,
                noKey = state.noKey,
                retryable = state.errorRetryable && !state.busy,
                onRetry = { controller.retry() },
                onOpenSettings = onOpenSettings,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(inputHintRes)) },
                maxLines = 4,
                enabled = state.ready,
                shape = RoundedCornerShape(4.dp), // M15-R2: 显式 4dp (§4.10)
            )
            if (state.busy) {
                IconButton(onClick = { controller.stop() }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.agent_stop),
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        controller.send(input)
                        input = ""
                    },
                    enabled = state.ready && input.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.agent_send),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    role: String,
    text: String,
    streaming: Boolean = false,
    onOpenArticle: ((url: String, title: String, sourceName: String) -> Unit)? = null,
    onOpenPaper: ((absUrl: String, title: String) -> Unit)? = null,
) {
    val isUser = role == "user"
    // M16: parse structured cards from assistant messages (not while streaming).
    val (prose, cards) = if (!isUser && !streaming) parseCards(text) else text to null

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            // M15 (§4.8): 4dp corners; user = ink fill / paper text, AI =
            // transparent with a hairline stroke. Behaviour unchanged.
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                contentColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                border = if (isUser) {
                    null
                } else {
                    BorderStroke(1.dp, editorialHairlineColor())
                },
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (isUser || streaming) {
                        // M32: while streaming, a cards block under construction
                        // collapses into a placeholder instead of raw JSON text.
                        val (visible, generatingCards) =
                            if (!isUser) splitStreamingCards(text) else text to false
                        Column {
                            SelectionContainer {
                                Text(text = visible, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (generatingCards) {
                                Text(
                                    text = stringResource(R.string.agent_cards_generating),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    } else {
                        MarkdownText(markdown = prose)
                    }
                }
            }
            // M16: recommendation cards below the prose bubble.
            if (cards != null && onOpenArticle != null && onOpenPaper != null) {
                RecommendCardList(
                    cards = cards,
                    onOpenArticle = onOpenArticle,
                    onOpenPaper = onOpenPaper,
                )
            }
        }
    }
}

/** Streaming status line: thinking / generating / tool execution. */
@Composable
private fun BusyStatus(state: ChatController.UiState) {
    val label = when {
        state.toolRunning != null ->
            stringResource(R.string.agent_tool_running, state.toolRunning!!)
        state.thinking -> stringResource(R.string.agent_thinking)
        state.streamingText.isEmpty() -> stringResource(R.string.agent_generating)
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ErrorRow(
    error: String,
    noKey: Boolean,
    retryable: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (noKey) stringResource(R.string.agent_no_key) else error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        if (noKey) {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.agent_go_settings))
            }
        } else if (retryable) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.agent_retry))
            }
        }
    }
}
