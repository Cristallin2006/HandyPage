package dev.handypage.app.local

import dev.handypage.app.vocab.ArticleStar
import dev.handypage.app.vocab.PaperStar

/**
 * One row of the 本机 tab's 收藏 section (M21): paper stars and article stars
 * merged into a single newest-first list, each tagged with its [StarredType]
 * so the row can show a type icon and route the tap to the right open flow.
 *
 * Pure mapping/sorting lives here so it is JVM-testable; the composable that
 * renders the list is in LocalScreen.
 */
enum class StarredType { ARTICLE, PAPER }

data class StarredItem(
    val url: String,
    val title: String,
    /** First supporting line (paper authors); empty when not applicable. */
    val metaTop: String,
    /** Second supporting line: date · category for papers, source for articles. */
    val metaBottom: String,
    val type: StarredType,
    val starredAt: Long,
)

object StarredMerge {

    /** Newest star first; ties keep papers above articles (stable sort input order). */
    fun merge(papers: List<PaperStar>, articles: List<ArticleStar>): List<StarredItem> =
        (papers.map(::fromPaper) + articles.map(::fromArticle))
            .sortedByDescending { it.starredAt }

    fun fromPaper(star: PaperStar): StarredItem = StarredItem(
        url = star.url,
        title = star.title,
        metaTop = star.authors,
        metaBottom = listOfNotNull(
            star.published.take(10).takeIf { it.isNotEmpty() },
            star.primaryCategory.takeIf { it.isNotEmpty() },
        ).joinToString(" · "),
        type = StarredType.PAPER,
        starredAt = star.starredAt,
    )

    fun fromArticle(star: ArticleStar): StarredItem = StarredItem(
        url = star.url,
        title = star.title,
        metaTop = "",
        metaBottom = star.sourceName,
        type = StarredType.ARTICLE,
        starredAt = star.starredAt,
    )
}
