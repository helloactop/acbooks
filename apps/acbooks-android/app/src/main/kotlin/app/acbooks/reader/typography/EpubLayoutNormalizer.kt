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
    private const val SPACER = "data-acbooks-spacer"
    private const val IMAGE_BLOCK = "data-acbooks-image-block"
    private const val IMAGE = "data-acbooks-image"
    private val imageWrappers = setOf("div", "p", "section", "article", "main", "a", "span")
    private val captionTags = setOf("span", "a", "sup", "sub", "small", "b", "strong", "i", "em", "img")
    private val noteNumber = Regex("""^\[\s*[0-9]+\s*]""")
    private val localBacklink = Regex("""^(?:[^:/?#]+/)*[^:/?#]*#[^\s]+$""")
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
            val spacers = mutableListOf<Element>()
            var current = firstMeaningfulChild(body, spacers)
            val wrappers = mutableListOf<Element>()
            while (current != null && current.normalName() in wrapperTags &&
                !isSpecial(current) && !isHeading(current) && wrappers.size < 12
            ) {
                wrappers.add(current)
                current = firstMeaningfulChild(current, spacers)
            }
            if (current != null && isHeading(current) && !isSpecial(current) &&
                current.text().isNotBlank() && current.selectFirst("img,svg,math,video,audio,object,iframe,table") == null
            ) {
                spacers.forEach { mark(it, SPACER) }
                wrappers.forEach { mark(it, LEADING) }
                mark(current, LEADING)
            }
        }
        // Only center image-only blocks, including nested wrappers.
        // Mixed prose, inline icons and protected content stay authored.
        for (image in body.select("img")) {
            if (isSpecial(image) || image.parents().any { isSpecial(it) } ||
                isSpecial(document.selectFirst("html"))) continue
            val blocks = mutableListOf<Element>()
            var parent = image.parent()
            while (parent != null && parent.normalName() in imageWrappers &&
                !isSpecial(parent) && isImageOnly(parent)
            ) {
                if (parent.normalName() != "a" && parent.normalName() != "span") blocks.add(parent)
                parent = parent.parent()
            }
            if (blocks.isNotEmpty() || (parent == body && isImageOnly(body))) {
                mark(image, IMAGE)
                blocks.forEach { mark(it, IMAGE_BLOCK) }
            }
        }
        // Legacy illustrated books often put a numbered backlink, a short
        // caption and one or more full-size images in the same paragraph.
        // This is not ordinary mixed prose: require a local superscript
        // backlink, a short nonempty caption, and images after all text.
        if (!isSpecial(document.selectFirst("html"))) {
            val eligible = body.select("p,div").filter { block ->
                !isSpecial(block) && block.parents().none { isSpecial(it) } &&
                    hasTrailingCaptionImages(block)
            }
            val numbered = eligible.filter { hasNumberedBacklink(it) }
            // A short unnumbered caption can reuse the exact same nonempty
            // class set as an established plate paragraph in this document.
            // Do not infer a caption from a class name alone.
            val plateStyles = numbered.map { it.classNames().toSet() }.filter { it.isNotEmpty() }.toSet()
            val unnumbered = eligible.filter { block ->
                block !in numbered && block.classNames().toSet() in plateStyles &&
                    block.text().trim().length in 1..40 &&
                    block.children().firstOrNull()?.normalName() == "span" &&
                    block.childNodes().none { it is TextNode && it.wholeText.isNotBlank() } &&
                    block.text().none { it in "。！？.!?；;" }
            }
            for (block in numbered + unnumbered) {
                mark(block, IMAGE_BLOCK)
                block.select("img").forEach { mark(it, IMAGE) }
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

    private fun isImageOnly(element: Element): Boolean =
        element.text().isBlank() && element.select("img").size == 1 &&
            element.getAllElements().all {
                it == element || (!isSpecial(it) && (it.normalName() in imageWrappers || it.normalName() == "img"))
            }

    private fun hasNumberedBacklink(element: Element): Boolean {
        val text = element.text().trim()
        val number = noteNumber.find(text) ?: return false
        if (text.substring(number.range.last + 1).isBlank()) return false
        return element.select("sup a[href]").any {
            it.text().trim() == number.value && localBacklink.matches(it.attr("href"))
        }
    }

    private fun hasTrailingCaptionImages(element: Element): Boolean {
        val text = element.text().trim()
        if (text.isEmpty() || text.length > 120) return false
        if (element.getAllElements().any {
                it != element && (isSpecial(it) || it.normalName() !in captionTags)
            }) return false

        // The images must be direct trailing children, not inline in a
        // sentence; multiple plate images in one paragraph are supported.
        var seenImage = false
        for (node in element.childNodes()) {
            when (node) {
                is TextNode -> if (seenImage && node.wholeText.isNotBlank()) return false
                is Element -> if (node.normalName() == "img") {
                    seenImage = true
                } else if (seenImage || node.selectFirst("img") != null) return false
            }
        }
        return seenImage
    }

    private fun firstMeaningfulChild(parent: Element, spacers: MutableList<Element>): Element? {
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
                    // Calibre/Sigil can emit empty named section anchors as
                    // block elements with paragraph margins. Collapse them only
                    // if a real heading follows; keep the ID and original node.
                    if (node.normalName() in setOf("div", "p") && node.id().isNotEmpty() &&
                        node.children().isEmpty() && node.text().isBlank() && !isSpecial(node)
                    ) {
                        spacers.add(node)
                        continue
                    }
                    return node
                }
            }
        }
        return null
    }
}
