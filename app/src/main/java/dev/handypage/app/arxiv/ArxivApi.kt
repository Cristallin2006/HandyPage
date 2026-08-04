package dev.handypage.app.arxiv

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One arXiv Atom `<entry>` flattened for the reader UI. */
data class ArxivEntry(
    /** Bare arXiv id with version, e.g. "1706.03762v5" (last path segment of the entry id URL). */
    val id: String,
    val title: String,
    val authors: List<String>,
    val summary: String,
    val published: String,
    val pdfUrl: String,
    val absUrl: String,
    val primaryCategory: String,
)

/** HTTP failure carrying the status code and the optional Retry-After header (seconds). */
class HttpStatusException(
    val statusCode: Int,
    val retryAfterSeconds: Long?,
    url: String,
) : IOException("HTTP $statusCode for $url")

/**
 * M25: app-global arXiv request gate — at most one request every
 * [minIntervalMs] across EVERY caller (list UI, Agent tools, PDF downloads,
 * HTML-version fetches). Blocking by design: callers run on Dispatchers.IO,
 * and the monitor both serialises and spaces requests, so fan-in from
 * several features can never burst past arXiv's one-request-per-3s etiquette.
 *
 * This replaces the pre-M25 "throttling is the caller's job" split, where
 * the list screen, the Agent's search_papers tool and downloads each kept
 * their own (or no) throttle and stacked into an IP ban.
 */
class ArxivGate(
    private val minIntervalMs: Long = 3000,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var lastRequestAt = 0L

    /** Blocks until this caller may fire the next request, then takes the slot. */
    @Synchronized
    fun awaitTurn() {
        val wait = minIntervalMs - (nowMs() - lastRequestAt)
        if (wait > 0) sleeper(wait)
        lastRequestAt = nowMs()
    }
}

/**
 * M25: TTL cache for identical feed queries. Repeat searches, back-and-forth
 * navigation and category re-taps within [ttlMs] cost zero network requests;
 * together with the per-key single-flight in [ArxivApi.queryFeed] an
 * identical concurrent query collapses into one request.
 */
class FeedCache(
    private val ttlMs: Long = 10 * 60_000L,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val entries = HashMap<String, Pair<Long, List<ArxivEntry>>>()

    @Synchronized
    fun get(key: String): List<ArxivEntry>? {
        val (at, value) = entries[key] ?: return null
        if (nowMs() - at > ttlMs) {
            entries.remove(key)
            return null
        }
        return value
    }

    @Synchronized
    fun put(key: String, value: List<ArxivEntry>) {
        entries[key] = nowMs() to value
    }
}

/**
 * Minimal arXiv API client (https://info.arxiv.org/help/api): queries the Atom
 * search feed and downloads PDFs.
 *
 * Rate limiting, retries and caching live INSIDE this class (M25), shared
 * app-wide via [sharedGate]/[sharedFeedCache]:
 * - every request takes a slot from the gate (≥3 s between requests);
 * - 429/5xx and socket timeouts retry up to 3 attempts with 3s→9s→27s
 *   exponential backoff (plus jitter), honouring the Retry-After header;
 * - identical feed queries are served from a 10-minute TTL cache.
 *
 * Android-free (OkHttp/Jsoup/java.io only), so it runs under plain JVM tests.
 */
class ArxivApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://export.arxiv.org",
    // The official HTML version is only served on the main site, not on the
    // export mirror that hosts the API feed.
    private val htmlBaseUrl: String = "https://arxiv.org",
    private val gate: ArxivGate = sharedGate,
    private val cache: FeedCache = sharedFeedCache,
    // Test hooks: production sleeps on the calling (IO) thread.
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val jitterMs: () -> Long = { kotlin.random.Random.nextLong(0, 1000) },
) {
    companion object {
        private const val USER_AGENT = "Handypage/1.0 (arxiv reader; contact: github.com/handypage)"
        /** M18 lesson: CN slow links need ~24 s for a big feed; aligned with the engine's 45 s. */
        private const val QUERY_TIMEOUT_SECONDS = 45L
        /** HTML-version fetch has a PDF fallback, so it fails faster and retries less. */
        private const val HTML_TIMEOUT_SECONDS = 30L
        private const val MAX_ATTEMPTS = 3
        private const val MAX_RETRY_AFTER_MS = 120_000L
        private val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)
        private val WS_RUN = Regex("\\s+")
        /** Official field prefixes (API manual §5.1); a token starting with one passes through untouched. */
        private val FIELD_PREFIX = Regex("^(ti|au|abs|co|jr|cat|rn|all):", RegexOption.IGNORE_CASE)
        /** Boolean operators the user may type explicitly (uppercase, per the manual). */
        private val BOOLEAN_OPS = setOf("AND", "OR", "ANDNOT")
        /**
         * M31: everything outside letters/digits/`._-` is stripped from a term.
         * Empirical (v0.2.x probes): hyphens pass through fine (`all:GPT-4`),
         * while quotes/parens/colons can silently corrupt the server-side parse
         * (an unbalanced quote makes the API rewrite the query into a term-OR).
         */
        private val TERM_STRIP = Regex("[^\\p{L}\\p{N}._-]+")

        /** App-wide single gate/cache shared by every ArxivApi instance. */
        val sharedGate = ArxivGate()
        val sharedFeedCache = FeedCache()
        private val sharedFlightLocks = ConcurrentHashMap<String, Any>()
    }

    /**
     * M31: free-text search built the official way (API manual §5.1) — terms
     * are split on whitespace, cleaned of parser-hostile characters and ANDed
     * (`all:diffusion AND all:transformer`), instead of the pre-M31 verbatim
     * `all:"<whole input>"` exact-phrase query that missed most relevant papers
     * (1 570 vs 7 486 hits for "diffusion transformer") and silently degraded
     * into a term-OR whenever the input contained an unbalanced quote.
     * Tokens with a recognised field prefix (`ti:`/`au:`/`abs:`/…) and
     * uppercase boolean operators pass through, so power users and the Agent
     * can write `au:hinton AND ti:dropout`. Empty input yields no results.
     */
    fun search(query: String, start: Int = 0, maxResults: Int = 25): List<ArxivEntry> {
        val q = buildSearchQuery(query)
        if (q.isEmpty()) return emptyList()
        return queryFeed(searchQuery = q, start = start, maxResults = maxResults, sortBy = "relevance")
    }

    /** Latest submissions in one subject category (e.g. "cs.CL"), newest first. */
    fun byCategory(category: String, start: Int = 0, maxResults: Int = 25): List<ArxivEntry> =
        queryFeed(searchQuery = "cat:$category", start = start, maxResults = maxResults, sortBy = "submittedDate")

    /** Combined keyword + category search (M16 Agent tool). M31: keyword part is parenthesised so a user-typed OR can't leak past the category filter. */
    fun searchAndCategory(query: String, category: String, start: Int = 0, maxResults: Int = 25): List<ArxivEntry> {
        val q = buildSearchQuery(query)
        val combined = if (q.isEmpty()) "cat:$category" else "($q) AND cat:$category"
        return queryFeed(searchQuery = combined, start = start, maxResults = maxResults, sortBy = "relevance")
    }

    /**
     * M31: turns free-form input into an official-style `search_query`.
     * Plain terms become `all:<term>` and are ANDed; a token carrying a
     * recognised field prefix (`ti:`/`au:`/`abs:`/…) stays as-is; an
     * uppercase `AND`/`OR`/`ANDNOT` between terms is honoured. Leading,
     * trailing and doubled operators are dropped, and characters that could
     * corrupt the server-side parse (quotes, parens, colons inside terms,
     * backslashes…) are stripped — the API never reports a malformed query,
     * it silently rewrites it, so the cleaning must happen client-side.
     * Returns "" when nothing usable remains.
     */
    internal fun buildSearchQuery(input: String): String {
        val sb = StringBuilder()
        var pendingOp: String? = null
        for (raw in input.trim().split(WS_RUN)) {
            if (raw.isEmpty()) continue
            if (raw in BOOLEAN_OPS) {
                // First operator wins between two terms; a leading operator is
                // ignored and a trailing one is never flushed.
                if (sb.isNotEmpty() && pendingOp == null) pendingOp = raw
                continue
            }
            val term = cleanTerm(raw) ?: continue
            if (sb.isNotEmpty()) sb.append(' ').append(pendingOp ?: "AND").append(' ')
            sb.append(term)
            pendingOp = null
        }
        return sb.toString()
    }

    /** Cleans one raw token; keeps a recognised field prefix intact. Null when nothing usable remains. */
    private fun cleanTerm(raw: String): String? {
        val prefix = FIELD_PREFIX.find(raw)?.value
        val body = TERM_STRIP.replace(raw.substring(prefix?.length ?: 0), "")
        if (body.isEmpty()) return null
        return (prefix ?: "all:") + body
    }

    private fun queryFeed(searchQuery: String, start: Int, maxResults: Int, sortBy: String): List<ArxivEntry> {
        val cacheKey = "$searchQuery|$start|$maxResults|$sortBy"
        cache.get(cacheKey)?.let { return it }
        // Single-flight: concurrent identical queries collapse into one request.
        val lock = sharedFlightLocks.getOrPut(cacheKey) { Any() }
        return synchronized(lock) {
            cache.get(cacheKey) ?: run {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("api/query")
                    .addQueryParameter("search_query", searchQuery)
                    .addQueryParameter("start", start.toString())
                    .addQueryParameter("max_results", maxResults.toString())
                    .addQueryParameter("sortBy", sortBy)
                    .addQueryParameter("sortOrder", "descending")
                    .build()
                val body = withRetry { httpGet(url, QUERY_TIMEOUT_SECONDS) }
                parseFeed(body).also { cache.put(cacheKey, it) }
            }
        }
    }

    /** GETs [url] as a string; throws [HttpStatusException] on non-2xx. */
    private fun httpGet(url: HttpUrl, timeoutSeconds: Long): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return client.newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw HttpStatusException(resp.code, resp.header("Retry-After")?.toLongOrNull(), url.toString())
                }
                resp.body.string()
            }
    }

    /**
     * Runs [block] through the gate, retrying retryable HTTP statuses and
     * socket timeouts with 3s→9s→27s exponential backoff (+ jitter); a
     * Retry-After header wins over the computed backoff. OkHttp's own
     * cancellation ("Canceled") is never retried — only genuine timeouts are.
     */
    private fun <T> withRetry(maxAttempts: Int = MAX_ATTEMPTS, block: () -> T): T {
        var backoffMs = 3000L
        var attempt = 0
        while (true) {
            attempt++
            gate.awaitTurn()
            try {
                return block()
            } catch (e: HttpStatusException) {
                if (e.statusCode !in RETRYABLE_CODES || attempt >= maxAttempts) throw e
                val waitMs = e.retryAfterSeconds
                    ?.let { (it * 1000).coerceAtMost(MAX_RETRY_AFTER_MS) }
                    ?: (backoffMs + jitterMs())
                if (waitMs > 0) sleeper(waitMs)
                backoffMs *= 3
            } catch (e: InterruptedIOException) {
                val isTimeout = e.message?.lowercase()
                    ?.let { it == "timeout" || "timed out" in it } == true
                if (!isTimeout || attempt >= maxAttempts) throw e
                sleeper(backoffMs + jitterMs())
                backoffMs *= 3
            }
        }
    }

    /** Parses the Atom response; entries without an id URL are skipped. */
    internal fun parseFeed(body: String): List<ArxivEntry> {
        val doc = Jsoup.parse(body, Parser.xmlParser())
        return doc.select("feed > entry").mapNotNull { el ->
            val idUrl = el.selectFirst("id")?.text()?.trim().orEmpty()
            if (idUrl.isEmpty()) return@mapNotNull null
            val absUrl = el.selectFirst("link[rel=alternate]")?.attr("href")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: idUrl
            ArxivEntry(
                id = idUrl.trimEnd('/').substringAfterLast('/'),
                title = collapseWs(el.selectFirst("title")?.text()),
                authors = el.select("author name").map { collapseWs(it.text()) },
                summary = collapseWs(el.selectFirst("summary")?.text()),
                published = el.selectFirst("published")?.text()?.trim().orEmpty(),
                // Entries normally carry <link title="pdf">; derive it from the
                // abstract page URL otherwise.
                pdfUrl = el.selectFirst("link[title=pdf]")?.attr("href")?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: absUrl.replace("/abs/", "/pdf/"),
                absUrl = absUrl,
                primaryCategory = el.selectFirst("category[term]")?.attr("term")?.trim().orEmpty(),
            )
        }
    }

    /**
     * Streams the PDF at [url] to [dest] via a ".part" sibling renamed on
     * success, so an interrupted download never leaves a truncated file at the
     * final name. [onProgress] receives the fraction 0..1 of contentLength, or
     * -1f when the length is unknown. Non-2xx and IO failures raise IOException.
     *
     * M25: goes through the shared gate and retry loop like every other arXiv
     * request; per-attempt timeouts are connect 15 s / read 30 s with no
     * overall cap, so multi-MB PDFs on slow links finish instead of dying at
     * OkHttp's 10 s defaults. A retried attempt restarts the download.
     */
    fun downloadPdf(url: String, dest: File, onProgress: (Float) -> Unit) {
        val downloadClient = client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        withRetry {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            downloadClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw HttpStatusException(resp.code, resp.header("Retry-After")?.toLongOrNull(), url)
                }
                val total = resp.body.contentLength()
                val part = File(dest.absoluteFile.parentFile, dest.name + ".part")
                try {
                    resp.body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                read += n
                                onProgress(if (total > 0) read.toFloat() / total else -1f)
                            }
                        }
                    }
                } catch (e: Exception) {
                    part.delete()
                    throw e
                }
                // File.renameTo fails on Windows when dest already exists (re-download).
                if (dest.exists() && !dest.delete()) {
                    part.delete()
                    throw IOException("failed to replace existing ${dest.name}")
                }
                if (!part.renameTo(dest)) {
                    part.delete()
                    throw IOException("failed to rename ${part.name} to ${dest.name}")
                }
            }
        }
    }

    /**
     * M12-B: fetches arXiv's official HTML version (ar5iv) of a paper, served
     * for papers with LaTeX source at `$htmlBaseUrl/html/<id>` (a bare id
     * redirects to the latest `/html/<id>vN`, which OkHttp follows).
     *
     * Returns the body on a 200 with an HTML content type; null on 404, any
     * other non-2xx, or an IO failure — callers fall back to PDF extraction,
     * so a flaky network must not break the reflow pipeline. Retries at most
     * once: the fallback keeps this fetch cheap to abandon.
     */
    fun fetchHtmlVersion(arxivId: String): String? {
        val url = htmlBaseUrl.toHttpUrl().newBuilder()
            .addPathSegments("html/$arxivId")
            .build()
        return try {
            withRetry(maxAttempts = 2) {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                client.newBuilder()
                    .callTimeout(HTML_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()
                    .newCall(request)
                    .execute().use { resp ->
                        if (!resp.isSuccessful) {
                            if (resp.code in RETRYABLE_CODES) {
                                throw HttpStatusException(resp.code, resp.header("Retry-After")?.toLongOrNull(), url.toString())
                            }
                            return@use null
                        }
                        val contentType = resp.header("Content-Type").orEmpty().lowercase()
                        if ("html" !in contentType) return@use null
                        resp.body.string()
                    }
            }
        } catch (e: IOException) {
            null
        }
    }

    /** Folds all whitespace runs (incl. newlines) into single spaces. */
    private fun collapseWs(s: String?): String =
        s?.replace(WS_RUN, " ")?.trim().orEmpty()
}
