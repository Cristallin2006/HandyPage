package dev.handypage.app

import dev.handypage.app.arxiv.ArxivApi
import dev.handypage.app.arxiv.ArxivEntry
import dev.handypage.app.arxiv.ArxivGate
import dev.handypage.app.arxiv.FeedCache
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.Dispatcher
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * JVM tests for the arXiv API client against MockWebServer: Atom feed parsing
 * (multi-author, whitespace folding, pdf-link derivation from the abs URL),
 * query URL shape for both search modes, the HTTP-error path, and the
 * .part-then-rename PDF download with progress callbacks.
 *
 * M25 additions: gate spacing, retry-with-backoff on 429/5xx, Retry-After
 * precedence, non-retryable 4xx, TTL feed cache and single-flight coalescing.
 * Tests pass a zero-interval gate, a no-op sleeper and zero jitter so nothing
 * ever really sleeps; the recording sleeper captures the backoff schedule.
 */
class ArxivApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ArxivApi
    private lateinit var sleeps: MutableList<Long>

    /**
     * Two-entry Atom feed. Entry 1: explicit pdf link, two authors, title and
     * summary with newlines/extra spaces, two categories. Entry 2: no pdf
     * link, so pdfUrl must be derived from the abs URL.
     */
    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>ArXiv Query: search_query=all:"attention"</title>
          <id>http://arxiv.org/api/query</id>
          <updated>2026-07-26T00:00:00Z</updated>
          <entry>
            <id>http://arxiv.org/abs/1706.03762v5</id>
            <published>2017-06-12T17:57:34Z</published>
            <title>Attention Is
                All   You Need</title>
            <summary>The dominant sequence
                transduction models.</summary>
            <author><name>Ashish Vaswani</name></author>
            <author><name>Noam Shazeer</name></author>
            <link href="http://arxiv.org/abs/1706.03762v5" rel="alternate" type="text/html"/>
            <link title="pdf" href="http://arxiv.org/pdf/1706.03762v5" rel="related" type="application/pdf"/>
            <category term="cs.CL" scheme="http://arxiv.org/schemas/atom"/>
            <category term="cs.AI" scheme="http://arxiv.org/schemas/atom"/>
          </entry>
          <entry>
            <id>http://arxiv.org/abs/2301.00001v2</id>
            <published>2023-01-01T00:00:00Z</published>
            <title>Second Paper</title>
            <summary>Another summary.</summary>
            <author><name>Jane Doe</name></author>
            <link href="http://arxiv.org/abs/2301.00001v2" rel="alternate" type="text/html"/>
            <category term="math.CO" scheme="http://arxiv.org/schemas/atom"/>
          </entry>
        </feed>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sleeps = mutableListOf()
        api = newApi()
    }

    /** API wired for tests: zero gate interval, recording sleeper, no jitter. */
    private fun newApi(
        gate: ArxivGate = ArxivGate(minIntervalMs = 0),
        cache: FeedCache = FeedCache(),
    ): ArxivApi = ArxivApi(
        OkHttpClient(),
        baseUrl = server.url("/").toString(),
        htmlBaseUrl = server.url("/").toString(),
        gate = gate,
        cache = cache,
        sleeper = { sleeps += it },
        jitterMs = { 0 },
    )

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueueFeed() {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/atom+xml; charset=utf-8")
                .body(feed)
                .build(),
        )
    }

    @Test
    fun `search parses entries and sends the relevance query`() {
        enqueueFeed()

        val entries = api.search("attention is all you need", start = 5, maxResults = 10)

        assertEquals(2, entries.size)
        val first = entries[0]
        assertEquals("1706.03762v5", first.id)
        assertEquals("Attention Is All You Need", first.title)
        assertEquals(listOf("Ashish Vaswani", "Noam Shazeer"), first.authors)
        assertEquals("The dominant sequence transduction models.", first.summary)
        assertEquals("2017-06-12T17:57:34Z", first.published)
        assertEquals("http://arxiv.org/abs/1706.03762v5", first.absUrl)
        assertEquals("http://arxiv.org/pdf/1706.03762v5", first.pdfUrl)
        assertEquals("cs.CL", first.primaryCategory)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/query", recorded.url.encodedPath)
        assertEquals(
            "all:attention AND all:is AND all:all AND all:you AND all:need",
            recorded.url.queryParameter("search_query"),
        )
        assertEquals("5", recorded.url.queryParameter("start"))
        assertEquals("10", recorded.url.queryParameter("max_results"))
        assertEquals("relevance", recorded.url.queryParameter("sortBy"))
        assertEquals("descending", recorded.url.queryParameter("sortOrder"))
    }

    @Test
    fun `byCategory derives the pdf url from the abs url and sorts by date`() {
        enqueueFeed()

        val entries = api.byCategory("cs.CL")

        val second = entries[1]
        assertEquals("2301.00001v2", second.id)
        assertEquals("http://arxiv.org/abs/2301.00001v2", second.absUrl)
        assertEquals("http://arxiv.org/pdf/2301.00001v2", second.pdfUrl)
        assertEquals("math.CO", second.primaryCategory)
        assertEquals(listOf("Jane Doe"), second.authors)

        val recorded = server.takeRequest()
        assertEquals("cat:cs.CL", recorded.url.queryParameter("search_query"))
        assertEquals("0", recorded.url.queryParameter("start"))
        assertEquals("25", recorded.url.queryParameter("max_results"))
        assertEquals("submittedDate", recorded.url.queryParameter("sortBy"))
    }

    // ---- M31: buildSearchQuery ----

    @Test
    fun `plain terms are ANDed under the all field`() {
        assertEquals("all:diffusion AND all:transformer", api.buildSearchQuery("diffusion transformer"))
    }

    @Test
    fun `field prefixes and explicit booleans pass through`() {
        assertEquals("ti:attention AND au:vaswani", api.buildSearchQuery("ti:attention au:vaswani"))
        assertEquals("all:llm OR all:vlm", api.buildSearchQuery("llm OR vlm"))
    }

    @Test
    fun `leading trailing and doubled operators are dropped`() {
        assertEquals("all:llm", api.buildSearchQuery("OR llm"))
        assertEquals("all:llm", api.buildSearchQuery("llm AND"))
        assertEquals("all:a AND all:b", api.buildSearchQuery("a AND OR b"))
    }

    @Test
    fun `parser-hostile characters are stripped but hyphens and dots survive`() {
        // Unbalanced quotes / parens / colons previously let the API silently
        // rewrite the query into a term-OR; hyphens are empirically safe.
        assertEquals("all:GPT-4", api.buildSearchQuery("GPT-4"))
        assertEquals("all:attention AND all:is AND all:all", api.buildSearchQuery("attention \"(is: all"))
        assertEquals("cat:cs.CL", api.buildSearchQuery("cat:cs.CL"))
    }

    @Test
    fun `empty or punctuation-only input yields an empty query and no request`() {
        assertEquals("", api.buildSearchQuery("  \"( )  "))
        assertTrue(api.search("\"( )").isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `searchAndCategory parenthesises the keyword part`() {
        enqueueFeed()

        api.searchAndCategory("llm OR vlm", "cs.CL")

        val recorded = server.takeRequest()
        assertEquals("(all:llm OR all:vlm) AND cat:cs.CL", recorded.url.queryParameter("search_query"))
    }

    @Test
    fun `HTTP 503 exhausts three attempts then fails with the status`() {
        repeat(3) { server.enqueue(MockResponse.Builder().code(503).body("retry later").build()) }

        try {
            api.search("anything")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("503"))
        }
        assertEquals(3, server.requestCount)
        assertEquals(listOf(3000L, 9000L), sleeps)
    }

    @Test
    fun `503 is retried with backoff and then succeeds`() {
        server.enqueue(MockResponse.Builder().code(503).body("busy").build())
        enqueueFeed()

        val entries = api.search("attention")

        assertEquals(2, entries.size)
        assertEquals(2, server.requestCount)
        assertEquals(listOf(3000L), sleeps)
    }

    @Test
    fun `Retry-After header overrides the computed backoff`() {
        server.enqueue(
            MockResponse.Builder()
                .code(503)
                .addHeader("Retry-After", "17")
                .body("busy")
                .build(),
        )
        enqueueFeed()

        api.search("attention")

        assertEquals(listOf(17_000L), sleeps)
    }

    @Test
    fun `429 gives up after three attempts`() {
        repeat(3) { server.enqueue(MockResponse.Builder().code(429).body("slow down").build()) }

        try {
            api.search("spam")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("429"))
        }
        assertEquals(3, server.requestCount)
        assertEquals(listOf(3000L, 9000L), sleeps)
    }

    @Test
    fun `400 bad query fails immediately without retries`() {
        server.enqueue(MockResponse.Builder().code(400).body("malformed").build())

        try {
            api.search("broken")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("400"))
        }
        assertEquals(1, server.requestCount)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `identical queries hit the network once and the second is served from cache`() {
        enqueueFeed()

        val first = api.search("attention")
        val second = api.search("attention")

        assertEquals(1, server.requestCount)
        assertEquals(first, second)
    }

    @Test
    fun `cache entry expires after the ttl`() {
        var now = 1_000_000L
        val ttlCache = FeedCache(ttlMs = 1000, nowMs = { now })
        api = newApi(cache = ttlCache)
        enqueueFeed()
        enqueueFeed()

        api.byCategory("cs.CL")
        assertEquals(1, server.requestCount)
        now += 2000
        api.byCategory("cs.CL")
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `concurrent identical queries collapse into one request`() {
        // Hold the feed response for 500 ms so thread B reaches the
        // single-flight lock while thread A's request is still in flight.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Thread.sleep(500)
                return MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/atom+xml; charset=utf-8")
                    .body(feed)
                    .build()
            }
        }
        val results = arrayOfNulls<List<ArxivEntry>>(2)
        val errors = arrayOfNulls<Throwable>(2)
        val start = CountDownLatch(1)

        val a = thread {
            start.await()
            try {
                results[0] = api.search("attention")
            } catch (t: Throwable) {
                errors[0] = t
            }
        }
        val b = thread {
            start.await()
            try {
                results[1] = api.search("attention")
            } catch (t: Throwable) {
                errors[1] = t
            }
        }
        start.countDown()
        a.join(10_000)
        b.join(10_000)

        assertNull(errors[0])
        assertNull(errors[1])
        assertEquals(2, results[0]?.size)
        assertEquals(2, results[1]?.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `gate spaces request slots by the minimum interval`() {
        var now = 100_000L
        val gateSleeps = mutableListOf<Long>()
        val gate = ArxivGate(minIntervalMs = 3000, sleeper = { gateSleeps += it }, nowMs = { now })

        gate.awaitTurn() // first ever request: no wait
        gate.awaitTurn()
        now += 1000     // only 1 s has passed
        gate.awaitTurn()

        assertEquals(listOf(3000L, 2000L), gateSleeps)
    }

    @Test
    fun `downloadPdf writes via part file and reports progress to 1`() {
        val payload = "%PDF-1.7 fake body\n".repeat(4096)
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/pdf")
                .body(payload)
                .build(),
        )
        val dest = File.createTempFile("arxiv", ".pdf")
        dest.deleteOnExit()
        val progress = mutableListOf<Float>()

        api.downloadPdf(server.url("/pdf/1706.03762v5").toString(), dest) { progress += it }

        assertEquals(payload, dest.readText())
        assertFalse("leftover .part file", File(dest.parentFile, dest.name + ".part").exists())
        assertTrue("no progress callbacks", progress.isNotEmpty())
        assertEquals(1f, progress.last())
    }

    @Test
    fun `downloadPdf retries a 503 and completes on the second attempt`() {
        val payload = "%PDF-1.7 fake body\n".repeat(1024)
        server.enqueue(MockResponse.Builder().code(503).body("busy").build())
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/pdf")
                .body(payload)
                .build(),
        )
        val dest = File.createTempFile("arxiv", ".pdf")
        dest.deleteOnExit()

        api.downloadPdf(server.url("/pdf/x").toString(), dest) { }

        assertEquals(payload, dest.readText())
        assertEquals(2, server.requestCount)
        assertEquals(listOf(3000L), sleeps)
    }

    @Test
    fun `fetchHtmlVersion returns the body on a 200 html response`() {
        val page = "<html><body><article class=\"ltx_document\">paper</article></body></html>"
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .body(page)
                .build(),
        )

        val html = api.fetchHtmlVersion("1706.03762v5")

        assertEquals(page, html)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/html/1706.03762v5", recorded.url.encodedPath)
    }

    @Test
    fun `fetchHtmlVersion returns null on 404`() {
        server.enqueue(MockResponse.Builder().code(404).body("no HTML for this paper").build())

        assertNull(api.fetchHtmlVersion("1234.56789v1"))
    }

    @Test
    fun `fetchHtmlVersion follows the bare-id redirect to the versioned url`() {
        val page = "<html><body>paper v2</body></html>"
        server.enqueue(
            MockResponse.Builder()
                .code(301)
                .addHeader("Location", server.url("/html/1706.03762v2").toString())
                .build(),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .body(page)
                .build(),
        )

        val html = api.fetchHtmlVersion("1706.03762")

        assertEquals(page, html)
        assertEquals("/html/1706.03762", server.takeRequest().url.encodedPath)
        assertEquals("/html/1706.03762v2", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `fetchHtmlVersion retries a 503 once and returns the body`() {
        val page = "<html><body>paper</body></html>"
        server.enqueue(MockResponse.Builder().code(503).body("busy").build())
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .body(page)
                .build(),
        )

        assertEquals(page, api.fetchHtmlVersion("1706.03762v5"))
        assertEquals(2, server.requestCount)
    }
}
