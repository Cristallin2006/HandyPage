package dev.handypage.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.handypage.app.HandypageApp
import dev.handypage.app.R
import dev.handypage.app.ReaderActivity
import dev.handypage.app.Sources
import dev.handypage.app.ai.AISettingsStore
import dev.handypage.app.epub.EpubPackager
import dev.handypage.app.reader.ChatController
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Agent tab (M15 editorial masthead + M16 recommendation cards + M17 history).
 * The kicker-right history icon opens the conversation history page.
 * Session switching is driven by [HandypageApp.currentGlobalSessionKey].
 */
@Composable
fun AgentScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HandypageApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // M17: track session key; when it changes the controller is rebuilt.
    var sessionKey by remember { mutableStateOf(app.currentGlobalSessionKey) }
    var revision by remember { mutableIntStateOf(0) }

    // Detect external session switch (from HistoryScreen).
    LaunchedEffect(Unit) {
        // Poll on resume: when this composable re-enters composition after
        // history pop, pick up the potentially changed key.
        if (app.currentGlobalSessionKey != sessionKey) {
            sessionKey = app.currentGlobalSessionKey
            revision++
        }
    }

    val controller = remember(revision) {
        ChatController(
            app = app,
            context = context,
            articleKey = sessionKey,
            articleTitle = "",
            articleUrl = "",
            sourceName = "",
            articleTextProvider = { "" },
            scope = scope,
        )
    }
    LaunchedEffect(controller) { controller.start() }

    val aiConfig = remember { AISettingsStore(context).selectedConfig() }

    val paperOpener = rememberPaperOpener(
        delaySeconds = 3.0,
        sourceId = "arxiv",
        sourceName = "arXiv",
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // M22: hoisted to the shell via MastheadHost when present (the
            // masthead never joins tab transitions); inline otherwise.
            EditorialMastheadSlot(Routes.AGENT) {
                EditorialMasthead(
                    title = stringResource(R.string.tab_agent),
                    titleEn = null,
                    meta = if (aiConfig.isUsable) {
                        "${aiConfig.preset.label} · ${aiConfig.effectiveModel}".uppercase(Locale.US)
                    } else {
                        stringResource(R.string.agent_meta_no_key)
                    },
                    kickerEnd = {
                        // M17: history entry (design-system §4.1 kicker tool slot)
                        IconButton(
                            onClick = onOpenHistory,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = stringResource(R.string.history_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
            }
            ChatPanel(
                controller = controller,
                onQuickGuide = null,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                applyInsets = false,
                inputHintRes = R.string.agent_input_hint_global,
                emptyState = {
                    AgentEmptyState(
                        onQuickRecommend = { controller.send("推荐几篇适合我的文章") },
                        onQuickPapers = { controller.send("推荐最新学术论文") },
                        onQuickPreference = { controller.send("我想设置阅读偏好") },
                        enabled = controller.state.value.ready && !controller.state.value.busy,
                    )
                },
                onOpenArticle = { url, title, sourceName ->
                    scope.launch {
                        try {
                            val sources = Sources.loadAll(context)
                            val cfg = sources.firstOrNull { it.name == sourceName }
                                ?: sources.firstOrNull { url.startsWith(it.homepage) }
                            if (cfg == null) {
                                snackbarHostState.showSnackbar("找不到对应阅读源")
                                return@launch
                            }
                            val article = app.engine.fetchArticle(cfg, url, fallbackTitle = title)
                            val outFile = withContext(Dispatchers.IO) {
                                EpubPackager.pack(article, cfg.name, epubFileFor(context, url))
                            }
                            withContext(Dispatchers.IO) {
                                app.vocabDb.articleRecordDao().recordOpen(
                                    url = url, title = article.title,
                                    sourceId = cfg.id, sourceName = cfg.name,
                                    epubPath = outFile.absolutePath,
                                    now = System.currentTimeMillis(),
                                )
                            }
                            context.startActivity(
                                Intent(context, ReaderActivity::class.java)
                                    .putExtra(ReaderActivity.EXTRA_EPUB_PATH, outFile.absolutePath),
                            )
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("打开失败：${e.message}")
                        }
                    }
                },
                onOpenPaper = { absUrl, title ->
                    val entry = dev.handypage.app.arxiv.ArxivEntry(
                        id = absUrl.trimEnd('/').substringAfterLast('/'),
                        title = title, authors = emptyList(), summary = "",
                        published = "", pdfUrl = absUrl.replace("/abs/", "/pdf/"),
                        absUrl = absUrl, primaryCategory = "",
                    )
                    paperOpener.open(entry)
                },
            )
        }
        PaperOpenDialog(opener = paperOpener)
    }
}

/** Editorial empty state with M16 quick-action chips. */
@Composable
private fun AgentEmptyState(
    onQuickRecommend: () -> Unit,
    onQuickPapers: () -> Unit,
    onQuickPreference: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = EditorialSpacing.lg)
            .padding(top = EditorialSpacing.xxl),
    ) {
        Text(
            text = stringResource(R.string.agent_empty_title),
            style = EditorialType.emptyTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EditorialHairline(modifier = Modifier.padding(vertical = EditorialSpacing.md))
        Text(
            text = stringResource(R.string.agent_empty_hint),
            style = EditorialType.guide,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.padding(top = EditorialSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(EditorialSpacing.sm),
        ) {
            AssistChip(onClick = onQuickRecommend, enabled = enabled,
                label = { Text(stringResource(R.string.agent_chip_recommend)) })
            AssistChip(onClick = onQuickPapers, enabled = enabled,
                label = { Text(stringResource(R.string.agent_chip_papers)) })
            AssistChip(onClick = onQuickPreference, enabled = enabled,
                label = { Text(stringResource(R.string.agent_chip_preference)) })
        }
    }
}

/** Room session key for the global Agent-tab conversation. */
const val GLOBAL_SESSION_KEY = "global"