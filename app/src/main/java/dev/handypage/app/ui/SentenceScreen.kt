package dev.handypage.app.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handypage.app.HandypageApp
import dev.handypage.app.R
import dev.handypage.app.ai.AIEvent
import dev.handypage.app.ai.AIFactory
import dev.handypage.app.ai.Prompts
import dev.handypage.app.vocab.SavedSentence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sentence book content (DESIGN.md §4.13, M10): saved sentences newest-first
 * from Room. Tap a row for the detail dialog — full text, a manually edited
 * 注释, and a one-shot AI note (translation + grammar breakdown + key
 * vocabulary) that streams into the dialog and persists on completion.
 * Long-press (or the detail dialog's 删除 button) deletes after a
 * confirmation. Scaffold-free like [VocabBookContent]; the 本机 tab hosts it.
 */
@Composable
fun SentenceBookContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Null until Room's first emission, so the empty state doesn't flash.
    val sentences by produceState<List<SavedSentence>?>(initialValue = null, app) {
        app.vocabDb.savedSentenceDao().observeAll().collect { value = it }
    }

    // Detail/edit/delete target by id, resolved against the live list so the
    // dialogs always render the freshest row after a note/aiNote update.
    var detailId by remember { mutableStateOf<Long?>(null) }
    var editNoteId by remember { mutableStateOf<Long?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }

    Box(modifier = modifier) {
        val list = sentences
        when {
            list == null -> Box(modifier = Modifier.fillMaxSize())
            list.isEmpty() -> StatusPanel(text = stringResource(R.string.sentence_empty))
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { sentence ->
                    Column { // M15: row + hairline under it (§4.3)
                    SentenceRow(
                        sentence = sentence,
                        date = dateFormat.format(Date(sentence.addedAt)),
                        onClick = { detailId = sentence.id },
                        onLongClick = { deleteId = sentence.id },
                    )
                    EditorialHairline(modifier = Modifier.padding(horizontal = EditorialSpacing.lg))
                    }
                }
            }
        }
    }

    // Targets resolve against the live list, so a note/aiNote update is
    // reflected immediately and a deleted-elsewhere row closes its dialog.
    sentences?.firstOrNull { it.id == detailId }?.let { s ->
        SentenceDetailDialog(
            sentence = s,
            date = dateFormat.format(Date(s.addedAt)),
            onDismiss = { detailId = null },
            onEditNote = { editNoteId = s.id },
            onDelete = {
                detailId = null
                deleteId = s.id
            },
        )
    }

    sentences?.firstOrNull { it.id == editNoteId }?.let { s ->
        NoteEditDialog(
            initial = s.note,
            onDismiss = { editNoteId = null },
            onSave = { note ->
                editNoteId = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        app.vocabDb.savedSentenceDao().updateNote(s.id, note)
                    }
                }
            },
        )
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            text = { Text(stringResource(R.string.sentence_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteId = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            app.vocabDb.savedSentenceDao().deleteById(id)
                        }
                    }
                }) {
                    Text(stringResource(R.string.vocab_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SentenceRow(
    sentence: SavedSentence,
    date: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(sentence.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            // A filled-in note/AI note earns a marker so the list shows at a
            // glance which sentences have been worked through.
            val markers = buildList {
                if (sentence.note.isNotBlank()) add(stringResource(R.string.sentence_note_label))
                if (sentence.aiNote.isNotBlank()) add(stringResource(R.string.sentence_ai_note_label))
            }
            if (markers.isNotEmpty()) {
                Text(
                    markers.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sentence.sourceName.isNotBlank()) {
                    Text(
                        sentence.sourceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun SentenceDetailDialog(
    sentence: SavedSentence,
    date: String,
    onDismiss: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    val scope = rememberCoroutineScope()

    // Live AI streaming state; persisted to aiNote only on successful
    // completion, so a failed/cancelled run never clobbers a saved note.
    var aiJob by remember { mutableStateOf<Job?>(null) }
    var aiStreaming by remember { mutableStateOf(false) }
    var aiThinking by remember { mutableStateOf(false) }
    var streamText by remember { mutableStateOf("") }

    // Dismissing the dialog (or a recomposition away from it) cancels the run.
    DisposableEffect(sentence.id) {
        onDispose { aiJob?.cancel() }
    }

    fun runAiAnnotate() {
        val provider = AIFactory.fromSettings(context)
        if (provider == null) {
            Toast.makeText(context, R.string.sentence_ai_no_key, Toast.LENGTH_SHORT).show()
            return
        }
        aiJob = scope.launch {
            aiStreaming = true
            aiThinking = false
            streamText = ""
            val acc = StringBuilder()
            try {
                provider.streamChat(Prompts.annotateSentence(sentence.text)).collect { event ->
                    when (event) {
                        is AIEvent.Reasoning -> aiThinking = true
                        is AIEvent.Content -> {
                            aiThinking = false
                            acc.append(event.text)
                            streamText = acc.toString()
                        }
                        else -> Unit
                    }
                }
                val result = acc.toString().trim()
                if (result.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        app.vocabDb.savedSentenceDao().updateAiNote(sentence.id, result)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(
                    context,
                    context.getString(R.string.sentence_ai_failed, e.message ?: ""),
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                aiStreaming = false
                aiThinking = false
                streamText = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.vocab_source_name, sentence.sourceName)
                    .takeIf { sentence.sourceName.isNotBlank() } ?: date,
                style = MaterialTheme.typography.titleSmall,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(sentence.text, style = MaterialTheme.typography.bodyLarge)
                }
                if (sentence.note.isNotBlank()) {
                    SectionLabel(stringResource(R.string.sentence_note_label))
                    SelectionContainer {
                        Text(sentence.note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // The AI section shows the saved note; while a run is in
                // flight it swaps to the live stream (or the thinking hint).
                // Finished notes render Markdown (Markwon); the in-flight
                // stream stays plain Text to avoid re-parsing partial
                // markers on every token (ChatPanel pattern).
                val shownAi = if (aiStreaming) streamText else sentence.aiNote
                if (shownAi.isNotBlank() || aiThinking) {
                    SectionLabel(stringResource(R.string.sentence_ai_note_label))
                    if (aiThinking && shownAi.isBlank()) {
                        Text(
                            stringResource(R.string.sentence_ai_thinking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (aiStreaming) {
                        SelectionContainer {
                            Text(shownAi, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        MarkdownText(markdown = shownAi)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    TextButton(onClick = onEditNote, enabled = !aiStreaming) {
                        Text(stringResource(R.string.sentence_edit_note))
                    }
                    TextButton(onClick = { runAiAnnotate() }, enabled = !aiStreaming) {
                        Text(
                            stringResource(
                                if (sentence.aiNote.isBlank()) R.string.sentence_ai_annotate
                                else R.string.sentence_ai_regenerate,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.vocab_delete))
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun NoteEditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sentence_edit_note)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.sentence_note_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(note.trim()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
