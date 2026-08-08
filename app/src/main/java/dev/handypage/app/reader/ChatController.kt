package dev.handypage.app.reader

import android.content.Context
import dev.handypage.app.HandypageApp
import dev.handypage.app.agent.AgentEvent
import dev.handypage.app.agent.AgentPreferencesStore
import dev.handypage.app.agent.AgentRunner
import dev.handypage.app.agent.AgentTool
import dev.handypage.app.agent.ArticleTextTool
import dev.handypage.app.agent.ChatMessageEntity
import dev.handypage.app.agent.ContextBuilder
import dev.handypage.app.handypageHttpClient
import dev.handypage.app.agent.DailyBudgetStore
import dev.handypage.app.agent.GetPreferencesTool
import dev.handypage.app.agent.LookupWordTool
import dev.handypage.app.agent.PaperIndex
import dev.handypage.app.agent.PaperOutlineTool
import dev.handypage.app.agent.ReadPaperSectionTool
import dev.handypage.app.agent.SavePreferenceTool
import dev.handypage.app.agent.SaveVocabTool
import dev.handypage.app.agent.SearchArticlesTool
import dev.handypage.app.agent.SearchInPaperTool
import dev.handypage.app.agent.SearchPapersTool
import dev.handypage.app.agent.UsageLog
import dev.handypage.app.Sources
import dev.handypage.app.arxiv.ArxivApi
import dev.handypage.app.ai.AIFactory
import dev.handypage.app.ai.ChatMessage
import dev.handypage.app.ai.Prompts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Glue between the reader's chat drawer (Compose) and the M5 agent core
 * (DESIGN.md §4.9). Owns one article-scoped session: it loads/persists
 * messages via Room, assembles the context through [ContextBuilder], runs
 * [AgentRunner], and exposes a single [state] flow for the panel.
 *
 * Plain class (not an AAC ViewModel): its lifetime is the hosting
 * [ReaderFragment]'s view lifecycle, and [scope] is the fragment's
 * viewLifecycleOwner scope, so everything is cancelled with the view.
 */
class ChatController(
    private val app: HandypageApp,
    context: Context,
    private val articleKey: String,
    private val articleTitle: String,
    private val articleUrl: String,
    private val sourceName: String,
    private val articleTextProvider: suspend () -> String,
    /**
     * Whether the full article text actually exists right now. The paper
     * reader's reflow EPUB is converted in the background, so the
     * ArticleTextTool must stay unregistered until it lands; the default
     * keeps the EPUB reader's always-available behavior.
     */
    private val articleTextAvailable: () -> Boolean = { true },
    /**
     * M33: when non-null (paper reader), the agent gets a [PaperIndex]
     * instead of a truncated text dump — the system prompt carries abstract
     * + outline, and the outline/section/search tools replace
     * ArticleTextTool.
     */
    private val paperIndexProvider: (suspend () -> PaperIndex?)? = null,
    private val scope: CoroutineScope,
) {

    /** One displayable message (user or assistant; tool exchanges are transient). */
    data class UiMessage(val id: Long, val role: String, val text: String)

    data class UiState(
        /** Session row created and history loaded; input stays disabled until then. */
        val ready: Boolean = false,
        val messages: List<UiMessage> = emptyList(),
        /** In-flight assistant answer, rendered separately until Completed. */
        val streamingText: String = "",
        val busy: Boolean = false,
        /** Thinking-mode model is streaming reasoning_content. */
        val thinking: Boolean = false,
        /** Name of the tool currently executing, or null. */
        val toolRunning: String? = null,
        val toolLimitNotice: Boolean = false,
        val error: String? = null,
        val errorRetryable: Boolean = false,
        /** No usable BYOK key: the panel offers a 去设置 action instead. */
        val noKey: Boolean = false,
    )

    val state = MutableStateFlow(UiState())

    private val appContext = context.applicationContext
    private val chatDao = app.vocabDb.chatDao()
    private val usageDao = app.vocabDb.usageDao()
    private val budgetStore = DailyBudgetStore(appContext)

    private var sessionId: Long = 0
    /** user/assistant pairs replayed into the context on every run. */
    private val history = ArrayList<ChatMessage>()
    private var runJob: Job? = null
    private var articleTextCache: String? = null
    /** M33: built once per session; a null build (conversion pending) is not cached. */
    private var paperIndexCache: PaperIndex? = null
    private var lastPrompt: String? = null
    private var lastDisplay: String = ""

    /** M33: lazily builds and caches the paper index (null until ready). */
    private suspend fun paperIndex(): PaperIndex? =
        paperIndexCache ?: paperIndexProvider?.invoke()?.also { if (!it.isEmpty) paperIndexCache = it }

    /** Creates/resumes the session and starts observing its messages. */
    fun start() {
        scope.launch {
            val session = withContext(Dispatchers.IO) {
                chatDao.getOrCreateSession(
                    articleKey = articleKey,
                    title = articleTitle,
                    now = System.currentTimeMillis(),
                )
            }
            sessionId = session.id
            // Seed the context history once from what is persisted (only
            // user/assistant rows are ever stored; tool rows stay transient).
            val persisted = withContext(Dispatchers.IO) {
                chatDao.observeMessages(session.id).first()
            }
            history.clear()
            persisted.forEach { history += ChatMessage(role = it.role, content = it.content) }

            chatDao.observeMessages(session.id).collect { entities ->
                state.update {
                    it.copy(
                        ready = true,
                        messages = entities.map { e -> UiMessage(e.id, e.role, e.content) },
                    )
                }
            }
        }
    }

    /**
     * Sends one user turn. [prompt] is what the model (and future context)
     * sees; [display] is what the chat bubble and Room row show, so template
     * prompts (导读/讲句子/问 AI) stay readable in the UI.
     */
    fun send(prompt: String, display: String = prompt) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || state.value.busy || !state.value.ready) return
        if (AIFactory.fromSettings(appContext) == null) {
            state.update { it.copy(noKey = true, error = NO_KEY_MESSAGE) }
            return
        }
        lastPrompt = trimmed
        lastDisplay = display
        run(trimmed, display, insertUserMessage = true)
    }

    /** Re-runs the last prompt after a retryable failure (no duplicate user row). */
    fun retry() {
        val prompt = lastPrompt ?: return
        if (state.value.busy) return
        run(prompt, lastDisplay, insertUserMessage = false)
    }

    /** Cancels the in-flight run; the partial answer is discarded. */
    fun stop() {
        runJob?.cancel()
        state.update { it.copy(busy = false, thinking = false, toolRunning = null, streamingText = "") }
    }

    private fun run(prompt: String, display: String, insertUserMessage: Boolean) {
        val provider = AIFactory.fromSettings(appContext) ?: return
        runJob = scope.launch {
            state.update {
                it.copy(
                    busy = true, error = null, streamingText = "",
                    thinking = false, toolLimitNotice = false,
                )
            }
            if (insertUserMessage) {
                withContext(Dispatchers.IO) {
                    chatDao.insertMessage(
                        ChatMessageEntity(
                            sessionId = sessionId, role = "user", content = display,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                    chatDao.touchSession(sessionId, System.currentTimeMillis())
                }
                history += ChatMessage(role = "user", content = prompt)
            }

            val budget = budgetStore.load()
            val runner = AgentRunner(
                provider = provider,
                tools = buildTools(),
                budget = budget,
                // M39: paper mode reads sections in paged tool calls — give
                // the loop the wider paper budget, news keeps the tight one.
                maxToolRounds = if (paperIndexProvider != null) {
                    AgentRunner.PAPER_MAX_TOOL_ROUNDS
                } else {
                    AgentRunner.MAX_TOOL_ROUNDS
                },
                callbacks = AgentRunner.Callbacks { promptTokens, completionTokens ->
                    budgetStore.save(budget)
                    withContext(Dispatchers.IO) {
                        usageDao.insert(
                            UsageLog(
                                promptTokens = promptTokens,
                                completionTokens = completionTokens,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                },
            )
            // M33: paper mode inlines abstract + outline instead of a
            // truncated full-text dump; navigation happens via the tools.
            val index = paperIndex()
            val articleText = if (index != null) {
                buildString {
                    if (index.abstractText.isNotBlank()) {
                        append("【论文摘要】\n").append(index.abstractText)
                    }
                    append("\n\n【论文大纲】\n").append(index.outline())
                }
            } else {
                articleTextCache
                    ?: articleTextProvider().also { if (it.isNotBlank()) articleTextCache = it }
            }
            val context = ContextBuilder.build(
                systemPrompt = Prompts.agentSystem(
                    articleTitle,
                    paperMode = paperIndexProvider != null,
                ),
                articleText = articleText,
                history = history.dropLast(1),
            )

            val answer = dev.handypage.app.agent.AnswerAccumulator()
            runner.run(context, prompt).collect { event ->
                when (event) {
                    is AgentEvent.AssistantDelta -> {
                        answer.onDelta(event.text)
                        state.update {
                            it.copy(thinking = false, streamingText = answer.currentText())
                        }
                    }
                    is AgentEvent.ReasoningDelta ->
                        state.update { it.copy(thinking = true) }
                    is AgentEvent.ToolStarted -> {
                        // M32: the round that just ended was tool-call preamble;
                        // its text must not reach the final bubble.
                        answer.onToolRoundEnded()
                        state.update { it.copy(toolRunning = event.name, streamingText = "") }
                    }
                    is AgentEvent.ToolFinished ->
                        state.update { it.copy(toolRunning = null) }
                    is AgentEvent.Completed -> finishRun(answer.finalText(), toolLimit = false)
                    AgentEvent.ToolLimitReached -> finishRun(answer.finalText(), toolLimit = true)
                    is AgentEvent.Failed -> state.update {
                        it.copy(
                            busy = false, thinking = false, toolRunning = null,
                            streamingText = "",
                            error = event.message, errorRetryable = event.retryable,
                        )
                    }
                }
            }
        }
    }

    /** Persists the final assistant answer (even a partial one) and settles the UI. */
    private suspend fun finishRun(text: String, toolLimit: Boolean) {
        if (text.isNotBlank()) {
            history += ChatMessage(role = "assistant", content = text)
            withContext(Dispatchers.IO) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId, role = "assistant", content = text,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                chatDao.touchSession(sessionId, System.currentTimeMillis())
            }
        }
        state.update {
            it.copy(
                busy = false, thinking = false, toolRunning = null,
                streamingText = "", toolLimitNotice = toolLimit,
            )
        }
    }

    private fun buildTools(): List<AgentTool> = buildList {
        add(LookupWordTool(dictionaryProvider = { app.requireDictionary() }))
        add(
            SaveVocabTool(
                vocabDao = app.vocabDb.vocabWordDao(),
                articleUrl = articleUrl,
                sourceName = sourceName,
                dictionaryProvider = { app.requireDictionary() },
            ),
        )
        // The global Agent-tab session has no article; exposing the tool
        // would only invite a hallucinated call against empty text. The
        // paper reader registers it only once the reflow EPUB exists.
        if (articleUrl.isNotBlank() && articleTextAvailable()) {
            if (paperIndexProvider != null) {
                // M33: paper memory tools replace the truncated text dump.
                add(PaperOutlineTool(indexProvider = { paperIndex() }))
                add(ReadPaperSectionTool(indexProvider = { paperIndex() }))
                add(SearchInPaperTool(indexProvider = { paperIndex() }))
            } else {
                add(
                    ArticleTextTool(textProvider = {
                        articleTextCache
                            ?: articleTextProvider().also { if (it.isNotBlank()) articleTextCache = it }
                    }),
                )
            }
        }
        // M16: recommendation tools are only registered for the global
        // Agent-tab session (no article context needed).
        if (articleUrl.isBlank()) {
            val prefsStore = AgentPreferencesStore(appContext)
            add(SearchArticlesTool(
                engine = app.engine,
                sourcesProvider = { Sources.loadAll(appContext) },
                preferencesStore = prefsStore,
            ))
            add(SearchPapersTool(
                api = ArxivApi(handypageHttpClient()),
                preferencesStore = prefsStore,
            ))
            add(SavePreferenceTool(preferencesStore = prefsStore))
            add(GetPreferencesTool(preferencesStore = prefsStore))
        }
    }

    private companion object {
        const val NO_KEY_MESSAGE = "未配置 API key"
    }
}
