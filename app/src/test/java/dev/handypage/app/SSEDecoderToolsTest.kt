package dev.handypage.app

import dev.handypage.app.ai.AIEvent
import dev.handypage.app.ai.AIProviderConfig
import dev.handypage.app.ai.ChatMessage
import dev.handypage.app.ai.OpenAICompatProvider
import dev.handypage.app.ai.SSEDecoder
import dev.handypage.app.ai.ToolCallRequest
import dev.handypage.app.ai.ToolSpec
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for the M5 tool-calling protocol (DESIGN.md §4.9): SSEDecoder
 * tool-call fragment accumulation across chunks and interleaved indices, the
 * usage chunk, garbage tolerance, and the provider wire format (tools in the
 * request body, Usage/ToolCalls terminal event order).
 */
class SSEDecoderToolsTest {

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
    fun `tool call fragments accumulate across chunks and interleaved indices`() {
        val acc = SSEDecoder.ToolCallAccumulator()
        val lines = listOf(
            // index 0 starts: id + name + first arguments piece
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\"," +
                "\"function\":{\"name\":\"lookup_word\",\"arguments\":\"{\\\"wo\"}}]}}]}",
            // index 1 interleaves before index 0 finishes
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1,\"id\":\"call_2\"," +
                "\"function\":{\"name\":\"save_vocab\",\"arguments\":\"{\\\"a\"}}]}}]}",
            // later chunks carry only the index and an arguments piece
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0," +
                "\"function\":{\"arguments\":\"rd\\\":\\\"died\\\"}\"}}]}}]}",
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1," +
                "\"function\":{\"arguments\":\"\\\":1}\"}}]}}]}",
        )
        for (line in lines) {
            when (val event = SSEDecoder.parseLine(line)) {
                is SSEDecoder.ToolCallDeltas -> acc.addAll(event)
                else -> fail("expected ToolCallDeltas for line, got $event")
            }
        }
        assertEquals(
            listOf(
                ToolCallRequest("call_1", "lookup_word", "{\"word\":\"died\"}"),
                ToolCallRequest("call_2", "save_vocab", "{\"a\":1}"),
            ),
            acc.complete(),
        )
    }

    @Test
    fun `tool call delta without id or arguments still yields a fragment`() {
        val event = SSEDecoder.parseLine(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":2," +
                "\"function\":{\"name\":\"get_article_text\"}}]}}]}",
        )
        assertEquals(
            SSEDecoder.ToolCallDeltas(
                listOf(SSEDecoder.ToolCallDelta(2, null, "get_article_text", null)),
            ),
            event,
        )
        // Missing id is synthesized so the reply linkage stays well-formed.
        val acc = SSEDecoder.ToolCallAccumulator()
        acc.addAll(event as SSEDecoder.ToolCallDeltas)
        assertEquals(
            listOf(ToolCallRequest("call_2", "get_article_text", "")),
            acc.complete(),
        )
    }

    @Test
    fun `usage chunk parses from an empty-choices data line`() {
        assertEquals(
            SSEDecoder.Usage(12, 7),
            SSEDecoder.parseLine(
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":7}}",
            ),
        )
        // No choices key at all, still a usage chunk.
        assertEquals(
            SSEDecoder.Usage(1, 2),
            SSEDecoder.parseLine(
                "data: {\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2}}",
            ),
        )
        // Choices-empty line without usage is ignorable.
        assertNull(SSEDecoder.parseLine("data: {\"choices\":[]}"))
    }

    @Test
    fun `garbage tool call payloads do not crash the decoder`() {
        assertNull(SSEDecoder.parseLine("data: {broken json"))
        assertNull(
            SSEDecoder.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":\"oops\"}}]}",
            ),
        )
        assertNull(
            SSEDecoder.parseLine(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[42]}}]}",
            ),
        )
        // Reasoning still parses alongside the new branches (M3 behaviour).
        assertEquals(
            SSEDecoder.Reasoning("hmm"),
            SSEDecoder.parseLine(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hmm\"}}]}",
            ),
        )
    }

    @Test
    fun `tools request carries tools and stream_options, stream ends usage then tool calls`() =
        runBlocking {
            val sse = buildString {
                append("data: {\"choices\":[{\"delta\":{\"content\":\"看\"}}]}\n\n")
                append(
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0," +
                        "\"id\":\"call_1\",\"function\":{\"name\":\"lookup_word\"," +
                        "\"arguments\":\"{\\\"word\\\":\\\"died\\\"}\"}}]}}]}\n\n",
                )
                append(
                    "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10," +
                        "\"completion_tokens\":5}}\n\n",
                )
                append("data: [DONE]\n\n")
            }
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .body(sse)
                    .build(),
            )

            val tools = listOf(
                ToolSpec(
                    name = "lookup_word",
                    description = "查询内置词典",
                    parametersSchema = JSONObject().put("type", "object"),
                ),
            )
            val events = provider(server.url("/v1").toString())
                .streamChat(listOf(ChatMessage(role = "user", content = "q")), tools)
                .toList()

            assertEquals(
                listOf(
                    AIEvent.Content("看"),
                    AIEvent.Usage(10, 5),
                    AIEvent.ToolCalls(
                        listOf(ToolCallRequest("call_1", "lookup_word", "{\"word\":\"died\"}")),
                    ),
                ),
                events,
            )

            val body = server.takeRequest().body!!.utf8()
            assertTrue(body.contains("\"tools\""))
            assertTrue(body.contains("lookup_word"))
            assertTrue(body.contains("\"stream_options\":{\"include_usage\":true}"))
        }

    @Test
    fun `request without tools carries neither tools nor stream_options`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n")
                .build(),
        )
        provider(server.url("/v1").toString())
            .streamChat(listOf(ChatMessage(role = "user", content = "Hi")))
            .toList()
        val body = server.takeRequest().body!!.utf8()
        assertFalse(body.contains("\"tools\""))
        assertFalse(body.contains("stream_options"))
    }

    @Test
    fun `assistant tool_calls and tool result messages serialize into the request`() =
        runBlocking {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n")
                    .build(),
            )
            val messages = listOf(
                ChatMessage(
                    role = "assistant",
                    content = "",
                    toolCalls = listOf(
                        ToolCallRequest("call_1", "lookup_word", "{\"word\":\"died\"}"),
                    ),
                ),
                ChatMessage(role = "tool", content = "died /daɪd/ v.", toolCallId = "call_1"),
            )
            provider(server.url("/v1").toString()).streamChat(messages).toList()

            val json = JSONObject(server.takeRequest().body!!.utf8())
            val jsonMessages = json.getJSONArray("messages")
            val assistant = jsonMessages.getJSONObject(0)
            val call = assistant.getJSONArray("tool_calls").getJSONObject(0)
            assertEquals("call_1", call.getString("id"))
            assertEquals("function", call.getString("type"))
            assertEquals("lookup_word", call.getJSONObject("function").getString("name"))
            assertEquals(
                "{\"word\":\"died\"}",
                call.getJSONObject("function").getString("arguments"),
            )
            val tool = jsonMessages.getJSONObject(1)
            assertEquals("tool", tool.getString("role"))
            assertEquals("call_1", tool.getString("tool_call_id"))
            assertEquals("died /daɪd/ v.", tool.getString("content"))
        }
}
