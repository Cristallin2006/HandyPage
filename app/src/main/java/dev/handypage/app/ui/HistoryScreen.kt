package dev.handypage.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handypage.app.R
import dev.handypage.app.agent.ChatSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * M17: Agent conversation history page (editorial print style).
 * Lists all global Agent sessions grouped by date (today / yesterday / earlier),
 * with tap-to-switch and long-press-to-delete interactions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    sessions: List<ChatSession>,
    messageCounts: Map<Long, Int>,
    currentSessionKey: String,
    onSelectSession: (sessionKey: String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (sessionId: Long) -> Unit,
    onBack: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<ChatSession?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: back arrow + serif title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EditorialSpacing.lg)
                .padding(top = EditorialSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(end = EditorialSpacing.sm)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.history_title),
                style = EditorialType.section,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (sessions.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = EditorialSpacing.lg)
                    .padding(top = EditorialSpacing.xxl),
            ) {
                Text(
                    text = stringResource(R.string.history_empty_title),
                    style = EditorialType.emptyTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                EditorialHairline(modifier = Modifier.padding(vertical = EditorialSpacing.md))
                Text(
                    text = stringResource(R.string.history_empty_hint),
                    style = EditorialType.guide,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val grouped = groupByDate(sessions)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                for ((label, items) in grouped) {
                    item(key = "header_$label") {
                        EditorialSectionHeader(
                            title = label,
                            titleEn = null,
                            count = null,
                            modifier = Modifier.padding(top = EditorialSpacing.lg),
                        )
                    }
                    items(items, key = { it.id }) { session ->
                        val count = messageCounts[session.id] ?: 0
                        val isCurrent = session.articleKey == currentSessionKey
                        SessionRow(
                            session = session,
                            messageCount = count,
                            isCurrent = isCurrent,
                            onClick = { onSelectSession(session.articleKey) },
                            onLongClick = { deleteTarget = session },
                        )
                    }
                }
                item(key = "new_chat") {
                    OutlinedButton(
                        onClick = onNewSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EditorialSpacing.lg)
                            .padding(vertical = EditorialSpacing.xl),
                    ) {
                        Text(stringResource(R.string.history_new_chat))
                    }
                }
                item(key = "hint") {
                    Text(
                        text = stringResource(R.string.history_delete_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EditorialSpacing.lg)
                            .padding(bottom = EditorialSpacing.xl),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.history_delete_confirm,
                        target.title.ifBlank { "未命名对话" },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(target.id)
                    deleteTarget = null
                }) {
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: ChatSession,
    messageCount: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = EditorialSpacing.lg, vertical = 14.dp),
    ) {
        Text(
            text = session.title.ifBlank { "未命名对话" },
            style = MaterialTheme.typography.bodyLarge,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (isCurrent) {
                androidx.compose.ui.text.font.FontWeight.SemiBold
            } else {
                androidx.compose.ui.text.font.FontWeight.Medium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(messageCount).append(" 条消息")
                append(" · ")
                append(formatTime(session.updatedAt))
                if (isCurrent) append(" · 当前")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = EditorialSpacing.xs),
        )
        EditorialHairline(modifier = Modifier.padding(top = 14.dp))
    }
}

// --- Date grouping helpers ---

private fun groupByDate(sessions: List<ChatSession>): List<Pair<String, List<ChatSession>>> {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_YEAR)
    val thisYear = cal.get(Calendar.YEAR)

    val groups = LinkedHashMap<String, MutableList<ChatSession>>()
    for (s in sessions) {
        cal.timeInMillis = s.updatedAt
        val label = when {
            cal.get(Calendar.YEAR) == thisYear && cal.get(Calendar.DAY_OF_YEAR) == today -> "今天"
            cal.get(Calendar.YEAR) == thisYear && cal.get(Calendar.DAY_OF_YEAR) == today - 1 -> "昨天"
            else -> "更早"
        }
        groups.getOrPut(label) { mutableListOf() }.add(s)
    }
    return groups.toList()
}

private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())
private val DATE_FMT = SimpleDateFormat("M月d日", Locale.getDefault())

private fun formatTime(millis: Long): String {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = millis
    return if (cal.get(Calendar.DAY_OF_YEAR) == today) {
        TIME_FMT.format(Date(millis))
    } else {
        DATE_FMT.format(Date(millis))
    }
}
/**
 * Bridge composable wiring Room session data to [HistoryScreen].
 * Hosted in the NavHost; observes global sessions and message counts.
 */
@Composable
fun AgentHistoryRoute(
    currentSessionKey: () -> String,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as dev.handypage.app.HandypageApp
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val sessions by app.vocabDb.chatDao().observeGlobalSessions()
        .collectAsState(initial = emptyList())

    // Load message counts for each session (simple snapshot on composition).
    var messageCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    LaunchedEffect(sessions) {
        val dao = app.vocabDb.chatDao()
        val counts = sessions.associate { it.id to dao.countMessages(it.id) }
        messageCounts = counts
    }

    HistoryScreen(
        sessions = sessions,
        messageCounts = messageCounts,
        currentSessionKey = currentSessionKey(),
        onSelectSession = onSelectSession,
        onNewSession = onNewSession,
        onDeleteSession = { sessionId ->
            scope.launch {
                app.vocabDb.chatDao().deleteSessionWithMessages(sessionId)
            }
        },
        onBack = onBack,
    )
}