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

    @Test
    fun emptyNamedAnchorBeforeNestedChapterDoesNotBlockLeadingDetection() {
        val input = html("""<div class="anchor" id="section.xhtml">
            </div><div class="wrapper"><h3><span class="center" id="chapter"><b>第</b><b>一</b><b>章</b></span></h3></div><div><p>Example body.</p></div>""")
        val result = DeepEpubTypography.injectStylesheet(
            input.toByteArray(), listOf(EpubHeadingHint("第一章", null, 0)),
        ).decodeToString()
        assertTrue(result.contains("""<div data-acbooks-spacer="true" class="anchor" id="section.xhtml">"""))
        assertTrue(result.contains("""<div data-acbooks-leading="true" class="wrapper">"""))
        assertTrue(result.contains("""<h3 data-acbooks-leading="true" data-acbooks-heading="chapter">"""))
        assertTrue(result.contains("""<span class="center" id="chapter"><b>第</b><b>一</b><b>章</b></span>"""))
    }

    @Test
    fun marksNestedImageOnlyBlocksWithoutMarkingMixedProseOrProtectedImages() {
        val input = html("""<div class="indent"><div class="inner"><img src="picture.jpg"/></div></div><p>Text <img src="icon.jpg"/> more text</p><figure><img src="protected.jpg"/></figure>""")
        val result = EpubLayoutNormalizer.normalize(input)
        assertEquals(2, Regex("data-acbooks-image-block=").findAll(result).count())
        assertTrue(result.contains("""<img data-acbooks-image="true" src="picture.jpg"/>"""))
        assertTrue(result.contains("""<p>Text <img src="icon.jpg"/> more text</p>"""))
        assertTrue(result.contains("""<img src="protected.jpg"/>"""))
        assertEquals(input, result.replace(Regex(""" data-acbooks-(?:image-block|image|preserve)="true""""), ""))
        assertEquals(result, EpubLayoutNormalizer.normalize(result))
    }

    @Test
    fun emptyAnchorWithoutFollowingTitleIsNotCollapsed() {
        val input = html("""<div id="blank"></div><p>Ordinary prose.</p>""")
        assertFalse(EpubLayoutNormalizer.normalize(input).contains("data-acbooks-spacer"))
    }

    @Test
    fun recognizesNumberedCaptionWithTrailingImagesButNotMixedProse() {
        val input = html("""<p class="plate"><sup><small><a href="chapter.html#ref" id="note">[3]</a></small></sup><span>Example plate</span><img src="a.jpg"/><img src="b.jpg"/></p>
            <p><sup><a href="#ref">[4]</a></sup>Text<img src="inline.jpg"/>continues here.</p>
            <p>Ordinary paragraph<img src="other.jpg"/></p>
            <aside><p><sup><a href="#ref">[5]</a></sup>Caption<img src="protected.jpg"/></p></aside>""")
        val result = EpubLayoutNormalizer.normalize(input)
        assertEquals(1, Regex("data-acbooks-image-block=").findAll(result).count())
        assertEquals(2, Regex("data-acbooks-image=").findAll(result).count())
        assertTrue(result.contains("""<a href="chapter.html#ref" id="note">[3]</a>"""))
        assertTrue(result.contains("""<img data-acbooks-image="true" src="a.jpg"/>"""))
        assertTrue(result.contains("""<img data-acbooks-image="true" src="b.jpg"/>"""))
        assertTrue(result.contains("""<img src="inline.jpg"/>"""))
        assertEquals(input, result.replace(Regex(""" data-acbooks-(?:image-block|image|preserve)="true""""), ""))
        assertEquals(result, EpubLayoutNormalizer.normalize(result))
    }
}
