package dev.handypage.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handypage.app.HandypageApp
import dev.handypage.app.R
import dev.handypage.app.arxiv.ArxivCategories
import dev.handypage.app.arxiv.ArxivEntry
import dev.handypage.app.engine.SourceConfig
import dev.handypage.app.vocab.PaperStar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * arXiv source screen (M11/M12-A/M13): a search box plus subject-category chips
 * on top, paper cards below. The chips show the source's preset categories, an
 * optional custom category persisted in SharedPreferences (picked via the
 * 「更多」 dialog, which lists the full arXiv taxonomy in [ArxivCategories]),
 * and the 「更多」 chip itself.
 *
 * Tapping a card runs the shared [PaperOpener] "download → open" flow; each
 * card's tail star toggles the paper in the 收藏 book (paper_stars table).
 *
 * arXiv API etiquette (one request per [SourceConfig.delaySeconds]) is
 * honoured by serialising every network call through the opener's Mutex with
 * a throttle in front; ArxivApi itself does not throttle.
 */
@Composable
fun ArxivArticleListScreen(cfg: SourceConfig, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    val scope = rememberCoroutineScope()
    val opener = rememberPaperOpener(
        delaySeconds = cfg.delaySeconds,
        sourceId = cfg.id,
        sourceName = cfg.name,
    )
    val snackbarHostState = opener.snackbarHostState

    var query by rememberSaveable { mutableStateOf("") }
    val prefs = remember {
        context.getSharedPreferences(ARXIV_PREFS, Context.MODE_PRIVATE)
    }
    /** Non-preset category pinned as the 6th chip; persisted across visits. */
    var customCategory by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_CUSTOM_CATEGORY, null).orEmpty())
    }
    var selectedCategory by rememberSaveable {
        mutableStateOf(customCategory.ifEmpty { cfg.categories.firstOrNull().orEmpty() })
    }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    /** Null while a request is in flight (or failed) with nothing to show. */
    var entries by remember { mutableStateOf<List<ArxivEntry>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }

    var fetchJob by remember { mutableStateOf<Job?>(null) }
    /** Monotonic request id; stale jobs check it before touching UI state. */
    var requestSeq by remember { mutableIntStateOf(0) }

    val starredUrls by app.vocabDb.paperStarDao().observeUrls()
        .collectAsState(initial = null)

    fun loadEntries(search: Boolean) {
        val seq = ++requestSeq
        fetchJob?.cancel()
        fetchJob = scope.launch {
            loading = true
            listError = null
            entries = null
            try {
                val fetched = opener.apiMutex.withLock {
                    opener.throttle()
                    withContext(Dispatchers.IO) {
                        if (search) opener.api.search(query.trim())
                        else opener.api.byCategory(selectedCategory)
                    }
                }
                ensureActive()
                entries = fetched
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                val msg = e.message ?: e.toString()
                listError = msg
                snackbarHostState.showSnackbar(msg)
            } finally {
                if (seq == requestSeq) loading = false
            }
        }
    }

    /** Dialog pick: non-preset codes join the chip row (persisted); then load. */
    fun pickCategory(code: String) {
        if (code !in cfg.categories) {
            customCategory = code
            prefs.edit().putString(KEY_CUSTOM_CATEGORY, code).apply()
        }
        selectedCategory = code
        loadEntries(search = false)
    }

    fun toggleStar(entry: ArxivEntry, starred: Boolean) {
        scope.launch(Dispatchers.IO) {
            val dao = app.vocabDb.paperStarDao()
            if (starred) {
                dao.deleteByUrl(entry.absUrl)
            } else {
                dao.insert(
                    PaperStar(
                        url = entry.absUrl,
                        title = entry.title,
                        authors = entry.authors.joinToString(", "),
                        primaryCategory = entry.primaryCategory,
                        published = entry.published,
                        starredAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        if (selectedCategory.isNotEmpty()) loadEntries(search = false)
    }

    Scaffold(
        topBar = { HandypageTopBar(title = cfg.name, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.arxiv_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (query.isNotBlank()) loadEntries(search = true) },
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { if (query.isNotBlank()) loadEntries(search = true) },
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.arxiv_search_hint),
                        )
                    }
                },
            )
            if (cfg.categories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    val chipCategories = cfg.categories +
                        listOfNotNull(customCategory.takeIf { it.isNotEmpty() && it !in cfg.categories })
                    chipCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                loadEntries(search = false)
                            },
                            label = { Text(cat) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { showCategoryPicker = true },
                        label = { Text(stringResource(R.string.arxiv_more_categories)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                val list = entries
                when {
                    loading && list == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    listError != null && list == null -> StatusPanel(text = listError.orEmpty())
                    list.isNullOrEmpty() -> StatusPanel(text = stringResource(R.string.arxiv_empty))
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(list, key = { it.absUrl }) { entry ->
                            val starred = starredUrls?.contains(entry.absUrl) == true
                            ArxivRow(
                                entry = entry,
                                starred = starred,
                                onToggleStar = { toggleStar(entry, starred) },
                                onClick = { opener.open(entry) },
                            )
                        }
                    }
                }
            }
        }
    }

    PaperOpenDialog(opener)

    if (showCategoryPicker) {
        CategoryPickerDialog(
            selected = selectedCategory,
            onPick = { code ->
                showCategoryPicker = false
                pickCategory(code)
            },
            onDismiss = { showCategoryPicker = false },
        )
    }
}

private const val ARXIV_PREFS = "arxiv_prefs"
private const val KEY_CUSTOM_CATEGORY = "arxiv_custom_category"

/**
 * M13 「更多」 category picker: the full arXiv taxonomy grouped by archive,
 * single-choice, with a code/label contains-filter (empty groups hidden).
 */
@Composable
private fun CategoryPickerDialog(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val groups = remember(filter) {
        val q = filter.trim().lowercase()
        ArxivCategories.ALL
            .filter { q.isEmpty() || q in it.code.lowercase() || q in it.label.lowercase() }
            .groupBy { it.group }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arxiv_pick_category)) },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.arxiv_search_category)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(420.dp)) {
                    groups.forEach { (group, cats) ->
                        item(key = "header-$group") {
                            Text(
                                text = group,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        items(cats, key = { it.code }) { cat ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(cat.code) },
                            ) {
                                RadioButton(
                                    selected = selected == cat.code,
                                    onClick = { onPick(cat.code) },
                                )
                                Text(
                                    text = "${cat.code} · ${cat.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ArxivRow(
    entry: ArxivEntry,
    starred: Boolean,
    onToggleStar: () -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.authors.isNotEmpty()) {
                Text(
                    entry.authors.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val dateAndCategory = listOfNotNull(
                entry.published.take(10).takeIf { it.isNotEmpty() },
                entry.primaryCategory.takeIf { it.isNotEmpty() },
            ).joinToString(" · ")
            if (dateAndCategory.isNotEmpty()) {
                Text(
                    dateAndCategory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (entry.summary.isNotEmpty()) {
                Text(
                    entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleStar) {
                    // M22: 120ms crossfade via the shared EditorialStarIcon
                    // (ink/grey dual coding, same tone as the other readers).
                    EditorialStarIcon(
                        starred = starred,
                        contentDescription = stringResource(R.string.local_stars),
                    )
                }
            }
        }
    }
}
