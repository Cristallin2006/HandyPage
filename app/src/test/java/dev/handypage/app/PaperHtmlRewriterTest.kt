package dev.handypage.app

import dev.handypage.app.paperhtml.CachedResource
import dev.handypage.app.paperhtml.PaperHtmlRewriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the ar5iv page rewriting that feeds the bilingual WebView:
 * chrome stripping, virtual-origin URL rewriting for cached resources,
 * remote absolutisation for links, and the bilingual asset injection.
 *
 * The sample mixes BOTH page generations arXiv serves:
 *  - the 2025+ wrapper (header/footer banners, `<id>/x3.png` relative
 *    figures resolved against `/html/<id>` without the trailing slash,
 *    absolute `/static/...` stylesheets);
 *  - classic ar5iv bodies (`x1.png` resolved against `/html/<id>/`).
 */
class PaperHtmlRewriterTest {

    private val sample = """
        <html>
        <head>
          <title>Sample Paper</title>
          <link rel="stylesheet" href="/static/browse/0.3.4/css/arxiv-html-papers.css">
          <link rel="stylesheet" href="arxiv.css">
          <script>tracker()</script>
        </head>
        <body>
          <header>
            <div class="ds-announcement">arXiv is now a nonprofit</div>
            <img class="ds-announcement-glyph" src="/static/base/1.0.1/images/smiley.svg">
          </header>
          <div class="ltx_banner">banner</div>
          <article class="ltx_document">
            <p>See <a href="x.html#S2">section 2</a> and <a href="/abs/1234.00001">ref</a>.</p>
            <figure class="ltx_figure"><img src="x1.png" alt="fig"/></figure>
            <figure class="ltx_figure"><img src="1706.03762/x3.png" alt="fig"/></figure>
            <figure class="ltx_figure"><img src="https://arxiv.org/html/1706.03762v5/x2.png"/></figure>
          </article>
          <footer>
            <div class="ds-funder">funders</div>
          </footer>
        </body>
        </html>
    """.trimIndent()

    private val prepared = PaperHtmlRewriter.prepare(
        html = sample,
        hash = "0123456789abcdef",
        absUrl = "https://arxiv.org/abs/1706.03762",
    )

    @Test
    fun `chrome and scripts are stripped`() {
        assertFalse(prepared.html.contains("ltx_page_header"))
        assertFalse(prepared.html.contains("ltx_page_footer"))
        assertFalse(prepared.html.contains("ltx_banner"))
        assertFalse(prepared.html.contains("ds-announcement"))
        assertFalse(prepared.html.contains("nonprofit"))
        assertFalse(prepared.html.contains("<header"))
        assertFalse(prepared.html.contains("<footer"))
        assertFalse(prepared.html.contains("tracker()"))
        // The only remaining script tag is the injected bilingual.js.
        val scripts = Regex("<script[^>]*>").findAll(prepared.html).toList()
        assertEquals(1, scripts.size)
        assertTrue(scripts.single().value.contains("bilingual.js"))
    }

    @Test
    fun `classic ar5iv figures resolve under the paper directory`() {
        val x1 = prepared.resources.first { it.relPath == "x1.png" }
        assertEquals("https://arxiv.org/html/1706.03762/x1.png", x1.remoteUrl)
        assertTrue(
            prepared.html.contains(
                "src=\"https://paper.local/html/0123456789abcdef/x1.png\"",
            ),
        )
    }

    @Test
    fun `new style id prefixed figures resolve to a single id segment`() {
        // Page URL is /html/1706.03762 (no trailing slash): the browser drops
        // the last segment, so "1706.03762/x3.png" must NOT double the id.
        val x3 = prepared.resources.first { it.relPath == "x3.png" }
        assertEquals("https://arxiv.org/html/1706.03762/x3.png", x3.remoteUrl)
        assertTrue(
            prepared.html.contains(
                "src=\"https://paper.local/html/0123456789abcdef/x3.png\"",
            ),
        )
    }

    @Test
    fun `absolute figure urls are resolved and cached under their file name`() {
        val x2 = prepared.resources.first { it.relPath.endsWith("x2.png") }
        assertEquals("https://arxiv.org/html/1706.03762v5/x2.png", x2.remoteUrl)
    }

    @Test
    fun `stylesheets are cached under res - relative and arxiv static`() {
        val css = prepared.resources.first { it.relPath == "res/arxiv.css" }
        assertEquals("https://arxiv.org/html/1706.03762/arxiv.css", css.remoteUrl)
        val static = prepared.resources.first { it.relPath == "res/arxiv-html-papers.css" }
        assertEquals(
            "https://arxiv.org/static/browse/0.3.4/css/arxiv-html-papers.css",
            static.remoteUrl,
        )
        assertTrue(
            prepared.html.contains(
                "href=\"https://paper.local/html/0123456789abcdef/res/arxiv.css\"",
            ),
        )
        assertTrue(
            prepared.html.contains(
                "href=\"https://paper.local/html/0123456789abcdef/res/arxiv-html-papers.css\"",
            ),
        )
    }

    @Test
    fun `anchors are absolutised but never cached`() {
        assertTrue(prepared.html.contains("href=\"https://arxiv.org/html/1706.03762/x.html#S2\""))
        assertTrue(prepared.html.contains("href=\"https://arxiv.org/abs/1234.00001\""))
    }

    @Test
    fun `bilingual assets and viewport are injected into head`() {
        assertTrue(prepared.html.contains("paper.local/assets/paper/bilingual.css"))
        assertTrue(prepared.html.contains("paper.local/assets/paper/bilingual.js"))
        assertTrue(prepared.html.contains("name=\"viewport\""))
    }

    @Test
    fun `restoreRemote swaps a failed resource back to its remote url`() {
        val failed = CachedResource(
            remoteUrl = "https://arxiv.org/html/1706.03762/x1.png",
            relPath = "x1.png",
        )
        val restored = PaperHtmlRewriter.restoreRemote(prepared.html, "0123456789abcdef", failed)
        assertTrue(restored.contains("src=\"https://arxiv.org/html/1706.03762/x1.png\""))
        assertFalse(restored.contains("paper.local/html/0123456789abcdef/x1.png"))
    }

    @Test
    fun `stylesheet imports and url refs are rewritten and collected`() {
        // The real arXiv wrapper sheet is exactly such an @import stub;
        // without rewriting it the imports resolve against the paper.local
        // cache dir and 404, killing table borders and the theme.
        val stub = "@import \"/static/browse/0.3.4/css/ar5iv.0.8.5.css\" layer(ar5iv);\n" +
            "@import \"https://use.typekit.net/xyz.css\";\n" +
            ".a { background: url('/static/base/1.0.1/images/bg.png'); }"
        val out = mutableListOf<CachedResource>()
        val rewritten = PaperHtmlRewriter.rewriteCssReferences(
            css = stub,
            cssRemoteUrl = "https://arxiv.org/static/browse/0.3.4/css/wrapper.css",
            hash = "0123456789abcdef",
            out = out,
        )
        assertTrue(
            rewritten.contains(
                "@import \"https://paper.local/html/0123456789abcdef/res/ar5iv.0.8.5.css\" layer(ar5iv);",
            ),
        )
        assertTrue(rewritten.contains("url(\"https://paper.local/html/0123456789abcdef/res/bg.png\")"))
        // Third-party stays untouched.
        assertTrue(rewritten.contains("https://use.typekit.net/xyz.css"))
        assertEquals(2, out.size)
        assertTrue(out.all { it.fromCss })
        assertEquals("https://arxiv.org/static/browse/0.3.4/css/ar5iv.0.8.5.css", out[0].remoteUrl)
        assertEquals("res/ar5iv.0.8.5.css", out[0].relPath)
        assertEquals("https://arxiv.org/static/base/1.0.1/images/bg.png", out[1].remoteUrl)
    }
}
