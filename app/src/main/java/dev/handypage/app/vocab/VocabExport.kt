package dev.handypage.app.vocab

import java.text.SimpleDateFormat
import java.util.Date

/**
 * M40: CSV export of the whole vocab book (SAF CreateDocument target) — pure
 * JVM, no Android deps, unit-tested by VocabExportTest.
 */

private val CSV_HEADER =
    "word,lemma,phonetic,translation,definition,sentence,source_name,article_url,mastery,added_at"

/**
 * Renders [rows] (every row, ungrouped — a full backup) as CSV. Nulls become
 * empty fields; [dateFormat] (caller-built `SimpleDateFormat("yyyy-MM-dd
 * HH:mm", Locale.getDefault())`) formats added_at.
 */
fun vocabCsv(rows: List<VocabWord>, dateFormat: SimpleDateFormat): String =
    buildString {
        append(CSV_HEADER).append('\n')
        rows.forEach { w ->
            append(
                listOf(
                    w.word, w.lemma, w.phonetic, w.translation, w.definition,
                    w.sentence, w.sourceName, w.articleUrl,
                    w.mastery.toString(), dateFormat.format(Date(w.addedAt)),
                ).joinToString(",") { csvEscape(it) },
            ).append('\n')
        }
    }

/** RFC-4180-ish: quote when the field holds a quote/comma/CR/LF; double inner quotes. */
private fun csvEscape(field: String?): String {
    val v = field ?: ""
    return if (v.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) {
        "\"" + v.replace("\"", "\"\"") + "\""
    } else {
        v
    }
}
