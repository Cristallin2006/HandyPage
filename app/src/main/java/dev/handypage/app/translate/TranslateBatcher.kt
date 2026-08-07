package dev.handypage.app.translate

import dev.handypage.app.ai.ChatMessage

/** One paragraph to translate, addressed by its in-page index. */
data class TranslateUnit(
    val index: Int,
    val text: String,
)

/**
 * Pure batching/prompt/parsing logic of the bilingual paper reader
 * (android-free, JVM-testable). The wire format is numbered paragraphs —
 * `[1] text` in, `[1] 译文` out — which survives every provider's quirks
 * better than JSON output and degrades gracefully: a malformed reply just
 * loses the batch, never the whole paper.
 */
object TranslateBatcher {

    /**
     * Source-text budget per batch (≈2 000 tokens in). M34: raised from
     * 2 000 — small batches multiply round trips, and every round trip used
     * to pay the model's thinking-phase latency; with thinking disabled and
     * 4-lane concurrency, bigger batches cut a paper from ~38 requests to ~10.
     */
    const val MAX_BATCH_CHARS = 8000

    /** Paragraph-count budget per batch: bounds the blast radius of a bad reply. */
    const val MAX_BATCH_PARAS = 20

    val SYSTEM_PROMPT =
        "你是学术论文翻译引擎，服务于英语学习者。将用户提供的编号段落翻译成简体中文，要求：\n" +
            "1. 逐段翻译，按原有编号 [n] 输出，编号数量和顺序与输入完全一致；\n" +
            "2. $...$ 包裹的数学公式、变量与符号一律原样保留，不得翻译或改写；\n" +
            "3. 模型名、数据集名、专有名词保留英文原文，首次出现可括号加注中文；\n" +
            "4. 译文准确流畅、符合学术表达；\n" +
            "5. 只输出编号译文，不要任何解释或额外内容。"

    /**
     * Groups [units] into batches honouring [MAX_BATCH_CHARS] and
     * [MAX_BATCH_PARAS]. A paragraph longer than the char budget forms its
     * own oversized batch (never split mid-paragraph: the model needs the
     * full context, and the cache keys are per paragraph anyway).
     */
    fun batch(units: List<TranslateUnit>): List<List<TranslateUnit>> {
        val batches = mutableListOf<MutableList<TranslateUnit>>()
        var current = mutableListOf<TranslateUnit>()
        var chars = 0
        for (unit in units) {
            val fits = current.isNotEmpty() &&
                chars + unit.text.length <= MAX_BATCH_CHARS &&
                current.size < MAX_BATCH_PARAS
            if (!fits) {
                if (current.isNotEmpty()) batches += current
                current = mutableListOf()
                chars = 0
            }
            current += unit
            chars += unit.text.length
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    /** Chat messages for one batch: the system instruction + numbered input. */
    fun buildMessages(batch: List<TranslateUnit>): List<ChatMessage> = listOf(
        ChatMessage(role = "system", content = SYSTEM_PROMPT),
        ChatMessage(role = "user", content = buildInput(batch)),
    )

    /** `[n] text` lines, blank-line separated. */
    fun buildInput(batch: List<TranslateUnit>): String =
        batch.joinToString("\n\n") { "[${it.index}] ${it.text.trim()}" }

    /** Matches `[3]` markers at line starts (models love indenting them). */
    private val MARKER = Regex("""(?m)^\s*\[(\d+)]\s*""")

    /**
     * Splits a reply into per-index translations. @return null when ANY
     * expected index is missing or blank — the caller then retries or falls
     * back to single-paragraph translation, so a half-parsed batch never
     * poisons the cache.
     */
    fun parseReply(reply: String, expectedIndices: List<Int>): Map<Int, String>? {
        val markers = MARKER.findAll(reply).toList()
        if (markers.isEmpty()) return null
        val parsed = HashMap<Int, String>()
        for ((i, marker) in markers.withIndex()) {
            val index = marker.groupValues[1].toIntOrNull() ?: continue
            val end = if (i + 1 < markers.size) markers[i + 1].range.first else reply.length
            val text = reply.substring(marker.range.last + 1, end).trim()
            if (text.isNotEmpty()) parsed[index] = text
        }
        val result = HashMap<Int, String>()
        for (index in expectedIndices) {
            result[index] = parsed[index] ?: return null
        }
        return result
    }
}
