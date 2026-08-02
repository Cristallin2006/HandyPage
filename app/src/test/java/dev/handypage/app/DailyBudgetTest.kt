package dev.handypage.app

import dev.handypage.app.agent.DailyBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the pure daily token budget gate. */
class DailyBudgetTest {

    @Test
    fun `canSpend is true under the limit and false at or beyond it`() {
        val budget = DailyBudget(limit = 100)
        assertTrue(budget.canSpend())
        budget.record(60)
        assertTrue(budget.canSpend())
        budget.record(40) // exactly at the limit: the next call is refused
        assertFalse(budget.canSpend())
        budget.record(1)
        assertFalse(budget.canSpend())
    }

    @Test
    fun `record accumulates and ignores non-positive values`() {
        val budget = DailyBudget(limit = 1000)
        budget.record(10)
        budget.record(0)
        budget.record(-5)
        budget.record(7)
        assertEquals(17, budget.usedToday)
    }

    @Test
    fun `limit zero means unlimited`() {
        val budget = DailyBudget(limit = 0)
        budget.record(1_000_000_000)
        assertTrue(budget.canSpend())
    }

    @Test
    fun `default limit is 200k and starts unused`() {
        val budget = DailyBudget()
        assertEquals(200_000, budget.limit)
        assertEquals(0, budget.usedToday)
        assertTrue(budget.canSpend())
    }
}
