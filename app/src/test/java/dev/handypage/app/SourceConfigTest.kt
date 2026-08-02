package dev.handypage.app

import dev.handypage.app.engine.IndexCfg
import dev.handypage.app.engine.SourceConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * All five bundled source.json configs must parse through SourceConfig.fromJson.
 * Reads from the copies bundled into app/src/main/assets/sources/ (unit-test
 * working dir is the app/ module dir).
 */
class SourceConfigTest {

    private val sourceIds = listOf(
        "npr", "korea_herald", "china_daily", "breaking_news_english", "news_in_levels",
    )

    private fun load(id: String): SourceConfig {
        val f = File("src/main/assets/sources/$id.json")
        assertTrue("missing bundled config ${f.absolutePath} (cwd=${File(".").absolutePath})", f.isFile)
        return SourceConfig.fromJson(JSONObject(f.readText()))
    }

    @Test
    fun allFiveBundledConfigsParse() {
        for (id in sourceIds) {
            val cfg = load(id)
            assertEquals(id, cfg.id)
            assertTrue(cfg.name.isNotBlank())
            assertTrue(cfg.index.url.startsWith("http"))
            assertTrue(cfg.article.content.isNotBlank())
            assertTrue(cfg.index.max > 0)
        }
    }

    @Test
    fun nprIndexIsRss() {
        val cfg = load("npr")
        assertEquals(IndexCfg.Type.RSS, cfg.index.type)
        assertEquals(1.0, cfg.delaySeconds, 0.0)
        assertNull(cfg.userAgent) // JSON null must not leak through as "null"/""
        assertEquals(listOf(".bucketwrap", ".enlarge_measure", ".enlarge_html", "aside", ".ad-wrap"), cfg.article.remove)
    }

    @Test
    fun chinaDailyIsHtmlWithLinkRegex() {
        val cfg = load("china_daily")
        assertEquals(IndexCfg.Type.HTML, cfg.index.type)
        assertEquals("a[href*='/a/']", cfg.index.linkCss)
        assertTrue(cfg.index.linkRegex!!.contains("/a/\\d{6}/"))
        assertTrue(cfg.article.remove.isEmpty())
    }

    @Test
    fun newsInLevelsRemovesFirstParagraph() {
        val cfg = load("news_in_levels")
        assertTrue(cfg.article.remove.contains("p:first-of-type"))
        assertEquals(".article-title", cfg.article.title)
    }

    @Test
    fun breakingNewsEnglishHasTwoSecondDelay() {
        val cfg = load("breaking_news_english")
        assertEquals(2.0, cfg.delaySeconds, 0.0)
        assertFalse(cfg.needsProxy)
        assertEquals("div.lesson-excerpt", cfg.article.content)
    }
}
