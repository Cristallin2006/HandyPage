package dev.handypage.app.arxiv

import dev.handypage.app.engine.ArticleContent
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * M12-B: converts arXiv's official HTML version (ar5iv, served at
 * https://arxiv.org/html/<id>) into [ArticleContent].
 *
 * Unlike PDF text extraction ([dev.handypage.app.pdf.PdfToArticle]) the HTML
 * version keeps figures, tables and MathML (`<math>`) — modern WebViews
 * render MathML and it is valid EPUB 3 — so both the reflow EPUB and the AI
 * context get the full paper. Android-free (Jsoup only), so it runs under
 * plain JVM tests.
 */
object ArxivHtml {

    /** Elements that never carry paper content (page chrome, interactive bits). */
    private const val DROP_SELECTORS =
        "script, style, noscript, header, footer, nav, form, button, iframe"

    /** arXiv page wrapper around the ar5iv paper (banners, logos, toolbars). */
    private const val ARXIV_CHROME_SELECTORS =
        ".ltx_page_header, .ltx_page_footer, .ltx_page_logo"

    /**
     * [html] is the full ar5iv page; [absUrl] is the paper's abstract page,
     * kept as [ArticleContent.sourceUrl]. The paper body is taken from the
     * ar5iv main container `article.ltx_document` (falling back to `main`,
     * then the whole body), cleaned of page chrome, with relative img/a
     * URLs absolutised against the HTML page directory.
     *
     * M28: URLs are resolved MANUALLY against `https://arxiv.org/html/<id>/`
     * — jsoup honours the ar5iv `<base>` tag, which points at
     * `arxiv.org/<id>/` and 404s every figure (curl-verified: only the
     * `/html/<id>/` prefix serves them).
     */
    fun toArticle(html: String, title: String, authors: List<String>, absUrl: String): ArticleContent {
        val doc = Jsoup.parse(html, "https://arxiv.org")
        val htmlBase = "https://arxiv.org/html/" +
            absUrl.trimEnd('/').substringAfterLast('/') + "/"
        val root: Element = doc.selectFirst("article.ltx_document")
            ?: doc.selectFirst("main")
            ?: doc.body()
        root.select(DROP_SELECTORS).remove()
        root.select(ARXIV_CHROME_SELECTORS).remove()
        for (img in root.select("img")) {
            if (img.hasAttr("src")) img.attr("src", resolveArxivUrl(htmlBase, img.attr("src")))
            img.attr("style", "max-width:100%;height:auto")
        }
        for (a in root.select("a[href]")) {
            a.attr("href", resolveArxivUrl(htmlBase, a.attr("href")))
        }
        val byline = if (authors.isEmpty()) {
            ""
        } else {
            "<p><i>by ${authors.joinToString(", ") { esc(it) }}</i></p>"
        }
        return ArticleContent(title = title, bodyHtml = byline + root.outerHtml(), sourceUrl = absUrl)
    }

    /**
     * Resolves [url] against the paper's HTML page directory, bypassing
     * jsoup's base machinery (the ar5iv `<base>` tag is wrong for figures).
     */
    private fun resolveArxivUrl(htmlBase: String, url: String): String {
        val u = url.trim()
        return when {
            u.isEmpty() || u.startsWith("#") -> u
            u.startsWith("http://") || u.startsWith("https://") -> u
            u.startsWith("//") -> "https:$u"
            u.startsWith("/") -> "https://arxiv.org$u"
            else -> htmlBase + u
        }
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(c)
            }
        }
    }
}
