package dev.handypage.app

import dev.handypage.app.vocab.SentenceText
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for [SentenceText] sentence-book normalization. */
class SentenceTextTest {

    @Test
    fun `normalize trims and collapses whitespace runs`() {
        assertEquals(
            "The quick brown fox jumps.",
            SentenceText.normalize("  The quick\n\n  brown\tfox   jumps.  "),
        )
    }

    @Test
    fun `normalize flattens EPUB selection newlines into single spaces`() {
        assertEquals(
            "one two three",
            SentenceText.normalize("one\rtwo\r\nthree"),
        )
    }

    @Test
    fun `normalize leaves single spaces and punctuation untouched`() {
        val sentence = "He said, “We’re ready.” — and left."
        assertEquals(sentence, SentenceText.normalize(sentence))
    }

    @Test
    fun `normalize of blank input is empty`() {
        assertEquals("", SentenceText.normalize("   \n\t  "))
    }
}
