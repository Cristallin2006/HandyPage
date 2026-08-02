package dev.handypage.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import dev.handypage.app.R

/**
 * Motion tokens — the single source of truth for every transition in the
 * app (docs/design-system.md §9, "B · 墨线轴线" chosen in the 2026-08-02
 * four-direction preview, dials 5/5/6).
 *
 * M3 emphasized curve family + vertical-axis semantics: forward = push up,
 * back = sink. Components must consume these specs; no hand-written
 * durations/easings elsewhere.
 */
object HpMotion {

    // M3 emphasized curve family (DNA lifted from the M3 motion spec).
    val Decel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val Accel = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    const val TabOut = 90
    const val TabIn = 210
    const val AxisIn = 240
    const val AxisOut = 120
    const val SheetIn = 250
    const val SheetOut = 180
    const val BarIn = 200
    const val BarOut = 150
    const val Indicator = 200
    const val State = 120

    /** ≈12dp rise for tab bodies — perceptible, but gentle (§9). */
    const val TabShift = 0.023f

    /** ≈24dp forward push. */
    const val AxisShift = 0.03f

    /** ≈10dp counter-shift for the outgoing/re-entering screen. */
    const val AxisExitShift = 0.012f

    // ---- tab / same-level destinations: fade-through + rise ---------------
    //
    // R2 lesson: translating the WHOLE destination moved the shared masthead
    // and read as "the page header jumps" (hand-test). Pure fade then read
    // as "nothing happened". R3: the masthead is hoisted into the shell
    // (MastheadHost) and merely crossfades its text, so the body below keeps
    // the B-mock fade+rise — perceptible motion, zero shared-structure jump.
    // No enter delay: tabs are tapped dozens of times a day, responsiveness
    // beats choreography.

    fun tabEnter(): EnterTransition =
        fadeIn(tween(TabIn, easing = Decel)) +
            slideInVertically(tween(TabIn, easing = Decel)) { (it * TabShift).toInt() }

    fun tabExit(): ExitTransition =
        fadeOut(tween(TabOut, easing = Accel)) +
            slideOutVertically(tween(TabOut, easing = Accel)) { (it * TabShift).toInt() }

    // ---- forward destinations (articles, history): shared axis Y ----------

    fun axisEnter(): EnterTransition =
        fadeIn(tween(AxisIn, easing = Decel)) +
            slideInVertically(tween(AxisIn, easing = Decel)) { (it * AxisShift).toInt() }

    fun axisExit(): ExitTransition =
        fadeOut(tween(AxisOut, easing = Accel)) +
            slideOutVertically(tween(AxisOut, easing = Accel)) { -(it * AxisExitShift).toInt() }

    fun axisPopEnter(): EnterTransition =
        fadeIn(tween(AxisIn, easing = Decel)) +
            slideInVertically(tween(AxisIn, easing = Decel)) { -(it * AxisExitShift).toInt() }

    fun axisPopExit(): ExitTransition =
        fadeOut(tween(AxisOut, easing = Accel)) +
            slideOutVertically(tween(AxisOut, easing = Accel)) { (it * AxisShift).toInt() }

    // ---- bottom overlays (chat drawer, Aa panel) --------------------------

    fun sheetEnter(): EnterTransition =
        fadeIn(tween(SheetIn, easing = Decel)) +
            slideInVertically(tween(SheetIn, easing = Decel)) { it }

    fun sheetExit(): ExitTransition =
        fadeOut(tween(SheetOut, easing = Accel)) +
            slideOutVertically(tween(SheetOut, easing = Accel)) { it }

    // ---- reader top bars ---------------------------------------------------

    fun barEnter(): EnterTransition =
        fadeIn(tween(BarIn, easing = Decel)) +
            slideInVertically(tween(BarIn, easing = Decel)) { -it }

    fun barExit(): ExitTransition =
        fadeOut(tween(BarOut, easing = Accel)) +
            slideOutVertically(tween(BarOut, easing = Accel)) { -it }
}

/**
 * Activity-level axis transitions (list ↔ reader/settings activities), the
 * platform twin of [HpMotion.axisEnter]/[HpMotion.axisPopEnter]. Call
 * [applyHpAxisOpenTransition] in `onCreate`; [applyHpAxisCloseTransition]
 * runs from `finish()`. API 34+ uses `overrideActivityTransition`, older
 * levels fall back to `overridePendingTransition`.
 */
fun Activity.applyHpAxisOpenTransition() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_OPEN,
            R.anim.hp_axis_enter,
            R.anim.hp_axis_exit,
        )
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.hp_axis_enter, R.anim.hp_axis_exit)
    }
}

/** Must be called BEFORE `super.finish()` (per `overrideActivityTransition` contract). */
fun Activity.applyHpAxisCloseTransition() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            R.anim.hp_axis_pop_enter,
            R.anim.hp_axis_close_exit,
        )
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.hp_axis_pop_enter, R.anim.hp_axis_close_exit)
    }
}
