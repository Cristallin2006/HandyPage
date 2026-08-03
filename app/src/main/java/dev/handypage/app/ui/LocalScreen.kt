package dev.handypage.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.handypage.app.HandypageApp
import dev.handypage.app.R
import dev.handypage.app.ReaderActivity
import dev.handypage.app.Sources
import dev.handypage.app.arxiv.ArxivEntry
import dev.handypage.app.epub.EpubPackager
import dev.handypage.app.local.ArticleRecord
import dev.handypage.app.local.StarredItem
import dev.handypage.app.local.StarredMerge
import dev.handypage.app.local.StarredType
import dev.handypage.app.paper.PaperReaderActivity
import dev.handypage.app.vocab.ArticleStar
import dev.handypage.app.vocab.PaperStar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本机 tab (design-system.md §4.1/§4.6, M15): on-device content under an
 * editorial masthead whose meta line carries the four real counts. 「文章」
 * lists every article ever opened (newest first) — the packed EPUB is still
 * on disk, so a tap reopens it fully offline; delete removes both the row and
 * the file. 「生词」embeds the vocab book; 「好句」embeds the M10 sentence
 * book. 「收藏」lists the M13 starred papers: a tap reopens the cached PDF
 * (or re-runs the shared download→open flow when it was evicted), long-press
 * removes the star (files and reading record are left alone).
 */
@Composable
fun LocalScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    var section by rememberSaveable { mutableIntStateOf(0) }

    // Masthead meta counts (§4.1 真实数据进 meta): the same observeAll()
    // flows the sections collect, reduced to sizes. Null until the first
    // emission; the meta line shows 0 until then.
    val articleCount by produceState<Int?>(initialValue = null, app) {
        app.vocabDb.articleRecordDao().observeAll().collect { value = it.size }
    }
    val wordCount by produceState<Int?>(initialValue = null, app) {
        app.vocabDb.vocabWordDao().observeAll().collect { value = it.size }
    }
    val sentenceCount by produceState<Int?>(initialValue = null, app) {
        app.vocabDb.savedSentenceDao().observeAll().collect { value = it.size }
    }
    val starCount by produceState<Int?>(initialValue = null, app) {
        app.vocabDb.paperStarDao().observeAll().collect { value = it.size }
    }
    val articleStarCount by produceState<Int?>(initialValue = null, app) {
        app.vocabDb.articleStarDao().observeAll().collect { value = it.size }
    }
    val starTotal = (starCount ?: 0) + (articleStarCount ?: 0)

    // The 收藏 section re-opens starred papers through the same flow as the
    // arXiv list screen; resolve the bundled arXiv source for its identity
    // and request pacing (fall back to the arXiv-etiquette 3 s).
    val arxivCfg = remember {
        runCatching { Sources.loadAll(context) }.getOrNull()
            ?.firstOrNull { it.id == "arxiv" }
    }
    val opener = rememberPaperOpener(
        sourceId = arxivCfg?.id ?: "arxiv",
        sourceName = arxivCfg?.name ?: "arXiv",
    )

    Scaffold(
        snackbarHost = { SnackbarHost(opener.snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // M22: hoisted to the shell via MastheadHost when present.
            EditorialMastheadSlot(Routes.LOCAL) {
                EditorialMasthead(
                    title = stringResource(R.string.tab_local),
                    titleEn = "LIBRARY",
                    meta = stringResource(
                        R.string.local_meta,
                        articleCount ?: 0,
                        wordCount ?: 0,
                        sentenceCount ?: 0,
                        starTotal,
                    ),
                )
            }
            EditorialTabRow(
                tabs = listOf(
                    stringResource(R.string.local_articles),
                    stringResource(R.string.local_vocab),
                    stringResource(R.string.local_sentences),
                    stringResource(R.string.local_stars),
                ),
                selectedIndex = section,
                onSelect = { section = it },
            )
            when (section) {
                0 -> ArticleRecords(modifier = Modifier.weight(1f))
                1 -> VocabBookContent(modifier = Modifier.weight(1f))
                2 -> SentenceBookContent(modifier = Modifier.weight(1f))
                else -> StarredContent(opener = opener, modifier = Modifier.weight(1f))
            }
        }
    }

    PaperOpenDialog(opener)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StarredContent(opener: PaperOpener, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    val scope = rememberCoroutineScope()

    // Null until Room's first emission, so the empty state doesn't flash.
    val papers by produceState<List<PaperStar>?>(initialValue = null, app) {
        app.vocabDb.paperStarDao().observeAll().collect { value = it }
    }
    val articles by produceState<List<ArticleStar>?>(initialValue = null, app) {
        app.vocabDb.articleStarDao().observeAll().collect { value = it }
    }
    var removeStar by remember { mutableStateOf<StarredItem?>(null) }
    /** Non-null while an article refetch is in flight; shows the overlay. */
    var fetching by remember { mutableStateOf<StarredItem?>(null) }

    /**
     * Article-star tap (M21): the cached EPUB opens straight into the
     * reader; if it was evicted (record deleted / cache cleared) the star
     * still holds sourceId, so refetch → pack → record → open, mirroring
     * the paper star's re-download path.
     */
    fun openArticleStar(item: StarredItem) {
        val epub = epubFileFor(context, item.url)
        if (epub.isFile) {
            context.startActivity(
                Intent(context, ReaderActivity::class.java)
                    .putExtra(ReaderActivity.EXTRA_EPUB_PATH, epub.absolutePath),
            )
            return
        }
        val star = articles.orEmpty().firstOrNull { it.url == item.url } ?: return
        scope.launch {
            fetching = item
            try {
                val cfg = runCatching { Sources.load(context, star.sourceId) }.getOrNull()
                if (cfg == null) {
                    opener.snackbarHostState.showSnackbar(
                        context.getString(R.string.star_source_missing),
                    )
                    return@launch
                }
                val article = app.engine.fetchArticle(cfg, star.url, fallbackTitle = star.title)
                val outFile = withContext(Dispatchers.IO) {
                    EpubPackager.pack(article, cfg.name, epubFileFor(context, star.url))
                }
                withContext(Dispatchers.IO) {
                    app.vocabDb.articleRecordDao().recordOpen(
                        url = star.url,
                        title = article.title,
                        sourceId = cfg.id,
                        sourceName = cfg.name,
                        epubPath = outFile.absolutePath,
                        now = System.currentTimeMillis(),
                    )
                }
                context.startActivity(
                    Intent(context, ReaderActivity::class.java)
                        .putExtra(ReaderActivity.EXTRA_EPUB_PATH, outFile.absolutePath),
                )
            } catch (e: Exception) {
                opener.snackbarHostState.showSnackbar(
                    context.getString(R.string.star_refetch_failed),
                )
            } finally {
                fetching = null
            }
        }
    }

    Box(modifier = modifier) {
        val list = papers?.let { p -> articles?.let { a -> StarredMerge.merge(p, a) } }
        when {
            list == null -> Unit // wait for the first emission
            list.isEmpty() -> StatusPanel(text = stringResource(R.string.stars_empty))
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.url }) { item ->
                    Column { // M15: row + hairline under it (§4.3)
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = if (item.type == StarredType.PAPER) {
                                    Icons.Outlined.Science
                                } else {
                                    Icons.Outlined.Newspaper
                                },
                                contentDescription = stringResource(
                                    if (item.type == StarredType.PAPER) {
                                        R.string.stars_type_paper
                                    } else {
                                        R.string.stars_type_article
                                    },
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        headlineContent = {
                            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Column {
                                if (item.metaTop.isNotEmpty()) {
                                    Text(
                                        text = item.metaTop,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (item.metaBottom.isNotEmpty()) {
                                    Text(
                                        text = item.metaBottom,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                when (item.type) {
                                    StarredType.ARTICLE -> openArticleStar(item)
                                    StarredType.PAPER ->
                                        papers.orEmpty()
                                            .firstOrNull { it.url == item.url }
                                            ?.let { star ->
                                                val paperFile = paperFileFor(context, star.url)
                                                if (paperFile.isFile) {
                                                    context.startActivity(
                                                        Intent(
                                                            context,
                                                            PaperReaderActivity::class.java,
                                                        )
                                                            .putExtra(
                                                                PaperReaderActivity.EXTRA_PDF_PATH,
                                                                paperFile.absolutePath,
                                                            )
                                                            .putExtra(
                                                                PaperReaderActivity.EXTRA_ABS_URL,
                                                                star.url,
                                                            )
                                                            .putExtra(
                                                                PaperReaderActivity.EXTRA_TITLE,
                                                                star.title,
                                                            )
                                                            .putExtra(
                                                                PaperReaderActivity.EXTRA_AUTHORS,
                                                                star.authors,
                                                            )
                                                            .putExtra(
                                                                PaperReaderActivity
                                                                    .EXTRA_PRIMARY_CATEGORY,
                                                                star.primaryCategory,
                                                            )
                                                            .putExtra(
                                                                PaperReaderActivity.EXTRA_PUBLISHED,
                                                                star.published,
                                                            )
                                                            .putExtra(
                                                                ReaderActivity.EXTRA_EPUB_PATH,
                                                                epubFileFor(
                                                                    context,
                                                                    star.url,
                                                                ).absolutePath,
                                                            ),
                                                    )
                                                } else {
                                                    // PDF evicted: re-run the shared
                                                    // download→open flow with an entry
                                                    // rebuilt from the star row.
                                                    opener.open(star.toArxivEntry())
                                                }
                                            }
                                }
                            },
                            onLongClick = { removeStar = item },
                        ),
                    )
                    EditorialHairline(modifier = Modifier.padding(horizontal = EditorialSpacing.lg))
                    }
                }
            }
        }

        // Refetch overlay, while an evicted article's EPUB is being rebuilt.
        fetching?.let {
            Surface(modifier = Modifier.fillMaxSize()) {
                StatusPanel(text = stringResource(R.string.article_fetching) + "\n" + it.title)
            }
        }
    }

    removeStar?.let { item ->
        AlertDialog(
            onDismissRequest = { removeStar = null },
            text = { Text(stringResource(R.string.star_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    removeStar = null
                    // Star row only: the cached PDF/EPUB and the reading
                    // record stay on disk.
                    scope.launch(Dispatchers.IO) {
                        when (item.type) {
                            StarredType.PAPER -> app.vocabDb.paperStarDao().deleteByUrl(item.url)
                            StarredType.ARTICLE -> app.vocabDb.articleStarDao().deleteByUrl(item.url)
                        }
                    }
                }) {
                    Text(stringResource(R.string.vocab_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { removeStar = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Rebuilds the feed-entry shape the shared open flow expects from a star row. */
private fun PaperStar.toArxivEntry(): ArxivEntry = ArxivEntry(
    id = url.trimEnd('/').substringAfterLast('/'),
    title = title,
    authors = authors.split(", ").filter { it.isNotBlank() },
    summary = "",
    published = published,
    pdfUrl = url.replace("/abs/", "/pdf/"),
    absUrl = url,
    primaryCategory = primaryCategory,
)

@Composable
private fun ArticleRecords(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // Null until Room's first emission, so the empty state doesn't flash.
    val records by produceState<List<ArticleRecord>?>(initialValue = null, app) {
        app.vocabDb.articleRecordDao().observeAll().collect { value = it }
    }
    var deleteRecord by remember { mutableStateOf<ArticleRecord?>(null) }

    Column(modifier = modifier) {
        val list = records
        when {
            list == null -> Unit // wait for the first emission
            list.isEmpty() -> StatusPanel(text = stringResource(R.string.local_articles_empty))
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { record ->
                    Column { // M15: row + hairline under it (§4.3)
                    ArticleRecordRow(
                        record = record,
                        date = dateFormat.format(Date(record.lastOpenedAt)),
                        cached = remember(record.epubPath) {
                            // arXiv papers may have only the PDF cached while
                            // the reflow EPUB is still being converted.
                            File(record.epubPath).isFile ||
                                paperFileFor(context, record.url).isFile
                        },
                        onClick = {
                            val paperFile = paperFileFor(context, record.url)
                            when {
                                // M12-A: arXiv records open the original PDF
                                // when it is still cached; fall through to
                                // the reflow EPUB when only that survives.
                                record.sourceId == "arxiv" && paperFile.isFile -> {
                                    context.startActivity(
                                        Intent(context, PaperReaderActivity::class.java)
                                            .putExtra(
                                                PaperReaderActivity.EXTRA_PDF_PATH,
                                                paperFile.absolutePath,
                                            )
                                            .putExtra(PaperReaderActivity.EXTRA_ABS_URL, record.url)
                                            .putExtra(PaperReaderActivity.EXTRA_TITLE, record.title)
                                            .putExtra(
                                                ReaderActivity.EXTRA_EPUB_PATH,
                                                record.epubPath,
                                            ),
                                    )
                                }
                                File(record.epubPath).isFile -> {
                                    context.startActivity(
                                        Intent(context, ReaderActivity::class.java)
                                            .putExtra(ReaderActivity.EXTRA_EPUB_PATH, record.epubPath),
                                    )
                                }
                                else -> {
                                    Toast.makeText(
                                        context,
                                        R.string.local_file_missing,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        onDelete = { deleteRecord = record },
                    )
                    EditorialHairline(modifier = Modifier.padding(horizontal = EditorialSpacing.lg))
                    }
                }
            }
        }
    }

    deleteRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteRecord = null },
            text = { Text(stringResource(R.string.local_delete_confirm, record.title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteRecord = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            File(record.epubPath).delete()
                            // arXiv papers keep the downloaded PDF next to the
                            // EPUB; absent for web articles, delete() no-ops.
                            paperFileFor(context, record.url).delete()
                            app.vocabDb.articleRecordDao().deleteById(record.id)
                        }
                    }
                }) {
                    Text(stringResource(R.string.vocab_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRecord = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ArticleRecordRow(
    record: ArticleRecord,
    date: String,
    cached: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(record.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cached) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.local_offline_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = "${record.sourceName} · $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = if (cached) 8.dp else 0.dp),
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.local_delete_record),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
