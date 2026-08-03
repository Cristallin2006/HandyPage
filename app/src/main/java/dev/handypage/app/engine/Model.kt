package dev.handypage.app.engine

import org.json.JSONObject

/**
 * Kotlin port of the source.json DSL consumed by
 * tools/converter/replay_fixtures.py (the reference implementation / spec).
 *
 * Field names map 1:1 to the JSON keys; only the keys the engine needs are
 * modelled (`origin`, `notes`, `test` are intentionally ignored; `language`
 * is display metadata for the sources shelf).
 */
data class SourceConfig(
    val id: String,
    val name: String,
    val language: String = "en",
    val version: Int,
    val homepage: String,
    val needsProxy: Boolean,
    /** Shelf grouping for the sources tab (M7): learning / news / science / tech. */
    val category: String = "news",
    /** arXiv subject codes (e.g. "cs.CL") for ARXIV-type sources; empty otherwise. */
    val categories: List<String> = emptyList(),
    val index: IndexCfg,
    val article: ArticleCfg,
    val delaySeconds: Double,
    val userAgent: String?,
    val readerCss: String?,
) {
    companion object {
        fun fromJson(o: JSONObject): SourceConfig {
            val req = o.optJSONObject("request")
            val cats = o.optJSONArray("categories")
            val categories = buildList {
                if (cats != null) {
                    for (i in 0 until cats.length()) add(cats.getString(i))
                }
            }
            return SourceConfig(
                id = o.getString("id"),
                name = o.getString("name"),
                language = o.optString("language", "en").ifBlank { "en" },
                version = o.optInt("version", 1),
                homepage = o.optString("homepage", ""),
                needsProxy = o.optBoolean("needs_proxy", false),
                category = o.optString("category", "news").ifBlank { "news" },
                categories = categories,
                index = IndexCfg.fromJson(o.getJSONObject("index")),
                article = ArticleCfg.fromJson(o.getJSONObject("article")),
                delaySeconds = req?.optDouble("delay_seconds", 1.0) ?: 1.0,
                // optString() maps JSON null to ""; normalise that to null.
                userAgent = req?.optString("user_agent")?.takeIf { it.isNotBlank() },
                readerCss = o.optString("reader_css").takeIf { it.isNotBlank() },
            )
        }
    }
}

data class IndexCfg(
    val type: Type,
    val url: String,
    val linkCss: String? = null,
    val linkRegex: String? = null,
    val max: Int = 20,
) {
    enum class Type { RSS, HTML, ARXIV }

    companion object {
        fun fromJson(o: JSONObject): IndexCfg = IndexCfg(
            type = when (val t = o.getString("type").lowercase()) {
                "rss" -> Type.RSS
                "html" -> Type.HTML
                "arxiv" -> Type.ARXIV
                else -> throw IllegalArgumentException("unknown index type: $t")
            },
            url = o.getString("url"),
            linkCss = o.optString("link_css").takeIf { it.isNotBlank() },
            linkRegex = o.optString("link_regex").takeIf { it.isNotBlank() },
            max = o.optInt("max", 20),
        )
    }
}

data class ArticleCfg(
    val content: String,
    val title: String? = null,
    val remove: List<String> = emptyList(),
    val encoding: String? = null,
) {
    companion object {
        fun fromJson(o: JSONObject): ArticleCfg {
            val arr = o.optJSONArray("remove")
            val remove = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
            }
            return ArticleCfg(
                content = o.getString("content"),
                title = o.optString("title").takeIf { it.isNotBlank() },
                remove = remove,
                encoding = o.optString("encoding").takeIf { it.isNotBlank() },
            )
        }
    }
}

data class IndexItem(
    val title: String,
    val url: String,
    val summary: String? = null,
    val published: String? = null,
)

data class ArticleContent(
    val title: String,
    val bodyHtml: String,
    val sourceUrl: String,
    /** M28: images to embed in the EPUB package — package-relative path ("images/img-…​.png") → bytes. */
    val images: Map<String, ByteArray> = emptyMap(),
)

/** Failure at a known pipeline stage; [stage] is e.g. "index_fetch", "article_parse". */
class EngineException(
    val stage: String,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(if (message != null) "$stage: $message" else stage, cause)
