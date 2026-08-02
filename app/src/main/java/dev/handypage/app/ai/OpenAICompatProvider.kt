package dev.handypage.app.ai

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenAI-compatible streaming chat provider (`POST {baseUrl}/chat/completions`,
 * `Authorization: Bearer <key>`, `{model, messages, stream: true}`).
 *
 * Covers DeepSeek, OpenAI, and Gemini's OpenAI-compat endpoint; they differ
 * only in base URL, model name, and key.
 */
class OpenAICompatProvider(
    private val config: AIProviderConfig,
    baseClient: OkHttpClient,
) : AIProvider {

    override val name: String
        get() = config.preset.label

    /**
     * Per-provider client derived from the shared one: 15 s connect timeout,
     * no read timeout — SSE responses are long-lived and must not be cut off
     * between tokens.
     */
    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun streamChat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>?,
    ): Flow<AIEvent> = callbackFlow {
        val request = buildRequest(messages, stream = true, tools = tools)
        dbg("streamChat -> ${request.url} bodyBytes=${request.body?.contentLength()}")
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                dbg("onFailure: ${e.javaClass.simpleName}: ${e.message}")
                close(AIException("网络请求失败: ${e.message}", e))
            }

            override fun onResponse(call: Call, response: Response) {
                dbg("onResponse: HTTP ${response.code}")
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string()?.take(500).orEmpty()
                        dbg("error body: $errBody")
                        close(AIException("HTTP ${resp.code}: $errBody"))
                        return
                    }
                    try {
                        var lines = 0
                        var events = 0
                        val toolAccumulator = SSEDecoder.ToolCallAccumulator()
                        var usage: AIEvent.Usage? = null
                        val source = resp.body!!.source()
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            lines++
                            if (lines == 1) dbg("first SSE line after request, len=${line.length}")
                            when (val event = SSEDecoder.parseLine(line)) {
                                is SSEDecoder.Delta -> { events++; trySend(AIEvent.Content(event.text)) }
                                is SSEDecoder.Reasoning -> { events++; trySend(AIEvent.Reasoning(event.text)) }
                                is SSEDecoder.ToolCallDeltas -> { events++; toolAccumulator.addAll(event) }
                                is SSEDecoder.Usage -> {
                                    events++
                                    usage = AIEvent.Usage(event.promptTokens, event.completionTokens)
                                }
                                SSEDecoder.Done -> break
                                null -> Unit // blank line, comment, or non-delta event
                            }
                        }
                        dbg("stream finished: lines=$lines events=$events")
                        // Terminal protocol order: usage accounting first,
                        // then the accumulated complete tool-call requests.
                        usage?.let { trySend(it) }
                        val toolCalls = toolAccumulator.complete()
                        if (toolCalls.isNotEmpty()) trySend(AIEvent.ToolCalls(toolCalls))
                        close()
                    } catch (e: Exception) {
                        dbg("read exception: ${e.javaClass.simpleName}: ${e.message}")
                        close(if (e is AIException) e else AIException("流读取失败: ${e.message}", e))
                    }
                }
            }
        })
        // Cancelling the collecting Job lands here and kills the HTTP call,
        // which closes the response socket.
        awaitClose { dbg("awaitClose: cancelling call"); call.cancel() }
    }

    /**
     * Non-streaming probe used by the settings screen's 测试连接 button:
     * a real chat/completions round-trip capped at 1 token. Returns the reply
     * text (or finish reason) on success; throws [AIException] otherwise.
     */
    fun testConnection(): Flow<String> = flow {
        // max_tokens=16: thinking-mode models spend tokens on reasoning_content
        // before content; 1 token would starve the reply entirely.
        val call = client.newCall(buildRequest(emptyList(), stream = false, maxTokens = 16))
        call.execute().use { resp ->
            if (!resp.isSuccessful) {
                throw AIException(
                    "HTTP ${resp.code}: ${resp.body?.string()?.take(500).orEmpty()}"
                )
            }
            emit(probeReplyText(JSONObject(resp.body!!.string())))
        }
    }.flowOn(Dispatchers.IO) // blocking execute() must not run on the caller's (main) thread

    private fun buildRequest(
        messages: List<ChatMessage>,
        stream: Boolean,
        maxTokens: Int? = null,
        tools: List<ToolSpec>? = null,
    ): Request {
        val jsonMessages = JSONArray()
        // The probe goes out with a minimal prompt; real calls use the given list.
        val effective = messages.ifEmpty {
            listOf(ChatMessage(role = "user", content = "Hi"))
        }
        for (m in effective) {
            jsonMessages.put(serializeMessage(m))
        }
        val body = JSONObject()
            .put("model", config.effectiveModel)
            .put("messages", jsonMessages)
            .put("stream", stream)
        if (maxTokens != null) body.put("max_tokens", maxTokens)
        if (!tools.isNullOrEmpty()) {
            val jsonTools = JSONArray()
            for (tool in tools) {
                jsonTools.put(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", tool.name)
                                .put("description", tool.description)
                                .put("parameters", tool.parametersSchema),
                        ),
                )
            }
            body.put("tools", jsonTools)
            // Token usage arrives as a final choices-empty chunk when streaming.
            if (stream) {
                body.put(
                    "stream_options",
                    JSONObject().put("include_usage", true),
                )
            }
        }

        return Request.Builder()
            .url(chatCompletionsUrl(config.effectiveBaseUrl))
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    /**
     * Serializes one message for the request body: plain role/content for
     * system/user, an assistant message additionally carries its `tool_calls`,
     * and a role "tool" message carries the `tool_call_id` it answers.
     */
    private fun serializeMessage(m: ChatMessage): JSONObject {
        val obj = JSONObject()
            .put("role", m.role)
            .put("content", m.content)
        if (m.toolCallId != null) obj.put("tool_call_id", m.toolCallId)
        if (m.toolCalls.isNotEmpty()) {
            val calls = JSONArray()
            for (call in m.toolCalls) {
                calls.put(
                    JSONObject()
                        .put("id", call.id)
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", call.name)
                                .put("arguments", call.argumentsJson),
                        ),
                )
            }
            obj.put("tool_calls", calls)
        }
        return obj
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Logcat in the app, stdout in JVM unit tests (android.util is not mocked). */
        internal fun dbg(msg: String) {
            try {
                Log.d("HandypageAI", msg)
            } catch (_: Throwable) {
                println("HandypageAI: $msg")
            }
        }

        /**
         * Probe reply text: content if present, an explicit thinking-mode note
         * when the reply spent its tokens on reasoning_content, else a generic
         * empty note. Pure + android-free for JVM tests.
         */
        fun probeReplyText(body: JSONObject): String {
            val msg = body.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
            val content = msg?.optString("content")?.takeIf { it.isNotEmpty() }
            val reasoning = msg?.optString("reasoning_content")?.takeIf { it.isNotEmpty() }
            return content
                ?: if (reasoning != null) "(连接成功，思考模式)" else "(连接成功，无文本内容)"
        }

        /** Exact join: never doubles a base URL already ending in the path. */
        fun chatCompletionsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/chat/completions")) {
                trimmed
            } else {
                "$trimmed/chat/completions"
            }
        }
    }
}

/**
 * Pure Server-Sent-Events decoder for the OpenAI streaming format — kept
 * android-free so it is JVM-testable.
 *
 * Lines look like `data: {"choices":[{"delta":{"content":"Hello"}}]}` and the
 * stream ends with `data: [DONE]`. The first delta usually carries only a
 * `role` and no `content`; those lines are skipped.
 */
object SSEDecoder {

    sealed interface Event

    data class Delta(val text: String) : Event

    /** A thinking-mode reasoning token (`delta.reasoning_content`). */
    data class Reasoning(val text: String) : Event

    /**
     * Raw `delta.tool_calls[]` fragments of one chunk. OpenAI streams each
     * call in pieces addressed by `index` (id/name once, arguments split over
     * many chunks, several indices interleaved); accumulate them across the
     * stream with [ToolCallAccumulator].
     */
    data class ToolCallDeltas(val deltas: List<ToolCallDelta>) : Event

    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsFragment: String?,
    )

    /**
     * Token usage chunk (`stream_options.include_usage`): arrives with an
     * empty `choices` array, typically right before `[DONE]`.
     */
    data class Usage(val promptTokens: Int, val completionTokens: Int) : Event

    object Done : Event

    /**
     * Parses one SSE line. @return [Delta] for a content token, [Reasoning]
     * for a thinking-mode reasoning token, [ToolCallDeltas] for tool-call
     * fragments, [Usage] for the usage chunk, [Done] at the `[DONE]`
     * sentinel, or null for anything ignorable (blank lines, comments,
     * non-data lines, data without usable payload).
     */
    fun parseLine(line: String): Event? {
        val payload = line.trim()
        if (!payload.startsWith("data:")) return null
        val data = payload.removePrefix("data:").trim()
        if (data == "[DONE]") return Done
        return try {
            val obj = JSONObject(data)
            val choices = obj.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                // Usage-only chunks carry no choices at all.
                obj.optJSONObject("usage")?.let { usage ->
                    return Usage(
                        usage.optInt("prompt_tokens"),
                        usage.optInt("completion_tokens"),
                    )
                }
                return null
            }
            val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null
            delta.optJSONArray("tool_calls")?.let { calls ->
                val fragments = (0 until calls.length()).mapNotNull { i ->
                    val call = calls.optJSONObject(i) ?: return@mapNotNull null
                    val function = call.optJSONObject("function")
                    ToolCallDelta(
                        index = call.optInt("index", 0),
                        id = call.optString("id").takeIf { it.isNotEmpty() },
                        name = function?.optString("name")?.takeIf { it.isNotEmpty() },
                        argumentsFragment = function?.optString("arguments")
                            ?.takeIf { it.isNotEmpty() },
                    )
                }
                if (fragments.isNotEmpty()) return ToolCallDeltas(fragments)
            }
            if (!delta.isNull("content")) {
                val content = delta.getString("content")
                if (content.isNotEmpty()) return Delta(content)
            }
            if (!delta.isNull("reasoning_content")) {
                val reasoning = delta.getString("reasoning_content")
                if (reasoning.isNotEmpty()) return Reasoning(reasoning)
            }
            null
        } catch (e: Exception) {
            // Non-JSON data line (e.g. a provider ping): ignore, never crash the stream.
            null
        }
    }

    /**
     * Reassembles streamed tool-call fragments into complete requests by
     * their `index` slot. Id and name are normally sent once, arguments in
     * arbitrary pieces; concatenation is used throughout so split id/name
     * fragments would also survive.
     */
    class ToolCallAccumulator {

        private class Slot {
            val id = StringBuilder()
            val name = StringBuilder()
            val arguments = StringBuilder()
        }

        private val slots = sortedMapOf<Int, Slot>()

        fun add(delta: ToolCallDelta) {
            val slot = slots.getOrPut(delta.index) { Slot() }
            delta.id?.let { slot.id.append(it) }
            delta.name?.let { slot.name.append(it) }
            delta.argumentsFragment?.let { slot.arguments.append(it) }
        }

        fun addAll(event: ToolCallDeltas) {
            for (delta in event.deltas) add(delta)
        }

        /**
         * Completed calls ordered by index, or an empty list when the stream
         * carried no tool calls. A missing id (non-compliant provider) is
         * synthesized so the matching role "tool" message stays well-formed.
         */
        fun complete(): List<ToolCallRequest> = slots.map { (index, slot) ->
            ToolCallRequest(
                id = slot.id.toString().ifEmpty { "call_$index" },
                name = slot.name.toString(),
                argumentsJson = slot.arguments.toString(),
            )
        }
    }
}
