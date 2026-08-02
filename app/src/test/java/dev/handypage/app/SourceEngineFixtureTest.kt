package dev.handypage.app

import dev.handypage.app.engine.SourceConfig
import dev.handypage.app.engine.SourceEngine
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Replays the saved fixtures through SourceEngine, mirroring the assertions of
 * tools/converter/replay_fixtures.py (>= 3 index items, >= 300 body chars,
 * clean body). The engine's index URL is swapped to a MockWebServer URL.
 */
class SourceEngineFixtureTest {

    private lateinit var server: MockWebServer
    private lateinit var engine: SourceEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = SourceEngine(OkHttpClient(), enforceDelays = false)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test fun npr() = check("npr")
    @Test fun koreaHerald() = check("korea_herald")
    @Test fun chinaDaily() = check("china_daily")
    @Test fun breakingNewsEnglish() = check("breaking_news_english")
    @Test fun newsInLevels() = check("news_in_levels")
    @Test fun rte() = check("rte")
    @Test fun dailyMirror() = check("daily_mirror")
    @Test fun moscowTimes() = check("moscowtimes_en")
    @Test fun nasa() = check("nasa")
    @Test fun liveScience() = check("livescience")
    @Test fun quantaMagazine() = check("quanta_magazine")
    @Test fun freeNature() = check("freenature")
    @Test fun techCrunch() = check("techcrunch")
    @Test fun proPublica() = check("propublica")
    @Test fun newScientist() = check("new_scientist")
    @Test fun apod() = check("apod")
    @Test fun nautilus() = check("nautilus")
    @Test fun ieeeSpectrum() = check("ieeespectrum")
    @Test fun lightspeed() = check("lightspeed")

    private fun loadConfig(id: String): SourceConfig {
        val f = File("src/main/assets/sources/$id.json")
        assertTrue("missing bundled config ${f.absolutePath}", f.isFile)
        return SourceConfig.fromJson(JSONObject(f.readText()))
    }

    private fun fixture(id: String, baseName: String): File {
        val dir = File("../sources/fixtures/$id")
        val f = dir.listFiles()?.firstOrNull { it.nameWithoutExtension == baseName }
        assertTrue("missing fixture $baseName.* in ${dir.absolutePath} (cwd=${File(".").absolutePath})", f != null)
        return f!!
    }

    private fun check(id: String) {
        val cfg0 = loadConfig(id)
        val indexFile = fixture(id, "index")
        val articleFile = fixture(id, "article")

        // --- index ---
        val indexMime = if (indexFile.extension == "xml") "application/rss+xml" else "text/html"
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "$indexMime; charset=utf-8")
                .body(indexFile.readText())
                .build(),
        )
        val cfg = cfg0.copy(index = cfg0.index.copy(url = server.url("/index").toString()))
        val items = runBlocking { engine.fetchIndex(cfg) }

        assertTrue("$id: expected >= 3 index items, got ${items.size}", items.size >= 3)
        assertTrue("$id: first item title blank", items.first().title.isNotBlank())
        assertTrue(
            "$id: non-absolute urls: ${items.filter { !it.url.startsWith("http") }.take(3)}",
            items.all { it.url.startsWith("http") },
        )
        assertTrue(
            "$id: raw html left in summaries: " +
                items.mapNotNull { it.summary }.firstOrNull { s -> s.contains("<a ") || s.contains("<p>") },
            items.none { s -> s.summary?.let { it.contains("<a ") || it.contains("<p>") } == true },
        )

        // --- article ---
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .body(articleFile.readText())
                .build(),
        )
        val articleUrl = server.url("/article/1").toString()
        val article = runBlocking {
            engine.fetchArticle(cfg, articleUrl, fallbackTitle = items.first().title)
        }

        val bodyText = Jsoup.parse(article.bodyHtml).text()
        assertTrue("$id: blank article title", article.title.isNotBlank())
        assertTrue("$id: body too short (${bodyText.length} chars)", bodyText.length >= 300)
        assertFalse("$id: <script> left in body", article.bodyHtml.contains("<script"))
        assertFalse("$id: relative img src left in body", article.bodyHtml.contains("src=\"/"))
        // Report line for the milestone summary.
        println(
            "FIXTURE-REPORT $id items=${items.size} bodyChars=${bodyText.length} " +
                "firstTitle=${items.first().title.take(50)} articleTitle=${article.title.take(60)}",
        )
    }
}
