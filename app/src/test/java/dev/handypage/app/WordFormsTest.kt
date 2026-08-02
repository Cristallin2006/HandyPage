package dev.handypage.app

import dev.handypage.app.dict.WordForms
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for the pure-Kotlin lookup-key helpers. The SQL chain itself
 * lives behind android.database.sqlite and is device-verified in M2b.
 */
class WordFormsTest {

    @Test
    fun `clean trims and lowercases`() {
        assertEquals("hello", WordForms.clean("  Hello "))
    }

    @Test
    fun `clean strips surrounding ASCII punctuation`() {
        assertEquals("hello", WordForms.clean("\"Hello,\""))
        assertEquals("run", WordForms.clean("(run)."))
    }

    @Test
    fun `clean strips full-width and unicode punctuation`() {
        assertEquals("café", WordForms.clean("（Café）"))
    }

    @Test
    fun `clean collapses inner whitespace to single spaces`() {
        assertEquals("new york", WordForms.clean("New\n  York"))
    }

    @Test
    fun `clean keeps inner apostrophes`() {
        assertEquals("rock'n'roll", WordForms.clean("rock'n'roll"))
    }

    @Test
    fun `clean keeps possessive for candidate expansion`() {
        assertEquals("john's", WordForms.clean("John's"))
        assertEquals("dogs", WordForms.clean("dogs'"))
    }

    @Test
    fun `candidates adds base form for possessive`() {
        assertEquals(listOf("john's", "john"), WordForms.candidates("john's"))
    }

    @Test
    fun `candidates leaves plain words and plurals alone`() {
        assertEquals(listOf("run"), WordForms.candidates("run"))
        assertEquals(listOf("dogs"), WordForms.candidates("dogs"))
    }

    @Test
    fun `sentence joins parts and collapses whitespace`() {
        assertEquals(
            "the quick brown fox jumps",
            WordForms.sentence("the quick\n", "brown", "  fox   jumps"),
        )
    }

    @Test
    fun `sentence tolerates null context parts`() {
        assertEquals("hello world", WordForms.sentence(null, "hello", " world"))
        assertEquals("", WordForms.sentence(null, null, null))
    }
}
