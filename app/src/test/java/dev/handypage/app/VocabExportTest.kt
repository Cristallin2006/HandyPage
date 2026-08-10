package dev.handypage.app

import dev.handypage.app.vocab.VocabWord
import dev.handypage.app.vocab.vocabCsv
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M40: the CSV export is a full backup of the vocab book through SAF. These
 * tests pin the header, the escaping rules and null handling; the date
 * formatter is fixed to UTC so the added_at column is deterministic.
 */
class VocabExportTest {

    private val utcFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun word(
        word: String = "run",
        lemma: String? = null,
        phonetic: String? = null,
        translation: String? = null,
        definition: String? = null,
        sentence: String? = null,
        sourceName: String = "",
        articleUrl: String = "",
        addedAt: Long = 0,
        mastery: Int = 0,
    ) = VocabWord(
        word = word,
        lemma = lemma,
        phonetic = phonetic,
        translation = translation,
        definition = definition,
        sentence = sentence,
        sourceName = sourceName,
        articleUrl = articleUrl,
        addedAt = addedAt,
        mastery = mastery,
    )

    @Test
    fun `first line is the header`() {
        val csv = vocabCsv(emptyList(), utcFormat)
        assertEquals(
            "word,lemma,phonetic,translation,definition,sentence,source_name,article_url,mastery,added_at",
            csv.lineSequence().first(),
        )
    }

    @Test
    fun `fields with comma quote or newline are quoted and quotes doubled`() {
        val csv = vocabCsv(
            listOf(
                word(
                    word = "well-known",
                    translation = "含有, 逗号",
                    definition = "said \"well\"",
                    sentence = "line one\nline two",
                ),
            ),
            utcFormat,
        )
        val row = csv.lineSequence().drop(1).first()
        assertTrue(row.contains("\"含有, 逗号\""))
        assertTrue(row.contains("\"said \"\"well\"\"\""))
        // The embedded newline lives inside quotes, not as a record break.
        assertTrue(csv.contains("\"line one\nline two\""))
        assertEquals(2, csv.trim().count { it == '\n' }) // header + 1 record
    }

    @Test
    fun `null fields become empty and dates are formatted`() {
        val csv = vocabCsv(
            listOf(word(addedAt = 0, mastery = 1)),
            utcFormat,
        )
        val row = csv.lineSequence().drop(1).first()
        assertEquals("run,,,,,,,,1,1970-01-01 00:00", row)
    }

    @Test
    fun `row count is header plus one line per word`() {
        val rows = listOf(word("a"), word("b"), word("c"))
        val csv = vocabCsv(rows, utcFormat)
        assertEquals(4, csv.trim().lines().size)
    }
}
