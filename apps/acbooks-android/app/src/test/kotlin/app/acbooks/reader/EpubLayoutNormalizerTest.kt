package app.acbooks.reader

import app.acbooks.reader.typography.DeepEpubTypography
import app.acbooks.reader.typography.EpubHeadingHint
import app.acbooks.reader.typography.EpubLayoutNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubLayoutNormalizerTest {
    private fun html(body: String) = "<html><head><title>Book</title></head><body>$body</body></html>"

    @Test
    fun marksOnlyLeadingWrapperChainAndPreservesSource() {
        val input = html("""<section id="chapter" style="padding-top:10em"><div class='inner'><header><a id="anchor"></a><h1>Title</h1></header><p>Body &amp; text</p></div></section><section><h2>Later</h2></section>""")
        val result = EpubLayoutNormalizer.normalize(input)
        assertEquals(4, Regex("""data-acbooks-leading="true"""").findAll(result).count())
        assertTrue(result.contains("""<section data-acbooks-leading="true" id="chapter""""))
        assertTrue(result.contains("""<h1 data-acbooks-leading="true">Title"""))
        assertTrue(result.contains("<section><h2>Later</h2></section>"))
        assertEquals(input, result.replace(""" data-acbooks-leading="true"""", ""))
        assertEquals(result, EpubLayoutNormalizer.normalize(result))
    }

    @Test
    fun doesNotCollapseWrappersAfterContentOrIllustrations() {
        listOf(
            "<p>Introduction</p><h1>Title</h1>",
            "<img src='illustration.jpg'/><h1>Title</h1>",
            "Opening text<h1>Title</h1>",
            "<h1><img src='title.jpg'/>Title</h1>",
        ).forEach { body ->
            val result = EpubLayoutNormalizer.normalize(html("<section>$body</section>"))
            assertFalse(result.contains("data-acbooks-leading"))
        }
    }

    @Test
    fun protectsPoetryNotesAndCoverStructures() {
        val input = html("""<section class="poem"><h1>Poem</h1><p>Line<br/>Line</p></section><aside epub:type="footnote"><p>Note</p></aside><figure><img src="cover.jpg"/></figure>""")
        val result = EpubLayoutNormalizer.normalize(input)
        assertFalse(result.contains("data-acbooks-leading"))
        assertEquals(3, Regex("""data-acbooks-preserve="true"""").findAll(result).count())
        assertEquals(input, result.replace(""" data-acbooks-preserve="true"""", ""))
    }

    @Test
    fun semanticHeadingParticipatesInLeadingWrapperNormalization() {
        val input = html("""<section id="chapter"><div><p id="title">认识世界</p><p>正文</p></div></section>""")
        val result = DeepEpubTypography.injectStylesheet(
            input.toByteArray(), listOf(EpubHeadingHint("认识世界", "title", 0)),
        ).decodeToString()
        assertTrue(result.contains("""<p data-acbooks-leading="true" data-acbooks-heading="chapter" id="title">认识世界"""))
        assertEquals(3, Regex("data-acbooks-leading=").findAll(result).count())
    }
}
