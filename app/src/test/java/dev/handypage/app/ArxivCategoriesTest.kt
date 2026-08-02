package dev.handypage.app

import dev.handypage.app.arxiv.ArxivCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the M13 full arXiv subject taxonomy: the table must cover the
 * well-known codes with unique codes and a non-empty archive group per row.
 */
class ArxivCategoriesTest {

    @Test
    fun `taxonomy is non-empty and codes are unique`() {
        assertTrue(ArxivCategories.ALL.isNotEmpty())
        val codes = ArxivCategories.ALL.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `well-known categories are present`() {
        val codes = ArxivCategories.ALL.map { it.code }
        assertTrue("cs.CL" in codes)
        assertTrue("math.OC" in codes)
        assertTrue("quant-ph" in codes)
        assertTrue("eess.AS" in codes)
    }

    @Test
    fun `byCode resolves code and label`() {
        val cl = ArxivCategories.byCode("cs.CL")
        assertNotNull(cl)
        assertEquals("Computation and Language", cl!!.label)
        assertEquals("cs", cl.group)
    }

    @Test
    fun `byCode misses unknown codes`() {
        assertNull(ArxivCategories.byCode("cs.XX"))
        assertNull(ArxivCategories.byCode(""))
    }

    @Test
    fun `every category has a non-empty group`() {
        ArxivCategories.ALL.forEach { cat ->
            assertTrue("empty group for ${cat.code}", cat.group.isNotEmpty())
        }
    }
}
