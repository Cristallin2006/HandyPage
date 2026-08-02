package dev.handypage.app.agent

import dev.handypage.app.ai.AIEvent
import dev.handypage.app.ai.AIProvider
import dev.handypage.app.ai.ChatMessage
import dev.handypage.app.ai.ToolCallRequest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** UI-facing events of one agent run (DESIGN.md §4.9). */
sealed interface AgentEvent {
    data class AssistantDelta(val text: String) : AgentEvent
    data class ReasoningDelta(val text: String) : AgentEvent
    data class ToolStarted(val name: String) : AgentEvent
    data class ToolFinished(val name: String, val success: Boolean) : AgentEvent
    data class Completed(val totalTokens: Int) : AgentEvent
    data class Failed(val message: String, val retryable: Boolean) : AgentEvent
    object ToolLimitReached : AgentEvent
}

/**
 * The M5 agent loop (DESIGN.md §4.9): send history + user message to the
 * provider, forward streamed deltas, execute requested tool calls, and loop
 * until the model answers without tools — bounded by hard gates, not prompt
 * self-discipline:
 *
 * - the daily token [budget] is checked before every provider round;
 * - at most [MAX_TOOL_ROUNDS] provider rounds per run ([AgentEvent.ToolLimitReached]);
 * - the first round fails visibly when no event arrives within
 *   [firstEventTimeoutMs] (same watchdog idea as the M3 panel's 60 s rule);
 * - tool results are capped at [ContextBuilder.TOOL_CONTENT_MAX_CHARS].
 *
 * Android-free apart from org.json, so the whole loop is JVM-testable with a
 * scripted fake provider.
 */
class AgentRunner(
    private val provider: AIProvider,
    tools: List<AgentTool>,
    private val budget: DailyBudget,
    private val callbacks: Callbacks = Callbacks.NONE,
    private val firstEventTimeoutMs: Long = FIRST_EVENT_TIMEOUT_MS,
) {

    /**
     * Side-effect hooks for persistence (Room usage log, budget store save).
     * Failures inside a hook are swallowed: persistence must never break a run.
     */
    fun interface Callbacks {
        suspend fun onUsage(promptTokens: Int, completionTokens: Int)

        companion object {
            val NONE = Callbacks { _, _ -> }
        }
    }

    private val specs = tools.map { it.spec }
    private val toolsByName = tools.associateBy { it.spec.name }

    /**
     * Runs one user turn. [history] is the already-assembled context
     * (ContextBuilder output); [userMessage] is appended as the final user
     * message. The flow emits until [AgentEvent.Completed],
     * [AgentEvent.Failed], or [AgentEvent.ToolLimitReached] terminates the run.
     */
    fun run(history: List<ChatMessage>, userMessage: String): Flow<AgentEvent> = flow {
        val messages = ArrayList(history)
        messages += ChatMessage(role = "user", content = userMessage)
        val toolSpecs = specs.ifEmpty { null }
        var totalTokens = 0
        var round = 0
        while (true) {
            if (!budget.canSpend()) {
                emit(AgentEvent.Failed(BUDGET_EXHAUSTED_MESSAGE, retryable = false))
                return@flow
            }
            if (round >= MAX_TOOL_ROUNDS) {
                emit(AgentEvent.ToolLimitReached)
                return@flow
            }
            round++
            val state = RoundState()
            try {
                val stream = provider.streamChat(messages, toolSpecs)
                if (round == 1) {
                    collectWithFirstEventWatchdog(stream, state)
                } else {
                    stream.collect { event -> handleEvent(event, state) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirstEventTimeoutException) {
                emit(
                    AgentEvent.Failed(
                        "生成超时（${firstEventTimeoutMs / 1000} 秒内无响应）",
                        retryable = true,
                    ),
                )
                return@flow
            } catch (e: Exception) {
                emit(AgentEvent.Failed(e.message ?: e.toString(), retryable = true))
                return@flow
            }
            totalTokens += state.tokens
            if (state.toolCalls.isEmpty()) {
                emit(AgentEvent.Completed(totalTokens))
                return@flow
            }
            // Feed the assistant's tool-call message back first, then one
            // role "tool" message per call — the ordering the API expects.
            messages += ChatMessage(
                role = "assistant",
                content = state.text.toString(),
                toolCalls = state.toolCalls,
            )
            for (call in state.toolCalls) {
                emit(AgentEvent.ToolStarted(call.name))
                val result = executeTool(call)
                emit(AgentEvent.ToolFinished(call.name, result.success))
                messages += ChatMessage(
                    role = "tool",
                    content = ContextBuilder.truncateToolContent(result.text),
                    toolCallId = call.id,
                )
            }
        }
    }

    /** Mutable per-round collection state shared with [handleEvent]. */
    private class RoundState {
        val text = StringBuilder()
        var toolCalls: List<ToolCallRequest> = emptyList()
        var tokens: Int = 0
    }

    private class ToolExecResult(val text: String, val success: Boolean)

    private class FirstEventTimeoutException : Exception()

    private suspend fun FlowCollector<AgentEvent>.handleEvent(
        event: AIEvent,
        state: RoundState,
    ) {
        when (event) {
            is AIEvent.Content -> {
                state.text.append(event.text)
                emit(AgentEvent.AssistantDelta(event.text))
            }
            is AIEvent.Reasoning -> emit(AgentEvent.ReasoningDelta(event.text))
            is AIEvent.ToolCalls -> state.toolCalls = event.calls
            is AIEvent.Usage -> {
                val tokens = event.promptTokens + event.completionTokens
                state.tokens += tokens
                budget.record(tokens)
                try {
                    callbacks.onUsage(event.promptTokens, event.completionTokens)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Persistence hooks must never break a run.
                }
            }
        }
    }

    /**
     * Collects the first provider round under the first-token watchdog: with
     * no read timeout on the SSE socket a hung connection would sit forever.
     * Throws [FirstEventTimeoutException] when nothing arrives in time.
     */
    private suspend fun FlowCollector<AgentEvent>.collectWithFirstEventWatchdog(
        stream: Flow<AIEvent>,
        state: RoundState,
    ) = coroutineScope {
        val gotAnyEvent = AtomicBoolean(false)
        val watchdog = launch {
            delay(firstEventTimeoutMs)
            if (!gotAnyEvent.get()) throw FirstEventTimeoutException()
        }
        try {
            stream.collect { event ->
                if (gotAnyEvent.compareAndSet(false, true)) watchdog.cancel()
                handleEvent(event, state)
            }
        } finally {
            watchdog.cancel()
        }
    }

    /**
     * Runs one requested tool call, converting every failure (unknown name,
     * malformed arguments JSON, tool exception) into error text for the model
     * instead of aborting the run.
     */
    private suspend fun executeTool(call: ToolCallRequest): ToolExecResult {
        val tool = toolsByName[call.name]
            ?: return ToolExecResult("error: unknown tool \"${call.name}\"", success = false)
        val arguments = try {
            JSONObject(call.argumentsJson.ifBlank { "{}" })
        } catch (e: Exception) {
            return ToolExecResult("error: invalid arguments JSON", success = false)
        }
        return try {
            ToolExecResult(tool.execute(arguments), success = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolExecResult("error: ${e.message ?: e.toString()}", success = false)
        }
    }

    companion object {
        /** Hard cap on provider rounds per run (tool-call loop bound). */
        const val MAX_TOOL_ROUNDS = 5

        /** First-round watchdog: mirrors the M3 panel's 60 s first-token rule. */
        const val FIRST_EVENT_TIMEOUT_MS = 60_000L

        const val BUDGET_EXHAUSTED_MESSAGE = "达到日 token 预算上限"
    }
}
