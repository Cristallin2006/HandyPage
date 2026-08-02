package dev.handypage.app

import dev.handypage.app.ai.AIEvent
import dev.handypage.app.ai.AIException
import dev.handypage.app.ai.AIProviderConfig
import dev.handypage.app.ai.ChatMessage
import dev.handypage.app.ai.OpenAICompatProvider
import dev.handypage.app.ai.SSEDecoder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for the OpenAI-compatible SSE provider against MockWebServer:
 * exact token sequence (including a role-only delta and the [DONE] sentinel),
 * request shape, URL joining, and the HTTP-error path. org.json comes from
 * the test classpath jar, so the production parse code runs unmodified.
 */
class OpenAICompatProviderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun provider(baseUrl: String): OpenAICompatProvider =
        OpenAICompatProvider(
            AIProviderConfig(
                presetId = "custom",
                apiKey = "test-key",
                baseUrl = baseUrl,
                model = "test-model",
            ),
            OkHttpClient(),
        )

    @Test
    fun `SSE stream yields exact delta sequence`() = runBlocking {
        val sse = buildString {
            append("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\", \"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"world\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"!\"}}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream")
                .body(sse)
                .build(),
        )

        val events = provider(server.url("/v1").toString())
            .streamChat(listOf(ChatMessage(role = "user", content = "Hi")))
            .toList()

        assertEquals(
            listOf(
                AIEvent.Content("Hello"),
                AIEvent.Content(", "),
                AIEvent.Content("world"),
                AIEvent.Content("!"),
            ),
            events,
        )

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.url.encodedPath)
        assertEquals("Bearer test-key", recorded.headers["Authorization"])
        assertTrue(recorded.body!!.utf8().contains("\"stream\":true"))
        assertTrue(recorded.body!!.utf8().contains("\"model\":\"test-model\""))
    }

    @Test
    fun `SSE stream without DONE still completes at EOF`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("data: {\"choices\":[{\"delta\":{\"content\":\"only\"}}]}\n\n")
                .build(),
        )
        val events = provider(server.url("/v1").toString())
            .streamChat(listOf(ChatMessage(role = "user", content = "Hi")))
            .toList()
        assertEquals(listOf(AIEvent.Content("only")), events)
    }

    @Test
    fun `thinking mode stream yields reasoning then content`() = runBlocking {
        val sse = buildString {
            append("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking…\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"still\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"answer\"}}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream")
                .body(sse)
                .build(),
        )
        val events = provider(server.url("/v1").toString())
            .streamChat(listOf(ChatMessage(role = "user", content = "Hi")))
            .toList()
        assertEquals(
            listOf(
                AIEvent.Reasoning("thinking…"),
                AIEvent.Reasoning("still"),
                AIEvent.Content("answer"),
            ),
            events,
        )
    }

    @Test
    fun `HTTP 401 fails with status and body snippet`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(401)
                .body("{\"error\":{\"message\":\"Invalid API key\"}}")
                .build(),
        )
        try {
            provider(server.url("/v1").toString())
                .streamChat(listOf(ChatMessage(role = "user", content = "Hi")))
                .toList()
            fail("expected AIException")
        } catch (e: AIException) {
            assertTrue("message was: ${e.message}", e.message!!.contains("401"))
            assertTrue("message was: ${e.message}", e.message!!.contains("Invalid API key"))
        }
    }

    @Test
    fun `url join does not double the chat completions path`() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            OpenAICompatProvider.chatCompletionsUrl("https://api.deepseek.com"),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            OpenAICompatProvider.chatCompletionsUrl("https://api.openai.com/v1"),
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            OpenAICompatProvider.chatCompletionsUrl("https://api.deepseek.com/chat/completions"),
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            OpenAICompatProvider.chatCompletionsUrl("https://api.deepseek.com/chat/completions/"),
        )
    }

    @Test
    fun `decoder skips blanks, comments, role-only deltas and empty content`() {
        assertNull(SSEDecoder.parseLine(""))
        assertNull(SSEDecoder.parseLine(": ping"))
        assertNull(SSEDecoder.parseLine("event: message"))
        assertNull(
            SSEDecoder.parseLine("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}")
        )
        assertNull(SSEDecoder.parseLine("data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}"))
        assertNull(SSEDecoder.parseLine("data: not-json"))
        assertEquals(SSEDecoder.Done, SSEDecoder.parseLine("data: [DONE]"))
        assertEquals(
            SSEDecoder.Delta("你好"),
            SSEDecoder.parseLine("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}"),
        )
    }

    @Test
    fun `decoder surfaces thinking-mode reasoning deltas`() {
        assertEquals(
            SSEDecoder.Reasoning("let me think"),
            SSEDecoder.parseLine(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"let me think\"}}]}"
            ),
        )
        assertNull(
            SSEDecoder.parseLine("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"\"}}]}")
        )
        // Content wins when both fields are present in one delta.
        assertEquals(
            SSEDecoder.Delta("answer"),
            SSEDecoder.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"answer\",\"reasoning_content\":\"r\"}}]}"
            ),
        )
    }

    @Test
    fun `probe reply reports content, thinking mode, or empty`() {
        val withContent = JSONObject(
            """{"choices":[{"message":{"content":"Hi there"}}]}"""
        )
        assertEquals("Hi there", OpenAICompatProvider.probeReplyText(withContent))

        val thinkingOnly = JSONObject(
            """{"choices":[{"message":{"content":"","reasoning_content":"hmm"}}]}"""
        )
        assertEquals("(连接成功，思考模式)", OpenAICompatProvider.probeReplyText(thinkingOnly))

        val empty = JSONObject("""{"choices":[{"message":{"content":""}}]}""")
        assertEquals("(连接成功，无文本内容)", OpenAICompatProvider.probeReplyText(empty))
    }
}
