package dev.handypage.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.concurrent.TimeUnit

/**
 * DSL engine: fetches an index page (RSS or HTML link list) and extracts
 * cleaned article bodies, driven by a [SourceConfig].
 *
 * This is a faithful port of the dev-side reference implementation
 * tools/converter/replay_fixtures.py (parse_index / parse_article), with
 * networking on top. All blocking IO happens on [Dispatchers.IO].
 */
class SourceEngine(
    private val client: OkHttpClient,
    private val userAgent: String = DESKTOP_UA,
    private val enforceDelays: Boolean = true,
    /** M28: embeds article images into the EPUB; null disables (offline fixture tests). */
    private val imageEmbedder: ImageEmbedder? = ImageEmbedder(client),
) {
    companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        /**
         * Whole-call cap (connect + TLS + full body transfer). 15s was too
         * tight: APOD's 334KB archive index arrives at ~14KB/s on a slow
         * international link (M18 device measurement: 23.7s), and
         * callTimeout covers the body transfer, not just the first byte.
         */
        private const val CALL_TIMEOUT_SECONDS = 45L

        /** Port of replay_fixtures.humanize_slug(). */
        fun humanizeSlug(url: String): String {
            var slug = url.trimEnd('/').substringAfterLast('/')
            slug = slug.replace(Regex("\\.html?$"), "")
            slug = slug.replace(Regex("-level-\\d+$"), "")
            slug = slug.replace(Regex("^\\d{6}-"), "") // BNE date prefix
            val s = slug.replace('-', ' ').trim()
            // Python str.capitalize(): uppercase first char, lowercase the rest.
            return if (s.isEmpty()) s else s.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    // Per-source id -> epoch millis of the (reserved) last request slot.
    private val lastRequestAt = mutableMapOf<String, Long>()
    private val politenessLock = Mutex()

    suspend fun fetchIndex(cfg: SourceConfig): List<IndexItem> = withContext(Dispatchers.IO) {
        val body = httpGet(cfg, cfg.index.url, stage = "index_fetch")
        try {
            when (cfg.index.type) {
                IndexCfg.Type.RSS -> parseRssIndex(cfg, body)
                IndexCfg.Type.HTML -> parseHtmlIndex(cfg, body)
                // ARXIV sources are served by arxiv.ArxivApi; the UI layer
                // dispatches on index.type before calling fetchIndex.
                IndexCfg.Type.ARXIV ->
                    throw EngineException("index_parse", "arxiv index is served by ArxivApi, not SourceEngine")
            }.take(cfg.index.max)
        } catch (e: EngineException) {
            throw e
        } catch (e: Exception) {
            throw EngineException("index_parse", cause = e)
        }
    }

    suspend fun fetchArticle(
        cfg: SourceConfig,
        url: String,
        fallbackTitle: String? = null,
    ): ArticleContent = withContext(Dispatchers.IO) {
        val body = httpGet(cfg, url, stage = "article_fetch")
        try {
            val doc = Jsoup.parse(body, url)
            val art = cfg.article
            val title = art.title
                ?.let { sel -> doc.selectFirst(sel)?.text()?.trim()?.takeIf { it.isNotEmpty() } }
                ?: fallbackTitle
                ?: doc.title()
            val content = doc.selectFirst(art.content)
                ?: throw EngineException(
                    "article_parse",
                    "content selector '${art.content}' matched nothing at $url",
                )
            for (sel in art.remove) {
                content.select(sel).remove()
            }
            content.select("script, style, iframe, noscript").remove()
            // Absolutize resource/link URLs so the body is self-contained.
            content.select("img[src]").forEach { it.attr("src", it.absUrl("src")) }
            content.select("a[href]").forEach { it.attr("href", it.absUrl("href")) }
            // M28: download images into the EPUB package (CDN hotlink blocks,
            // offline reading, cleartext policy); failures keep remote URLs.
            val embedded = imageEmbedder?.embed(content.html(), url, cfg.userAgent ?: userAgent)
            ArticleContent(
                title = title,
                bodyHtml = embedded?.html ?: content.html(),
                sourceUrl = url,
                images = embedded?.images ?: emptyMap(),
            )
        } catch (e: EngineException) {
            throw e
        } catch (e: Exception) {
            throw EngineException("article_parse", cause = e)
        }
    }

    /** RSS 2.0 `<item>` list with an Atom `<entry>` fallback (replay_fixtures.parse_index, rss branch). */
    private fun parseRssIndex(cfg: SourceConfig, body: String): List<IndexItem> {
        val doc = Jsoup.parse(body, cfg.index.url, Parser.xmlParser())
        var entries = doc.select("item")
        if (entries.isEmpty()) entries = doc.select("entry") // Atom
        return entries.mapNotNull { el ->
            val linkEl = el.selectFirst("link") ?: return@mapNotNull null
            // RSS 2.0: <link>text</link>; Atom: <link href="..."/>.
            val url = (if (linkEl.hasAttr("href")) linkEl.attr("href") else linkEl.text()).trim()
            if (url.isEmpty()) return@mapNotNull null
            val title = el.selectFirst("title")?.text()?.trim().orEmpty()
            // RSS 2.0 uses <description>, Atom uses <summary>. Feed authors
            // frequently embed escaped HTML there (ProPublica: "<p>The post
            // <a href=..."), so flatten it to plain text for the list row.
            val rawSummary = (el.selectFirst("description") ?: el.selectFirst("summary"))
                ?.text()?.trim().orEmpty()
            IndexItem(
                title = title.ifBlank { humanizeSlug(url) },
                url = url,
                summary = rawSummary.takeIf { it.isNotEmpty() }
                    ?.let { Jsoup.parse(it).text().trim().ifBlank { null } },
                published = (
                    el.selectFirst("pubDate")
                        ?: el.selectFirst("published")
                        ?: el.selectFirst("updated")
                    )?.text()?.trim()?.ifBlank { null },
            )
        }
    }

    /** HTML link list, deduped by URL keeping the longest anchor text (parse_index, html branch). */
    private fun parseHtmlIndex(cfg: SourceConfig, body: String): List<IndexItem> {
        val linkCss = cfg.index.linkCss
            ?: throw EngineException("index_parse", "html index requires link_css")
        val rx = Regex(
            cfg.index.linkRegex ?: throw EngineException("index_parse", "html index requires link_regex"),
        )
        val doc = Jsoup.parse(body, cfg.index.url) // base uri for absUrl()
        val best = LinkedHashMap<String, String>() // url -> longest anchor text, first-seen order
        for (a in doc.select(linkCss)) {
            val href = a.attr("href")
            if (!rx.containsMatchIn(href)) continue
            val url = a.absUrl("href")
            val text = a.text().trim()
            // Bug-for-bug port: strictly-longer text wins, so a URL first seen
            // with empty anchor text (image link) is dropped unless a later
            // anchor carries real text.
            if (text.length > (best[url]?.length ?: 0)) {
                best[url] = text
            }
        }
        return best.map { (url, text) ->
            IndexItem(title = text.ifBlank { humanizeSlug(url) }, url = url)
        }
    }

    private suspend fun httpGet(cfg: SourceConfig, url: String, stage: String): String {
        throttle(cfg)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", cfg.userAgent ?: userAgent)
            .build()
        val call = client.newBuilder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
            .newCall(request)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw EngineException(stage, "HTTP ${resp.code} for $url")
                }
                return resp.body.string()
            }
        } catch (e: EngineException) {
            throw e
        } catch (e: Exception) {
            throw EngineException(stage, cause = e)
        }
    }

    /** Enforces cfg.delaySeconds as a minimum interval between requests to one source. */
    private suspend fun throttle(cfg: SourceConfig) {
        if (!enforceDelays) return
        val waitMs = politenessLock.withLock {
            val now = System.currentTimeMillis()
            val last = lastRequestAt[cfg.id] ?: 0L
            val earliest = last + (cfg.delaySeconds * 1000).toLong()
            if (earliest > now) {
                lastRequestAt[cfg.id] = earliest // reserve the slot for this call
                earliest - now
            } else {
                lastRequestAt[cfg.id] = now
                0L
            }
        }
        if (waitMs > 0) delay(waitMs)
    }
}
