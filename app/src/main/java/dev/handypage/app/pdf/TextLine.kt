package dev.handypage.app.pdf

/**
 * One physical text line extracted from a PDF page, in page coordinates with
 * the origin at the TOP-LEFT of the page and y growing downwards (PDFBox's
 * direction-adjusted coordinates, see [PdfChars]).
 *
 * Pure Kotlin data — no Android or PDFBox types — so the layout pipeline is
 * unit-testable on the JVM.
 */
data class TextLine(
    val page: Int,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val text: String,
    val fontSize: Float,
    val bold: Boolean,
) {
    val height get() = y1 - y0
    val width get() = x1 - x0
}
