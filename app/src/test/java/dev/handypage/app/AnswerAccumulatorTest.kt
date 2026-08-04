package dev.handypage.app

import dev.handypage.app.agent.AnswerAccumulator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for the M32 [AnswerAccumulator]: only the final tool round's
 * text is the answer; earlier rounds' text (tool-call preamble, stray JSON)
 * is fallback only.
 */
class AnswerAccumulatorTest {

    @Test
    fun `single round text is the answer`() {
        val acc = AnswerAccumulator()
        acc.onDelta("你")
        acc.onDelta("好")
        assertEquals("你好", acc.finalText())
    }

    @Test
    fun `tool round preamble is dropped from the answer`() {
        val acc = AnswerAccumulator()
        acc.onDelta("我来调用 search_articles，参数 {\"query\":\"ai\"}")
        acc.onToolRoundEnded()
        assertEquals("", acc.currentText())
        acc.onDelta("推荐如下：")
        assertEquals("推荐如下：", acc.finalText())
    }

    @Test
    fun `multiple tool rounds keep only the last round`() {
        val acc = AnswerAccumulator()
        acc.onDelta("round1")
        acc.onToolRoundEnded()
        acc.onDelta("round2")
        acc.onToolRoundEnded()
        acc.onDelta("final")
        assertEquals("final", acc.finalText())
    }

    @Test
    fun `empty final round falls back to earlier preamble`() {
        val acc = AnswerAccumulator()
        acc.onDelta("唯一文本")
        acc.onToolRoundEnded()
        assertEquals("唯一文本", acc.finalText())
    }

    @Test
    fun `blank preamble is not kept as fallback`() {
        val acc = AnswerAccumulator()
        acc.onDelta("  ")
        acc.onToolRoundEnded()
        assertEquals("", acc.finalText())
    }
}
