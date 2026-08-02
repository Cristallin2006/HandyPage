package dev.handypage.app

import dev.handypage.app.arxiv.ArxivApi
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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

/**
 * JVM tests for the arXiv API client against MockWebServer: Atom feed parsing
 * (multi-author, whitespace folding, pdf-link derivation from the abs URL),
 * query URL shape for both search modes, the HTTP-error path, and the
 * .part-then-rename PDF download with progress callbacks.
 */
class ArxivApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ArxivApi

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
        api = ArxivApi(
            OkHttpClient(),
            baseUrl = server.url("/").toString(),
            htmlBaseUrl = server.url("/").toString(),
        )
    }

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
        assertEquals("all:\"attention is all you need\"", recorded.url.queryParameter("search_query"))
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

    @Test
    fun `HTTP 503 fails with an IOException carrying the status`() {
        server.enqueue(MockResponse.Builder().code(503).body("retry later").build())

        try {
            api.search("anything")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("503"))
        }
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
}
