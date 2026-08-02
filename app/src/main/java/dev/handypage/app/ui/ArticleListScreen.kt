package dev.handypage.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.handypage.app.HandypageApp
import dev.handypage.app.R
import dev.handypage.app.ReaderActivity
import dev.handypage.app.Sources
import dev.handypage.app.engine.EngineException
import dev.handypage.app.engine.IndexCfg
import dev.handypage.app.engine.IndexItem
import dev.handypage.app.engine.SourceConfig
import dev.handypage.app.epub.EpubPackager
import dev.handypage.app.vocab.ArticleStar
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Article index for one source (DESIGN.md §5). Same chain as the old
 * ArticleListActivity: refresh the index on entry (and on pull-to-refresh),
 * then a tap fetches the article, packs a single-chapter EPUB and opens it
 * in [ReaderActivity]. Failures stay on screen as selectable text so they
 * are diagnosable from a screenshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(sourceId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    val scope = rememberCoroutineScope()

    var cfg by remember { mutableStateOf<SourceConfig?>(null) }
    var items by remember { mutableStateOf<List<IndexItem>?>(null) }
    /** Error/empty text replacing the list; null while the list is showing. */
    var statusText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    /** Non-null while an article fetch+pack is in flight; shows the overlay. */
    var fetchingTitle by remember { mutableStateOf<String?>(null) }

    // M21: starred article URLs drive the row-end star icon (dual encoding).
    val starredUrls by produceState<Set<String>>(initialValue = emptySet(), app) {
        app.vocabDb.articleStarDao().observeUrls().collect { value = it.toSet() }
    }

    fun errorText(e: Throwable): String = if (e is EngineException) {
        "Engine failure at stage '${e.stage}' (source: ${cfg?.id ?: "?"})\n\n" +
            e.stackTraceToString()
    } else {
        e.stackTraceToString()
    }

    fun refresh() {
        val c = cfg ?: return
        scope.launch {
            loading = true
            statusText = null
            try {
                val fetched = app.engine.fetchIndex(c)
                items = fetched
                if (fetched.isEmpty()) {
                    statusText = context.getString(R.string.articles_empty, c.name)
                }
            } catch (e: Exception) {
                statusText = errorText(e)
            } finally {
                loading = false
            }
        }
    }

    fun openArticle(item: IndexItem) {
        val c = cfg ?: return
        scope.launch {
            fetchingTitle = item.title
            try {
                val article = app.engine.fetchArticle(c, item.url, fallbackTitle = item.title)
                val outFile = withContext(Dispatchers.IO) {
                    EpubPackager.pack(article, c.name, epubFileFor(context, item.url))
                }
                withContext(Dispatchers.IO) {
                    app.vocabDb.articleRecordDao().recordOpen(
                        url = item.url,
                        title = article.title,
                        sourceId = c.id,
                        sourceName = c.name,
                        epubPath = outFile.absolutePath,
                        now = System.currentTimeMillis(),
                    )
                }
                context.startActivity(
                    Intent(context, ReaderActivity::class.java)
                        .putExtra(ReaderActivity.EXTRA_EPUB_PATH, outFile.absolutePath),
                )
            } catch (e: Exception) {
                statusText = errorText(e)
            } finally {
                fetchingTitle = null
            }
        }
    }

    /** M21: row-end star — save/remove the article without opening it. */
    fun toggleStar(item: IndexItem) {
        val c = cfg ?: return
        scope.launch(Dispatchers.IO) {
            val dao = app.vocabDb.articleStarDao()
            if (dao.exists(item.url)) {
                dao.deleteByUrl(item.url)
            } else {
                dao.insert(
                    ArticleStar(
                        url = item.url,
                        title = item.title,
                        sourceId = c.id,
                        sourceName = c.name,
                        starredAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    LaunchedEffect(sourceId) {
        cfg = try {
            Sources.load(context, sourceId)
        } catch (e: Exception) {
            statusText = errorText(e)
            null
        }
        // ARXIV sources are served by ArxivApi in ArxivArticleListScreen, not
        // by engine.fetchIndex (which throws for them).
        if (cfg?.index?.type != IndexCfg.Type.ARXIV) refresh()
    }

    cfg?.takeIf { it.index.type == IndexCfg.Type.ARXIV }?.let { arxivCfg ->
        ArxivArticleListScreen(cfg = arxivCfg, onBack = onBack)
        return
    }

    Scaffold(
        topBar = {
            HandypageTopBar(
                title = cfg?.name ?: stringResource(R.string.app_name),
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { refresh() },
                        enabled = !loading && fetchingTitle == null,
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val text = statusText
            when {
                text != null -> StatusPanel(text)
                loading && items == null -> LoadingPanel(
                    stringResource(R.string.articles_loading) + "\n" + cfg?.name.orEmpty(),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items.orEmpty(), key = { it.url }) { item ->
                        ArticleRow(
                            item = item,
                            starred = item.url in starredUrls,
                            onClick = { openArticle(item) },
                            onToggleStar = { toggleStar(item) },
                        )
                    }
                }
            }
            if (fetchingTitle != null) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoadingPanel(
                        stringResource(R.string.article_fetching) + "\n" + fetchingTitle,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleRow(
    item: IndexItem,
    starred: Boolean,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val sub = listOfNotNull(item.published, item.summary)
        .joinToString(" — ")
        .take(160)
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = if (sub.isBlank()) null else ({ Text(sub) }),
        // M21 star: dual encoding (solid ink = starred, hollow grey = not).
        // M22: 120ms crossfade via the shared EditorialStarIcon.
        trailingContent = {
            IconButton(onClick = onToggleStar) {
                EditorialStarIcon(
                    starred = starred,
                    contentDescription = stringResource(
                        if (starred) R.string.star_remove else R.string.star_add,
                    ),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Centred spinner with a caption, used for index load and article fetch. */
@Composable
private fun LoadingPanel(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** Stable 16-hex-char hash per article/paper URL; shared by epub and pdf naming. */
private fun urlHash16(url: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .substring(0, 16)

/** Stable file name per article URL; refetches overwrite the same file. */
internal fun epubFileFor(context: Context, url: String): File {
    val dir = File(context.filesDir, "epubs").apply { mkdirs() }
    return File(dir, "${urlHash16(url)}.epub")
}

/**
 * Downloaded arXiv PDF for a paper URL, keyed by the same hash as
 * [epubFileFor] so both can be derived from the record URL (papers are
 * immutable, so the file doubles as a permanent cache).
 */
internal fun paperFileFor(context: Context, url: String): File {
    val dir = File(context.filesDir, "papers").apply { mkdirs() }
    return File(dir, "${urlHash16(url)}.pdf")
}
