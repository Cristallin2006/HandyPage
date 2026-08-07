package dev.handypage.app.ai

import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/**
 * One chat message. Assistant messages may carry [toolCalls] (function-call
 * requests the model asked for); role "tool" messages carry a tool result
 * plus the [toolCallId] of the request they answer.
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val toolCallId: String? = null,
)

/** A completed function-call request, accumulated from streamed fragments. */
data class ToolCallRequest(
    val id: String,
    val name: String,
    /** Raw JSON text of the arguments object, exactly as streamed. */
    val argumentsJson: String,
)

/** A tool exposed to the model via OpenAI function calling. */
data class ToolSpec(
    val name: String,
    val description: String,
    /** JSON Schema of the parameters object (`{"type":"object",...}`). */
    val parametersSchema: JSONObject,
)

/**
 * One streamed event from a provider. Thinking-mode models (e.g.
 * deepseek-v4-pro, `thinking.type=enabled` by default) emit [Reasoning]
 * deltas before the final [Content] answer — the UI shows a "思考中…"
 * indicator for them but does not render the reasoning text.
 *
 * When tools are attached to the request, the provider also emits [Usage]
 * (from the stream's usage chunk) and [ToolCalls] (the accumulated,
 * complete call requests), in that order just before the flow completes.
 */
sealed interface AIEvent {
    data class Content(val text: String) : AIEvent
    data class Reasoning(val text: String) : AIEvent

    /** Complete tool-call requests assembled at end of stream. */
    data class ToolCalls(val calls: List<ToolCallRequest>) : AIEvent

    /** Token accounting from the usage chunk (`stream_options.include_usage`). */
    data class Usage(val promptTokens: Int, val completionTokens: Int) : AIEvent
}

/** A chat-completion provider streaming reply deltas. */
interface AIProvider {
    val name: String

    /**
     * Streams assistant reply events; the flow completes at end of stream.
     * When [tools] is non-empty the request carries OpenAI function-calling
     * tool definitions and asks for streamed usage accounting; null or empty
     * keeps the legacy no-tools behaviour byte-for-byte.
     *
     * M34: [disableThinking] asks hybrid-reasoning providers (DeepSeek V4,
     * thinking on by default) to skip the reasoning phase for this call —
     * `thinking: {"type":"disabled"}` on the wire. The batch translation
     * pipeline uses it: translation needs zero reasoning, and the thinking
     * phase costs 10-60 s of pure latency per batch. Providers without a
     * thinking toggle ignore the flag.
     */
    fun streamChat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>? = null,
        disableThinking: Boolean = false,
    ): Flow<AIEvent>
}

/** Provider failure with a user-displayable message (status/body included). */
class AIException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
