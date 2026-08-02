package dev.handypage.app

import dev.handypage.app.agent.ContextBuilder
import dev.handypage.app.ai.ChatMessage
import dev.handypage.app.ai.Prompts
import dev.handypage.app.ai.ToolCallRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the pure context assembler (budgets, ordering, tool caps). */
class ContextBuilderTest {

    private fun user(text: String) = ChatMessage(role = "user", content = text)

    @Test
    fun `system message comes first and embeds the article`() {
        val result = ContextBuilder.build(
            systemPrompt = "SYS",
            articleText = "The article body.",
            history = listOf(user("hello")),
        )
        assertEquals("system", result[0].role)
        assertTrue(result[0].content.startsWith("SYS"))
        assertTrue(result[0].content.contains("The article body."))
        assertEquals(listOf("hello"), result.drop(1).map { it.content })
    }

    @Test
    fun `article beyond the cap is truncated with the marker`() {
        val article = "a".repeat(ContextBuilder.ARTICLE_MAX_CHARS + 500)
        val system = ContextBuilder.build("SYS", article, emptyList())[0].content
        assertTrue(system.contains(Prompts.TRUNCATED_MARKER))
        val bodyStart = system.indexOf("aaaa")
        val markerStart = system.indexOf(Prompts.TRUNCATED_MARKER, startIndex = bodyStart)
        assertEquals(ContextBuilder.ARTICLE_MAX_CHARS, markerStart - bodyStart)
    }

    @Test
    fun `blank article leaves the system prompt untouched`() {
        assertEquals(
            "SYS",
            ContextBuilder.build("SYS", null, emptyList())[0].content,
        )
        assertEquals(
            "SYS",
            ContextBuilder.build("SYS", "   ", emptyList())[0].content,
        )
    }

    @Test
    fun `history is kept newest-first within the char budget`() {
        val m1 = user("a".repeat(4000)) // oldest — dropped
        val m2 = user("b".repeat(4000))
        val m3 = user("c".repeat(4000)) // newest
        val result = ContextBuilder.build("SYS", null, listOf(m1, m2, m3))
        assertEquals(listOf(m2, m3), result.drop(1))
        // A history exactly at the budget is fully kept.
        val exact = ContextBuilder.build("SYS", null, listOf(m2, m3))
        assertEquals(listOf(m2, m3), exact.drop(1))
    }

    @Test
    fun `newest message overflowing the whole budget is truncated, not dropped`() {
        val huge = user("x".repeat(ContextBuilder.HISTORY_BUDGET_CHARS + 1000))
        val result = ContextBuilder.build("SYS", null, listOf(huge))
        assertEquals(2, result.size)
        assertEquals(ContextBuilder.HISTORY_BUDGET_CHARS, result[1].content.length)
    }

    @Test
    fun `single tool message content is capped at 2000 chars`() {
        val assistant = ChatMessage(
            role = "assistant",
            content = "",
            toolCalls = listOf(ToolCallRequest("call_1", "lookup_word", "{}")),
        )
        val tool = ChatMessage(
            role = "tool",
            content = "t".repeat(5000),
            toolCallId = "call_1",
        )
        val result = ContextBuilder.build("SYS", null, listOf(assistant, tool))
        assertEquals(3, result.size)
        assertEquals(ContextBuilder.TOOL_CONTENT_MAX_CHARS, result[2].content.length)
        assertTrue(result[2].content.endsWith(Prompts.TRUNCATED_MARKER))
    }

    @Test
    fun `budget cut drops an orphan tool result at the front`() {
        val assistant = ChatMessage(
            role = "assistant",
            content = "x".repeat(5000),
            toolCalls = listOf(ToolCallRequest("call_1", "lookup_word", "{}")),
        )
        val tool = ChatMessage(role = "tool", content = "y".repeat(1000), toolCallId = "call_1")
        val newest = user("z".repeat(3000))
        // newest(3000) + tool(1000) fit; the 5000-char assistant message does
        // not, which would orphan the tool result at the front.
        val result = ContextBuilder.build("SYS", null, listOf(assistant, tool, newest))
        assertEquals(listOf(newest), result.drop(1))
        assertFalse(result.drop(1).any { it.role == "tool" })
    }

    @Test
    fun `assistant messages with tool calls survive with their results`() {
        val assistant = ChatMessage(
            role = "assistant",
            content = "查一下。",
            toolCalls = listOf(ToolCallRequest("call_1", "lookup_word", "{}")),
        )
        val tool = ChatMessage(role = "tool", content = "died /daɪd/", toolCallId = "call_1")
        val result = ContextBuilder.build("SYS", null, listOf(assistant, tool))
        assertEquals(listOf(assistant, tool), result.drop(1))
    }
}
