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
     * URLs absolutised against arxiv.org.
     */
    fun toArticle(html: String, title: String, authors: List<String>, absUrl: String): ArticleContent {
        val doc = Jsoup.parse(html, "https://arxiv.org")
        val root: Element = doc.selectFirst("article.ltx_document")
            ?: doc.selectFirst("main")
            ?: doc.body()
        root.select(DROP_SELECTORS).remove()
        root.select(ARXIV_CHROME_SELECTORS).remove()
        for (img in root.select("img")) {
            if (img.hasAttr("src")) img.attr("src", img.absUrl("src"))
            img.attr("style", "max-width:100%;height:auto")
        }
        for (a in root.select("a[href]")) {
            a.attr("href", a.absUrl("href"))
        }
        val byline = if (authors.isEmpty()) {
            ""
        } else {
            "<p><i>by ${authors.joinToString(", ") { esc(it) }}</i></p>"
        }
        return ArticleContent(title = title, bodyHtml = byline + root.outerHtml(), sourceUrl = absUrl)
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
