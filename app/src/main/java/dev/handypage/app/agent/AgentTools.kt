package dev.handypage.app.agent

import dev.handypage.app.ai.Prompts
import dev.handypage.app.ai.ToolSpec
import dev.handypage.app.dict.DictEntry
import dev.handypage.app.dict.Dictionary
import dev.handypage.app.vocab.VocabWord
import dev.handypage.app.vocab.VocabWordDao
import org.json.JSONArray
import org.json.JSONObject

/**
 * One tool the agent may call (DESIGN.md §4.9). [execute] returns the text
 * handed back to the model as the role "tool" message; the caller
 * (AgentRunner) caps it at [ContextBuilder.TOOL_CONTENT_MAX_CHARS].
 */
interface AgentTool {
    val spec: ToolSpec

    suspend fun execute(arguments: JSONObject): String
}

/**
 * `lookup_word`: ECDICT lookup via [Dictionary.lookup], which already runs
 * the word-form (inflection/possessive) fallback chain. The dictionary is
 * opened lazily through [dictionaryProvider] so a run that never looks
 * anything up never pays the one-time asset extraction.
 */
class LookupWordTool(
    private val dictionaryProvider: suspend () -> Dictionary,
) : AgentTool {

    override val spec = ToolSpec(
        name = "lookup_word",
        description = "查询内置英语词典，返回音标、词性、中文释义、英文释义和原形。" +
            "任意词形均可（词典自带词形还原）。",
        parametersSchema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().put(
                    "word",
                    JSONObject()
                        .put("type", "string")
                        .put("description", "要查询的英文单词"),
                ),
            )
            .put("required", JSONArray().put("word")),
    )

    override suspend fun execute(arguments: JSONObject): String {
        val word = arguments.optString("word").trim()
        if (word.isEmpty()) return "missing parameter: word"
        val entry = dictionaryProvider().lookup(word) ?: return "not found: $word"
        return formatEntry(entry)
    }

    companion object {
        /**
         * Compact gloss text for the model: looked-up form first, phonetic,
         * part of speech, Chinese and English glosses, and the base form when
         * the hit came through the lemmatization chain.
         */
        fun formatEntry(entry: DictEntry): String {
            val gloss = entry.lemmaEntry ?: entry
            return buildString {
                append(entry.word)
                gloss.phonetic?.let { append(" /$it/") }
                gloss.pos?.takeIf { it.isNotBlank() }?.let { append(" [$it]") }
                if (entry.lemmaEntry != null) append(" (原形: ${gloss.word})")
                gloss.translation?.let { append("\n中文: ").append(it.replace('\n', ' ')) }
                gloss.definition?.let { append("\n英文: ").append(it.replace('\n', ' ')) }
            }
        }
    }
}

/**
 * `save_vocab`: inserts one word plus its source sentence into the Room
 * vocab book, enriched with the dictionary gloss (phonetic/translation/
 * definition/lemma) when available. The (word, articleUrl) dedup index
 * turns a repeated save into the "already saved" receipt.
 */
class SaveVocabTool(
    private val vocabDao: VocabWordDao,
    private val articleUrl: String,
    private val sourceName: String,
    private val dictionaryProvider: suspend () -> Dictionary,
) : AgentTool {

    override val spec = ToolSpec(
        name = "save_vocab",
        description = "把一个英文单词和它的原文句子保存到生词本。",
        parametersSchema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "word",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "要保存的英文单词"),
                    )
                    .put(
                        "sentence",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "该词所在的原文句子"),
                    ),
            )
            .put("required", JSONArray().put("word").put("sentence")),
    )

    override suspend fun execute(arguments: JSONObject): String {
        val word = arguments.optString("word").trim()
        if (word.isEmpty()) return "missing parameter: word"
        val sentence = arguments.optString("sentence").trim()
        // A missing/failed dictionary must never block saving the word.
        val entry = try {
            dictionaryProvider().lookup(word)
        } catch (e: Exception) {
            null
        }
        val gloss = entry?.lemmaEntry ?: entry
        val id = vocabDao.insert(
            VocabWord(
                word = word,
                lemma = entry?.lemmaEntry?.word ?: entry?.lemma,
                phonetic = gloss?.phonetic,
                translation = gloss?.translation,
                definition = gloss?.definition,
                sentence = sentence.ifEmpty { null },
                articleUrl = articleUrl,
                sourceName = sourceName,
                addedAt = System.currentTimeMillis(),
            ),
        )
        return if (id >= 0) "saved id=$id" else "already saved"
    }
}

/**
 * `get_article_text`: returns the current article body via [textProvider],
 * capped at [Prompts.GUIDE_MAX_CHARS] with the truncation marker appended,
 * mirroring the 导读 template's contract.
 */
class ArticleTextTool(
    private val textProvider: suspend () -> String,
) : AgentTool {

    override val spec = ToolSpec(
        name = "get_article_text",
        description = "获取当前文章的英文正文（过长时截断）。",
        parametersSchema = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()),
    )

    override suspend fun execute(arguments: JSONObject): String {
        val text = textProvider().trim()
        return if (text.length > Prompts.GUIDE_MAX_CHARS) {
            text.substring(0, Prompts.GUIDE_MAX_CHARS) + Prompts.TRUNCATED_MARKER
        } else {
            text
        }
    }
}

// ---------------------------------------------------------------------------
// M16: Recommendation tools for the global Agent session.
// ---------------------------------------------------------------------------

/**
 * `search_articles`: fetches the latest article indexes from all configured
 * reading sources (RSS/HTML, excluding arXiv) via [SourceEngine], filters by
 * optional query/category, and returns a JSON array of candidates for the
 * model to recommend.
 *
 * Single-source failures are silently skipped (the tool reports how many
 * sources failed); only when ALL sources fail does it return an error string.
 */
class SearchArticlesTool(
    private val engine: dev.handypage.app.engine.SourceEngine,
    private val sourcesProvider: () -> List<dev.handypage.app.engine.SourceConfig>,
    private val preferencesStore: PreferencesProvider,
) : AgentTool {

    override val spec = ToolSpec(
        name = "search_articles",
        description = "检索已配置的新闻/学习阅读源的最新文章。返回标题、来源、链接和摘要。" +
            "可按关键词和分类过滤。需要联网。",
        parametersSchema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "query",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "可选：按标题关键词过滤"),
                    )
                    .put(
                        "category",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "可选：learning / news / science / tech"),
                    )
                    .put(
                        "max",
                        JSONObject()
                            .put("type", "integer")
                            .put("description", "返回条数上限，默认 5"),
                    ),
            ),
    )

    override suspend fun execute(arguments: JSONObject): String {
        val query = arguments.optString("query", "").trim().lowercase()
        val category = arguments.optString("category", "").trim().lowercase()
        val max = arguments.optInt("max", 5).coerceIn(1, 20)

        // Spec §search_articles step 1: read user preferences as soft signal.
        val prefs = preferencesStore.load()
        val prefsNote = if (prefs.isSet) "[用户偏好: ${prefs.describe()}]\n" else ""

        val allSources = sourcesProvider().filter {
            it.index.type != dev.handypage.app.engine.IndexCfg.Type.ARXIV
        }
        val filtered = if (category.isNotEmpty()) {
            allSources.filter { it.category.equals(category, ignoreCase = true) }
        } else {
            allSources
        }
        if (filtered.isEmpty()) return "没有匹配分类 \"$category\" 的阅读源"

        val results = ArrayList<JSONObject>()
        var failedCount = 0
        for (cfg in filtered) {
            try {
                val items = engine.fetchIndex(cfg)
                for (item in items) {
                    if (query.isNotEmpty() &&
                        !item.title.lowercase().contains(query) &&
                        !(item.summary?.lowercase()?.contains(query) == true)
                    ) continue
                    results += JSONObject()
                        .put("type", "article")
                        .put("title", item.title)
                        .put("source", cfg.name)
                        .put("url", item.url)
                        .put("summary", item.summary ?: "")
                        .put("published", item.published ?: "")
                }
            } catch (_: Exception) {
                failedCount++
            }
        }
        if (results.isEmpty() && failedCount == filtered.size) {
            return "所有阅读源均抓取失败（可能需要联网或源已改版）"
        }
        // Sort by published descending (best-effort; empty dates sink).
        val sorted = results.sortedByDescending { it.optString("published") }
        val capped = sorted.take(max)
        val note = if (failedCount > 0) " ($failedCount 个源抓取失败已跳过)" else ""
        return prefsNote + JSONArray(capped).toString() + note
    }
}

/**
 * `search_papers`: queries the arXiv API for academic papers matching a
 * keyword search and/or subject category. Falls back to the user's saved
 * topic preferences when neither is provided.
 *
 * Observes arXiv's 3-second etiquette delay internally.
 */
class SearchPapersTool(
    private val api: dev.handypage.app.arxiv.ArxivApi,
    private val preferencesStore: PreferencesProvider,
) : AgentTool {

    override val spec = ToolSpec(
        name = "search_papers",
        description = "检索 arXiv 学术论文。可按关键词搜索、按学科分类浏览，或两者组合。" +
            "返回标题、作者、分类、摘要和链接。需要联网。",
        parametersSchema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "query",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "可选：搜索关键词（英文效果最佳）"),
                    )
                    .put(
                        "category",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "可选：arXiv 分类码，如 cs.CL、cs.LG、cs.CV、cs.AI、stat.ML"),
                    )
                    .put(
                        "max",
                        JSONObject()
                            .put("type", "integer")
                            .put("description", "返回条数上限，默认 5"),
                    ),
            ),
    )

    override suspend fun execute(arguments: JSONObject): String {
        var query = arguments.optString("query", "").trim()
        val category = arguments.optString("category", "").trim()
        val max = arguments.optInt("max", 5).coerceIn(1, 25)

        // Fall back to user's saved topics when no explicit query.
        if (query.isEmpty() && category.isEmpty()) {
            val prefs = preferencesStore.load()
            query = prefs.topics.substringBefore(',').trim()
            if (query.isEmpty()) return "请提供搜索关键词或 arXiv 分类码"
        }

        // arXiv etiquette: at least 3 s between API calls.
        kotlinx.coroutines.delay(3000)

        val entries = try {
            when {
                query.isNotEmpty() && category.isNotEmpty() ->
                    api.searchAndCategory(query, category, maxResults = max)
                query.isNotEmpty() ->
                    api.search(query, maxResults = max)
                else ->
                    api.byCategory(category, maxResults = max)
            }
        } catch (e: Exception) {
            return "arXiv 检索失败：${e.message}（可能需要联网）"
        }
        if (entries.isEmpty()) return "未找到匹配的论文"

        val arr = JSONArray()
        for (entry in entries.take(max)) {
            arr.put(
                JSONObject()
                    .put("type", "paper")
                    .put("title", entry.title)
                    .put("authors", entry.authors.take(3).joinToString(", "))
                    .put("category", entry.primaryCategory)
                    .put("summary", entry.summary.take(200))
                    .put("published", entry.published)
                    .put("absUrl", entry.absUrl),
            )
        }
        return arr.toString()
    }
}

/**
 * `save_preference`: persists the user's declared reading preferences
 * (difficulty level and topic interests) for future recommendation filtering.
 */
class SavePreferenceTool(
    private val preferencesStore: PreferencesProvider,
) : AgentTool {

    override val spec = ToolSpec(
        name = "save_preference",
        description = "保存用户的阅读偏好（难度和兴趣主题），后续推荐将参考这些偏好。" +
            "当用户表达阅读水平或兴趣方向时调用。",
        parametersSchema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "difficulty",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "阅读难度：beginner / intermediate / advanced"),
                    )
                    .put(
                        "topics",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "兴趣主题，逗号分隔，如 \"machine learning, linguistics\""),
                    )
                    .put(
                        "action",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "set（默认）或 clear（清除全部偏好）"),
                    ),
            ),
    )

    override suspend fun execute(arguments: JSONObject): String {
        val action = arguments.optString("action", "set").trim().lowercase()
        if (action == "clear") {
            preferencesStore.clear()
            return "已清除全部阅读偏好"
        }
        val difficulty = arguments.optString("difficulty", "").trim().lowercase()
        val topics = arguments.optString("topics", "").trim()
        val current = preferencesStore.load()
        val updated = AgentPreferences(
            difficulty = difficulty.ifEmpty { current.difficulty },
            topics = topics.ifEmpty { current.topics },
            updatedAt = System.currentTimeMillis(),
        )
        preferencesStore.save(updated)
        return "已更新偏好：${updated.describe()}"
    }
}

/**
 * `get_preferences`: reads the user's saved reading preferences.
 * Lightweight read-only tool so the model can check before recommending.
 */
class GetPreferencesTool(
    private val preferencesStore: PreferencesProvider,
) : AgentTool {

    override val spec = ToolSpec(
        name = "get_preferences",
        description = "获取用户已保存的阅读偏好（难度和兴趣主题）。推荐前先调用此工具了解用户水平。",
        parametersSchema = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()),
    )

    override suspend fun execute(arguments: JSONObject): String {
        val prefs = preferencesStore.load()
        return if (prefs.isSet) prefs.describe() else "尚未设置偏好"
    }
}
