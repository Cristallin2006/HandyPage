package dev.handypage.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.handypage.app.HandypageApp
import dev.handypage.app.R
import dev.handypage.app.vocab.MASTERY_LEARNING
import dev.handypage.app.vocab.MASTERY_MASTERED
import dev.handypage.app.vocab.MASTERY_NEW
import dev.handypage.app.vocab.VocabGroup
import dev.handypage.app.vocab.VocabSort
import dev.handypage.app.vocab.VocabWord
import dev.handypage.app.vocab.filterGroups
import dev.handypage.app.vocab.groupVocabWords
import dev.handypage.app.vocab.sortGroups
import dev.handypage.app.vocab.vocabCsv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vocab book content (DESIGN.md §5): saved words from Room. M7 embeds this
 * inside the 本机 tab, so it is scaffold-free — the host supplies the top bar
 * and padding.
 *
 * M40: the flat row list became a managed book. Rows are GROUPED by lemma
 * ([groupVocabWords]) so the same word saved from different articles shows
 * once with a ×N occurrence count; the in-content header offers search
 * (headword/translation/definition), mastery filter chips (全部/新词/学习中/
 * 已掌握), a sort menu (最近/频次/字母) and full-book CSV export via SAF.
 * Every group carries a mastery level (0/1/2, dot in the row leading slot);
 * tap opens a detail dialog listing every occurrence where the level cycles
 * 新词→学习中→已掌握, long-press enters selection mode for batch 标为已掌握 /
 * 删除. Mastered words stop being weak-highlighted in articles and papers.
 */
@Composable
fun VocabBookContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Null until Room's first emission, so the empty state doesn't flash.
    val rawWords by produceState<List<VocabWord>?>(initialValue = null, app) {
        app.vocabDb.vocabWordDao().observeAll().collect { value = it }
    }
    val groups = remember(rawWords) { groupVocabWords(rawWords.orEmpty()) }

    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(VocabSort.RECENT.name) }
    var masteryFilter by rememberSaveable { mutableStateOf(-1) } // -1 = 全部
    val sort = VocabSort.valueOf(sortName)
    val shown = remember(groups, query, sortName, masteryFilter) {
        sortGroups(
            filterGroups(groups, query, masteryFilter.takeIf { it >= 0 }),
            sort,
        )
    }

    // Selection mode: a non-empty set of group keys.
    var selected by remember { mutableStateOf(setOf<String>()) }
    val selectionMode = selected.isNotEmpty()
    var detailKey by remember { mutableStateOf<String?>(null) }
    var deleteGroup by remember { mutableStateOf<VocabGroup?>(null) }
    var batchDelete by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // M40: full-book CSV backup via SAF — always ALL rows, not the filtered view.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) { // null = user cancelled the picker
            val rows = rawWords.orEmpty()
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                        out.write(
                            vocabCsv(
                                rows,
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
                            ),
                        )
                    }
                }
                Toast.makeText(context, R.string.vocab_export_done, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // The group a detail dialog shows may vanish (deleted / merged away) —
    // dismiss the dialog instead of showing a stale snapshot.
    LaunchedEffect(groups) {
        if (detailKey != null && groups.none { it.key == detailKey }) detailKey = null
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectionMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = EditorialSpacing.xs),
            ) {
                IconButton(onClick = { selected = emptySet() }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                    )
                }
                Text(
                    stringResource(R.string.vocab_selected, selected.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { selected = groups.map { it.key }.toSet() }) {
                    Text(stringResource(R.string.vocab_select_all))
                }
                TextButton(onClick = {
                    val ids = selectedIds(groups, selected)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            app.vocabDb.vocabWordDao().updateMastery(ids, MASTERY_MASTERED)
                        }
                    }
                    selected = emptySet()
                }) {
                    Text(stringResource(R.string.vocab_mark_mastered))
                }
                TextButton(onClick = { batchDelete = true }) {
                    Text(stringResource(R.string.vocab_delete))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EditorialSpacing.lg),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = EditorialSpacing.md),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.vocab_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = EditorialSpacing.sm),
                ) {
                    FilterChip(
                        selected = masteryFilter < 0,
                        onClick = { masteryFilter = -1 },
                        label = { Text(stringResource(R.string.vocab_filter_all)) },
                        modifier = Modifier.padding(end = EditorialSpacing.sm),
                    )
                    FilterChip(
                        selected = masteryFilter == MASTERY_NEW,
                        onClick = { masteryFilter = MASTERY_NEW },
                        label = { Text(stringResource(R.string.vocab_mastery_new)) },
                        modifier = Modifier.padding(end = EditorialSpacing.sm),
                    )
                    FilterChip(
                        selected = masteryFilter == MASTERY_LEARNING,
                        onClick = { masteryFilter = MASTERY_LEARNING },
                        label = { Text(stringResource(R.string.vocab_mastery_learning)) },
                        modifier = Modifier.padding(end = EditorialSpacing.sm),
                    )
                    FilterChip(
                        selected = masteryFilter == MASTERY_MASTERED,
                        onClick = { masteryFilter = MASTERY_MASTERED },
                        label = { Text(stringResource(R.string.vocab_mastery_mastered)) },
                        modifier = Modifier.padding(end = EditorialSpacing.sm),
                    )
                    Spacer(Modifier.weight(1f))
                    Box { // dropdown anchor
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            SortMenuItem(VocabSort.RECENT, R.string.vocab_sort_recent, sort) {
                                sortName = VocabSort.RECENT.name
                                sortMenuOpen = false
                            }
                            SortMenuItem(VocabSort.FREQUENCY, R.string.vocab_sort_frequency, sort) {
                                sortName = VocabSort.FREQUENCY.name
                                sortMenuOpen = false
                            }
                            SortMenuItem(VocabSort.ALPHABETICAL, R.string.vocab_sort_alpha, sort) {
                                sortName = VocabSort.ALPHABETICAL.name
                                sortMenuOpen = false
                            }
                        }
                    }
                    IconButton(onClick = { exportLauncher.launch("handypage-vocab.csv") }) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = stringResource(R.string.vocab_export),
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                rawWords == null -> Box(modifier = Modifier.fillMaxSize())
                groups.isEmpty() -> StatusPanel(text = stringResource(R.string.vocab_empty))
                shown.isEmpty() -> StatusPanel(text = stringResource(R.string.vocab_no_match))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.key }) { group ->
                        Column { // M15: row + hairline under it (§4.3)
                            VocabGroupRow(
                                group = group,
                                date = dateFormat.format(Date(group.latestAt)),
                                selected = group.key in selected,
                                onClick = {
                                    if (selectionMode) {
                                        selected =
                                            if (group.key in selected) {
                                                selected - group.key
                                            } else {
                                                selected + group.key
                                            }
                                    } else {
                                        detailKey = group.key
                                    }
                                },
                                onLongClick = { selected = selected + group.key },
                            )
                            EditorialHairline(
                                modifier = Modifier.padding(horizontal = EditorialSpacing.lg),
                            )
                        }
                    }
                }
            }
        }
    }

    // Detail dialog: keyed by group KEY so mastery edits (which rebuild
    // `groups`) are reflected on the next recomposition.
    detailKey?.let { key ->
        groups.firstOrNull { it.key == key }?.let { group ->
            AlertDialog(
                onDismissRequest = { detailKey = null },
                title = { Text(group.headword) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        group.phonetic?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                "/$it/",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        group.translation?.takeIf { it.isNotBlank() }?.let {
                            Text(it, modifier = Modifier.padding(top = EditorialSpacing.sm))
                        }
                        group.definition?.takeIf { it.isNotBlank() }?.let {
                            Text(it, modifier = Modifier.padding(top = EditorialSpacing.sm))
                        }
                        group.rows.forEachIndexed { index, row ->
                            if (index > 0) {
                                EditorialHairline(
                                    modifier = Modifier.padding(vertical = EditorialSpacing.md),
                                )
                            } else {
                                Spacer(Modifier.height(EditorialSpacing.sm))
                            }
                            row.sentence?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    stringResource(R.string.vocab_sentence, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            val provenance =
                                if (row.sourceName.isNotBlank()) {
                                    "${row.sourceName} · ${dateFormat.format(Date(row.addedAt))}"
                                } else {
                                    dateFormat.format(Date(row.addedAt))
                                }
                            Text(
                                stringResource(R.string.vocab_source_name, provenance),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = EditorialSpacing.xs),
                            )
                        }
                    }
                },
                confirmButton = {
                    Row {
                        // Cycles 新词 → 学习中 → 已掌握 → 新词, persisted on every row.
                        TextButton(onClick = {
                            val next = (group.mastery + 1) % 3
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    app.vocabDb.vocabWordDao()
                                        .updateMastery(group.rows.map { it.id }, next)
                                }
                            }
                        }) {
                            Text(masteryLabel(group.mastery))
                        }
                        TextButton(onClick = { detailKey = null }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        detailKey = null
                        deleteGroup = group
                    }) {
                        Text(stringResource(R.string.vocab_delete))
                    }
                },
            )
        }
    }

    // Detail-dialog delete: drops EVERY occurrence row of the group.
    deleteGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteGroup = null },
            text = {
                Text(
                    stringResource(
                        R.string.vocab_delete_group_confirm,
                        group.headword,
                        group.occurrences,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteGroup = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            app.vocabDb.vocabWordDao().deleteByIds(group.rows.map { it.id })
                        }
                    }
                }) {
                    Text(stringResource(R.string.vocab_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroup = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Selection-mode batch delete.
    if (batchDelete) {
        val selectedGroups = groups.filter { it.key in selected }
        AlertDialog(
            onDismissRequest = { batchDelete = false },
            text = {
                Text(
                    stringResource(
                        R.string.vocab_delete_selected_confirm,
                        selectedGroups.size,
                        selectedGroups.sumOf { it.occurrences },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    batchDelete = false
                    val ids = selectedIds(groups, selected)
                    selected = emptySet()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            app.vocabDb.vocabWordDao().deleteByIds(ids)
                        }
                    }
                }) {
                    Text(stringResource(R.string.vocab_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { batchDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Every row id of every selected group — batch ops act on rows, not groups. */
private fun selectedIds(groups: List<VocabGroup>, selected: Set<String>): List<Long> =
    groups.filter { it.key in selected }.flatMap { g -> g.rows.map { it.id } }

@Composable
private fun SortMenuItem(
    value: VocabSort,
    labelRes: Int,
    current: VocabSort,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = onClick,
        leadingIcon = {
            if (value == current) Icon(Icons.Filled.Check, contentDescription = null)
        },
    )
}

/** Mastery level → its Chinese label (新词/学习中/已掌握). */
@Composable
private fun masteryLabel(mastery: Int): String = stringResource(
    when (mastery) {
        MASTERY_LEARNING -> R.string.vocab_mastery_learning
        MASTERY_MASTERED -> R.string.vocab_mastery_mastered
        else -> R.string.vocab_mastery_new
    },
)

/** Row dot colour: 新词 = accent, 学习中 = tertiary, 已掌握 = faded outline. */
@Composable
private fun masteryDotColor(mastery: Int): Color = when (mastery) {
    MASTERY_LEARNING -> MaterialTheme.colorScheme.tertiary
    MASTERY_MASTERED -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.primary
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VocabGroupRow(
    group: VocabGroup,
    date: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        colors = if (selected) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            Box(
                Modifier
                    .size(EditorialSpacing.sm) // 8dp mastery dot
                    .background(masteryDotColor(group.mastery), CircleShape),
            )
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.Bottom) {
                // M15: headword in Fraunces 18sp/600 (§3); CJK falls back.
                Text(
                    group.headword,
                    style = EditorialType.headword,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                group.phonetic?.takeIf { it.isNotBlank() }?.let {
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
                group.translation?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
            if (!firstLine.isNullOrEmpty()) {
                Text(firstLine, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                if (group.occurrences > 1) {
                    Text(
                        "×${group.occurrences}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}
