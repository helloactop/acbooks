package app.acbooks.reader.typography

import java.text.Normalizer
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

internal data class EpubHeadingHint(val title: String, val fragment: String?, val depth: Int)

/**
 * Recognize high-confidence headings without serializing the parsed DOM.
 * Source-position edits add only an inert data attribute to existing start tags;
 * text nodes, whitespace, anchors and the publisher's attributes stay intact.
 */
internal object EpubSemanticAnalyzer {
    private const val ATTRIBUTE = "data-acbooks-heading"
    private const val MAX_TITLE_LENGTH = 160
    private val headingTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val inlineTags = setOf("a", "abbr", "b", "bdi", "bdo", "br", "cite", "code", "em",
        "i", "mark", "q", "ruby", "rp", "rt", "s", "small", "span", "strong", "sub", "sup", "u", "wbr")
    private val excludedTags = setOf("nav", "table", "figure", "figcaption", "blockquote", "aside",
        "li", "pre", "svg", "math", "script", "style", "footer")
    private val excludedTypes = setOf("toc", "cover", "titlepage", "footnote", "endnote", "footnotes",
        "endnotes", "noteref", "bibliography", "index", "pagebreak", "epigraph")
    private val chapterClasses = setOf("chapter-title", "chaptertitle", "chapter-heading", "chapterheading",
        "chapter_title", "ch-title", "chaptitle")
    private val sectionClasses = setOf("section-title", "sectiontitle", "section-heading",
        "subheading", "sub-heading", "section_title")
    private val numberPattern = Regex(
        """^(?:第\s*[零〇一二三四五六七八九十百千万两0-9IVXLCDM]+\s*[章节卷部篇回]|(?:chapter|part|book)\s+(?:[0-9]+|[ivxlcdm]+))[\s.:：、．]*$""",
        RegexOption.IGNORE_CASE,
    )
    // Printed-book TOCs sometimes retain a dotted leader and page number.
    // Strip only an explicit leader, never ordinary title-ending digits.
    private val pageLeader = Regex("""(?:\.{3,}|…+|·{3,})\s*[0-9]+\s*$""")
    private val noteLabel = Regex("""^(?:\[\s*[0-9]+\s*]|【\s*[0-9]+\s*】)$""")
    private val subsectionPrefix = Regex("""^\(\s*[零〇一二三四五六七八九十百千万两0-9]+\s*\)\s*\S""")
    private val whitespaceAndPunctuation = Regex("""[\p{P}\p{Z}\s]+""")
    private val tagStart = Regex("""^<[a-zA-Z][a-zA-Z0-9:_-]*""")

    fun annotate(html: String, hints: List<EpubHeadingHint>): String {
        val document = Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true))
        val candidates = document.body().select("h1,h2,h3,h4,h5,h6,p,div")
            .filter { element ->
                element.parents().none { excluded(it) } && !excluded(element) &&
                    element.children().all { it.normalName() in inlineTags } &&
                    element.selectFirst("img,svg,math,video,audio,object,iframe,table") == null
            }
        val minDepth = hints.minOfOrNull { it.depth } ?: 0
        val normalizedHints = hints.mapNotNull { hint ->
            val label = Normalizer.normalize(hint.title.trim(), Normalizer.Form.NFKC)
                .replace(pageLeader, "").trim()
            if (noteLabel.matches(label)) null
            else normalize(label).takeIf { it.isNotEmpty() }?.let { hint.copy(title = label) to it }
        }
        val hasRootChapter = normalizedHints.any { (hint, _) -> hint.fragment == null && hint.depth == minDepth }
        val texts = candidates.map { it.text().trim() }
        val keys = texts.map { if (it.length <= MAX_TITLE_LENGTH) normalize(it) else "" }
        val counts = keys.groupingBy { it }.eachCount()
        val roles = mutableMapOf<Int, String>()

        candidates.forEachIndexed { index, element ->
            val key = keys[index]
            if (key.isEmpty() || element.hasAttr(ATTRIBUTE)) return@forEachIndexed
            val explicit = explicitRole(element)
            val ids = buildSet {
                if (element.id().isNotEmpty()) add(element.id())
                element.select("[id],a[name]").forEach { child ->
                    if (child.id().isNotEmpty()) add(child.id())
                    if (child.hasAttr("name")) add(child.attr("name"))
                }
            }
            // An anchor alone is not enough: arbitrary paragraphs can be linked
            // from the TOC too. Require matching text or explicit heading evidence.
            val nearbyIds = buildSet {
                element.parents().forEach { if (it.id().isNotEmpty()) add(it.id()) }
                val previous = element.previousElementSibling()
                if (previous != null && previous.normalName() == "a" && previous.text().isBlank()) {
                    if (previous.id().isNotEmpty()) add(previous.id())
                    if (previous.hasAttr("name")) add(previous.attr("name"))
                }
            }
            val anchored = normalizedHints.firstOrNull { (hint, title) ->
                val fragment = hint.fragment
                fragment != null && (
                    (fragment in ids && (key == title || explicit != null || element.normalName() in headingTags)) ||
                        (fragment in nearbyIds && key == title && counts[key] == 1)
                    )
            }?.first
            val byText = if (index < 6 && key.length >= 2 && counts[key] == 1) {
                normalizedHints.firstOrNull { (hint, title) -> hint.fragment == null && key == title }?.first
            } else null
            val match = anchored ?: byText
            val role = explicit ?: match?.let {
                // Some NCX files flatten chapters and numbered subsections to
                // the same depth. A root resource entry plus an anchored,
                // parenthesized ordinal is sufficient evidence of a subsection.
                if (it.depth > minDepth ||
                    (hasRootChapter && it.fragment != null && subsectionPrefix.containsMatchIn(it.title))
                ) "section" else "chapter"
            }
            if (role != null) roles[index] = role
        }

        // A chapter number and its title are frequently two separate elements.
        // Only split a leading pair when the TOC or an explicit title identifies it.
        if (candidates.size >= 2 && numberPattern.matches(texts[0]) && keys[1].isNotEmpty()) {
            val combined = normalize(texts[0] + " " + texts[1])
            val combinedHint = normalizedHints.firstOrNull { (hint, title) ->
                hint.depth == minDepth && title == combined
            }
            if (combinedHint != null || roles[1] == "chapter") {
                if (!candidates[0].hasAttr(ATTRIBUTE) && !candidates[1].hasAttr(ATTRIBUTE)) {
                    roles[0] = "number"
                    roles[1] = "chapter"
                }
            }
        }

        val edits = roles.mapNotNull { (index, role) ->
            val element = candidates[index]
            val range = element.sourceRange()
            if (!range.isTracked || range.isImplicit) return@mapNotNull null
            val start = range.start().pos()
            val end = range.end().pos()
            if (start < 0 || end > html.length || end <= start) return@mapNotNull null
            val original = html.substring(start, end)
            val tag = tagStart.find(original)?.value ?: return@mapNotNull null
            if (!tag.drop(1).equals(element.tagName(), ignoreCase = true)) return@mapNotNull null
            (start + tag.length) to " $ATTRIBUTE=\"$role\""
        }.distinctBy { it.first }.sortedByDescending { it.first }
        if (edits.isEmpty()) return html
        return StringBuilder(html).apply {
            edits.forEach { (offset, attribute) -> insert(offset, attribute) }
        }.toString()
    }

    private fun explicitRole(element: Element): String? {
        val types = tokens(element.attr("epub:type"))
        val classes = element.classNames().map { it.lowercase(Locale.ROOT) }.toSet()
        if (classes.any { it in chapterClasses } ||
            element.attr("role") == "doc-chapter" ||
            ("title" in types && element.parents().any { "chapter" in tokens(it.attr("epub:type")) })
        ) return "chapter"
        if (classes.any { it in sectionClasses }) return "section"
        if (element.attr("role") == "heading") {
            return if (element.attr("aria-level") == "1") "chapter" else "section"
        }
        return null
    }

    private fun excluded(element: Element): Boolean =
        element.normalName() in excludedTags ||
            tokens(element.attr("epub:type")).any { it in excludedTypes } ||
            element.hasAttr("hidden") || element.attr("aria-hidden") == "true"

    private fun tokens(value: String): Set<String> =
        value.lowercase(Locale.ROOT).split(Regex("""\s+""")).filter { it.isNotEmpty() }.toSet()

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace(whitespaceAndPunctuation, "")
}
