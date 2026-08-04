package dev.handypage.app.agent

/**
 * M32: merges streamed assistant text across tool rounds of one agent run.
 *
 * Pre-M32 the controller concatenated EVERY round's deltas into the final
 * bubble, so a model's round-1 preamble ("我来调用 search_articles，参数
 * {…}") — or worse, stray JSON the model typed instead of using the
 * tool-call channel — ended up persisted and displayed as part of the
 * answer. Only the FINAL round's text is the answer; earlier rounds' text
 * is kept solely as a fallback for the degenerate case where the model
 * calls tools and then answers with nothing.
 */
class AnswerAccumulator {

    private val current = StringBuilder()
    private var earlier: String? = null

    fun onDelta(text: String) {
        current.append(text)
    }

    /** The text of the round currently in flight (for the streaming UI). */
    fun currentText(): String = current.toString()

    /** Marks the end of a tool round: its text is preamble, not the answer. */
    fun onToolRoundEnded() {
        if (current.isNotBlank()) earlier = current.toString()
        current.clear()
    }

    /** The final answer: last round's text, falling back to earlier preamble. */
    fun finalText(): String = current.toString().ifBlank { earlier.orEmpty() }
}
