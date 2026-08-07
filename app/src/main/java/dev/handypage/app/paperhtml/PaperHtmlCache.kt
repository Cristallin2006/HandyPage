package dev.handypage.app.paperhtml

import dev.handypage.app.arxiv.ArxivApi
import dev.handypage.app.arxiv.ArxivEntry
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.coroutines.coroutineContext

/**
 * The paper has no official HTML version (ar5iv/arXiv HTML only exists for
 * papers with LaTeX source). Callers fall back to the PDF view.
 */
class PaperHtmlUnavailableException(val absUrl: String) :
    IOException("no official HTML version for $absUrl")

/**
 * One downloadble page resource: remote URL + its local relative path.
 * [fromCss] marks references discovered INSIDE a cached stylesheet: their
 * failure is patched inside that stylesheet (not in [PreparedHtml.html]).
 */
data class CachedResource(
    val remoteUrl: String,
    val relPath: String,
    val fromCss: Boolean = false,
)

/**
 * Result of [PaperHtmlRewriter.prepare]: the rewritten page (chrome stripped,
 * resource URLs pointing at the `paper.local` virtual origin, bilingual
 * assets injected) plus the resources that must be downloaded for it to work
 * offline.
 */
data class PreparedHtml(
    val html: String,
    val resources: List<CachedResource>,
    val hash: String,
)

/**
 * Pure Jsoup rewriting of an ar5iv/arXiv-HTML page for local WebView
 * rendering (android-free, JVM-testable):
 *
 *  - page chrome (arXiv banners/footers) and scripts are stripped;
 *  - relative img/link URLs are absolutised against
 *    `https://arxiv.org/html/<id>/` — manually, because jsoup honours the
 *    ar5iv `<base>` tag which points at the wrong directory (same gotcha as
 *    [dev.handypage.app.arxiv.ArxivHtml], M28) — then rewritten to the
 *    virtual origin `https://paper.local/html/<hash>/<relPath>`;
 *  - `a[href]` links are absolutised but stay remote (no link caching);
 *  - viewport meta + bilingual.css/bilingual.js references are injected.
 */
object PaperHtmlRewriter {

    const val VIRTUAL_HOST = "paper.local"

    /** Elements never needed for offline reading (interactive bits, chrome). */
    private const val DROP_SELECTORS =
        "script, style, noscript, iframe, form, button, nav, header, footer, " +
            ".ltx_page_header, .ltx_page_footer, .ltx_page_logo, .ltx_banner, " +
            ".ds-announcement, .arxiv-header, #arxiv-header, #arxiv-footer"

    private const val INJECTED_HEAD =
        """<meta name="viewport" content="width=device-width, initial-scale=1">""" +
            """<link rel="stylesheet" href="https://$VIRTUAL_HOST/assets/paper/bilingual.css">""" +
            """<script src="https://$VIRTUAL_HOST/assets/paper/bilingual.js"></script>"""

    /**
     * Rewrites [html] (the full official HTML page of the paper whose abs
     * page is [absUrl]) for serving under `https://[VIRTUAL_HOST]/html/[hash]/`.
     */
    fun prepare(html: String, hash: String, absUrl: String): PreparedHtml {
        val doc: Document = Jsoup.parse(html, "https://arxiv.org")
        val bareId = absUrl.trimEnd('/').substringAfterLast('/')
        val virtualBase = "https://$VIRTUAL_HOST/html/$hash/"

        doc.select(DROP_SELECTORS).remove()

        val resources = mutableListOf<CachedResource>()
        val seen = HashSet<String>()

        for (img in doc.select("img[src]")) {
            val src = img.attr("src").trim()
            // ar5iv sometimes emits absolute URLs pointing back into the
            // paper's own HTML directory — cache those by file name too.
            val selfAbsolute = src.startsWith("https://arxiv.org/html/") ||
                src.startsWith("http://arxiv.org/html/")
            val relPath = if (selfAbsolute) {
                src.substringAfterLast('/').takeIf {
                    it.isNotBlank() && !it.contains("..") && !it.contains('/')
                }
            } else {
                relativeCachePath(src)?.let(::stripIdPrefix)
            }
            if (relPath == null) {
                // Absolute/data/odd URLs stay remote — the page still works online.
                img.attr("src", resolveUrl(bareId, src))
            } else {
                val remote = resolveUrl(bareId, src)
                if (seen.add(relPath)) resources += CachedResource(remote, relPath)
                img.attr("src", virtualBase + relPath)
            }
            img.attr("style", "max-width:100%;height:auto")
        }

        // Stylesheets keep the academic layout. Relative ones AND the arXiv
        // wrapper's own absolute /static/... sheets are cached; third-party
        // hosts (typekit etc.) stay remote: offline the page falls back to
        // unstyled-but-readable text.
        for (link in doc.select("link[rel=stylesheet][href]")) {
            val href = link.attr("href").trim()
            val remote = resolveUrl(bareId, href)
            val cacheable = relativeCachePath(href) != null ||
                remote.startsWith("https://arxiv.org/static/")
            if (!cacheable) continue
            val localPath = "res/" + href.substringBefore('?').substringAfterLast('/')
            if (seen.add(localPath)) resources += CachedResource(remote, localPath)
            link.attr("href", virtualBase + localPath)
        }

        for (a in doc.select("a[href]")) {
            a.attr("href", resolveUrl(bareId, a.attr("href")))
        }

        doc.head().append(INJECTED_HEAD)
        return PreparedHtml(doc.outerHtml(), resources, hash)
    }

    /**
     * Rewrites a failed-download resource back to its remote URL inside
     * [html] (plain string replace: the virtual URL is unambiguous).
     */
    fun restoreRemote(html: String, hash: String, resource: CachedResource): String =
        html.replace(virtualUrlOf(hash, resource), resource.remoteUrl)

    /** The virtual-origin URL a cached resource is served from. */
    fun virtualUrlOf(hash: String, resource: CachedResource): String =
        "https://$VIRTUAL_HOST/html/$hash/${resource.relPath}"

    /** `@import "..."` / `@import url(...)` statements of a stylesheet. */
    private val CSS_IMPORT = Regex(
        """@import\s+(?:"([^"]+)"|'([^']+)'|url\(\s*["']?([^"')]+)["']?\s*\))""",
    )

    /** `url(...)` value references of a stylesheet. */
    private val CSS_URL = Regex("""url\(\s*["']?([^"')]+)["']?\s*\)""")

    /**
     * Rewrites a cached stylesheet so its `@import` / `url()` references
     * load from the virtual origin too, collecting them into [out].
     *
     * The arXiv HTML wrapper sheet is a 183-byte stub of `@import`s
     * (`ar5iv.*.css` carries the table borders, `…-theme-…css` the theme):
     * caching the stub alone makes every import resolve against the
     * `paper.local` cache dir and 404, so the page renders without table
     * lines or theme. arXiv-hosted references are cached flat under `res/`;
     * third-party ones (typekit) stay remote.
     */
    fun rewriteCssReferences(
        css: String,
        cssRemoteUrl: String,
        hash: String,
        out: MutableList<CachedResource>,
    ): String {
        var result = css
        for (m in CSS_IMPORT.findAll(css)) {
            val ref = m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }
            result = rewriteCssRef(result, ref, m.value, cssRemoteUrl, hash, out)
        }
        for (m in CSS_URL.findAll(css)) {
            val ref = m.groupValues[1]
            if (ref.startsWith("data:")) continue
            result = rewriteCssRef(result, ref, m.value, cssRemoteUrl, hash, out)
        }
        return result
    }

    private fun rewriteCssRef(
        css: String,
        ref: String,
        wholeMatch: String,
        cssRemoteUrl: String,
        hash: String,
        out: MutableList<CachedResource>,
    ): String {
        val trimmed = ref.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("data:") || trimmed.startsWith("//")) return css
        val remote = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("/") -> "https://arxiv.org$trimmed"
            else -> cssRemoteUrl.substringBeforeLast('/') + "/" + trimmed.removePrefix("./")
        }
        if (!remote.startsWith("https://arxiv.org/")) return css
        val localName = trimmed.substringBefore('?').substringAfterLast('/')
        if (localName.isEmpty() || localName.contains("..")) return css
        val relPath = "res/$localName"
        out += CachedResource(remote, relPath, fromCss = true)
        // Full virtual URLs (not bare relatives): a failed download can be
        // patched back to remote with the same restoreRemote() as the page.
        val virtual = "https://$VIRTUAL_HOST/html/$hash/$relPath"
        val quoted = if (wholeMatch.startsWith("@import")) "@import \"$virtual\"" else "url(\"$virtual\")"
        return css.replace(wholeMatch, quoted)
    }

    /**
     * Resolves [url] the way a browser resolves it against the paper's HTML
     * page `https://arxiv.org/html/<id>` (NO trailing slash — that is how
     * arXiv serves it): a relative URL replaces the last path segment, so
     * `<id>/x1.png` lands at `/html/<id>/x1.png`. Older ar5iv pages emit
     * bare `x1.png`, which that same rule would drop into `/html/x1.png` —
     * for those the id prefix is missing and the paper directory wins
     * (same M28 gotcha as [dev.handypage.app.arxiv.ArxivHtml]).
     */
    private fun resolveUrl(bareId: String, url: String): String {
        val u = url.trim()
        return when {
            u.isEmpty() || u.startsWith("#") || u.startsWith("javascript:") -> u
            u.startsWith("http://") || u.startsWith("https://") -> u
            u.startsWith("//") -> "https:$u"
            u.startsWith("/") -> "https://arxiv.org$u"
            else -> effectiveBase(bareId, u.removePrefix("./")) + u.removePrefix("./")
        }
    }

    /** Paper directory for [rel]: id-prefixed URLs resolve under `/html/`. */
    private fun effectiveBase(bareId: String, rel: String): String =
        if (rel.startsWith("$bareId/")) "https://arxiv.org/html/"
        else "https://arxiv.org/html/$bareId/"

    /** Cache path strips the self-referencing `<id>/` prefix. */
    private fun stripIdPrefix(rel: String): String {
        val slash = rel.indexOf('/')
        if (slash <= 0) return rel
        val first = rel.substring(0, slash)
        // Only an arXiv id-shaped first segment ("1706.03762", "...v5",
        // or old-style "math/0309136") is the paper's own directory; any
        // other folder (e.g. "extracted/...") keeps its path intact.
        val looksLikeArxivId =
            Regex("^\\d{4}\\.\\d{4,5}(v\\d+)?$").matches(first) ||
                Regex("^[a-z-]+(\\.[A-Z]{2})?$").matches(first) &&
                Regex("^\\d{7}(v\\d+)?$").matches(rel.substring(slash + 1).substringBefore('/'))
        return if (looksLikeArxivId) rel.substring(slash + 1) else rel
    }

    /**
     * Local cache path for a candidate URL, or null when it should stay
     * remote (absolute URLs, data URIs, traversal, empty).
     */
    private fun relativeCachePath(url: String): String? {
        val u = url.trim().removePrefix("./")
        if (u.isEmpty() || u.startsWith("#")) return null
        if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("//")) return null
        if (u.startsWith("data:") || u.startsWith("/")) return null
        if (u.contains("..")) return null
        return u
    }
}

/**
 * Downloads and caches a paper's official HTML version for offline WebView
 * rendering:
 *
 *     filesDir/paperhtml/<urlHash16(absUrl)>/
 *         index.html          (rewritten page, entry for the virtual origin)
 *         <relPath>           (figures etc., same relative layout as ar5iv)
 *         res/<name>.css      (cached stylesheets)
 *
 * Individual resource failures never break the page — the URL is rewritten
 * back to the remote one so it still loads while online.
 */
class PaperHtmlCache(
    private val client: OkHttpClient,
    private val api: ArxivApi,
) {

    companion object {
        /** Same etiquette as ArxivApi: identify the app on every request. */
        private const val USER_AGENT =
            "Handypage/1.0 (arxiv reader; contact: github.com/handypage)"

        /** Virtual-origin entry URL of a cached paper page. */
        fun entryUrl(hash: String): String =
            "https://${PaperHtmlRewriter.VIRTUAL_HOST}/html/$hash/index.html"

        /** The cache dir of a paper (may not exist yet). */
        fun dirFor(filesDir: File, hash: String): File = File(filesDir, "paperhtml/$hash")
    }

    /**
     * Ensures the paper's HTML page is cached, returning its `index.html`.
     * Throws [PaperHtmlUnavailableException] when arXiv has no HTML version.
     * Callers run this on Dispatchers.IO; [onProgress] reports 0..1.
     */
    suspend fun ensureCached(
        filesDir: File,
        entry: ArxivEntry,
        hash: String,
        onProgress: (Float) -> Unit = {},
    ): File {
        val dir = File(filesDir, "paperhtml/$hash")
        val index = File(dir, "index.html")
        if (index.isFile) return index

        val pageHtml = api.fetchHtmlVersion(entry.id)
            ?: throw PaperHtmlUnavailableException(entry.absUrl)
        val prepared = PaperHtmlRewriter.prepare(pageHtml, hash, entry.absUrl)

        dir.mkdirs()
        // Queue, not a fixed list: cached stylesheets reveal their own
        // @import/url() references only once downloaded (the arXiv wrapper
        // sheet is a stub importing the real ar5iv/theme stylesheets).
        val queue = prepared.resources.toMutableList()
        val failed = mutableListOf<CachedResource>()
        var done = 0
        while (done < queue.size) {
            coroutineContext.ensureActive()
            val res = queue[done++]
            if (!downloadResource(dir, res)) {
                failed += res
            } else if (res.relPath.endsWith(".css")) {
                val file = File(dir, res.relPath)
                val extra = mutableListOf<CachedResource>()
                val rewritten =
                    PaperHtmlRewriter.rewriteCssReferences(file.readText(), res.remoteUrl, hash, extra)
                file.writeText(rewritten)
                for (e in extra) {
                    if (queue.none { it.relPath == e.relPath }) queue += e
                }
            }
            onProgress(done.toFloat() / (queue.size + 1))
        }

        var html = prepared.html
        for (res in failed) {
            if (res.fromCss) {
                // Patch inside the cached stylesheets that reference it.
                dir.walkTopDown().filter { it.extension == "css" }.forEach { f ->
                    val text = f.readText()
                    val patched = PaperHtmlRewriter.restoreRemote(text, hash, res)
                    if (patched != text) f.writeText(patched)
                }
            } else {
                html = PaperHtmlRewriter.restoreRemote(html, hash, res)
            }
        }

        // .part + rename: a crash never leaves a truncated index.html
        // masquerading as a complete cache entry.
        val tmp = File(dir, "index.html.part")
        tmp.writeText(html, Charsets.UTF_8)
        if (!tmp.renameTo(index)) {
            tmp.delete()
            throw IOException("failed to move ${tmp.name} into place as ${index.name}")
        }
        onProgress(1f)
        return index
    }

    /** Downloads one resource with one retry; false keeps the remote URL. */
    private fun downloadResource(dir: File, res: CachedResource): Boolean {
        val dest = File(dir, res.relPath)
        if (dest.isFile) return true
        repeat(2) { attempt ->
            try {
                val request = Request.Builder().url(res.remoteUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return false
                    dest.parentFile?.mkdirs()
                    val part = File(dest.parentFile, dest.name + ".part")
                    resp.body.byteStream().use { input ->
                        part.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!part.renameTo(dest)) {
                        part.delete()
                        return false
                    }
                    return true
                }
            } catch (e: IOException) {
                if (attempt == 1) return false
            }
        }
        return false
    }
}
