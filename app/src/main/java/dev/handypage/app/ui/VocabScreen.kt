package dev.handypage.app.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import dev.handypage.app.HandypageApp
import dev.handypage.app.R
import dev.handypage.app.vocab.VocabWord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vocab book content (DESIGN.md §5): saved words newest-first from Room.
 * Tap a row for the full entry; long-press (or the detail dialog's 删除
 * button) deletes after a confirmation. M7 embeds this inside the 本机 tab,
 * so it is scaffold-free — the host supplies the top bar and padding.
 */
@Composable
fun VocabBookContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Null until Room's first emission, so the empty state doesn't flash.
    val words by produceState<List<VocabWord>?>(initialValue = null, app) {
        app.vocabDb.vocabWordDao().observeAll().collect { value = it }
    }

    var detailWord by remember { mutableStateOf<VocabWord?>(null) }
    var deleteWord by remember { mutableStateOf<VocabWord?>(null) }

    Box(modifier = modifier) {
        val list = words
        when {
            list == null -> Box(modifier = Modifier.fillMaxSize())
            list.isEmpty() -> StatusPanel(text = stringResource(R.string.vocab_empty))
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { word ->
                    Column { // M15: row + hairline under it (§4.3)
                    VocabRow(
                        word = word,
                        date = dateFormat.format(Date(word.addedAt)),
                        onClick = { detailWord = word },
                        onLongClick = { deleteWord = word },
                    )
                    EditorialHairline(modifier = Modifier.padding(horizontal = EditorialSpacing.lg))
                    }
                }
            }
        }
    }

    detailWord?.let { word ->
        AlertDialog(
            onDismissRequest = { detailWord = null },
            title = { Text(word.word) },
            text = { SelectionContainer { Text(detailMessage(word, context)) } },
            confirmButton = {
                TextButton(onClick = { detailWord = null }) {
                    Text(stringResource(R.string.close))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    detailWord = null
                    deleteWord = word
                }) {
                    Text(stringResource(R.string.vocab_delete))
                }
            },
        )
    }

    deleteWord?.let { word ->
        AlertDialog(
            onDismissRequest = { deleteWord = null },
            text = { Text(stringResource(R.string.vocab_delete_confirm, word.word)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteWord = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            app.vocabDb.vocabWordDao().deleteById(word.id)
                        }
                    }
                }) {
                    Text(stringResource(R.string.vocab_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteWord = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VocabRow(
    word: VocabWord,
    date: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.Bottom) {
                // M15: headword in Fraunces 18sp/600 (§3); CJK falls back.
                Text(
                    word.word,
                    style = EditorialType.headword,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                word.phonetic?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        " /$it/",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        supportingContent = {
            val firstLine =
                word.translation?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
            if (!firstLine.isNullOrEmpty()) {
                Text(firstLine, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (word.sourceName.isNotBlank()) {
                    Text(
                        word.sourceName,
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

/** Same message body as the old detail dialog: gloss, sentence, provenance. */
private fun detailMessage(word: VocabWord, context: Context): String = buildString {
    word.phonetic?.takeIf { it.isNotBlank() }?.let { append("/$it/\n") }
    word.translation?.takeIf { it.isNotBlank() }?.let { append("\n$it\n") }
    word.definition?.takeIf { it.isNotBlank() }?.let { append("\n$it\n") }
    word.sentence?.takeIf { it.isNotBlank() }?.let {
        append("\n").append(context.getString(R.string.vocab_sentence, it)).append('\n')
    }
    if (word.sourceName.isNotBlank()) {
        append("\n").append(context.getString(R.string.vocab_source_name, word.sourceName))
    }
    if (word.articleUrl.isNotBlank()) {
        append('\n').append(word.articleUrl)
    }
}.trim().ifBlank { context.getString(R.string.not_in_dict) }
