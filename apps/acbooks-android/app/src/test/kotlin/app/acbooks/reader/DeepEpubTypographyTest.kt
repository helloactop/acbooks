package app.acbooks.reader

import app.acbooks.reader.typography.DeepEpubTypography
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepEpubTypographyTest {
    private val linkUrl = "https://readium_assets/reader/acbooks-typography.css"

    @Test
    fun injectsIntoHeadWithoutChangingBodyOrPublisherStyles() {
        val html = """<?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head>
            <link rel="stylesheet" href="book.css" />
            </head><body><h1 id="chapter">第一章</h1><p>正文 <a href="#note">1</a></p>
            <aside id="note">脚注</aside></body></html>""".trimIndent()
        val result = DeepEpubTypography.injectStylesheet(html.toByteArray()).decodeToString()
        assertTrue(result.indexOf(linkUrl) in 1 until result.indexOf("</head>"))
        assertEquals(html.substringAfter("</head>"), result.substringAfter("</head>"))
        assertTrue(result.contains("""<link rel="stylesheet" href="book.css" />"""))
        assertEquals(result, DeepEpubTypography.injectStylesheet(result.toByteArray()).decodeToString())
    }

    @Test
    fun unstyledDocumentsStillGetReadiumDefaultCss() {
        val result = DeepEpubTypography.injectStylesheet(
            "<html><head><title>Chapter</title></head><body><h1>Title</h1></body></html>".toByteArray()
        ).decodeToString()
        assertTrue(result.contains(linkUrl))
        // Match Readium 3.4.0's detection of author-provided styles.
        assertFalse(result.contains("<link ", ignoreCase = true))
        assertFalse(result.contains(" style=", ignoreCase = true))
        assertFalse(Regex("<style.*?>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).containsMatchIn(result))
    }

    @Test
    fun ignoresFakeHeadClosingTagsInCommentsAndScripts() {
        val html = """<html><head><!-- </head> --><script>let x = "</head>";</script></head><body>Text</body></html>"""
        val result = DeepEpubTypography.injectStylesheet(html.toByteArray()).decodeToString()
        assertTrue(result.indexOf(linkUrl) > result.indexOf("</script>"))
        assertTrue(result.indexOf(linkUrl) < result.lastIndexOf("</head>"))
    }

    @Test
    fun unsupportedOrBrokenResourcesPassThrough() {
        val cases = listOf(
            "<html><body>No head</body></html>".toByteArray(),
            """<?xml version="1.0" encoding="ISO-8859-1"?><html><head></head></html>""".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28),
            ByteArray(2 * 1024 * 1024 + 1),
        )
        cases.forEach { assertArrayEquals(it, DeepEpubTypography.injectStylesheet(it)) }
    }
}
