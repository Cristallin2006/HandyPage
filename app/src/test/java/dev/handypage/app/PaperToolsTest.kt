package dev.handypage.app

import dev.handypage.app.agent.PaperIndex
import dev.handypage.app.agent.PaperOutlineTool
import dev.handypage.app.agent.ReadPaperSectionTool
import dev.handypage.app.agent.SearchInPaperTool
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the M33 paper-memory tools: outline, section drill-down
 * (param validation), and in-paper search, all over a shared fake index,
 * plus the not-ready path when the reflow EPUB does not exist yet.
 */
class PaperToolsTest {

    private val doc = """
        <html><body><article class="ltx_document">
        <div class="ltx_abstract"><p>Abstract about diffusion models.</p></div>
        <section class="ltx_section">
          <h2 class="ltx_title">1 Intro</h2><p>Diffusion models generate images.</p>
        </section>
        </article></body></html>
    """.trimIndent()

    private val index = PaperIndex.fromHtmlDocuments(listOf(doc))
    private val provider: suspend () -> PaperIndex? = { index }
    private val nullProvider: suspend () -> PaperIndex? = { null }

    @Test
    fun `outline tool returns the section map`() = runBlocking {
        val out = PaperOutlineTool(provider).execute(JSONObject())
        assertTrue(out.contains("0. Abstract ("))
        assertTrue(out.contains("1. 1 Intro ("))
    }

    @Test
    fun `read section validates params and returns content`() = runBlocking {
        val tool = ReadPaperSectionTool(provider)
        assertEquals("missing parameter: index", tool.execute(JSONObject()))
        assertTrue(tool.execute(JSONObject().put("index", 1)).contains("generate images"))
        assertTrue(tool.execute(JSONObject().put("index", 0)).contains("diffusion models"))
        assertTrue(tool.execute(JSONObject().put("index", 5)).startsWith("error: no section 5"))
    }

    @Test
    fun `search validates params and returns labelled hits`() = runBlocking {
        val tool = SearchInPaperTool(provider)
        assertEquals("missing parameter: query", tool.execute(JSONObject()))
        val hits = tool.execute(JSONObject().put("query", "diffusion"))
        assertTrue(hits.contains("【Abstract】") || hits.contains("【1. 1 Intro】"))
        assertTrue(hits.contains("Diffusion models generate images."))
    }

    @Test
    fun `null index reports not-ready`() = runBlocking {
        assertEquals("论文内容尚未就绪", PaperOutlineTool(nullProvider).execute(JSONObject()))
        assertEquals(
            "论文内容尚未就绪",
            ReadPaperSectionTool(nullProvider).execute(JSONObject().put("index", 1)),
        )
        assertEquals(
            "论文内容尚未就绪",
            SearchInPaperTool(nullProvider).execute(JSONObject().put("query", "x")),
        )
    }
}
