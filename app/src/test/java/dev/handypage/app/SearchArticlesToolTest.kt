package dev.handypage.app

import dev.handypage.app.agent.AgentPreferences
import dev.handypage.app.agent.PreferencesProvider
import dev.handypage.app.agent.SearchArticlesTool
import dev.handypage.app.engine.IndexCfg
import dev.handypage.app.engine.SourceConfig
import dev.handypage.app.engine.SourceEngine
import dev.handypage.app.engine.ArticleCfg
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for [SearchArticlesTool]: verifies multi-source index fetching,
 * category filtering, query filtering, error resilience (single-source
 * failure skipped), and the all-fail error path.
 */
class SearchArticlesToolTest {

    private lateinit var server: MockWebServer
    private lateinit var engine: SourceEngine
    private lateinit var prefsStore: FakePreferences
    private lateinit var tool: SearchArticlesTool

    private val rssFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Test Source</title>
            <item>
              <title>Climate Change Report</title>
              <link>http://example.com/climate</link>
              <description>New climate findings.</description>
              <pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate>
            </item>
            <item>
              <title>AI Breakthrough</title>
              <link>http://example.com/ai</link>
              <description>Latest AI news.</description>
              <pubDate>Tue, 02 Jan 2024 00:00:00 GMT</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private fun makeSource(id: String, name: String, category: String, url: String) = SourceConfig(
        id = id,
        name = name,
        language = "en",
        version = 1,
        homepage = url,
        needsProxy = false,
        category = category,
        index = IndexCfg(type = IndexCfg.Type.RSS, url = url, max = 20),
        article = ArticleCfg(content = "article"),
        delaySeconds = 0.0,
        userAgent = null,
        readerCss = null,
    )

    private fun enqueueRss() {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/rss+xml; charset=utf-8")
                .body(rssFeed)
                .build(),
        )
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = SourceEngine(OkHttpClient(), enforceDelays = false)
        prefsStore = FakePreferences()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `fetches articles from multiple sources`() = runBlocking {
        enqueueRss()
        enqueueRss()
        val baseUrl = server.url("/rss").toString()
        val sources = listOf(
            makeSource("src1", "Source One", "news", baseUrl),
            makeSource("src2", "Source Two", "science", baseUrl),
        )
        tool = SearchArticlesTool(engine, { sources }, prefsStore)
        val result = tool.execute(JSONObject())
        assertTrue(result.contains("Climate Change Report"))
        assertTrue(result.contains("AI Breakthrough"))
    }

    @Test
    fun `category filter narrows sources`() = runBlocking {
        enqueueRss()
        val baseUrl = server.url("/rss").toString()
        val sources = listOf(
            makeSource("src1", "Source One", "news", baseUrl),
            makeSource("src2", "Source Two", "science", baseUrl),
        )
        tool = SearchArticlesTool(engine, { sources }, prefsStore)
        val result = tool.execute(JSONObject().put("category", "science"))
        assertTrue(result.contains("Climate Change Report"))
        assertTrue(server.requestCount == 1)
    }

    @Test
    fun `query filter matches title`() = runBlocking {
        enqueueRss()
        val baseUrl = server.url("/rss").toString()
        tool = SearchArticlesTool(engine, { listOf(makeSource("s", "S", "news", baseUrl)) }, prefsStore)
        val result = tool.execute(JSONObject().put("query", "climate"))
        assertTrue(result.contains("Climate Change Report"))
        assertTrue(!result.contains("AI Breakthrough"))
    }

    @Test
    fun `single source failure is skipped`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("error").build())
        enqueueRss()
        val baseUrl = server.url("/rss").toString()
        val sources = listOf(
            makeSource("bad", "Bad Source", "news", baseUrl),
            makeSource("good", "Good Source", "news", baseUrl),
        )
        tool = SearchArticlesTool(engine, { sources }, prefsStore)
        val result = tool.execute(JSONObject())
        assertTrue(result.contains("Climate Change Report"))
        assertTrue(result.contains("1"))
    }

    @Test
    fun `all sources fail returns error`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("error").build())
        val baseUrl = server.url("/rss").toString()
        tool = SearchArticlesTool(engine, { listOf(makeSource("s", "S", "news", baseUrl)) }, prefsStore)
        val result = tool.execute(JSONObject())
        assertTrue(result.contains("所有阅读源均抓取失败"))
    }

    @Test
    fun `arxiv sources are excluded`() = runBlocking {
        val arxivSource = SourceConfig(
            id = "arxiv",
            name = "arXiv",
            language = "en",
            version = 1,
            homepage = "https://arxiv.org",
            needsProxy = false,
            category = "papers",
            index = IndexCfg(type = IndexCfg.Type.ARXIV, url = "https://arxiv.org", max = 20),
            article = ArticleCfg(content = "article"),
            delaySeconds = 0.0,
            userAgent = null,
            readerCss = null,
        )
        tool = SearchArticlesTool(engine, { listOf(arxivSource) }, prefsStore)
        val result = tool.execute(JSONObject())
        assertTrue(result.contains("没有匹配分类"))
    }

    private class FakePreferences : PreferencesProvider {
        var prefs = AgentPreferences()
        override fun load(): AgentPreferences = prefs
        override fun save(preferences: AgentPreferences) { prefs = preferences }
        override fun clear() { prefs = AgentPreferences() }
    }
}