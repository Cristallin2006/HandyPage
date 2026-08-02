package dev.handypage.app

import dev.handypage.app.agent.AgentPreferences
import dev.handypage.app.agent.PreferencesProvider
import dev.handypage.app.agent.SearchPapersTool
import dev.handypage.app.arxiv.ArxivApi
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
 * JVM tests for [SearchPapersTool]: verifies query/category dispatch to the
 * arXiv API (via MockWebServer Atom replay), the preference fallback when
 * no explicit query is given, and error handling.
 */
class SearchPapersToolTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ArxivApi
    private lateinit var prefsStore: FakePreferences
    private lateinit var tool: SearchPapersTool

    private val atomFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <id>http://arxiv.org/abs/2401.00001v1</id>
            <published>2024-01-01T00:00:00Z</published>
            <title>Deep Learning for NLP</title>
            <summary>A survey of deep learning methods.</summary>
            <author><name>Alice Smith</name></author>
            <link href="http://arxiv.org/abs/2401.00001v1" rel="alternate"/>
            <link title="pdf" href="http://arxiv.org/pdf/2401.00001v1" rel="related"/>
            <category term="cs.CL"/>
          </entry>
          <entry>
            <id>http://arxiv.org/abs/2401.00002v1</id>
            <published>2024-01-02T00:00:00Z</published>
            <title>Reinforcement Learning Advances</title>
            <summary>Recent advances in RL.</summary>
            <author><name>Bob Jones</name></author>
            <link href="http://arxiv.org/abs/2401.00002v1" rel="alternate"/>
            <category term="cs.LG"/>
          </entry>
        </feed>
    """.trimIndent()

    private fun enqueueFeed() {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/atom+xml; charset=utf-8")
                .body(atomFeed)
                .build(),
        )
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ArxivApi(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
        prefsStore = FakePreferences()
        tool = SearchPapersTool(api = api, preferencesStore = prefsStore)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `search by query returns results`() = runBlocking {
        enqueueFeed()
        val result = tool.execute(JSONObject().put("query", "deep learning"))
        assertTrue(result.contains("Deep Learning for NLP"))
        assertTrue(result.contains("cs.CL"))
        val recorded = server.takeRequest()
        assertTrue(recorded.url.queryParameter("search_query")!!.contains("all:"))
    }

    @Test
    fun `search by category returns results`() = runBlocking {
        enqueueFeed()
        val result = tool.execute(JSONObject().put("category", "cs.CL"))
        assertTrue(result.contains("Deep Learning for NLP"))
        val recorded = server.takeRequest()
        assertTrue(recorded.url.queryParameter("search_query")!!.contains("cat:cs.CL"))
    }

    @Test
    fun `fallback to preferences topics when no query`() = runBlocking {
        prefsStore.prefs = AgentPreferences(topics = "transformers, attention")
        enqueueFeed()
        val result = tool.execute(JSONObject())
        assertTrue(result.contains("Deep Learning for NLP"))
        val recorded = server.takeRequest()
        assertTrue(recorded.url.queryParameter("search_query")!!.contains("transformers"))
    }

    @Test
    fun `no query no category no prefs returns error`() = runBlocking {
        val result = tool.execute(JSONObject())
        assertTrue(result.contains("请提供搜索关键词"))
    }

    @Test
    fun `max parameter limits results`() = runBlocking {
        enqueueFeed()
        val result = tool.execute(JSONObject().put("query", "learning").put("max", 1))
        assertTrue(result.contains("Deep Learning for NLP"))
        assertTrue(!result.contains("Reinforcement Learning"))
    }

    @Test
    fun `network error returns friendly message`() = runBlocking {
        server.close()
        val result = tool.execute(JSONObject().put("query", "test"))
        assertTrue(result.contains("arXiv 检索失败"))
    }

    private class FakePreferences : PreferencesProvider {
        var prefs = AgentPreferences()
        override fun load(): AgentPreferences = prefs
        override fun save(preferences: AgentPreferences) { prefs = preferences }
        override fun clear() { prefs = AgentPreferences() }
    }
}