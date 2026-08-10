package dev.handypage.app

import dev.handypage.app.vocab.MASTERY_LEARNING
import dev.handypage.app.vocab.MASTERY_MASTERED
import dev.handypage.app.vocab.MASTERY_NEW
import dev.handypage.app.vocab.VocabSort
import dev.handypage.app.vocab.VocabWord
import dev.handypage.app.vocab.filterGroups
import dev.handypage.app.vocab.groupKeyOf
import dev.handypage.app.vocab.groupVocabWords
import dev.handypage.app.vocab.sortGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * M40: the vocab book lists lemma-groups, not raw rows — the (word,
 * articleUrl) unique index lets one lemma be saved once per article. These
 * tests pin the grouping/filter/sort contract behind VocabScreen.
 */
class VocabGroupingTest {

    private fun word(
        word: String,
        lemma: String? = null,
        phonetic: String? = null,
        translation: String? = null,
        definition: String? = null,
        sentence: String? = null,
        addedAt: Long = 0,
        mastery: Int = MASTERY_NEW,
    ) = VocabWord(
        word = word,
        lemma = lemma,
        phonetic = phonetic,
        translation = translation,
        definition = definition,
        sentence = sentence,
        addedAt = addedAt,
        mastery = mastery,
    )

    @Test
    fun `same lemma across articles merges into one group`() {
        val groups = groupVocabWords(
            listOf(
                word("running", lemma = "run", addedAt = 100),
                word("ran", lemma = "run", addedAt = 200),
                word("runs", lemma = "run", addedAt = 150),
            ),
        )
        assertEquals(1, groups.size)
        val g = groups.single()
        assertEquals("run", g.key)
        assertEquals(3, g.occurrences)
        assertEquals(200, g.latestAt)
        // rows newest-first
        assertEquals(listOf("ran", "runs", "running"), g.rows.map { it.word })
    }

    @Test
    fun `blank or null lemma falls back to the surface form`() {
        assertEquals("apple", groupKeyOf(word(" Apple ", lemma = null)))
        assertEquals("banana", groupKeyOf(word("Banana", lemma = "  ")))
        assertEquals("run", groupKeyOf(word("running", lemma = " Run ")))
    }

    @Test
    fun `grouping is case insensitive`() {
        val groups = groupVocabWords(
            listOf(
                word("Run", addedAt = 1),
                word("run", addedAt = 2),
                word("RUN", lemma = "run", addedAt = 3),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(3, groups.single().occurrences)
    }

    @Test
    fun `display fields come from the latest row with non-blank fallback`() {
        val groups = groupVocabWords(
            listOf(
                word("run", translation = "跑；运行", phonetic = "rʌn", addedAt = 100),
                // Latest re-save from another article carries no gloss.
                word("ran", lemma = "run", translation = " ", addedAt = 200),
            ),
        )
        val g = groups.single()
        assertEquals("ran", g.headword) // headword = latest row's surface form
        assertEquals("跑；运行", g.translation) // falls back to latest non-blank
        assertEquals("rʌn", g.phonetic)
        assertNull(g.definition) // blank everywhere stays null
    }

    @Test
    fun `group mastery is the minimum of its rows`() {
        val groups = groupVocabWords(
            listOf(
                word("run", addedAt = 1, mastery = MASTERY_MASTERED),
                word("ran", lemma = "run", addedAt = 2, mastery = MASTERY_LEARNING),
                word("runs", lemma = "run", addedAt = 3, mastery = MASTERY_NEW),
            ),
        )
        assertEquals(MASTERY_NEW, groups.single().mastery)
    }

    @Test
    fun `filter matches headword translation and definition case insensitively`() {
        val groups = groupVocabWords(
            listOf(
                word("run", translation = "跑", definition = "to move fast", addedAt = 1),
                word("apple", translation = "苹果", addedAt = 2),
            ),
        )
        assertEquals(listOf("run"), filterGroups(groups, "RUN", null).map { it.key })
        assertEquals(listOf("apple"), filterGroups(groups, "苹果", null).map { it.key })
        assertEquals(listOf("run"), filterGroups(groups, "move fast", null).map { it.key })
        assertEquals(2, filterGroups(groups, "  ", null).size) // blank = all
        assertEquals(0, filterGroups(groups, "zzz", null).size)
    }

    @Test
    fun `filter by mastery level`() {
        val groups = groupVocabWords(
            listOf(
                word("run", addedAt = 1, mastery = MASTERY_NEW),
                word("apple", addedAt = 2, mastery = MASTERY_MASTERED),
            ),
        )
        assertEquals(2, filterGroups(groups, "", null).size)
        assertEquals(listOf("run"), filterGroups(groups, "", MASTERY_NEW).map { it.key })
        assertEquals(listOf("apple"), filterGroups(groups, "", MASTERY_MASTERED).map { it.key })
        assertEquals(0, filterGroups(groups, "", MASTERY_LEARNING).size)
    }

    @Test
    fun `sort recent orders by latest save`() {
        val groups = groupVocabWords(
            listOf(
                word("old", addedAt = 1),
                word("new", addedAt = 3),
                word("mid", addedAt = 2),
            ),
        )
        assertEquals(
            listOf("new", "mid", "old"),
            sortGroups(groups, VocabSort.RECENT).map { it.headword },
        )
    }

    @Test
    fun `sort frequency orders by occurrences then recency`() {
        val groups = groupVocabWords(
            listOf(
                word("solo", addedAt = 5),
                word("run", addedAt = 1),
                word("ran", lemma = "run", addedAt = 2),
                word("twice-a", addedAt = 3),
                word("twice-b", lemma = "twice-a", addedAt = 4),
            ),
        )
        // run(×2, latest 2) before twice-a(×2, latest 4)? No: recency tiebreak
        // puts twice-a (latest 4) first; solo (×1) last.
        assertEquals(
            listOf("twice-a", "run", "solo"),
            sortGroups(groups, VocabSort.FREQUENCY).map { it.key },
        )
    }

    @Test
    fun `sort alphabetical orders by headword`() {
        val groups = groupVocabWords(
            listOf(
                word("cherry", addedAt = 1),
                word("Apple", addedAt = 2),
                word("banana", addedAt = 3),
            ),
        )
        assertEquals(
            listOf("Apple", "banana", "cherry"),
            sortGroups(groups, VocabSort.ALPHABETICAL).map { it.headword },
        )
    }
}
