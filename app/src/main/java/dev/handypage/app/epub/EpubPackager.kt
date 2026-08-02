package dev.handypage.app.epub

import dev.handypage.app.engine.ArticleContent
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs a single extracted article into a minimal valid EPUB 3 file.
 *
 * Port of tools/epub/make_demo_epub.py, specialised to one chapter:
 * mimetype first entry (STORED), container.xml, content.opf, nav.xhtml,
 * ch1.xhtml. Pure JVM (java.util.zip), so it is unit-testable off-device.
 */
object EpubPackager {

    private const val CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

    private const val OPF_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">%1${'$'}s</dc:identifier>
    <dc:title>%2${'$'}s</dc:title>
    <dc:language>en</dc:language>
    <dc:creator>%3${'$'}s</dc:creator>
    <dc:source>%4${'$'}s</dc:source>
    <dc:date>%5${'$'}s</dc:date>
    <meta property="dcterms:modified">%5${'$'}s</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="ch1"/>
  </spine>
</package>
"""

    private const val NAV_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
  <title>%1${'$'}s</title>
  <meta charset="utf-8"/>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Table of Contents</h1>
    <ol>
      <li><a href="ch1.xhtml">%1${'$'}s</a></li>
    </ol>
  </nav>
</body>
</html>
"""

    // Tiny built-in reading stylesheet; keep it small on purpose.
    // h1 stays left even when the user justifies body text (M9): a
    // multi-line justified title gets ugly stretched word spacing.
    private const val READER_CSS =
        "body { font-family: Georgia, 'Times New Roman', serif; line-height: 1.6; margin: 5%; }\n" +
            "h1 { font-size: 1.4em; line-height: 1.3; text-align: left; }\n" +
            "img { max-width: 100%; height: auto; }"

    // lang="en" lets CSS `hyphens: auto` (Readium justify preference, M9)
    // find the right hyphenation dictionary — without it justification
    // falls back to stretched word spacing.
    private const val XHTML_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en" xml:lang="en">
<head>
  <title>%1${'$'}s</title>
  <meta charset="utf-8"/>
  <style type="text/css">
%3${'$'}s
  </style>
</head>
<body>
  <h1>%1${'$'}s</h1>
%2${'$'}s
</body>
</html>
"""

    // 1980-01-01T00:00:00Z in epoch millis (DOS epoch), for reproducible zips.
    private const val FIXED_ENTRY_TIME = 315532800000L

    fun pack(article: ArticleContent, sourceName: String, outFile: File): File {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
        val uid = "urn:sha256:" + sha256Hex(article.sourceUrl).substring(0, 32)

        val files = linkedMapOf(
            "META-INF/container.xml" to CONTAINER_XML,
            "OEBPS/content.opf" to OPF_TEMPLATE.format(
                uid, esc(article.title), esc(sourceName), esc(article.sourceUrl), now,
            ),
            "OEBPS/nav.xhtml" to NAV_TEMPLATE.format(esc(article.title)),
            "OEBPS/ch1.xhtml" to XHTML_TEMPLATE.format(
                esc(article.title), toXhtml(article.bodyHtml), READER_CSS,
            ),
        )

        outFile.parentFile?.mkdirs()
        ZipOutputStream(outFile.outputStream().buffered()).use { z ->
            // mimetype MUST be the first entry and stored uncompressed (EPUB spec).
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.UTF_8)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                crc = crc32(mimeBytes)
                time = FIXED_ENTRY_TIME
            }
            z.putNextEntry(mimeEntry)
            z.write(mimeBytes)
            z.closeEntry()
            for ((name, content) in files) {
                val bytes = content.toByteArray(Charsets.UTF_8)
                z.putNextEntry(
                    ZipEntry(name).apply {
                        method = ZipEntry.DEFLATED
                        time = FIXED_ENTRY_TIME
                    },
                )
                z.write(bytes)
                z.closeEntry()
            }
        }
        return outFile
    }

    /** XML-escape text interpolated into templates (superset of saxutils.escape). */
    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    /**
     * Re-serializes an HTML fragment as well-formed XHTML (void elements
     * self-closed, XML escaping) so the chapter parses as XML in readers.
     */
    private fun toXhtml(bodyHtml: String): String {
        val doc = Jsoup.parse(bodyHtml)
        stripForeignNamespaces(doc)
        doc.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .prettyPrint(false)
        return doc.body().html()
    }

    /**
     * Readers parse XHTML namespace-aware, but scraped HTML carries prefixed
     * attributes (`xlink:href` on SVG `<use>`, CMS junk like `o:…`) whose
     * xmlns declarations don't survive article extraction — the WebView then
     * rejects the whole chapter with "Namespace prefix … not defined" and
     * shows an error banner (Nature registration-wall SVGs, 2026-07-26).
     *
     * Strip every prefixed attribute except the XML-predefined `xml:*`
     * (xml:lang etc.), and unwrap prefixed elements keeping their text. The
     * lost references point to icon sprites we never ship, so nothing
     * visible is removed.
     */
    private fun stripForeignNamespaces(doc: Document) {
        for (el in doc.allElements) {
            val doomed = el.attributes().asList()
                .map { it.key }
                .filter { ':' in it && !it.startsWith("xml:") }
            doomed.forEach { el.removeAttr(it) }
        }
        doc.allElements
            .filter { ':' in it.tagName() }
            .forEach { it.unwrap() }
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun crc32(bytes: ByteArray): Long =
        CRC32().apply { update(bytes) }.value
}
