package app.acbooks.reader.typography

import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

/**
 * Mark only the uninterrupted leading ancestor chain of a recognized heading.
 * Publisher styles, content and anchors are preserved. All visual overrides
 * are applied by the opt-in stylesheet, never by permanent style deletion.
 */
internal object EpubLayoutNormalizer {
    private const val LEADING = "data-acbooks-leading"
    private const val PROTECTED = "data-acbooks-preserve"
    private val wrapperTags = setOf("div", "section", "article", "header", "main")
    private val specialTags = setOf("nav", "aside", "blockquote", "pre", "figure", "figcaption", "table", "svg", "math", "footer")
    private val specialTypes = setOf("cover", "titlepage", "toc", "footnote", "endnote",
        "footnotes", "endnotes", "epigraph", "verse", "poem", "pagebreak", "bibliography", "index")
    private val specialClasses = setOf("poem", "poetry", "verse", "stanza", "haiku", "lyrics",
        "cover", "titlepage", "title-page", "footnote", "endnote", "epigraph")
    private val headings = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val tagStart = Regex("""^<[a-zA-Z][a-zA-Z0-9:_-]*""")

    fun normalize(html: String): String {
        val document = Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true))
        val body = document.body()
        val edits = mutableMapOf<Int, MutableList<String>>()

        fun mark(element: Element, name: String) {
            if (element.hasAttr(name)) return
            val range = element.sourceRange()
            if (!range.isTracked || range.isImplicit) return
            val start = range.start().pos()
            val end = range.end().pos()
            if (start < 0 || end > html.length || end <= start) return
            val tag = tagStart.find(html.substring(start, end))?.value ?: return
            if (!tag.drop(1).equals(element.tagName(), ignoreCase = true)) return
            edits.getOrPut(start + tag.length) { mutableListOf() }.add(""" $name="true"""")
        }

        // Keep explicit poetry/cover/annotation structures out of all app overrides.
        // Do not treat arbitrary names such as calibre12 as semantic signals.
        for (element in body.getAllElements()) {
            if (isSpecial(element)) mark(element, PROTECTED)
        }
        if (isSpecial(body) || isSpecial(document.selectFirst("html"))) {
            mark(body, PROTECTED)
        } else {
            var current = firstMeaningfulChild(body)
            val wrappers = mutableListOf<Element>()
            while (current != null && current.normalName() in wrapperTags &&
                !isSpecial(current) && !isHeading(current) && wrappers.size < 12
            ) {
                wrappers.add(current)
                current = firstMeaningfulChild(current)
            }
            if (current != null && isHeading(current) && !isSpecial(current) &&
                current.text().isNotBlank() && current.selectFirst("img,svg,math,video,audio,object,iframe,table") == null
            ) {
                wrappers.forEach { mark(it, LEADING) }
                mark(current, LEADING)
            }
        }
        if (edits.isEmpty()) return html
        return StringBuilder(html).apply {
            edits.toSortedMap(compareByDescending { it }).forEach { (offset, attributes) ->
                insert(offset, attributes.joinToString(""))
            }
        }.toString()
    }

    private fun isHeading(element: Element): Boolean =
        element.normalName() in headings ||
            element.attr("data-acbooks-heading") in setOf("chapter", "number", "section")

    private fun isSpecial(element: Element?): Boolean {
        if (element == null) return false
        return element.normalName() in specialTags ||
            element.hasAttr(PROTECTED) ||
            element.hasAttr("hidden") || element.attr("aria-hidden") == "true" ||
            element.attr("epub:type").lowercase(Locale.ROOT).split(Regex("""\s+""")).any { it in specialTypes } ||
            element.classNames().any { it.lowercase(Locale.ROOT) in specialClasses }
    }

    private fun firstMeaningfulChild(parent: Element): Element? {
        for (node in parent.childNodes()) {
            when (node) {
                is TextNode -> if (node.wholeText.isNotBlank()) return null
                is Element -> {
                    // An empty anchor or a hidden element does not occupy layout.
                    if (node.hasAttr("hidden") || node.attr("aria-hidden") == "true") {
                        // aria-hidden alone can still occupy layout: do not skip it.
                        if (!node.hasAttr("hidden")) return null
                        continue
                    }
                    if (node.normalName() == "a" && node.text().isBlank() && node.children().isEmpty() &&
                        (node.hasAttr("id") || node.hasAttr("name"))
                    ) continue
                    return node
                }
            }
        }
        return null
    }
}
