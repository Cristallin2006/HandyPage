package dev.handypage.app.agent

import dev.handypage.app.ai.ChatMessage
import dev.handypage.app.ai.Prompts

/**
 * Assembles the outgoing message list for one agent run (DESIGN.md §4.9):
 * the system prompt with the article body inlined as untrusted reference
 * data, plus as much recent history as fits the character budget.
 *
 * Pure Kotlin (no android imports) so every boundary is JVM-testable.
 */
object ContextBuilder {

    /** Article body inlined into the system message is capped at this many chars. */
    const val ARTICLE_MAX_CHARS = Prompts.GUIDE_MAX_CHARS

    /** Total content characters kept from the conversation history. */
    const val HISTORY_BUDGET_CHARS = 8_000

    /**
     * A single role "tool" message's content is capped at this many chars.
     * M33: raised 2 000 → 4 000 so paper-section reads ([PaperIndex]'s
     * READ_WINDOW_CHARS) survive intact; the 8 000-char history budget
     * still bounds the total.
     */
    const val TOOL_CONTENT_MAX_CHARS = 4_000

    fun build(
        systemPrompt: String,
        articleText: String?,
        history: List<ChatMessage>,
    ): List<ChatMessage> {
        val system = StringBuilder(systemPrompt)
        val article = articleText?.trim().orEmpty()
        if (article.isNotEmpty()) {
            val truncated = article.length > ARTICLE_MAX_CHARS
            system.append("\n\n以下是用户正在阅读的文章正文")
            if (truncated) system.append("（过长，已截断，结尾处有 ${Prompts.TRUNCATED_MARKER} 标记）")
            system.append("：\n")
            if (truncated) {
                system.append(article, 0, ARTICLE_MAX_CHARS).append(Prompts.TRUNCATED_MARKER)
            } else {
                system.append(article)
            }
        }

        // Walk history newest-first, keeping a contiguous suffix whose total
        // content length stays within the budget.
        val kept = ArrayDeque<ChatMessage>()
        var remaining = HISTORY_BUDGET_CHARS
        for (message in history.asReversed()) {
            val capped = message.withToolContentCapped()
            if (capped.content.length > remaining) {
                if (kept.isEmpty() && capped.content.isNotEmpty()) {
                    // Even the newest message overflows the whole budget:
                    // keep its head rather than dropping it silently.
                    kept.addFirst(capped.copy(content = capped.content.take(remaining)))
                }
                break
            }
            remaining -= capped.content.length
            kept.addFirst(capped)
        }
        // A budget cut may land mid tool exchange; orphan tool results at
        // the front would be rejected by the chat-completions API.
        while (kept.firstOrNull()?.role == "tool") kept.removeFirst()

        return listOf(ChatMessage(role = "system", content = system.toString())) + kept
    }

    /**
     * Caps one tool-result text at [TOOL_CONTENT_MAX_CHARS]; the truncation
     * marker is part of the budget so the result never exceeds the cap.
     */
    fun truncateToolContent(text: String): String =
        if (text.length <= TOOL_CONTENT_MAX_CHARS) {
            text
        } else {
            text.take(TOOL_CONTENT_MAX_CHARS - Prompts.TRUNCATED_MARKER.length) +
                Prompts.TRUNCATED_MARKER
        }

    private fun ChatMessage.withToolContentCapped(): ChatMessage =
        if (role == "tool" && content.length > TOOL_CONTENT_MAX_CHARS) {
            copy(content = truncateToolContent(content))
        } else {
            this
        }
}
