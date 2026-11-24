package app.acbooks.reader.typography

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.resource.TransformingContainer
import org.readium.r2.shared.util.resource.TransformingResource

/**
 * First-stage, non-destructive typography for reflowable EPUB spine documents.
 * Readium 3.4.0 updates --USER__advancedSettings on all active pages when the
 * publisher-layout preference changes; the stylesheet uses that same switch.
 */
@OptIn(ExperimentalReadiumApi::class)
internal object DeepEpubTypography {
    const val ASSET_PATH = "reader/acbooks-typography.css"
    private const val ASSET_URL = "https://readium_assets/" + ASSET_PATH
    private const val MAX_CHAPTER_BYTES = 2 * 1024 * 1024

    // The newline after "link" is intentional. In 3.4.0 ReadiumCss.hasStyles()
    // checks for "<link " and "<style...>"; this reader-owned stylesheet must
    // not suppress Readium's language-specific default CSS in unstyled books.
    private const val STYLESHEET_LINK =
        "<link\nrel=\"stylesheet\" type=\"text/css\" href=\"$ASSET_URL\" />"

    private val headTokens = Regex(
        """<!--.*?-->|<!\[CDATA\[.*?\]\]>|<(script|style)\b[^>]*>.*?</\1\s*>|</head\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val headStart = Regex("""<head(?:\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val headEnd = Regex("""</head\s*>""", RegexOption.IGNORE_CASE)
    private val encoding = Regex(
        """<\?xml\s+[^?]*encoding\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    fun install(builder: Publication.Builder) {
        if (builder.manifest.metadata.layout == Layout.FIXED) return

        val chapterUrls = builder.manifest.readingOrder
            .filter { link ->
                link.mediaType?.isHtml == true &&
                    "cover" !in link.rels &&
                    "contents" !in link.rels &&
                    link.properties["layout"] != "fixed"
            }
            .map { it.url().removeFragment() }
            .toSet()
        if (chapterUrls.isEmpty()) return

        builder.container = TransformingContainer(builder.container) { url, resource ->
            if (url.removeFragment() !in chapterUrls) resource
            else TransformingResource(resource) { bytes ->
                Try.success(injectStylesheet(bytes))
            }
        }
    }

    /**
     * Insert a single XHTML-safe link without reserializing the document or
     * changing body nodes, ids, text, hrefs, whitespace or publisher styles.
     * Unsupported encodings, malformed headers and large chapters fail open.
     */
    internal fun injectStylesheet(bytes: ByteArray): ByteArray {
        if (bytes.size > MAX_CHAPTER_BYTES) return bytes
        val html = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            return bytes
        }
        if (html.indexOf('\u0000') >= 0 || ASSET_URL in html) return bytes
        val declaredEncoding = encoding.find(html.take(512))?.groupValues?.get(1)
        if (declaredEncoding != null && !declaredEncoding.equals("utf-8", ignoreCase = true)) return bytes
        val close = headTokens.findAll(html).firstOrNull { headEnd.matches(it.value) } ?: return bytes
        if (headStart.find(html)?.range?.first?.let { it < close.range.first } != true) return bytes
        return buildString(html.length + STYLESHEET_LINK.length + 2) {
            append(html, 0, close.range.first)
            append('\n')
            append(STYLESHEET_LINK)
            append('\n')
            append(html, close.range.first, html.length)
        }.toByteArray(Charsets.UTF_8)
    }
}
