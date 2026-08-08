package dev.handypage.app

import dev.handypage.app.agent.AgentEvent
import dev.handypage.app.agent.AgentRunner
import dev.handypage.app.agent.AgentTool
import dev.handypage.app.agent.ContextBuilder
import dev.handypage.app.agent.DailyBudget
import dev.handypage.app.ai.AIEvent
import dev.handypage.app.ai.AIProvider
import dev.handypage.app.ai.ChatMessage
import dev.handypage.app.ai.ToolCallRequest
import dev.handypage.app.ai.ToolSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the agent loop against a scripted fake provider: plain Q&A,
 * one tool round, the 5-round tool cap, the budget gate, and the first-round
 * watchdog.
 */
class AgentRunnerTest {

    /** Provider replaying a per-round script and recording every call. */
    private class FakeProvider(
        private val script: (round: Int, messages: List<ChatMessage>) -> List<AIEvent>,
    ) : AIProvider {
        override val name = "fake"
        val calls = mutableListOf<List<ChatMessage>>()

        override fun streamChat(
            messages: List<ChatMessage>,
            tools: List<ToolSpec>?,
            disableThinking: Boolean,
        ): Flow<AIEvent> = flow {
            calls += messages.toList()
            for (event in script(calls.size - 1, messages)) emit(event)
        }
    }

    /** Provider that sleeps before its first event (watchdog target). */
    private class SlowProvider(private val delayMs: Long) : AIProvider {
        override val name = "slow"

        override fun streamChat(
            messages: List<ChatMessage>,
            tools: List<ToolSpec>?,
            disableThinking: Boolean,
        ): Flow<AIEvent> = flow {
            delay(delayMs)
            emit(AIEvent.Content("late"))
        }
    }

    private class FakeTool(
        override val spec: ToolSpec,
        private val result: String = "tool-result",
    ) : AgentTool {
        val invocations = mutableListOf<JSONObject>()

        override suspend fun execute(arguments: JSONObject): String {
            invocations += arguments
            return result
        }
    }

    private fun echoTool(result: String = "tool-result") = FakeTool(
        ToolSpec("echo", "echo back", JSONObject().put("type", "object")),
        result,
    )

    @Test
    fun `plain Q&A completes with usage recorded`() = runBlocking {
        val provider = FakeProvider { _, _ ->
            listOf(AIEvent.Content("你"), AIEvent.Content("好"), AIEvent.Usage(10, 5))
        }
        val budget = DailyBudget(limit = 1000)
        val usages = mutableListOf<Pair<Int, Int>>()
        val runner = AgentRunner(
            provider, emptyList(), budget,
            callbacks = { p, c -> usages += p to c },
        )
        val events = runner.run(emptyList(), "hi").toList()
        assertEquals(
            listOf(
                AgentEvent.AssistantDelta("你"),
                AgentEvent.AssistantDelta("好"),
                AgentEvent.Completed(15),
            ),
            events,
        )
        assertEquals(15, budget.usedToday)
        assertEquals(listOf(10 to 5), usages)
        assertEquals(listOf("user"), provider.calls.single().map { it.role })
    }

    @Test
    fun `one tool round feeds the result back and completes`() = runBlocking {
        val tool = echoTool()
        val provider = FakeProvider { round, _ ->
            when (round) {
                0 -> listOf(
                    AIEvent.ToolCalls(
                        listOf(ToolCallRequest("call_1", "echo", "{\"word\":\"hi\"}")),
                    ),
                    AIEvent.Usage(3, 2),
                )
                else -> listOf(AIEvent.Content("done"), AIEvent.Usage(4, 6))
            }
        }
        val events = AgentRunner(provider, listOf(tool), DailyBudget())
            .run(emptyList(), "hi")
            .toList()
        assertEquals(
            listOf(
                AgentEvent.ToolStarted("echo"),
                AgentEvent.ToolFinished("echo", true),
                AgentEvent.AssistantDelta("done"),
                AgentEvent.Completed(15), // (3+2) + (4+6)
            ),
            events,
        )
        assertEquals("hi", tool.invocations.single().getString("word"))
        // Round 2 saw the assistant tool_calls message then the tool result.
        val roundTwo = provider.calls[1]
        val assistant = roundTwo[roundTwo.size - 2]
        assertEquals("assistant", assistant.role)
        assertEquals("call_1", assistant.toolCalls.single().id)
        val toolMessage = roundTwo.last()
        assertEquals("tool", toolMessage.role)
        assertEquals("call_1", toolMessage.toolCallId)
        assertEquals("tool-result", toolMessage.content)
    }

    @Test
    fun `persistent tool demands hit the 5-round cap`() = runBlocking {
        val provider = FakeProvider { _, _ ->
            listOf(AIEvent.ToolCalls(listOf(ToolCallRequest("c", "echo", "{}"))))
        }
        val events = AgentRunner(provider, listOf(echoTool()), DailyBudget())
            .run(emptyList(), "hi")
            .toList()
        assertEquals(5, provider.calls.size)
        assertEquals(AgentEvent.ToolLimitReached, events.last())
        assertEquals(5, events.filterIsInstance<AgentEvent.ToolStarted>().size)
        assertEquals(5, events.filterIsInstance<AgentEvent.ToolFinished>().size)
        assertTrue(events.none { it is AgentEvent.Completed })
    }

    @Test
    fun `paper-mode cap allows twelve rounds before the limit`() = runBlocking {
        // M39: paged paper-section reads legitimately need more than 5 rounds.
        val provider = FakeProvider { _, _ ->
            listOf(AIEvent.ToolCalls(listOf(ToolCallRequest("c", "echo", "{}"))))
        }
        val events = AgentRunner(
            provider, listOf(echoTool()), DailyBudget(),
            maxToolRounds = AgentRunner.PAPER_MAX_TOOL_ROUNDS,
        ).run(emptyList(), "hi").toList()
        assertEquals(AgentRunner.PAPER_MAX_TOOL_ROUNDS, provider.calls.size)
        assertEquals(AgentEvent.ToolLimitReached, events.last())
        assertEquals(
            AgentRunner.PAPER_MAX_TOOL_ROUNDS,
            events.filterIsInstance<AgentEvent.ToolStarted>().size,
        )
        assertTrue(events.none { it is AgentEvent.Completed })
    }

    @Test
    fun `exhausted budget refuses the call without hitting the provider`() = runBlocking {
        val provider = FakeProvider { _, _ -> listOf(AIEvent.Content("never")) }
        val events = AgentRunner(
            provider, emptyList(),
            DailyBudget(limit = 100, usedToday = 100),
        ).run(emptyList(), "hi").toList()
        assertEquals(
            listOf(AgentEvent.Failed("达到日 token 预算上限", retryable = false)),
            events,
        )
        assertTrue(provider.calls.isEmpty())
    }

    @Test
    fun `first-round watchdog fails visibly when no event arrives`() = runBlocking {
        val runner = AgentRunner(
            SlowProvider(delayMs = 5_000),
            emptyList(),
            DailyBudget(),
            firstEventTimeoutMs = 200,
        )
        val events = runner.run(emptyList(), "hi").toList()
        val failure = events.single()
        assertTrue(failure is AgentEvent.Failed && failure.retryable)
    }

    @Test
    fun `unknown tool name is reported back to the model as error text`() = runBlocking {
        val provider = FakeProvider { round, _ ->
            when (round) {
                0 -> listOf(AIEvent.ToolCalls(listOf(ToolCallRequest("c", "nope", "{}"))))
                else -> listOf(AIEvent.Content("ok"))
            }
        }
        val events = AgentRunner(provider, listOf(echoTool()), DailyBudget())
            .run(emptyList(), "hi")
            .toList()
        assertEquals(
            listOf(
                AgentEvent.ToolStarted("nope"),
                AgentEvent.ToolFinished("nope", false),
                AgentEvent.AssistantDelta("ok"),
                AgentEvent.Completed(0),
            ),
            events,
        )
        assertTrue(provider.calls[1].last().content.contains("unknown tool"))
    }

    @Test
    fun `tool results are capped at 2000 chars before going back to the model`() = runBlocking {
        val tool = echoTool(result = "x".repeat(5000))
        val provider = FakeProvider { round, _ ->
            when (round) {
                0 -> listOf(AIEvent.ToolCalls(listOf(ToolCallRequest("c", "echo", "{}"))))
                else -> listOf(AIEvent.Content("ok"))
            }
        }
        AgentRunner(provider, listOf(tool), DailyBudget()).run(emptyList(), "hi").toList()
        assertEquals(
            ContextBuilder.TOOL_CONTENT_MAX_CHARS,
            provider.calls[1].last().content.length,
        )
    }
}
