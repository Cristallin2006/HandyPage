package dev.handypage.app.agent

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * M33: a section-level index over a long document (the arXiv reflow EPUB),
 * built once per paper and handed to the agent as its "memory" of the paper.
 *
 * Pre-M33 the paper was flattened with Jsoup `.text()` (losing all section
 * structure) and then truncated at 6 000 chars — the agent saw only the
 * abstract and the start of the introduction and had to guess the rest.
 * With the index, the agent navigates like a human reader: it gets the
 * abstract + [outline] inline, then drills into sections with
 * [readSection] and locates passages with [search].
 *
 * Structure sources, in order of preference:
 * 1. ar5iv markup (`div.ltx_abstract`, `section.ltx_section`,
 *    `section.ltx_appendix`, `div.ltx_bibliography`) — the arXiv HTML pipeline;
 * 2. fallback: no recognised structure (PDF extraction, plain HTML) — the
 *    document is chunked into ~[FALLBACK_CHUNK_CHARS]-char parts.
 *
 * Section text keeps paragraph boundaries ("\n\n"-joined block elements) so
 * [search] can return whole paragraphs. Pure JVM (Jsoup only), testable.
 */
class PaperIndex private constructor(
    val abstractText: String,
    val sections: List<Section>,
) {

    data class Section(val heading: String, val text: String)

    val isEmpty: Boolean
        get() = abstractText.isBlank() && sections.isEmpty()

    /** Compact numbered outline: `0. Abstract`, then `1..N` sections. */
    fun outline(): String = buildString {
        if (abstractText.isNotBlank()) {
            appendLine("0. Abstract (${abstractText.length} chars)")
        }
        sections.forEachIndexed { i, s ->
            appendLine("${i + 1}. ${s.heading} (${s.text.length} chars)")
        }
    }.trim()

    /**
     * Reads a window of one section; [index] follows [outline] numbering
     * (0 = abstract, 1..N = sections), [offset] resumes a long section.
     * A continuation hint carrying the next offset is appended when the
     * section is not fully shown. Errors are returned as text for the model.
     */
    fun readSection(index: Int, offset: Int = 0, maxChars: Int = READ_WINDOW_CHARS): String {
        val text = when {
            index == 0 -> abstractText
            index in 1..sections.size -> sections[index - 1].text
            else -> return "error: no section $index (outline range: 0..${sections.size})"
        }
        if (text.isEmpty()) return "error: section $index is empty"
        if (offset < 0 || offset >= text.length) {
            return "error: offset $offset out of range (section length ${text.length})"
        }
        val end = minOf(offset + maxChars, text.length)
        val body = text.substring(offset, end)
        return if (end < text.length) {
            "$body\n…[本节共 ${text.length} 字符，已显示到 $end；用 offset=$end 续读]"
        } else {
            body
        }
    }

    /**
     * Paragraph-level keyword search across the abstract and all sections.
     * Returns up to [maxHits] hits, each labelled with its section.
     */
    fun search(query: String, maxHits: Int = 5, maxChars: Int = READ_WINDOW_CHARS): String {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return "error: empty query"
        val hits = StringBuilder()
        var count = 0
        fun scan(scope: String, text: String) {
            if (count >= maxHits || hits.length >= maxChars) return
            for (para in text.split("\n\n")) {
                if (para.lowercase().contains(q)) {
                    count++
                    hits.append("【").append(scope).append("】").append(para.trim()).append("\n\n")
                    if (count >= maxHits || hits.length >= maxChars) break
                }
            }
        }
        if (abstractText.isNotBlank()) scan("Abstract", abstractText)
        sections.forEachIndexed { i, s -> scan("${i + 1}. ${s.heading}", s.text) }
        if (count == 0) return "未找到包含 \"$query\" 的段落"
        return hits.toString().trim().take(maxChars)
    }

    companion object {
        /** One [readSection] window; matches the agent's tool-result cap. */
        const val READ_WINDOW_CHARS = 4_000

        /** Chunk size for the structureless fallback path. */
        const val FALLBACK_CHUNK_CHARS = 4_000

        /** Block elements whose text makes up a section's paragraphs. */
        private const val BLOCK_SELECTOR = "p, h1, h2, h3, h4, h5, h6, li, figcaption"

        /**
         * Builds the index from the EPUB's HTML documents (in reading order).
         * Returns an empty index when nothing textual is found.
         */
        fun fromHtmlDocuments(docs: List<String>): PaperIndex {
            val abstractText = StringBuilder()
            val sections = ArrayList<Section>()
            for (html in docs) {
                val body = Jsoup.parse(html).body()
                for (abs in body.select("div.ltx_abstract")) {
                    val t = blockText(abs)
                    if (t.isNotBlank()) {
                        if (abstractText.isNotEmpty()) abstractText.append("\n\n")
                        abstractText.append(t)
                    }
                    // Keep it out of section scans (it sits outside sections in
                    // ar5iv, but never count it twice).
                    abs.remove()
                }
                // ar5iv marks top-level sections ltx_section; subsections are
                // ltx_subsection and stay inside their parent's text.
                for (sec in body.select("section.ltx_section, section.ltx_appendix")) {
                    if (sec.parent() is Element &&
                        (sec.parent() as Element).`is`("section.ltx_section, section.ltx_appendix")
                    ) {
                        continue
                    }
                    val heading = sec.selectFirst("h2.ltx_title, h3.ltx_title")
                        ?.text()?.trim().orEmpty()
                        .ifEmpty { "Section ${sections.size + 1}" }
                    val text = blockText(sec)
                    if (text.isNotBlank()) sections += Section(heading, text)
                }
                for (bib in body.select("div.ltx_bibliography")) {
                    val t = blockText(bib)
                    if (t.isNotBlank()) sections += Section("References", t)
                }
            }
            if (sections.isEmpty()) {
                fallbackChunks(docs, sections)
            }
            return PaperIndex(abstractText.toString().trim(), sections)
        }

        /** Structureless documents: accumulate blocks into ~chunk-sized parts. */
        private fun fallbackChunks(docs: List<String>, out: MutableList<Section>) {
            val current = StringBuilder()
            fun flush() {
                if (current.isNotBlank()) {
                    out += Section("Part ${out.size + 1}", current.toString().trim())
                    current.clear()
                }
            }
            for (html in docs) {
                for (block in Jsoup.parse(html).body().select(BLOCK_SELECTOR)) {
                    val t = block.text().trim()
                    if (t.isEmpty()) continue
                    if (current.length + t.length > FALLBACK_CHUNK_CHARS && current.isNotBlank()) flush()
                    current.append(t).append("\n\n")
                }
            }
            flush()
        }

        /** Block text with paragraph breaks preserved ("\n\n"-joined). */
        private fun blockText(root: Element): String =
            root.select(BLOCK_SELECTOR)
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")
    }
}
