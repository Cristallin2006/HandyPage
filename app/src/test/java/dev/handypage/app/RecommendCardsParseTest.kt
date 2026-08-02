package dev.handypage.app

import dev.handypage.app.ui.RecommendCard
import dev.handypage.app.ui.parseCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the M16 recommendation card parser ([parseCards]).
 * Covers: normal article/paper cards, malformed JSON, missing block,
 * empty array, and mixed valid/invalid entries.
 */
class RecommendCardsParseTest {

    @Test
    fun `no cards block returns full text and null cards`() {
        val text = "Here are some recommendations.\n\nEnjoy reading!"
        val (prose, cards) = parseCards(text)
        assertEquals(text, prose)
        assertNull(cards)
    }

    @Test
    fun `valid article cards parsed correctly`() {
        val text = """
            推荐如下：
            
            ```cards
            [{"type":"article","title":"Test Article","source":"NPR","url":"https://npr.org/1","summary":"A test."}]
            ```
        """.trimIndent()
        val (prose, cards) = parseCards(text)
        assertEquals("推荐如下：", prose)
        assertEquals(1, cards!!.size)
        val card = cards[0]
        assertEquals("article", card.type)
        assertEquals("Test Article", card.title)
        assertEquals("NPR", card.source)
        assertEquals("https://npr.org/1", card.url)
        assertEquals("A test.", card.summary)
        assertEquals("", card.category)
    }

    @Test
    fun `valid paper cards parsed correctly`() {
        val text = """
            ```cards
            [{"type":"paper","title":"Attention","authors":"Vaswani, Shazeer","category":"cs.CL","absUrl":"https://arxiv.org/abs/1706.03762","summary":"Transformers."}]
            ```
        """.trimIndent()
        val (prose, cards) = parseCards(text)
        assertEquals("", prose)
        assertEquals(1, cards!!.size)
        val card = cards[0]
        assertEquals("paper", card.type)
        assertEquals("Attention", card.title)
        assertEquals("Vaswani, Shazeer", card.source)
        assertEquals("https://arxiv.org/abs/1706.03762", card.url)
        assertEquals("cs.CL", card.category)
    }

    @Test
    fun `mixed article and paper cards`() {
        val json = """[{"type":"article","title":"A","source":"S","url":"u","summary":""},
                       {"type":"paper","title":"P","authors":"X","category":"cs.AI","absUrl":"a","summary":""}]"""
        val text = "推荐：\n```cards\n$json\n```"
        val (_, cards) = parseCards(text)
        assertEquals(2, cards!!.size)
        assertEquals("article", cards[0].type)
        assertEquals("paper", cards[1].type)
    }

    @Test
    fun `malformed JSON returns null cards`() {
        val text = "text\n```cards\n{not valid json}\n```"
        val (prose, cards) = parseCards(text)
        assertEquals("text", prose)
        assertNull(cards)
    }

    @Test
    fun `empty array returns null cards`() {
        val text = "text\n```cards\n[]\n```"
        val (_, cards) = parseCards(text)
        assertNull(cards)
    }

    @Test
    fun `invalid type entries are skipped`() {
        val json = """[{"type":"video","title":"V","url":"x"},
                       {"type":"article","title":"A","source":"S","url":"u","summary":""}]"""
        val text = "```cards\n$json\n```"
        val (_, cards) = parseCards(text)
        assertEquals(1, cards!!.size)
        assertEquals("A", cards[0].title)
    }

    @Test
    fun `unclosed cards block returns null`() {
        val text = "text\n```cards\n[{\"type\":\"article\"}]"
        val (prose, cards) = parseCards(text)
        assertEquals(text, prose)
        assertNull(cards)
    }
}