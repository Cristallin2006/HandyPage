package dev.handypage.app

import dev.handypage.app.arxiv.ArxivHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for ar5iv HTML -> ArticleContent conversion (M12-B): the paper is
 * taken from `article.ltx_document`, page chrome and scripts are stripped,
 * relative img/a URLs are absolutised against arxiv.org, MathML and figures
 * survive, and the byline (same format as PdfToArticle) leads the body.
 */
class ArxivHtmlTest {

    private val sample = """
        <html>
        <head>
          <title>Sample Paper</title>
          <script>headTracker()</script>
        </head>
        <body>
          <header class="ltx_page_header">arXiv banner</header>
          <article class="ltx_document">
            <script>evil()</script>
            <header class="ltx_page_header">inner banner</header>
            <h2>1 Introduction</h2>
            <p>See <a href="/abs/1234.00001">related work</a>.</p>
            <figure class="ltx_figure">
              <img src="x.png" alt="architecture"/>
              <figcaption>Figure 1: overview.</figcaption>
            </figure>
            <math xmlns="http://www.w3.org/1998/Math/MathML">
              <mi>x</mi><mo>=</mo><mn>1</mn>
            </math>
          </article>
          <footer class="ltx_page_footer">page footer</footer>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun `strips chrome, absolutises urls, keeps mathml, byline first`() {
        val article = ArxivHtml.toArticle(
            html = sample,
            title = "Sample Paper",
            authors = listOf("Alice & Bob", "Carol <C>"),
            absUrl = "https://arxiv.org/abs/1706.03762v5",
        )
        val body = article.bodyHtml

        assertTrue(
            "byline must lead the body",
            body.startsWith("<p><i>by Alice &amp; Bob, Carol &lt;C&gt;</i></p>"),
        )
        assertFalse("script removed", body.contains("<script"))
        assertFalse("script text removed", body.contains("evil()"))
        assertFalse("ltx_page_header removed", body.contains("ltx_page_header"))
        assertFalse("inner banner text removed", body.contains("inner banner"))
        assertFalse("footer outside root never included", body.contains("page footer"))
        assertTrue(
            "img src absolutised against the /html/<id>/ page dir (M28)",
            body.contains("""src="https://arxiv.org/html/1706.03762v5/x.png""""),
        )
        assertTrue("img style added", body.contains("max-width:100%"))
        assertTrue(
            "a href absolutised",
            body.contains("""href="https://arxiv.org/abs/1234.00001""""),
        )
        assertTrue("MathML kept", body.contains("<math"))
        assertTrue("figure kept", body.contains("<figure"))
        assertTrue("figcaption kept", body.contains("Figure 1: overview."))
        assertEquals("Sample Paper", article.title)
        assertEquals("https://arxiv.org/abs/1706.03762v5", article.sourceUrl)
    }
}
