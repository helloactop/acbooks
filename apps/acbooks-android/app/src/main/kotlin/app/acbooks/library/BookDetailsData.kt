package app.acbooks.library

import app.acbooks.core.database.LocalBookReadingPosition
import app.acbooks.reader.readPdf
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.xmlpull.v1.XmlPullParserException

internal data class BookDetailsData(
    val metadata: BookBibliography? = null,
    val shelves: List<String> = emptyList(),
    val formats: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val readingStatus: BookReadingStatus? = null,
    val readingStatistics: BookReadingStatistics? = null,
    val readingLocation: BookReadingLocation? = null,
)

internal enum class BookReadingStatus { Unread, Reading, Finished }

internal data class BookReadingStatistics(
    val activeSeconds: Long,
    val sessionCount: Int,
    val readingDays: Int,
    val lastReadAt: Long,
)

internal sealed interface BookReadingLocation {
    val fraction: Float?

    data class Epub(val locator: Locator) : BookReadingLocation {
        override val fraction: Float? get() = locator.locations.totalProgression?.toFloat()
    }

    data class Pdf(val page: Int, val pageCount: Int?) : BookReadingLocation {
        override val fraction: Float?
            get() = pageCount?.takeIf { page in 1..it }?.let { page.toFloat() / it }
    }
}

internal data class BookDetailsFile(
    val metadata: BookBibliography? = null,
    val pdfPageCount: Int? = null,
)

internal fun readBookLocation(position: LocalBookReadingPosition?, pdfPageCount: Int?): BookReadingLocation? {
    val saved = position?.locator ?: return null
    val json = try {
        JSONObject(saved)
    } catch (_: JSONException) {
        return null
    }
    return when (position.format) {
        "epub" -> Locator.fromJSON(json)?.let { BookReadingLocation.Epub(it) }
        "pdf" -> json.optInt("page", -1).takeIf { it in 0 until Int.MAX_VALUE }
            ?.let { BookReadingLocation.Pdf(it + 1, pdfPageCount) }
        else -> null
    }
}

internal suspend fun readBookDetailsFile(source: ReadableBookFile?): BookDetailsFile? = withContext(Dispatchers.IO) {
    if (source == null) return@withContext null
    try {
        when (source.format) {
            "epub" -> BookDetailsFile(metadata = readEpubBibliography(source.file))
            "pdf" -> BookDetailsFile(pdfPageCount = readPdf(source.file) { it.pageCount })
            else -> null
        }
    } catch (_: IOException) {
        null
    } catch (_: XmlPullParserException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
