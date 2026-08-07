package dev.handypage.app

import dev.handypage.app.translate.TranslateBatcher
import dev.handypage.app.translate.TranslateUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the bilingual reader's batch wire format: batching budgets,
 * the numbered input/prompt shape, and the `[n]` reply parser including its
 * failure contract (null -> caller retries / falls back to singles).
 */
class TranslateBatcherTest {

    private fun unit(i: Int, len: Int = 50) = TranslateUnit(i, "x".repeat(len))

    @Test
    fun `batches respect the paragraph count budget`() {
        val units = (0 until 45).map { unit(it, len = 10) }
        val batches = TranslateBatcher.batch(units)
        assertEquals(3, batches.size)
        assertTrue(batches.all { it.size <= TranslateBatcher.MAX_BATCH_PARAS })
        assertEquals(units, batches.flatten())
    }

    @Test
    fun `batches respect the char budget`() {
        // Sized cap-relative: two units per batch (2×len ≤ cap, 3×len > cap).
        val len = TranslateBatcher.MAX_BATCH_CHARS / 2 - 50
        val units = (0 until 5).map { unit(it, len = len) }
        val batches = TranslateBatcher.batch(units)
        assertEquals(3, batches.size) // 2+2+1
        assertTrue(
            batches.all { batch ->
                batch.sumOf { it.text.length } <= TranslateBatcher.MAX_BATCH_CHARS || batch.size == 1
            },
        )
    }

    @Test
    fun `an oversized paragraph forms its own batch`() {
        val units = listOf(unit(0, len = TranslateBatcher.MAX_BATCH_CHARS + 500), unit(1, len = 10))
        val batches = TranslateBatcher.batch(units)
        assertEquals(2, batches.size)
        assertEquals(listOf(0), batches[0].map { it.index })
    }

    @Test
    fun `input carries numbered paragraphs and prompt keeps formulas`() {
        val messages = TranslateBatcher.buildMessages(
            listOf(TranslateUnit(3, "Let \$x\$ be small."), TranslateUnit(4, "Done.")),
        )
        assertEquals("system", messages[0].role)
        assertTrue(messages[0].content.contains("\$...\$"))
        assertEquals("user", messages[1].role)
        assertTrue(messages[1].content.contains("[3] Let \$x\$ be small."))
        assertTrue(messages[1].content.contains("[4] Done."))
    }

    @Test
    fun `parseReply splits numbered translations`() {
        val reply =
            "[1] 第一段译文。\n\n[2] 第二段，保留 \$E=mc^2\$。"
        val parsed = TranslateBatcher.parseReply(reply, listOf(1, 2))
        assertEquals(mapOf(1 to "第一段译文。", 2 to "第二段，保留 \$E=mc^2\$。"), parsed)
    }

    @Test
    fun `parseReply tolerates indented markers`() {
        val reply = "  [7] 译文内容"
        val parsed = TranslateBatcher.parseReply(reply, listOf(7))
        assertEquals(mapOf(7 to "译文内容"), parsed)
    }

    @Test
    fun `parseReply returns null on a missing index`() {
        val reply = "[1] 只有一段。"
        assertNull(TranslateBatcher.parseReply(reply, listOf(1, 2)))
    }

    @Test
    fun `parseReply returns null on prose without markers`() {
        assertNull(TranslateBatcher.parseReply("抱歉，我无法翻译。", listOf(1)))
    }
}
