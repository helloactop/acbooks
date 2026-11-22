package app.acbooks.library

import android.text.Html
import android.util.Xml
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

internal data class BookMetadata(val title: String, val author: String? = null, val cover: ByteArray? = null)

internal data class BookBibliography(
    val description: String? = null,
    val translators: List<String> = emptyList(),
    val isbn: String? = null,
    val publisher: String? = null,
    val publicationDate: String? = null,
    val languages: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
)

internal fun readBookMetadata(file: File, format: String, fallback: String): BookMetadata = when (format) {
    "pdf" -> {
        RandomAccessFile(file, "r").use { pdf ->
            val header = ByteArray(minOf(pdf.length(), 1024).toInt()).also(pdf::readFully)
            pdf.seek((pdf.length() - 4096).coerceAtLeast(0))
            val trailer = ByteArray((pdf.length() - pdf.filePointer).toInt()).also(pdf::readFully)
            if (!header.toString(Charsets.ISO_8859_1).contains("%PDF-") ||
                !trailer.toString(Charsets.ISO_8859_1).contains("%%EOF")
            ) throw IOException("Invalid PDF")
        }
        BookMetadata(fallback)
    }
    "epub" -> try {
        ZipFile(file).use { epub ->
            val rootfile = epub.packagePath()
            var title: String? = null
            val authors = mutableListOf<String>()
            var legacyCoverId: String? = null
            val images = mutableMapOf<String, String>()
            var coverHref: String? = null
            var sawPackage = false
            parseXml(epub.readEntry(rootfile)) {
                if (depth == 1 && name == "package") sawPackage = true
                if (depth == 3 && namespace == DublinCore) {
                    when (name) {
                        "title" -> if (title == null) title = nextText().trim().take(512).ifEmpty { null }
                        "creator" -> if (authors.size < 12) nextText().trim().take(128)
                            .takeIf(String::isNotEmpty)?.let(authors::add)
                    }
                }
                if (depth == 3 && name == "meta" && getAttributeValue(null, "name") == "cover") {
                    legacyCoverId = getAttributeValue(null, "content")
                }
                if (depth == 3 && name == "item" && getAttributeValue(null, "media-type") in coverImageTypes) {
                    val id = getAttributeValue(null, "id")
                    val href = getAttributeValue(null, "href")
                    if (id != null && href != null) {
                        images[id] = href
                        if (getAttributeValue(null, "properties")?.split(' ')?.contains("cover-image") == true) {
                            coverHref = href
                        }
                    }
                }
            }
            requireEpub(sawPackage)
            val cover = (coverHref ?: legacyCoverId?.let(images::get))?.let { epub.readCover(rootfile, it) }
            BookMetadata(title ?: fallback, authors.distinct().joinToString(", ").ifEmpty { null }, cover)
        }
    } catch (failure: XmlPullParserException) {
        throw IOException("Invalid EPUB package", failure)
    }
    else -> throw IOException("Unsupported format")
}

internal fun readEpubBibliography(file: File): BookBibliography = ZipFile(file).use { epub ->
    var description: String? = null
    var isbn: String? = null
    var publisher: String? = null
    var publicationDate: String? = null
    val translators = mutableListOf<String>()
    val contributors = mutableMapOf<String, String>()
    val translatorIds = mutableSetOf<String>()
    val languages = mutableListOf<String>()
    val categories = mutableListOf<String>()
    parseXml(epub.readEntry(epub.packagePath())) {
        if (depth != 3) return@parseXml
        if (namespace == DublinCore) {
            when (name) {
                "description" -> if (description == null) {
                    description = Html.fromHtml(nextText(), Html.FROM_HTML_MODE_COMPACT)
                        .toString().trim().takeIf(String::isNotEmpty)
                }
                "creator", "contributor" -> {
                    val id = getAttributeValue(null, "id")
                    val role = getAttributeValue(Opf, "role")
                    val contributor = nextText().trim()
                    if (id != null) contributors[id] = contributor
                    if (role == "trl" && contributor.isNotEmpty()) translators.add(contributor)
                }
                "identifier" -> if (isbn == null) {
                    val scheme = getAttributeValue(Opf, "scheme")
                    val identifier = nextText().trim()
                    val prefix = "urn:isbn:"
                    isbn = when {
                        identifier.startsWith(prefix, ignoreCase = true) -> identifier.substring(prefix.length)
                        scheme.equals("ISBN", ignoreCase = true) -> identifier
                        else -> null
                    }?.trim()?.takeIf(String::isNotEmpty)
                }
                "publisher" -> if (publisher == null) publisher = nextText().trim().takeIf(String::isNotEmpty)
                "date" -> if (publicationDate == null) {
                    val event = getAttributeValue(Opf, "event")
                    if (event == null || event == "publication") {
                        publicationDate = nextText().trim().takeIf(String::isNotEmpty)
                    }
                }
                "language" -> nextText().trim().takeIf(String::isNotEmpty)?.let(languages::add)
                "subject" -> nextText().trim().takeIf(String::isNotEmpty)?.let(categories::add)
            }
        } else if (namespace == Opf && name == "meta" && getAttributeValue(null, "property") == "role") {
            val refines = getAttributeValue(null, "refines")
            if (refines?.startsWith("#") == true && nextText().trim() == "trl") {
                translatorIds.add(refines.substring(1))
            }
        }
    }
    BookBibliography(
        description = description,
        translators = (translators + translatorIds.mapNotNull(contributors::get)).filter(String::isNotBlank).distinct(),
        isbn = isbn,
        publisher = publisher,
        publicationDate = publicationDate,
        languages = languages.distinct(),
        categories = categories.distinct(),
    )
}

private const val DublinCore = "http://purl.org/dc/elements/1.1/"
private const val Opf = "http://www.idpf.org/2007/opf"

private fun ZipFile.packagePath(): String {
    requireEpub(readEntry("mimetype", 64).toString(Charsets.US_ASCII).trim() == "application/epub+zip")
    var packagePath: String? = null
    parseXml(readEntry("META-INF/container.xml")) {
        if (name == "rootfile" && packagePath == null) packagePath = getAttributeValue(null, "full-path")
    }
    return packagePath ?: throw IOException("Missing EPUB package")
}

private fun requireEpub(valid: Boolean) {
    if (!valid) throw IOException("Invalid EPUB")
}

private val coverImageTypes = setOf("image/jpeg", "image/png", "image/webp")

private fun ZipFile.readCover(rootfile: String, href: String): ByteArray? = try {
    val path = URI(null, null, "/$rootfile", null).resolve(href).normalize()
    val entryPath = path.path
    if (path.isAbsolute || path.authority != null || path.query != null || entryPath.isNullOrBlank() ||
        entryPath.split('/').any { it == ".." }
    ) null else readEntry(entryPath.removePrefix("/"), 4 * 1024 * 1024)
} catch (_: IllegalArgumentException) {
    null
} catch (_: java.net.URISyntaxException) {
    null
} catch (_: IOException) {
    null
}

private fun ZipFile.readEntry(path: String, limit: Int = 512 * 1024): ByteArray {
    val entry = getEntry(path) ?: throw IOException("Missing EPUB entry")
    if (entry.isDirectory || entry.size > limit) throw IOException("Invalid EPUB entry")
    return getInputStream(entry).use { stream ->
        val buffer = ByteArray(limit + 1)
        var size = 0
        while (size < buffer.size) {
            val read = stream.read(buffer, size, buffer.size - size)
            if (read < 0) break
            size += read
        }
        if (size > limit) throw IOException("Oversized EPUB entry")
        buffer.copyOf(size)
    }
}

private fun parseXml(bytes: ByteArray, onTag: XmlPullParser.() -> Unit) {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    parser.setInput(ByteArrayInputStream(bytes), null)
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.DOCDECL -> throw IOException("Unsupported XML declaration")
            XmlPullParser.START_TAG -> parser.onTag()
        }
        parser.nextToken()
    }
}
