package dev.handypage.app.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.handypage.app.R
import dev.handypage.app.Sources
import dev.handypage.app.engine.SourceConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 阅读 shelf (design-system.md §4.1–§4.4, M15): bundled sources grouped by
 * category (分级学习/新闻/科学/科技/论文) under an editorial masthead. Each
 * group gets an [EditorialSectionHeader] with a count; each row is a serif
 * monogram + name + host (+ proxy badge) over hairlines — no cards. The
 * settings gear is gone from here (诊断 #4: the bottom settings tab is the
 * single entry). The ECDICT attribution footer lives at the end of the list.
 */

/** Fixed shelf order; categories outside this list sort last, alphabetically. */
private val CATEGORY_ORDER = listOf("learning", "news", "science", "tech", "papers")

@Composable
fun SourcesScreen(onOpenSource: (String) -> Unit) {
    val context = LocalContext.current
    // Bundled assets never change at runtime, so load once.
    val sources = remember { runCatching { Sources.loadAll(context) } }
    val list = sources.getOrNull()
    val date = remember {
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date()).uppercase(Locale.US)
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // M22: hoisted to the shell via MastheadHost when present.
            EditorialMastheadSlot(Routes.SOURCES) {
                EditorialMasthead(
                    title = stringResource(R.string.tab_sources),
                    titleEn = "READING",
                    meta = if (list != null) {
                        stringResource(
                            R.string.sources_meta,
                            list.size,
                            list.map { it.category }.distinct().size,
                        ) + " · " + date
                    } else {
                        date
                    },
                )
            }
            when {
                list == null -> StatusPanel(
                    text = stringResource(R.string.sources_load_failed) + "\n\n" +
                        sources.exceptionOrNull()?.stackTraceToString().orEmpty(),
                    modifier = Modifier.weight(1f),
                )
                list.isEmpty() -> StatusPanel(
                    text = stringResource(R.string.sources_empty),
                    modifier = Modifier.weight(1f),
                )
                else -> SourceShelf(
                    sources = list,
                    onOpenSource = onOpenSource,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SourceShelf(
    sources: List<SourceConfig>,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(sources) {
        sources.groupBy { it.category }.toList()
            .sortedBy { (cat, _) ->
                CATEGORY_ORDER.indexOf(cat).takeIf { it >= 0 } ?: CATEGORY_ORDER.size
            }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        groups.forEach { (category, itemsInGroup) ->
            item(key = "header-$category") {
                EditorialSectionHeader(
                    title = categoryLabel(category),
                    titleEn = category,
                    count = itemsInGroup.size,
                    modifier = Modifier.padding(
                        start = EditorialSpacing.lg,
                        end = EditorialSpacing.lg,
                        top = EditorialSpacing.xl, // 分类组间距 24 (§5)
                    ),
                )
            }
            items(itemsInGroup, key = { it.id }) { cfg ->
                SourceRow(cfg = cfg, onClick = { onOpenSource(cfg.id) })
            }
        }
        item(key = "footer-credit") {
            Text(
                text = stringResource(R.string.dict_credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
    }
}

@Composable
private fun categoryLabel(category: String): String {
    val labelRes = when (category) {
        "learning" -> R.string.sources_category_learning
        "science" -> R.string.sources_category_science
        "tech" -> R.string.sources_category_tech
        "fiction" -> R.string.sources_category_fiction
        "papers" -> R.string.sources_category_papers
        else -> R.string.sources_category_news
    }
    return stringResource(labelRes)
}

/**
 * Source row (§4.3): serif first-letter monogram (no ring, no fill) + name
 * 15sp/500 + host 12sp sub + optional proxy badge; 60dp row, hairline below.
 */
@Composable
private fun SourceRow(cfg: SourceConfig, onClick: () -> Unit) {
    val host = remember(cfg.homepage) {
        Uri.parse(cfg.homepage).host?.removePrefix("www.") ?: cfg.homepage
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = EditorialSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (cfg.name.firstOrNull()?.uppercaseChar() ?: '?').toString(),
                style = EditorialType.monogram,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = EditorialSpacing.md)) {
                Text(
                    text = cfg.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = host,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (cfg.needsProxy) {
                ProxyBadge(text = stringResource(R.string.source_needs_proxy))
            }
        }
        EditorialHairline(modifier = Modifier.padding(horizontal = EditorialSpacing.lg))
    }
}

/** Centred, scrollable, selectable panel for status and error text. */
@Composable
fun StatusPanel(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        SelectionContainer {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
