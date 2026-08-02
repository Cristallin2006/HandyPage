package dev.handypage.app

import dev.handypage.app.pdf.PdfLayoutParser
import dev.handypage.app.pdf.RawWord
import dev.handypage.app.pdf.TextLine
import dev.handypage.app.pdf.XYCut
import dev.handypage.app.pdf.buildLinesFromWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layout parsing from hand-built TextLine geometry (letter page: 612x792).
 * PDFBox stays out of JVM unit tests — the PDFBox-facing half of PdfChars is
 * exercised on-device only; the word->line assembly ([buildLinesFromWords])
 * is android-free and covered here directly.
 */
class PdfLayoutParserTest {

    private fun line(
        page: Int = 1,
        x0: Float = LEFT_X0,
        y0: Float,
        x1: Float = LEFT_X1,
        text: String,
        fontSize: Float = 10f,
        bold: Boolean = false,
        height: Float = 12f,
    ) = TextLine(page, x0, y0, x1, y0 + height, text, fontSize, bold)

    private fun word(
        x: Float,
        y: Float,
        w: Float,
        text: String,
        fontSize: Float = 10f,
        bold: Boolean = false,
        h: Float = 12f,
    ) = RawWord(x, y, w, h, fontSize, bold, text)

    // ---- word -> line assembly (PdfChars' android-free half) ----

    @Test
    fun `words on one baseline join with single spaces`() {
        val lines = buildLinesFromWords(
            listOf(
                word(x = 50f, y = 100f, w = 30f, text = "open-ended"),
                word(x = 83f, y = 100f, w = 45f, text = "exploration."),
            ),
            page = 1,
        )
        assertEquals(1, lines.size)
        assertEquals("open-ended exploration.", lines[0].text)
    }

    @Test
    fun `large word gap splits line at column gutter`() {
        val lines = buildLinesFromWords(
            listOf(
                word(x = 50f, y = 100f, w = 60f, text = "left"),
                word(x = 320f, y = 100f, w = 60f, text = "right"),
            ),
            page = 1,
        )
        assertEquals(listOf("left", "right"), lines.map { it.text })
    }

    @Test
    fun `word whitespace runs fold to single space`() {
        val lines = buildLinesFromWords(
            listOf(
                word(x = 50f, y = 100f, w = 40f, text = "code  is"),
                word(x = 95f, y = 100f, w = 60f, text = "available   at"),
            ),
            page = 1,
        )
        assertEquals(1, lines.size)
        assertEquals("code is available at", lines[0].text)
    }

    // ---- reading order (XYCut) ----

    @Test
    fun `two-column page orders left column before right column`() {
        val left = listOf(
            line(y0 = 100f, text = "L1"),
            line(y0 = 116f, text = "L2"),
            line(y0 = 132f, text = "L3"),
        )
        val right = listOf(
            line(y0 = 100f, x0 = RIGHT_X0, x1 = RIGHT_X1, text = "R1"),
            line(y0 = 116f, x0 = RIGHT_X0, x1 = RIGHT_X1, text = "R2"),
            line(y0 = 132f, x0 = RIGHT_X0, x1 = RIGHT_X1, text = "R3"),
        )
        val interleaved = listOf(left[0], right[0], left[1], right[1], left[2], right[2])
        val ordered = XYCut.order(interleaved, PAGE_WIDTH)
        assertEquals(listOf("L1", "L2", "L3", "R1", "R2", "R3"), ordered.map { it.text })
    }

    @Test
    fun `full-width large title comes first as h2`() {
        val lines = listOf(
            line(y0 = 80f, x1 = 560f, text = "Attention Is All You Need", fontSize = 17f, height = 20f),
            line(y0 = 120f, x1 = 270f, text = "The dominant sequence"),
            line(y0 = 136f, x1 = 268f, text = "transduction models are"),
            line(y0 = 120f, x0 = RIGHT_X0, x1 = 545f, text = "based on complex recurrent"),
            line(y0 = 136f, x0 = RIGHT_X0, x1 = 548f, text = "or convolutional networks"),
        )
        val html = PdfLayoutParser.toHtml(lines, PAGE_WIDTH, PAGE_HEIGHT)
        assertTrue(html, html.startsWith("<h2>Attention Is All You Need</h2>"))
        assertTrue(
            html,
            html.indexOf("The dominant sequence") < html.indexOf("based on complex recurrent"),
        )
    }

    // ---- first-page abstract skip and orphan marks ----

    @Test
    fun `page one skips title author block before abstract`() {
        val lines = listOf(
            line(y0 = 80f, x1 = 560f, text = "Some Paper Title", fontSize = 17f, height = 20f),
            line(y0 = 120f, x1 = 400f, text = "Jane Doe, John Smith"),
            line(y0 = 140f, x1 = 380f, text = "University of Somewhere"),
            line(y0 = 180f, x1 = 120f, text = "Abstract"),
            line(y0 = 200f, x1 = 270f, text = "We present a method for testing."),
            line(y0 = 216f, x1 = 268f, text = "It works on real papers."),
        )
        val html = PdfLayoutParser.toHtml(lines, PAGE_WIDTH, PAGE_HEIGHT)
        assertTrue(html, html.startsWith("<h3>Abstract</h3>"))
        assertFalse(html, html.contains("Some Paper Title"))
        assertFalse(html, html.contains("Jane Doe"))
        assertFalse(html, html.contains("University of Somewhere"))
        assertTrue(html, html.contains("We present a method for testing."))
    }

    @Test
    fun `page one without abstract keeps everything`() {
        val lines = listOf(
            line(y0 = 80f, x1 = 560f, text = "Some Paper Title", fontSize = 17f, height = 20f),
            line(y0 = 120f, x1 = 400f, text = "Jane Doe, John Smith"),
            line(y0 = 180f, x1 = 270f, text = "We start directly with the introduction text."),
        )
        val html = PdfLayoutParser.toHtml(lines, PAGE_WIDTH, PAGE_HEIGHT)
        assertTrue(html, html.contains("<h2>Some Paper Title</h2>"))
        assertTrue(html, html.contains("Jane Doe, John Smith"))
        assertTrue(html, html.contains("We start directly with the introduction text."))
    }

    @Test
    fun `orphan footnote marks are dropped but digits in prose survive`() {
        val lines = listOf(
            line(y0 = 100f, x1 = 270f, text = "In 2024, models improved a lot."),
            line(y0 = 120f, x1 = 60f, text = "2"),
            line(y0 = 140f, x1 = 270f, text = "Further results follow here."),
            line(y0 = 160f, x1 = 60f, text = "†"),
        )
        val html = PdfLayoutParser.toHtml(lines, PAGE_WIDTH, PAGE_HEIGHT)
        assertTrue(html, html.contains("In 2024, models improved a lot."))
        assertTrue(html, html.contains("Further results follow here."))
        assertFalse(html, html.contains("<p>2</p>"))
        assertFalse(html, html.contains("†"))
    }

    // ---- paragraphs and headings ----

    @Test
    fun `hyphenated line wrap joins without space`() {
        val lines = listOf(
            line(y0 = 100f, x1 = 270f, text = "transfor-"),
            line(y0 = 116f, x1 = 260f, text = "mer architectures"),
        )
        val html = PdfLayoutParser.toHtml(lines, PAGE_WIDTH, PAGE_HEIGHT)
        assertTrue(html, html.contains("<p>transformer architectures</p>"))
    }

    @Test
    fun `repeating header and page numbers are dropped`() {
        val lines = mutableListOf<TextLine>()
        for (page in 1..3) {
            lines += line(page = page, y0 = 30f, x1 = 400f, text = "Journal of Testing 12 (2026)")
            lines += line(page = page, y0 = 100f, x1 = 270f, text = "Body text page $page first line")
            lines += line(page = page, y0 = 116f, x1 = 268f, text = "second line of the body")
            lines += line(page = page, y0 = 760f, x1 = 70f, text = "$page")
        }
        val html = PdfLayoutParser.toHtml(lines, PAGE_WIDTH, PAGE_HEIGHT)
        assertFalse(html, html.contains("Journal of Testing"))
        assertFalse(html, html.contains("<p>1</p>"))
        assertTrue(html, html.contains("Body text page 1 first line"))
        assertTrue(html, html.contains("Body text page 3 first line"))
    }

    @Test
    fun `slightly larger line becomes h3`() {
        // 1.3x body: above the (raised) 1.25x h3 threshold. Was 1.2x while the
        // threshold was 1.15x; see the 1.2x/1.3x boundary test below.
        val lines = listOf(
            line(y0 = 100f, x1 = 270f, text = "Some body text that introduces the section"),
            line(y0 = 130f, x1 = 200f, text = "Methods overview", fontSize = 13f),
            line(y0 = 150f, x1 = 270f, text = "More body text follows here"),
        )
        val html = PdfLayoutParser.toHtml(lines, PAGE_WIDTH, PAGE_HEIGHT)
        assertTrue(html, html.contains("<h3>Methods overview</h3>"))
    }

    @Test
    fun `bold short line at 1_2x body stays body but 1_3x becomes h3`() {
        // Simulates bold in-figure labels (~1.2x body) that must NOT become headings.
        val lines = listOf(
            line(y0 = 100f, x1 = 270f, text = "Body text establishes the mode font size."),
            line(y0 = 120f, x1 = 200f, text = "Environment-bound", fontSize = 12f, bold = true),
            line(y0 = 140f, x1 = 270f, text = "More body text to keep the mode at ten points."),
            line(y0 = 170f, x1 = 210f, text = "Real Section", fontSize = 13f, bold = true),
            line(y0 = 190f, x1 = 270f, text = "Final body text paragraph line here."),
        )
        val html = PdfLayoutParser.toHtml(lines, PAGE_WIDTH, PAGE_HEIGHT)
        assertFalse(html, html.contains("<h3>Environment-bound</h3>"))
        assertTrue(html, html.contains("Environment-bound"))
        assertTrue(html, html.contains("<h3>Real Section</h3>"))
    }

    @Test
    fun `adjacent full-width lines merge into one paragraph`() {
        val lines = listOf(
            line(y0 = 100f, x1 = 270f, text = "First line of the paragraph."),
            line(y0 = 116f, x1 = 268f, text = "second line continues."),
        )
        val html = PdfLayoutParser.toHtml(lines, PAGE_WIDTH, PAGE_HEIGHT)
        assertEquals("<p>First line of the paragraph. second line continues.</p>", html)
    }

    private companion object {
        const val PAGE_WIDTH = 612f
        const val PAGE_HEIGHT = 792f
        const val LEFT_X0 = 50f
        const val LEFT_X1 = 280f
        const val RIGHT_X0 = 320f
        const val RIGHT_X1 = 550f
    }
}
