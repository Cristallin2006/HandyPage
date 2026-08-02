package dev.handypage.app.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Extracts positioned text lines from a PDF file via pdfbox-android.
 *
 * This is the ONLY class in the app that touches PDFBox directly — keep it
 * thin. Everything downstream ([XYCut], [PdfLayoutParser]) works on plain
 * [TextLine] geometry and stays JVM-testable.
 *
 * Word boundaries come from PDFBox's own segmentation: the stripper
 * accumulates glyphs, applies its spacing-tolerance heuristics (average char
 * width based, tolerant of pdftex fonts that encode no space glyphs), and
 * calls writeString(String, List<TextPosition>) once per detected word —
 * verified against pdfbox-android 2.0.27.0 bytecode (writePage ->
 * normalize -> writeLine). We collect words there. A previous version split
 * characters by hand with a fixed gap-vs-fontSize threshold; on real pdftex
 * output that both dropped real spaces ("dopen-endedexploration.") and
 * invented runs of fake ones ("code        is"), so the hand-rolled heuristic
 * was deleted in favour of PDFBox's.
 *
 * Coordinates come from TextPosition.getXDirAdj()/getYDirAdj(), i.e. origin
 * at the top-left of the page, y growing downwards.
 *
 * Note: pdfbox-android requires PDFBoxResourceLoader.init(context) once
 * before first use (app startup, outside this class). Blocking IO — call
 * from Dispatchers.IO.
 */
object PdfChars {

    /** Extraction result: all lines of the document plus the first page's media box size. */
    data class PdfLines(
        val lines: List<TextLine>,
        val pageWidth: Float,
        val pageHeight: Float,
    )

    fun extract(pdfFile: File): PdfLines {
        PDDocument.load(pdfFile).use { document ->
            if (document.numberOfPages == 0) return PdfLines(emptyList(), 0f, 0f)
            val mediaBox = document.getPage(0).mediaBox
            val lines = mutableListOf<TextLine>()
            for (page in 1..document.numberOfPages) {
                lines += buildLinesFromWords(collectWords(document, page), page)
            }
            return PdfLines(lines, mediaBox.width, mediaBox.height)
        }
    }

    /** Collects one page's words via PDFTextStripper's per-word callback. */
    private fun collectWords(document: PDDocument, page: Int): List<RawWord> {
        val words = mutableListOf<RawWord>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(word: String, positions: MutableList<TextPosition>) {
                if (word.isBlank() || positions.isEmpty()) return
                val fontSize = positions.maxOf { it.fontSizeInPt }
                if (fontSize <= 0f) return
                val x0 = positions.minOf { it.xDirAdj }
                val x1 = positions.maxOf { it.xDirAdj + it.widthDirAdj }
                val y0 = positions.minOf { it.yDirAdj }
                val y1 = positions.maxOf {
                    it.yDirAdj + if (it.heightDir > 0f) it.heightDir else it.fontSizeInPt
                }
                words += RawWord(
                    x = x0,
                    y = y0,
                    w = x1 - x0,
                    h = y1 - y0,
                    fontSize = fontSize,
                    bold = positions.any { isBoldFont(it.font?.name.orEmpty()) },
                    text = word,
                )
            }
        }
        stripper.startPage = page
        stripper.endPage = page
        stripper.getText(document) // output discarded; writeString did the work
        return words
    }

    private fun isBoldFont(fontName: String): Boolean =
        fontName.contains("Bold", ignoreCase = true) ||
            fontName.contains("Black", ignoreCase = true) ||
            fontName.contains("Semibold", ignoreCase = true)
}

/** One PDFBox-segmented word with page geometry (android-free, JVM-testable). */
internal data class RawWord(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val fontSize: Float,
    val bold: Boolean,
    val text: String,
)

private val WHITESPACE_RUN = Regex("\\s+")

/** Vertical tolerance for clustering words into one line, × font size. */
private const val LINE_Y_TOLERANCE = 0.4f

/**
 * Horizontal gap (× font size) between adjacent words that splits a y-cluster
 * into two lines: guards against merging the left/right column words of one
 * row into a single line (arXiv/revtex columnsep ≈ 1.8× body size, so any gap
 * above it is a gutter, not a word space).
 */
private const val WORD_LINE_GAP_FACTOR = 1.8f

/**
 * Assembles PDFBox-segmented words into [TextLine]s: cluster by baseline
 * (y tolerance [LINE_Y_TOLERANCE] × font size), split at column-scale x gaps
 * ([WORD_LINE_GAP_FACTOR]), join with single spaces and fold residual
 * whitespace runs. Pure Kotlin so JVM unit tests cover it without PDFBox.
 */
internal fun buildLinesFromWords(words: List<RawWord>, page: Int): List<TextLine> {
    val kept = words.filter { it.text.isNotBlank() }
    if (kept.isEmpty()) return emptyList()
    val rows = mutableListOf<MutableList<RawWord>>()
    var rowY = 0f
    var rowFontSize = 0f
    for (w in kept.sortedWith(compareBy({ it.y }, { it.x }))) {
        val tolerance = LINE_Y_TOLERANCE * max(w.fontSize, rowFontSize)
        if (rows.isEmpty() || abs(w.y - rowY) > tolerance) {
            rows += mutableListOf(w)
            rowY = w.y
            rowFontSize = w.fontSize
        } else {
            rows.last() += w
        }
    }
    val lines = mutableListOf<TextLine>()
    for (row in rows) {
        row.sortBy { it.x }
        var segmentStart = 0
        for (i in 1..row.size) {
            val endsSegment = i == row.size ||
                row[i].x - (row[i - 1].x + row[i - 1].w) > WORD_LINE_GAP_FACTOR * row[i - 1].fontSize
            if (endsSegment) {
                lines += wordsToTextLine(row.subList(segmentStart, i), page)
                segmentStart = i
            }
        }
    }
    return lines.filter { it.text.isNotBlank() }
}

private fun wordsToTextLine(segment: List<RawWord>, page: Int): TextLine =
    TextLine(
        page = page,
        x0 = segment.minOf { it.x },
        y0 = segment.minOf { it.y },
        x1 = segment.maxOf { it.x + it.w },
        y1 = segment.maxOf { it.y + it.h },
        text = segment.joinToString(" ") { it.text }
            .replace(WHITESPACE_RUN, " ")
            .trim(),
        fontSize = segment.map { it.fontSize }.average().toFloat(),
        bold = segment.any { it.bold },
    )
