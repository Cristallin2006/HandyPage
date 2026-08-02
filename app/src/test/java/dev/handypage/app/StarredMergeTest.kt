package dev.handypage.app

import dev.handypage.app.local.StarredItem
import dev.handypage.app.local.StarredMerge
import dev.handypage.app.local.StarredType
import dev.handypage.app.vocab.ArticleStar
import dev.handypage.app.vocab.PaperStar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the M21 收藏-section merge (DESIGN.md §4.23): type mapping
 * and newest-first ordering of mixed paper/article stars.
 */
class StarredMergeTest {

    private fun paper(url: String, starredAt: Long) = PaperStar(
        url = url,
        title = "Paper $url",
        authors = "Ada Lovelace, Alan Turing",
        primaryCategory = "cs.CL",
        published = "2026-07-28T00:00:00Z",
        starredAt = starredAt,
    )

    private fun article(url: String, starredAt: Long) = ArticleStar(
        url = url,
        title = "Article $url",
        sourceId = "chinadaily",
        sourceName = "China Daily",
        starredAt = starredAt,
    )

    @Test
    fun `merge sorts mixed stars newest first`() {
        val merged = StarredMerge.merge(
            papers = listOf(paper("p1", 100), paper("p2", 300)),
            articles = listOf(article("a1", 200), article("a2", 400)),
        )
        assertEquals(listOf("a2", "p2", "a1", "p1"), merged.map { it.url })
    }

    @Test
    fun `merge tags each row with its type`() {
        val merged = StarredMerge.merge(
            papers = listOf(paper("p1", 1)),
            articles = listOf(article("a1", 2)),
        )
        assertEquals(StarredType.ARTICLE, merged[0].type)
        assertEquals(StarredType.PAPER, merged[1].type)
    }

    @Test
    fun `paper row carries authors and date dot category`() {
        val item = StarredMerge.fromPaper(paper("p1", 1))
        assertEquals("Ada Lovelace, Alan Turing", item.metaTop)
        assertEquals("2026-07-28 · cs.CL", item.metaBottom)
    }

    @Test
    fun `paper row drops empty date and category cleanly`() {
        val item = StarredMerge.fromPaper(
            paper("p1", 1).copy(published = "", primaryCategory = ""),
        )
        assertEquals("", item.metaBottom)
    }

    @Test
    fun `article row carries the source name and no top line`() {
        val item = StarredMerge.fromArticle(article("a1", 1))
        assertEquals("", item.metaTop)
        assertEquals("China Daily", item.metaBottom)
    }

    @Test
    fun `empty inputs merge to an empty list`() {
        assertTrue(StarredMerge.merge(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `rows keep url and title for click-through`() {
        val items: List<StarredItem> = StarredMerge.merge(
            papers = emptyList(),
            articles = listOf(article("https://example.com/x", 1)),
        )
        assertEquals("https://example.com/x", items[0].url)
        assertEquals("Article https://example.com/x", items[0].title)
    }
}
