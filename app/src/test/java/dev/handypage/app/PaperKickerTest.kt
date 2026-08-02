package dev.handypage.app

import dev.handypage.app.paper.paperKicker
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for the M17 paper-reader top-bar kicker builder. */
class PaperKickerTest {

    @Test
    fun `category and ISO published join with separators`() {
        assertEquals("arXiv · cs.CL · 2024", paperKicker("cs.CL", "2024-01-03T08:00:00Z"))
    }

    @Test
    fun `blank category drops out`() {
        assertEquals("arXiv · 2023", paperKicker("  ", "2023-11-30"))
    }

    @Test
    fun `published without a year prefix drops the year`() {
        assertEquals("arXiv · cs.AI", paperKicker("cs.AI", "n/a"))
        assertEquals("arXiv · cs.AI", paperKicker("cs.AI", ""))
    }

    @Test
    fun `everything blank stays bare arxiv`() {
        assertEquals("arXiv", paperKicker("", ""))
    }
}
