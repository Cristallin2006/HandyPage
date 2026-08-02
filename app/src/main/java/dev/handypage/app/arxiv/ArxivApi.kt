package dev.handypage.app.arxiv

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
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

/**
 * Minimal arXiv API client (https://info.arxiv.org/help/api): queries the Atom
 * search feed and downloads PDFs.
 *
 * arXiv API etiquette asks for an identifiable User-Agent and at most one
 * request every 3 seconds. This class does NOT sleep or serialise itself:
 * throttling is the caller's job (the UI layer issues calls sequentially with
 * its own delay).
 *
 * Android-free (OkHttp/Jsoup/java.io only), so it runs under plain JVM tests.
 */
class ArxivApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://export.arxiv.org",
    // The official HTML version is only served on the main site, not on the
    // export mirror that hosts the API feed.
    private val htmlBaseUrl: String = "https://arxiv.org",
) {
    companion object {
        private const val USER_AGENT = "Handypage/1.0 (arxiv reader; contact: github.com/handypage)"
        private const val CALL_TIMEOUT_SECONDS = 15L
        private val WS_RUN = Regex("\\s+")
    }

    /** Full-text relevance search: `search_query=all:"<query>"`, most relevant first. */
    fun search(query: String, start: Int = 0, maxResults: Int = 25): List<ArxivEntry> =
        queryFeed(searchQuery = "all:\"$query\"", start = start, maxResults = maxResults, sortBy = "relevance")

    /** Latest submissions in one subject category (e.g. "cs.CL"), newest first. */
    fun byCategory(category: String, start: Int = 0, maxResults: Int = 25): List<ArxivEntry> =
        queryFeed(searchQuery = "cat:$category", start = start, maxResults = maxResults, sortBy = "submittedDate")

    /** Combined keyword + category search (M16 Agent tool). */
    fun searchAndCategory(query: String, category: String, start: Int = 0, maxResults: Int = 25): List<ArxivEntry> =
        queryFeed(searchQuery = "all:\"$query\" AND cat:$category", start = start, maxResults = maxResults, sortBy = "relevance")


    private fun queryFeed(searchQuery: String, start: Int, maxResults: Int, sortBy: String): List<ArxivEntry> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/query")
            .addQueryParameter("search_query", searchQuery)
            .addQueryParameter("start", start.toString())
            .addQueryParameter("max_results", maxResults.toString())
            .addQueryParameter("sortBy", sortBy)
            .addQueryParameter("sortOrder", "descending")
            .build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val body = client.newBuilder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
                resp.body.string()
            }
        return parseFeed(body)
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
     */
    fun downloadPdf(url: String, dest: File, onProgress: (Float) -> Unit) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
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

    /**
     * M12-B: fetches arXiv's official HTML version (ar5iv) of a paper, served
     * for papers with LaTeX source at `$htmlBaseUrl/html/<id>` (a bare id
     * redirects to the latest `/html/<id>vN`, which OkHttp follows).
     *
     * Returns the body on a 200 with an HTML content type; null on 404, any
     * other non-2xx, or an IO failure 鈥?callers fall back to PDF extraction,
     * so a flaky network must not break the reflow pipeline.
     */
    fun fetchHtmlVersion(arxivId: String): String? {
        val url = htmlBaseUrl.toHttpUrl().newBuilder()
            .addPathSegments("html/$arxivId")
            .build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return try {
            client.newBuilder()
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val contentType = resp.header("Content-Type").orEmpty().lowercase()
                    if ("html" !in contentType) return@use null
                    resp.body.string()
                }
        } catch (e: IOException) {
            null
        }
    }

    /** Folds all whitespace runs (incl. newlines) into single spaces. */
    private fun collapseWs(s: String?): String =
        s?.replace(WS_RUN, " ")?.trim().orEmpty()
}

