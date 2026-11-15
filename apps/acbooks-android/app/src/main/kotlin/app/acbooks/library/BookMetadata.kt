package app.acbooks.library

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
            requireEpub(epub.readEntry("mimetype", 64).toString(Charsets.US_ASCII).trim() == "application/epub+zip")
            var packagePath: String? = null
            parseXml(epub.readEntry("META-INF/container.xml")) {
                if (name == "rootfile" && packagePath == null) packagePath = getAttributeValue(null, "full-path")
            }
            val rootfile = packagePath ?: throw IOException("Missing EPUB package")
            var title: String? = null
            val authors = mutableListOf<String>()
            var legacyCoverId: String? = null
            val images = mutableMapOf<String, String>()
            var coverHref: String? = null
            var sawPackage = false
            parseXml(epub.readEntry(rootfile)) {
                if (depth == 1 && name == "package") sawPackage = true
                if (depth == 3 && namespace == "http://purl.org/dc/elements/1.1/") {
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
