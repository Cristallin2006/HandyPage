/*
 * Ported and simplified from org.opendataloader.pdf (OpenDataLoader PDF)
 * Copyright 2025-2026 Hancom Inc. Licensed under Apache License 2.0.
 * Modified: rewritten in Kotlin, simplified for arXiv single/two-column papers.
 */
package dev.handypage.app.pdf

import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns positioned [TextLine]s into semantic HTML (`<h2>`/`<h3>`/`<p>`).
 *
 * Two ideas are ported from the reference implementation:
 *  - Header/footer removal (processors/HeaderFooterProcessor.java): text
 *    repeating in the top/bottom page zones across most pages is a running
 *    header or footer and is dropped, along with bare page numbers.
 *  - Heading detection (utils/TextNodeStatistics.java): the document's font
 *    size mode is the body size; lines printed noticeably larger (or bold
 *    with a numbered-section prefix) are headings.
 *
 * arXiv-specific simplifications of our own: first-page title/author block
 * skip (metadata comes from the arXiv API), orphan footnote-mark removal,
 * paragraph merging and hyphen repair.
 */
object PdfLayoutParser {

    /** Header/footer candidate zone: this fraction of page height at top and bottom. */
    private const val EDGE_ZONE_FRACTION = 0.08f

    /** A zone pattern must appear on at least this fraction of pages to count as running furniture. */
    private const val REPEAT_PAGE_FRACTION = 0.6

    /** Max vertical gap (× previous line height) for joining two lines into one paragraph. */
    private const val PARA_GAP_FACTOR = 1.4f

    /** The previous line must end within this fraction of page width from the column's right edge. */
    private const val RIGHT_EDGE_FRACTION = 0.04f

    /** Left-edge tolerance (pt) for "same column". */
    private const val X0_TOLERANCE = 4f

    /**
     * Font size multiples of the body size that mark h2 / h3 headings. The h3
     * factor is 1.25, not 1.15: bold in-figure labels on real arXiv papers
     * sit around 1.15-1.2× body and were promoted to spurious headings.
     */
    private const val H2_SIZE_FACTOR = 1.4f
    private const val H3_SIZE_FACTOR = 1.25f

    /** A standalone line starting with "abstract" (case-insensitive) and short enough to be a heading. */
    private const val ABSTRACT_HEADING_MAX_LEN = 20

    private val PAGE_NUMBER = Regex("^\\d{1,4}$")
    private val NUMBERED_HEADING = Regex("^(\\d+\\.?)+\\s+\\p{Lu}")
    private val HYPHEN_BREAK = Regex("(\\p{L})-$")
    private val DIGIT = Regex("\\d")
    private val WHITESPACE = Regex("\\s+")
    private val ABSTRACT_HEADING = Regex("^\\s*abstract\\b", RegexOption.IGNORE_CASE)

    /** Author-block superscripts and footnote markers standing alone on a line. */
    private val ORPHAN_MARK = Regex("^[\\d†‡§*¶]{1,3}$")

    fun toHtml(lines: List<TextLine>, pageWidth: Float, pageHeight: Float): String {
        val kept = dropHeadersAndFooters(lines.filter { it.text.isNotBlank() }, pageHeight)
        if (kept.isEmpty()) return ""

        // Reading order, page by page.
        val orderedPages = kept.groupBy { it.page }
            .toSortedMap()
            .mapValues { (_, pageLines) -> XYCut.order(pageLines, pageWidth) }
            .toMutableMap()

        // arXiv title/authors come from API metadata, so the PDF's own
        // first-page title/author block is dead weight (and its superscript
        // soup pollutes the text): drop everything before the "Abstract"
        // heading line and emit that line as an h3. No Abstract line -> skip
        // nothing (non-arXiv or abstract-less documents keep their first page).
        val forcedH3 = Collections.newSetFromMap(IdentityHashMap<TextLine, Boolean>())
        orderedPages[1]?.let { firstPage ->
            val abstractIndex = firstPage.indexOfFirst {
                it.text.trim().length <= ABSTRACT_HEADING_MAX_LEN && ABSTRACT_HEADING.containsMatchIn(it.text)
            }
            if (abstractIndex >= 0) {
                forcedH3 += firstPage[abstractIndex]
                orderedPages[1] = firstPage.drop(abstractIndex)
            }
        }

        val ordered = orderedPages.values.flatten()
            .filterNot { line -> !inEdgeZone(line, pageHeight) && ORPHAN_MARK.matches(line.text.trim()) }
        if (ordered.isEmpty()) return ""
        val bodySize = bodyFontSize(ordered)

        val out = StringBuilder()
        val paragraph = StringBuilder()
        var prev: TextLine? = null
        var columnRight = 0f // running estimate of the current column's right edge

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                val text = paragraph.toString().replace(WHITESPACE, " ").trim()
                out.append("<p>").append(esc(text)).append("</p>")
                paragraph.setLength(0)
            }
        }

        for (line in ordered) {
            when (if (line in forcedH3) 3 else headingLevel(line, bodySize)) {
                2 -> {
                    flushParagraph()
                    prev = null
                    out.append("<h2>").append(esc(line.text.trim())).append("</h2>")
                }
                3 -> {
                    flushParagraph()
                    prev = null
                    out.append("<h3>").append(esc(line.text.trim())).append("</h3>")
                }
                else -> {
                    val p = prev
                    val sameColumn = p != null && abs(line.x0 - p.x0) <= X0_TOLERANCE
                    // A wrapped line (not a new paragraph) starts in the same
                    // column, follows within one leading, and its predecessor
                    // reached (nearly) the column's right edge. A LaTeX-style
                    // indented first line fails sameColumn -> new paragraph.
                    val continues = p != null && sameColumn &&
                        line.y0 - p.y1 < PARA_GAP_FACTOR * p.height.coerceAtLeast(1f) &&
                        p.x1 >= columnRight - pageWidth * RIGHT_EDGE_FRACTION
                    if (p == null || !continues) {
                        flushParagraph()
                        paragraph.append(line.text.trim())
                        columnRight = line.x1
                    } else {
                        if (HYPHEN_BREAK.containsMatchIn(paragraph)) {
                            // "transfor-" + "mer" -> "transformer": unwrap the hyphen.
                            paragraph.setLength(paragraph.length - 1)
                        } else {
                            paragraph.append(' ')
                        }
                        paragraph.append(line.text.trim())
                        columnRight = maxOf(columnRight, line.x1)
                    }
                    prev = line
                }
            }
        }
        flushParagraph()
        return out.toString()
    }

    /**
     * Drops running headers/footers: lines in the top/bottom 8% of the page
     * whose normalised text (lowercase, digits -> '#', whitespace folded)
     * appears on >= 60% of pages, plus bare page numbers in those zones.
     * Cross-page statistics need at least two pages; on a single page only
     * page numbers are dropped (first-page footnotes must survive).
     */
    private fun dropHeadersAndFooters(lines: List<TextLine>, pageHeight: Float): List<TextLine> {
        val pages = lines.groupBy { it.page }
        val repeated = if (pages.size >= 2) {
            val patternPages = mutableMapOf<String, MutableSet<Int>>()
            for ((page, pageLines) in pages) {
                pageLines.filter { inEdgeZone(it, pageHeight) }
                    .map { normalize(it.text) }
                    .filter { it.isNotEmpty() }
                    .toSet()
                    .forEach { pattern -> patternPages.getOrPut(pattern) { mutableSetOf() }.add(page) }
            }
            val minPages = pages.size * REPEAT_PAGE_FRACTION
            patternPages.filterValues { it.size >= minPages }.keys
        } else {
            emptySet()
        }
        return lines.filterNot { line ->
            inEdgeZone(line, pageHeight) &&
                (normalize(line.text) in repeated || PAGE_NUMBER.matches(line.text.trim()))
        }
    }

    private fun inEdgeZone(line: TextLine, pageHeight: Float): Boolean {
        val zone = pageHeight * EDGE_ZONE_FRACTION
        return line.y1 <= zone || line.y0 >= pageHeight - zone
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(DIGIT, "#").replace(WHITESPACE, " ").trim()

    /** Body font size: the font-size mode (0.5pt buckets), weighted by text length. */
    private fun bodyFontSize(lines: List<TextLine>): Float {
        val weights = mutableMapOf<Float, Int>()
        for (line in lines) {
            val bucket = (line.fontSize * 2).roundToInt() / 2f
            weights[bucket] = (weights[bucket] ?: 0) + line.text.length
        }
        return weights.maxByOrNull { it.value }?.key ?: 10f
    }

    /** 0 = body text, 2 = h2, 3 = h3. Heading lines never join paragraphs. */
    private fun headingLevel(line: TextLine, bodySize: Float): Int = when {
        line.fontSize >= H2_SIZE_FACTOR * bodySize -> 2
        line.fontSize >= H3_SIZE_FACTOR * bodySize -> 3
        line.bold && NUMBERED_HEADING.containsMatchIn(line.text.trim()) -> 3
        else -> 0
    }

    /** HTML-escape body text (& < >). */
    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(c)
            }
        }
    }
}
