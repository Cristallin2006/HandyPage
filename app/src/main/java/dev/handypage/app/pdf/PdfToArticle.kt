package dev.handypage.app.pdf

import dev.handypage.app.engine.ArticleContent
import java.io.File
import java.io.IOException

/**
 * High-level PDF -> ArticleContent pipeline: [PdfChars] extraction,
 * [PdfLayoutParser] layout analysis, byline prepend.
 *
 * Blocking IO and CPU-heavy — call from Dispatchers.IO.
 */
object PdfToArticle {

    /**
     * Minimum letters/non-whitespace ratio in the extracted text. Below this
     * the "text layer" is garbage (scanned page or broken ToUnicode CMap
     * yielding symbol junk) and we refuse to build an article from it.
     */
    private const val MIN_LETTER_RATIO = 0.5f

    fun convert(pdfFile: File, title: String, authors: List<String>, absUrl: String): ArticleContent {
        val extracted = PdfChars.extract(pdfFile)
        if (extracted.lines.isEmpty() || isGarbage(extracted.lines)) {
            throw IOException("PDF text extraction failed")
        }
        val html = PdfLayoutParser.toHtml(extracted.lines, extracted.pageWidth, extracted.pageHeight)
        if (html.isBlank()) throw IOException("PDF text extraction failed")
        val byline = if (authors.isEmpty()) {
            ""
        } else {
            "<p><i>by ${authors.joinToString(", ") { esc(it) }}</i></p>"
        }
        return ArticleContent(title = title, bodyHtml = byline + html, sourceUrl = absUrl)
    }

    private fun isGarbage(lines: List<TextLine>): Boolean {
        var letters = 0
        var nonSpace = 0
        for (line in lines) {
            for (c in line.text) {
                if (!c.isWhitespace()) {
                    nonSpace++
                    if (c.isLetter()) letters++
                }
            }
        }
        return nonSpace == 0 || letters.toFloat() / nonSpace < MIN_LETTER_RATIO
    }

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
