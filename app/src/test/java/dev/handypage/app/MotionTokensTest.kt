package dev.handypage.app

import dev.handypage.app.ui.HpMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the M22 motion tokens (docs/design-system.md §9,
 * "B · 墨线轴线"): asymmetric out-faster-than-in durations, ≤250ms ceiling,
 * emphasized curve shapes, and axis shift ordering. Guards the design
 * anchor so nobody hand-tunes a spec back into mush.
 */
class MotionTokensTest {

    @Test
    fun `exit is faster than enter for every surface`() {
        assertTrue(HpMotion.SheetOut < HpMotion.SheetIn)
        assertTrue(HpMotion.BarOut < HpMotion.BarIn)
        assertTrue(HpMotion.AxisOut < HpMotion.AxisIn)
        assertTrue(HpMotion.TabOut < HpMotion.TabIn)
    }

    @Test
    fun `no UI motion exceeds the 250ms ceiling`() {
        listOf(
            HpMotion.TabIn, HpMotion.TabOut,
            HpMotion.AxisIn, HpMotion.AxisOut,
            HpMotion.SheetIn, HpMotion.SheetOut,
            HpMotion.BarIn, HpMotion.BarOut,
            HpMotion.Indicator, HpMotion.State,
        ).forEach { assertTrue("duration $it over ceiling", it <= 250) }
    }

    @Test
    fun `fade-through matches the approved preview timing`() {
        assertEquals(90, HpMotion.TabOut)
        assertEquals(210, HpMotion.TabIn)
    }

    @Test
    fun `tab rise stays gentler than the forward axis push`() {
        assertTrue(HpMotion.TabShift in 0.01f..0.03f)
        assertTrue(HpMotion.TabShift < HpMotion.AxisShift)
    }

    @Test
    fun `axis shift is stronger than the exit counter-shift`() {
        assertTrue(HpMotion.AxisShift > HpMotion.AxisExitShift)
    }

    @Test
    fun `decelerate starts fast, accelerate starts slow`() {
        assertTrue(HpMotion.Decel.transform(0.5f) > 0.5f)
        assertTrue(HpMotion.Accel.transform(0.5f) < 0.5f)
        // Standard stays monotonic across the range.
        var prev = -1f
        for (i in 0..10) {
            val v = HpMotion.Standard.transform(i / 10f)
            assertTrue(v >= prev)
            prev = v
        }
    }

    @Test
    fun `spec builders produce transitions`() {
        // Exercises the Compose builders on the JVM so a signature drift
        // (e.g. tween parameter reorder) fails here instead of at runtime.
        assertTrue(HpMotion.tabEnter() != HpMotion.tabExit())
        assertTrue(HpMotion.axisEnter() != HpMotion.axisPopEnter())
        assertTrue(HpMotion.sheetEnter() != HpMotion.barEnter())
    }
}
