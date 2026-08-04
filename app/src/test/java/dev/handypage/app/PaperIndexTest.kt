package dev.handypage.app

import dev.handypage.app.agent.PaperIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the M33 [PaperIndex]: ar5iv structure extraction (abstract,
 * top-level sections, subsection folding, bibliography), the structureless
 * fallback chunking, section windowing with offset continuation, and
 * paragraph-level search.
 */
class PaperIndexTest {

    private val ar5ivDoc = """
        <html><body>
        <article class="ltx_document">
        <div class="ltx_abstract">
          <h6 class="ltx_title">Abstract</h6>
          <p>We present a Transformer model for sequence transduction.</p>
        </div>
        <section class="ltx_section">
          <h2 class="ltx_title">1 Introduction</h2>
          <p>Sequence modeling has a long history.</p>
          <section class="ltx_subsection">
            <h3 class="ltx_title">1.1 Background</h3>
            <p>Recurrent models dominated the field.</p>
          </section>
        </section>
        <section class="ltx_section">
          <h2 class="ltx_title">2 Methods</h2>
          <p>We use multi-head attention with scaled dot-product.</p>
        </section>
        <div class="ltx_bibliography">
          <p>[1] Vaswani et al. Attention is all you need.</p>
        </div>
        </article>
        </body></html>
    """.trimIndent()

    private fun ar5ivIndex() = PaperIndex.fromHtmlDocuments(listOf(ar5ivDoc))

    @Test
    fun `ar5iv structure yields abstract sections and references`() {
        val index = ar5ivIndex()
        assertFalse(index.isEmpty)
        assertTrue(index.abstractText.contains("Transformer model"))
        assertEquals(3, index.sections.size)
        assertEquals("1 Introduction", index.sections[0].heading)
        assertEquals("2 Methods", index.sections[1].heading)
        assertEquals("References", index.sections[2].heading)
    }

    @Test
    fun `subsection text folds into its parent section`() {
        val index = ar5ivIndex()
        assertTrue(index.sections[0].text.contains("Recurrent models dominated"))
        // No separate subsection entry in the outline.
        assertFalse(index.outline().contains("1.1 Background ("))
    }

    @Test
    fun `outline numbers the abstract as 0 and sections from 1`() {
        val outline = ar5ivIndex().outline()
        assertTrue(outline.contains("0. Abstract ("))
        assertTrue(outline.contains("1. 1 Introduction ("))
        assertTrue(outline.contains("2. 2 Methods ("))
        assertTrue(outline.contains("3. References ("))
    }

    @Test
    fun `readSection reads the abstract and errors on bad indices`() {
        val index = ar5ivIndex()
        assertTrue(index.readSection(0).contains("Transformer model"))
        assertTrue(index.readSection(1).contains("long history"))
        assertTrue(index.readSection(9).startsWith("error: no section 9"))
        assertTrue(index.readSection(1, offset = 999999).startsWith("error: offset"))
    }

    @Test
    fun `long sections window with a continuation hint`() {
        val longPara = "attention ".repeat(600).trim() // ~6 000 chars
        val doc = """
            <html><body><article class="ltx_document">
            <section class="ltx_section"><h2 class="ltx_title">1 Big</h2><p>$longPara</p></section>
            </article></body></html>
        """.trimIndent()
        val index = PaperIndex.fromHtmlDocuments(listOf(doc))
        val first = index.readSection(1)
        assertEquals(PaperIndex.READ_WINDOW_CHARS, first.indexOf("\n…["))
        assertTrue(first.contains("offset="))
        val nextOffset = first.substringAfter("offset=").substringBefore(" ").toInt()
        val second = index.readSection(1, offset = nextOffset)
        assertFalse(second.contains("…["))
        assertTrue(second.endsWith("attention"))
    }

    @Test
    fun `search finds paragraphs with section labels`() {
        val index = ar5ivIndex()
        val hits = index.search("ATTENTION")
        assertTrue(hits.contains("【2. 2 Methods】"))
        assertTrue(hits.contains("scaled dot-product"))
        assertEquals("未找到包含 \"zzzzz\" 的段落", index.search("zzzzz"))
    }

    @Test
    fun `structureless html falls back to chunked parts`() {
        val para = "plain text paragraph. ".repeat(40) // ~880 chars each
        val doc = buildString {
            append("<html><body>")
            repeat(12) { append("<p>$para</p>") }
            append("</body></html>")
        }
        val index = PaperIndex.fromHtmlDocuments(listOf(doc))
        assertTrue(index.abstractText.isEmpty())
        assertTrue(index.sections.size >= 2)
        assertEquals("Part 1", index.sections[0].heading)
        assertTrue(index.sections[0].text.length <= PaperIndex.FALLBACK_CHUNK_CHARS + para.length)
        // Whole content survives across parts.
        val total = index.sections.sumOf { it.text.length }
        assertTrue(total > 12 * 800)
    }

    @Test
    fun `empty input yields an empty index`() {
        assertTrue(PaperIndex.fromHtmlDocuments(listOf("<html><body></body></html>")).isEmpty)
    }
}
