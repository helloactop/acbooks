package app.acbooks.reader

import app.acbooks.reader.typography.DeepEpubTypography
import app.acbooks.reader.typography.EpubHeadingHint
import app.acbooks.reader.typography.EpubSemanticAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubSemanticAnalyzerTest {
    private fun html(body: String) = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Book</title></head><body>$body</body></html>"""

    @Test
    fun tocRecognizesNonStandardTitlesAndNestedSectionsWithoutChangingOtherBytes() {
        val input = html("""<p id='c1' class="calibre12" style="text-align:center"><b>认识世界</b></p>
            <p>正文 &amp; 原有空格  <a href="#s1">链接</a></p>
            <div id="s1" class="calibre13">观察方法</div>""")
        val result = EpubSemanticAnalyzer.annotate(input, listOf(
            EpubHeadingHint("认识世界", "c1", 0),
            EpubHeadingHint("观察方法", "s1", 1),
        ))
        assertTrue(result.contains("""<p data-acbooks-heading="chapter" id='c1'"""))
        assertTrue(result.contains("""<div data-acbooks-heading="section" id="s1""""))
        assertEquals(input, result.replace(Regex(""" data-acbooks-heading="[^"]+""""), ""))
        assertEquals(result, EpubSemanticAnalyzer.annotate(result, listOf(
            EpubHeadingHint("认识世界", "c1", 0), EpubHeadingHint("观察方法", "s1", 1),
        )))
    }

    @Test
    fun recognizesWrapperAndNamedAnchorsButNotUnrelatedText() {
        val input = html("""<p>第一节</p><section id="c"><p>第一章 正文</p></section>
            <a name="s"></a><div>第一节</div><p id="other">普通段落</p>""")
        // Duplicate unanchored text is deliberately not used as a weak match.
        val result = EpubSemanticAnalyzer.annotate(input, listOf(
            EpubHeadingHint("第一章 正文", "c", 0),
            EpubHeadingHint("第一节", "s", 1),
            EpubHeadingHint("不匹配", "other", 1),
        ))
        assertTrue(result.contains("""<p data-acbooks-heading="chapter">第一章 正文"""))
        assertFalse(result.contains("""<p data-acbooks-heading="chapter">第一节"""))
        assertFalse(result.contains("""data-acbooks-heading="section" id="other""""))
        val unique = EpubSemanticAnalyzer.annotate(
            html("""<a name="s"></a><div>独立标题</div>"""),
            listOf(EpubHeadingHint("独立标题", "s", 0)),
        )
        assertTrue(unique.contains("""<div data-acbooks-heading="chapter">独立标题"""))
    }

    @Test
    fun recognizesSeparateChapterNumberAndTitleWithTocEvidence() {
        val input = html("<p>第一章</p><div>认识世界</div><p>正文</p>")
        val result = EpubSemanticAnalyzer.annotate(input, listOf(EpubHeadingHint("第一章：认识世界", null, 0)))
        assertTrue(result.contains("""<p data-acbooks-heading="number">第一章"""))
        assertTrue(result.contains("""<div data-acbooks-heading="chapter">认识世界"""))
        assertEquals(input, EpubSemanticAnalyzer.annotate(input, emptyList()))
    }

    @Test
    fun explicitSemanticsWorkWithoutTocButProtectedContentIsUntouched() {
        val input = html("""<section epub:type="chapter"><p epub:type="title">章节</p>
            <div class="section-title">小标题</div><p role="heading" aria-level="3">次级标题</p>
            <p class="calibre1">短正文</p><aside epub:type="footnote"><p class="chapter-title">脚注</p></aside>
            <blockquote><p class="chapter-title">引用</p></blockquote>
            <p class="chapter-title"><img src="cover.jpg" /></p>
            <div class="chapter-title"><p>普通段落</p></div></section>""")
        val result = EpubSemanticAnalyzer.annotate(input, emptyList())
        assertTrue(result.contains("""<p data-acbooks-heading="chapter" epub:type="title""""))
        assertTrue(result.contains("""<div data-acbooks-heading="section" class="section-title""""))
        assertTrue(result.contains("""<p data-acbooks-heading="section" role="heading""""))
        assertEquals(3, Regex("data-acbooks-heading=").findAll(result).count())
    }

    @Test
    fun ambiguousOrLateUnanchoredTitlesAreNotPromoted() {
        val input = html("<p>重复标题</p><p>重复标题</p>" + (1..8).joinToString("") { "<p>普通段落 $it</p>" } + "<p>末尾标题</p>")
        assertEquals(input, EpubSemanticAnalyzer.annotate(input, listOf(
            EpubHeadingHint("重复标题", null, 0), EpubHeadingHint("末尾标题", null, 0),
        )))
    }

    @Test
    fun integrationInjectsCssAndSemanticMarkersTogether() {
        val input = html("""<p id="chapter">Chapter One</p><p>Body</p>""")
        val result = DeepEpubTypography.injectStylesheet(input.toByteArray(), listOf(
            EpubHeadingHint("Chapter One", "chapter", 0),
        )).decodeToString()
        assertTrue(result.contains("acbooks-typography.css"))
        assertTrue(result.contains("""<p data-acbooks-leading="true" data-acbooks-heading="chapter" id="chapter">Chapter One"""))
        assertTrue(result.contains("<p>Body</p>"))
    }

    @Test
    fun printedPageLeadersAndFlatNumberedSubsectionsMatchWithoutPromotingNotes() {
        val input = html("""<p id="c"><span class="bold">一 造型艺术</span></p>
            <p id="s1"><span class="bold">（一）形态</span></p><p>Ordinary body.</p>
            <p id="s2">（二）色彩</p><p id="note">[1]</p><p id="year">艺术2026</p>""")
        val result = EpubSemanticAnalyzer.annotate(input, listOf(
            EpubHeadingHint("一 造型艺术... 4", null, 0),
            EpubHeadingHint("（一）形态... 4", "s1", 0),
            EpubHeadingHint("（二）色彩……13", "s2", 0),
            EpubHeadingHint("[1]", "note", 0),
            EpubHeadingHint("艺术2026", "year", 1),
        ))
        assertTrue(result.contains("""<p data-acbooks-heading="chapter" id="c">"""))
        assertTrue(result.contains("""<p data-acbooks-heading="section" id="s1">"""))
        assertTrue(result.contains("""<p data-acbooks-heading="section" id="s2">"""))
        assertTrue(result.contains("""<p id="note">[1]</p>"""))
        assertTrue(result.contains("""<p data-acbooks-heading="section" id="year">艺术2026"""))
        assertEquals(input, result.replace(Regex(""" data-acbooks-heading="[^"]+""""), ""))
    }
}
