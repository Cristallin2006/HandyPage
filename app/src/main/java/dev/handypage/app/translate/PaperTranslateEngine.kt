package dev.handypage.app.translate

import android.content.Context
import dev.handypage.app.agent.UsageLog
import dev.handypage.app.ai.AIFactory
import dev.handypage.app.ai.AIEvent
import dev.handypage.app.ai.AIException
import dev.handypage.app.ai.AIProvider
import dev.handypage.app.ai.AISettingsStore
import dev.handypage.app.ai.ChatMessage
import dev.handypage.app.vocab.PaperTranslation
import dev.handypage.app.vocab.VocabDatabase
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Drives the bilingual paper reader's BYOK translation pipeline:
 *
 *  1. cache lookup — paragraph hashes already translated under the current
 *     model are emitted immediately (second opens render instantly, fully
 *     offline);
 *  2. the remaining paragraphs are batched ([TranslateBatcher]) and sent
 *     through a fixed worker pool ([WORKER_LANES] lanes, M34 — strictly
 *     sequential sending multiplied the thinking-phase latency into
 *     minutes) — each successful batch is written to Room from its lane as
 *     soon as it lands, so cancelling or crashing mid-paper never loses
 *     finished work;
 *  3. every request runs with thinking disabled (`thinking.type=disabled`,
 *     M34) — translation needs no reasoning phase;
 *  4. token usage is logged char-based estimates: plain chat requests get no
 *     usage chunk from the provider (only tool calls enable it), so the
 *     设置 screen's counter stays honest-ish rather than blind.
 *
 * Cancellation: collecting this flow inside a cancellable scope kills the
 * in-flight HTTP calls via the provider's awaitClose.
 */
class PaperTranslateEngine(
    private val context: Context,
    private val db: VocabDatabase,
) {

    /** One progress event: freshly arrived translations + batch counters. */
    data class Progress(
        val completedBatches: Int,
        val totalBatches: Int,
        /** Translations arrived with THIS event (index -> translated text). */
        val arrived: List<TranslateUnit>,
        val finished: Boolean,
    )

    /** "preset:effectiveModel" — the cache dimension that survives preset edits. */
    fun modelFingerprint(): String {
        val config = AISettingsStore(context).selectedConfig()
        return "${config.presetId}:${config.effectiveModel}"
    }

    /** SHA-1 hex of the whitespace-normalized paragraph text. */
    fun paraHash(text: String): String {
        val normalized = text.replace(WHITESPACE_RUN, " ").trim()
        return MessageDigest.getInstance("SHA-1")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Translates [paragraphs] of paper [paperKey], streaming [Progress]
     * events. Throws [AIException] when no BYOK provider is configured or a
     * batch fails beyond its retry/fallback budget.
     *
     * M34: batches run through a [WORKER_LANES]-lane worker pool instead of
     * strictly sequentially, every batch completes with thinking disabled
     * (the reasoning phase is pure latency for translation), and [onStreamChars]
     * reports throttled in-flight character counts so the UI shows life
     * between batch completions. Finished batches persist to Room from their
     * lane and emit in COMPLETION order.
     */
    fun translate(
        paperKey: String,
        paragraphs: List<TranslateUnit>,
        onStreamChars: (Int) -> Unit = {},
    ): Flow<Progress> = flow {
        val provider = AIFactory.fromSettings(context)
            ?: throw AIException("请先在设置中配置 AI 服务商")
        val model = modelFingerprint()
        val dao = db.paperTranslationDao()

        val hashByIndex = paragraphs.associate { it.index to paraHash(it.text) }
        val cached = dao.cachedFor(paperKey, model, hashByIndex.values.toList())
            .associateBy { it.paraHash }
        val hits = paragraphs.mapNotNull { unit ->
            cached[hashByIndex[unit.index]]?.let { TranslateUnit(unit.index, it.translatedText) }
        }
        val pending = paragraphs.filter { !cached.containsKey(hashByIndex[it.index]) }
        val batches = TranslateBatcher.batch(pending)

        if (batches.isEmpty()) {
            emit(Progress(0, 0, hits, finished = true))
            return@flow
        }
        if (hits.isNotEmpty()) {
            emit(Progress(0, batches.size, hits, finished = false))
        }

        val queue = Channel<List<TranslateUnit>>(Channel.UNLIMITED)
        val doneEvents = Channel<Map<Int, String>>(Channel.UNLIMITED)
        val streamedChars = AtomicInteger(0)
        coroutineScope {
            for (batch in batches) queue.send(batch)
            queue.close()
            repeat(WORKER_LANES) {
                launch(Dispatchers.IO) {
                    for (batch in queue) {
                        val translations = translateBatchWithFallback(provider, batch) { delta ->
                            onStreamChars(streamedChars.addAndGet(delta))
                        }
                        if (translations.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            dao.insertAll(
                                translations.map { (index, text) ->
                                    PaperTranslation(
                                        paperKey = paperKey,
                                        paraHash = hashByIndex.getValue(index),
                                        model = model,
                                        translatedText = text,
                                        createdAt = now,
                                    )
                                },
                            )
                        }
                        doneEvents.send(translations)
                    }
                }
            }
            var done = 0
            repeat(batches.size) {
                val translations = doneEvents.receive()
                done++
                emit(
                    Progress(
                        completedBatches = done,
                        totalBatches = batches.size,
                        arrived = translations.map { (index, text) -> TranslateUnit(index, text) },
                        finished = done == batches.size,
                    ),
                )
            }
        }
    }

    /**
     * One batch with its resilience budget: parse failure → retry once →
     * fall back to single-paragraph requests. Thinking is disabled on every
     * call (M34). [onChars] relays throttled in-flight content counts.
     * @return the successfully translated subset; throws when the provider
     * itself failed and nothing was salvaged.
     */
    private suspend fun translateBatchWithFallback(
        provider: AIProvider,
        batch: List<TranslateUnit>,
        onChars: (Int) -> Unit,
    ): Map<Int, String> {
        var lastError: Exception? = null
        repeat(2) {
            try {
                val reply = chatOnce(provider, TranslateBatcher.buildMessages(batch), onChars)
                val parsed = TranslateBatcher.parseReply(reply, batch.map { it.index })
                if (parsed != null) return parsed
            } catch (e: Exception) {
                lastError = e
            }
        }
        // Degraded path: one request per paragraph salvages what it can.
        val salvaged = HashMap<Int, String>()
        for (unit in batch) {
            try {
                val reply = chatOnce(provider, TranslateBatcher.buildMessages(listOf(unit)), onChars)
                TranslateBatcher.parseReply(reply, listOf(unit.index))?.let { salvaged.putAll(it) }
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (salvaged.isEmpty()) {
            throw lastError ?: AIException("翻译失败")
        }
        return salvaged
    }

    /**
     * One non-interactive round trip: collects the streamed content whole,
     * thinking disabled (M34). [onChars] fires with the number of NEWLY
     * arrived content characters, throttled to ≥120-char steps.
     */
    private suspend fun chatOnce(
        provider: AIProvider,
        messages: List<ChatMessage>,
        onChars: (Int) -> Unit,
    ): String {
        val reply = StringBuilder()
        var sinceReport = 0
        provider.streamChat(messages, disableThinking = true).collect { event ->
            if (event is AIEvent.Content) {
                reply.append(event.text)
                sinceReport += event.text.length
                if (sinceReport >= 120) {
                    onChars(sinceReport)
                    sinceReport = 0
                }
            }
        }
        if (sinceReport > 0) onChars(sinceReport)
        val text = reply.toString().trim()
        if (text.isEmpty()) throw AIException("模型未返回内容")
        // Char-based estimate (no usage chunk on tool-less requests).
        val promptChars = messages.sumOf { it.content.length }
        db.usageDao().insert(
            UsageLog(
                promptTokens = promptChars / 2 + 1,
                completionTokens = text.length / 2 + 1,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return text
    }

    private companion object {
        val WHITESPACE_RUN = Regex("\\s+")

        /** M34: concurrent translation lanes over the batch queue. */
        const val WORKER_LANES = 4
    }
}
