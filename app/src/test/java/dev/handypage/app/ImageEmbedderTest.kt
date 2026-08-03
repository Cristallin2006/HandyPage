package dev.handypage.app

import dev.handypage.app.engine.ImageEmbedder
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for the M28 image embedder against MockWebServer: successful
 * downloads rewrite `src` to package paths and collect bytes (with the
 * hotlink-defeating header set), failures keep the remote URL, non-image
 * payloads are refused, data: URIs are never fetched, and the image/byte
 * caps hold.
 */
class ImageEmbedderTest {

    private lateinit var server: MockWebServer
    private lateinit var embedder: ImageEmbedder
    private lateinit var sleeps: MutableList<Long>

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sleeps = mutableListOf()
        embedder = ImageEmbedder(OkHttpClient(), sleeper = { sleeps += it })
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueuePng() {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "image/png")
                .body("%PNG fake")
                .build(),
        )
    }

    @Test
    fun `successful download rewrites src, collects bytes, sends hotlink headers`() {
        enqueuePng()
        val url = server.url("/pic.png").toString()

        val result = embedder.embed(
            """<p>a</p><img src="$url"/>""",
            referer = "https://example.com/article",
            userAgent = "TestUA/1.0",
        )

        assertEquals(1, result.images.size)
        val (path, bytes) = result.images.entries.single()
        assertTrue(path.startsWith("images/img-"))
        assertTrue(path.endsWith(".png"))
        assertEquals("%PNG fake", bytes.toString(Charsets.UTF_8))
        assertTrue(result.html.contains("""src="$path""""))
        assertEquals(0, result.failedCount)

        val recorded = server.takeRequest()
        assertEquals("TestUA/1.0", recorded.headers["User-Agent"])
        assertTrue(recorded.headers["Accept"].orEmpty().contains("image/"))
        assertEquals("https://example.com/article", recorded.headers["Referer"])
    }

    @Test
    fun `404 keeps the remote src and counts as failed`() {
        server.enqueue(MockResponse.Builder().code(404).body("nope").build())
        val url = server.url("/missing.png").toString()

        val result = embedder.embed("""<img src="$url"/>""", "https://x.com", "UA")

        assertTrue(result.images.isEmpty())
        assertEquals(1, result.failedCount)
        assertTrue(result.html.contains("""src="$url""""))
    }

    @Test
    fun `non-image payload is refused even on a 200`() {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/html")
                .body("<html>not an image</html>")
                .build(),
        )
        val url = server.url("/trap").toString() // no usable extension either

        val result = embedder.embed("""<img src="$url"/>""", "https://x.com", "UA")

        assertTrue(result.images.isEmpty())
        assertEquals(1, result.failedCount)
        assertTrue(result.html.contains("""src="$url""""))
    }

    @Test
    fun `data uri is never fetched`() {
        val html = """<img src="data:image/gif;base64,R0lGODdhAQABAIAAAP///////ywAAAAAAQABAAACAkQBADs="/>"""

        val result = embedder.embed(html, "https://x.com", "UA")

        assertTrue(result.images.isEmpty())
        assertEquals(0, result.failedCount)
        assertEquals(0, server.requestCount)
        assertTrue(result.html.contains("data:image/gif"))
    }

    @Test
    fun `extension falls back to the url when content-type is missing`() {
        server.enqueue(MockResponse.Builder().code(200).body("raw").build())
        val url = server.url("/photo.webp").toString()

        val result = embedder.embed("""<img src="$url"/>""", "https://x.com", "UA")

        assertEquals(1, result.images.size)
        assertTrue(result.images.keys.single().endsWith(".webp"))
    }

    @Test
    fun `oversized image is rejected and keeps the remote src`() {
        val big = ByteArray(ImageEmbedder.MAX_BYTES + 1)
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "image/jpeg")
                .body(okio.Buffer().write(big))
                .build(),
        )
        val url = server.url("/huge.jpg").toString()

        val result = embedder.embed("""<img src="$url"/>""", "https://x.com", "UA")

        assertTrue(result.images.isEmpty())
        assertEquals(1, result.failedCount)
        assertTrue(result.html.contains("""src="$url""""))
    }

    @Test
    fun `500 is retried once and the image embeds on the second attempt`() {
        server.enqueue(MockResponse.Builder().code(500).body("boom").build())
        enqueuePng()
        val url = server.url("/flaky.png").toString()

        val result = embedder.embed("""<img src="$url"/>""", "https://x.com", "UA")

        assertEquals(1, result.images.size)
        assertEquals(0, result.failedCount)
        assertEquals(2, server.requestCount)
        assertEquals(listOf(2000L), sleeps)
    }

    @Test
    fun `persistent 500 keeps the remote src after three attempts`() {
        repeat(3) { server.enqueue(MockResponse.Builder().code(500).body("boom").build()) }
        val url = server.url("/dead.png").toString()

        val result = embedder.embed("""<img src="$url"/>""", "https://x.com", "UA")

        assertTrue(result.images.isEmpty())
        assertEquals(1, result.failedCount)
        assertEquals(3, server.requestCount)
        assertEquals(listOf(2000L, 4000L), sleeps)
        assertTrue(result.html.contains("""src="$url""""))
    }

    @Test
    fun `404 is fatal and not retried`() {
        server.enqueue(MockResponse.Builder().code(404).body("nope").build())

        embedder.embed("""<img src="${server.url("/x.png")}"/>""", "https://x.com", "UA")

        assertEquals(1, server.requestCount)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `webp payload is transcoded to a safe format when a transcoder is present`() {
        val transcodingEmbedder = ImageEmbedder(
            OkHttpClient(),
            sleeper = { },
            transcoder = { bytes, fromExt ->
                assertEquals("webp", fromExt)
                "png-bytes".toByteArray() to "png"
            },
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "image/webp")
                .body("webp-bytes")
                .build(),
        )
        val url = server.url("/pic.webp").toString()

        val result = transcodingEmbedder.embed("""<img src="$url"/>""", "https://x.com", "UA")

        val (path, bytes) = result.images.entries.single()
        assertTrue(path.endsWith(".png"))
        assertEquals("png-bytes", bytes.toString(Charsets.UTF_8))
        assertTrue(result.html.contains("""src="$path""""))
    }

    @Test
    fun `webp payload is kept as-is when the transcoder cannot decode it`() {
        val failing = ImageEmbedder(
            OkHttpClient(),
            sleeper = { },
            transcoder = { _, _ -> null },
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "image/webp")
                .body("webp-bytes")
                .build(),
        )
        val url = server.url("/pic.webp").toString()

        val result = failing.embed("""<img src="$url"/>""", "https://x.com", "UA")

        val (path, _) = result.images.entries.single()
        assertTrue(path.endsWith(".webp"))
    }

    @Test
    fun `srcset sizes and loading are stripped so they cannot override the embedded src`() {
        enqueuePng()
        val url = server.url("/pic.png").toString()
        val html = """<img src="$url" srcset="$url 1x, https://cdn.example.com/big.png 2x" """ +
            """sizes="100vw" loading="lazy"/>"""

        val result = embedder.embed(html, "https://x.com", "UA")

        assertFalse("srcset survived", result.html.contains("srcset"))
        assertFalse("sizes survived", result.html.contains("sizes="))
        assertFalse("loading survived", result.html.contains("loading="))
        assertFalse("remote srcset url leaked", result.html.contains("cdn.example.com"))
        assertEquals(1, result.images.size)
    }

    @Test
    fun `image count cap leaves the rest remote`() {
        repeat(ImageEmbedder.MAX_IMAGES) { enqueuePng() }
        val html = (1..ImageEmbedder.MAX_IMAGES + 5)
            .joinToString("") { """<img src="${server.url("/p$it.png")}"/>""" }

        val result = embedder.embed(html, "https://x.com", "UA")

        assertEquals(ImageEmbedder.MAX_IMAGES, result.images.size)
        assertEquals(ImageEmbedder.MAX_IMAGES, server.requestCount)
        // The 5 overflow images keep their remote URLs.
        assertTrue(result.html.contains(server.url("/p${ImageEmbedder.MAX_IMAGES + 1}.png").toString()))
    }
}
