package dev.handypage.app

import dev.handypage.app.engine.ArticleContent
import dev.handypage.app.epub.EpubPackager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** EpubPackager must produce a structurally valid EPUB 3 (mirrors make_demo_epub.py's self-check). */
class EpubPackagerTest {

    @Test
    fun packProducesValidEpub() {
        val article = ArticleContent(
            title = "A <Tricky> \"Title\" & Co.",
            bodyHtml = "<p>Hello <b>world</b></p><img src=\"http://example.com/x.png\"><p>Second para.</p>",
            sourceUrl = "https://example.com/news/123",
        )
        val out = File.createTempFile("handypage-test", ".epub")
        try {
            EpubPackager.pack(article, "Example Source", out)
            assertTrue("epub not written", out.length() > 0)

            ZipFile(out).use { z ->
                val entries = z.entries().toList()
                // mimetype: first entry, STORED (EPUB spec)
                assertEquals("mimetype", entries.first().name)
                assertEquals(ZipEntry.STORED, entries.first().method)

                val names = entries.map { it.name }.toSet()
                for (expected in listOf(
                    "META-INF/container.xml", "OEBPS/content.opf", "OEBPS/nav.xhtml", "OEBPS/ch1.xhtml",
                )) {
                    assertTrue("missing entry $expected in $names", expected in names)
                }

                val opf = z.read("OEBPS/content.opf")
                // XML-escaped title + source must land in the OPF metadata.
                assertTrue("escaped title missing from opf", opf.contains("A &lt;Tricky&gt; &quot;Title&quot; &amp; Co."))
                assertTrue("dc:source missing from opf", opf.contains("<dc:source>https://example.com/news/123</dc:source>"))

                // OPF and both XHTML docs must be well-formed XML.
                for (name in listOf("OEBPS/content.opf", "OEBPS/nav.xhtml", "OEBPS/ch1.xhtml")) {
                    parseXml(z.read(name))
                }
                val ch1 = z.read("OEBPS/ch1.xhtml")
                assertTrue("title <h1> missing from chapter", ch1.contains("<h1>A &lt;Tricky&gt; &quot;Title&quot; &amp; Co.</h1>"))
                assertTrue("body missing from chapter", ch1.contains("<p>Hello <b>world</b></p>"))
            }
        } finally {
            out.delete()
        }
    }

    @Test
    fun packEmbedsImagesIntoPackageAndManifest() {
        // M28: image bytes land under OEBPS/images/, and every image is
        // declared in the OPF manifest with the media-type for its extension.
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val article = ArticleContent(
            title = "With images",
            bodyHtml = """<p>text</p><img src="images/img-abc123def456.png"/>""",
            sourceUrl = "https://example.com/with-images",
            images = linkedMapOf(
                "images/img-abc123def456.png" to png,
                "images/img-999888777666.jpeg" to byteArrayOf(1, 2, 3),
            ),
        )
        val out = File.createTempFile("handypage-test-img", ".epub")
        try {
            EpubPackager.pack(article, "Example Source", out)
            ZipFile(out).use { z ->
                val names = z.entries().toList().map { it.name }
                assertTrue("png missing from package", "OEBPS/images/img-abc123def456.png" in names)
                assertTrue("jpeg missing from package", "OEBPS/images/img-999888777666.jpeg" in names)
                assertEquals(
                    "png bytes corrupted",
                    png.toList(),
                    z.getInputStream(z.getEntry("OEBPS/images/img-abc123def456.png"))
                        .readBytes().toList(),
                )
                val opf = z.read("OEBPS/content.opf")
                assertTrue(
                    "manifest missing png item",
                    opf.contains("""<item id="img0" href="images/img-abc123def456.png" media-type="image/png"/>"""),
                )
                assertTrue(
                    "manifest missing jpeg item",
                    opf.contains("""<item id="img1" href="images/img-999888777666.jpeg" media-type="image/jpeg"/>"""),
                )
                parseXml(opf)
            }
        } finally {
            out.delete()
        }
    }

    @Test
    fun packStripsUndeclaredNamespaces() {
        // Scraped HTML in the wild carries prefixed junk: SVG <use xlink:href>
        // (Nature registration wall) and CMS tags like <o:p>. The xmlns
        // declarations don't survive extraction, and the WebView parses ch1
        // namespace-aware — an undeclared prefix rejects the whole chapter.
        val article = ArticleContent(
            title = "NS test",
            bodyHtml = """<p xml:lang="en">Real text</p>
                |<svg width="24" height="24"><use xlink:href="#icon-x"/></svg>
                |<o:p>kept text</o:p>""".trimMargin(),
            sourceUrl = "https://example.com/ns",
        )
        val out = File.createTempFile("handypage-test-ns", ".epub")
        try {
            EpubPackager.pack(article, "Example Source", out)
            ZipFile(out).use { z ->
                val ch1 = z.read("OEBPS/ch1.xhtml")
                assertFalse("xlink attribute survived", ch1.contains("xlink"))
                assertFalse("prefixed element survived", ch1.contains("<o:p"))
                assertTrue("text of unwrapped element lost", ch1.contains("kept text"))
                assertTrue("predefined xml: prefix must survive", ch1.contains("xml:lang"))
                // The assertion that would have caught the Nature banner:
                // parse the chapter namespace-aware, like the WebView does.
                parseXml(ch1, namespaceAware = true)
            }
        } finally {
            out.delete()
        }
    }

    private fun ZipFile.read(name: String): String =
        getInputStream(getEntry(name)).readBytes().toString(Charsets.UTF_8)

    private fun parseXml(xml: String, namespaceAware: Boolean = false) {
        val dbf = DocumentBuilderFactory.newInstance()
        // dcterms:modified is a reserved OPF 3 prefix and deliberately not
        // namespace-declared (same as the Python reference), so OPF parsing
        // stays namespace-unaware: that checks well-formedness only. Chapter
        // XHTML must additionally survive namespace-aware parsing because
        // that is what the reader's WebView does.
        dbf.isNamespaceAware = namespaceAware
        dbf.isValidating = false
        dbf.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }
}
