/*
 * Ported and simplified from org.opendataloader.pdf (OpenDataLoader PDF)
 * Copyright 2025-2026 Hancom Inc. Licensed under Apache License 2.0.
 * Modified: rewritten in Kotlin, simplified for arXiv single/two-column papers.
 */
package dev.handypage.app.pdf

import kotlin.math.max

/**
 * Reading-order detection for one PDF page: a simplified XY-Cut++
 * (processors/readingorder/XYCutPlusPlusSorter.java in the reference).
 *
 * Recursive projection-profile segmentation:
 *  1. Pre-mask cross-layout lines (full-width titles, spanning equations) —
 *     they bridge any vertical gutter, so cut detection runs on the remaining
 *     "core" lines and the masked lines are merged back by y position.
 *  2. Y-cut: a horizontal blank band crossing the whole region (gap between
 *     consecutive lines' projected y-extents > [Y_GAP_FACTOR] × median line
 *     height) splits the region into top/bottom bands, output top-first.
 *  3. X-cut: otherwise a vertical blank gutter wider than
 *     [X_GAP_PAGE_FRACTION] × page width (clear for all core lines, i.e.
 *     spanning the band's full height) splits it into left/right columns,
 *     output left-first.
 *  4. Otherwise the region is a single block: plain (y, x) order.
 *
 * Recursion is capped at [MAX_DEPTH]. The pre-masking in step 1 is what lets
 * "通栏标题 + 双栏正文" pages come out as title, left column, right column.
 */
object XYCut {

    private const val MAX_DEPTH = 4

    /** Y-cut threshold: vertical gap as a multiple of the region's median line height. */
    private const val Y_GAP_FACTOR = 1.8f

    /** X-cut threshold: gutter width as a fraction of the page width. */
    private const val X_GAP_PAGE_FRACTION = 0.015f

    /** A line this wide (relative to its region) is treated as cross-layout and masked. */
    private const val WIDE_LINE_FRACTION = 0.7f

    /** Returns [lines] (one page) in reading order. */
    fun order(lines: List<TextLine>, pageWidth: Float): List<TextLine> =
        segment(lines, pageWidth, depth = 0)

    private fun segment(region: List<TextLine>, pageWidth: Float, depth: Int): List<TextLine> {
        if (region.size <= 1) return region
        if (depth >= MAX_DEPTH) return byYThenX(region)

        val regionWidth = region.maxOf { it.x1 } - region.minOf { it.x0 }
        // Mask cross-layout lines, but never ALL of them: when every line is
        // wide (plain single-column text) there is nothing to mask — keeping
        // them in `wide` while `core` also holds them would duplicate every
        // line at merge-back time.
        val wide = region.filter { it.width >= WIDE_LINE_FRACTION * regionWidth }
            .let { if (it.size == region.size) emptyList() else it }
        val core = region - wide.toSet()
        if (core.size <= 1) return byYThenX(region)

        findHorizontalCut(core)?.let { cutY ->
            val above = core.filter { centerY(it) < cutY }
            val below = core.filter { centerY(it) >= cutY }
            if (above.isNotEmpty() && below.isNotEmpty()) {
                val ordered = segment(above, pageWidth, depth + 1) + segment(below, pageWidth, depth + 1)
                return mergeWide(ordered, wide)
            }
        }

        findVerticalCut(core, pageWidth)?.let { cutX ->
            val left = core.filter { centerX(it) < cutX }
            val right = core.filter { centerX(it) >= cutX }
            if (left.isNotEmpty() && right.isNotEmpty()) {
                val ordered = segment(left, pageWidth, depth + 1) + segment(right, pageWidth, depth + 1)
                return mergeWide(ordered, wide)
            }
        }

        return mergeWide(byYThenX(core), wide)
    }

    /**
     * Largest horizontal blank band across the region (y projection with a
     * running bottom edge), or null when no gap reaches the threshold.
     */
    private fun findHorizontalCut(lines: List<TextLine>): Float? {
        if (lines.size < 2) return null
        val minGap = Y_GAP_FACTOR * median(lines.map { it.height })
        val sorted = lines.sortedBy { it.y0 }
        var prevBottom = sorted.first().y1
        var bestGap = 0f
        var bestPosition: Float? = null
        for (line in sorted.drop(1)) {
            val gap = line.y0 - prevBottom
            if (gap > bestGap) {
                bestGap = gap
                bestPosition = (prevBottom + line.y0) / 2f
            }
            prevBottom = max(prevBottom, line.y1)
        }
        return bestPosition?.takeIf { bestGap >= minGap }
    }

    /**
     * Largest vertical blank gutter in the region (x projection with a
     * running right edge), or null. A gutter found this way is clear for
     * every core line, so it spans the region's full height by construction.
     */
    private fun findVerticalCut(lines: List<TextLine>, pageWidth: Float): Float? {
        if (lines.size < 2) return null
        val minGap = X_GAP_PAGE_FRACTION * pageWidth
        val sorted = lines.sortedBy { it.x0 }
        var prevRight = sorted.first().x1
        var bestGap = 0f
        var bestPosition: Float? = null
        for (line in sorted.drop(1)) {
            val gap = line.x0 - prevRight
            if (gap > bestGap) {
                bestGap = gap
                bestPosition = (prevRight + line.x0) / 2f
            }
            prevRight = max(prevRight, line.x1)
        }
        return bestPosition?.takeIf { bestGap > minGap }
    }

    /** Re-inserts masked cross-layout lines at their vertical position (XY-Cut++ phase 4). */
    private fun mergeWide(ordered: List<TextLine>, wide: List<TextLine>): List<TextLine> {
        if (wide.isEmpty()) return ordered
        if (ordered.isEmpty()) return byYThenX(wide)
        val result = ordered.toMutableList()
        for (line in byYThenX(wide)) {
            val index = result.indexOfFirst { it.y0 > line.y0 }
            if (index < 0) result.add(line) else result.add(index, line)
        }
        return result
    }

    private fun byYThenX(lines: List<TextLine>): List<TextLine> =
        lines.sortedWith(compareBy({ it.y0 }, { it.x0 }))

    private fun centerX(line: TextLine): Float = (line.x0 + line.x1) / 2f

    private fun centerY(line: TextLine): Float = (line.y0 + line.y1) / 2f

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
