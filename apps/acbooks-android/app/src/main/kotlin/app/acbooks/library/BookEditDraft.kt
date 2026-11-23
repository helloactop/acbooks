package app.acbooks.library

import app.acbooks.core.database.BookEditRelations
import app.acbooks.core.database.LocalBook
import app.acbooks.R
import java.io.File
import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class BookEditDraft(
    val title: String,
    val authors: String = "",
    val translators: String = "",
    val isbn: String = "",
    val publisher: String = "",
    val publicationDate: String = "",
    val description: String = "",
    val series: String = "",
    val tags: String = "",
    val shelves: String = "",
    val readingStatus: String = "unread",
    val cover: File? = null,
) {
    fun fill(candidate: DoubanMetadataCandidate) = copy(
        title = candidate.title,
        authors = candidate.authors.asLines().ifEmpty { authors },
        translators = candidate.translators.asLines().ifEmpty { translators },
        isbn = candidate.isbn ?: isbn,
        publisher = candidate.publisher ?: publisher,
        publicationDate = candidate.publicationDate ?: publicationDate,
        description = candidate.description ?: description,
        series = candidate.series ?: series,
        tags = candidate.tags.asLines().ifEmpty { tags },
    )

    fun relations() = BookEditRelations(
        authors = authors.names(),
        translators = translators.names(),
        isbn = isbn.normalizedBookIsbn().ifEmpty { null },
        tags = tags.names(),
        shelves = shelves.names(),
    )

    fun values(): JSONObject {
        val relations = relations()
        return JSONObject()
            .put("title", title.trim())
            .put("authors", JSONArray(relations.authors))
            .put("translators", JSONArray(relations.translators))
            .put("isbn", relations.isbn ?: JSONObject.NULL)
            .put("publisher", publisher.trimOrNull() ?: JSONObject.NULL)
            .put("publicationDate", publicationDate.trimOrNull() ?: JSONObject.NULL)
            .put("description", description.trimOrNull() ?: JSONObject.NULL)
            .put("series", series.trimOrNull() ?: JSONObject.NULL)
            .put("tags", JSONArray(relations.tags))
            .put("shelves", JSONArray(relations.shelves))
            .put("readingStatus", readingStatus)
    }

    fun validationError(): Int? {
        val lists = listOf(authors.names(), translators.names(), tags.names(), shelves.names())
        val publication = listOf(publisher.trim(), series.trim())
        val normalizedIsbn = isbn.normalizedBookIsbn()
        return when {
            title.isBlank() -> R.string.book_edit_title_required
            normalizedIsbn.isNotEmpty() && !bookIsbnPattern.matches(normalizedIsbn) -> R.string.book_edit_invalid_isbn
            !title.trim().validText(500) ||
                !publicationDate.trim().validText(80) ||
                !description.trim().validText(20_000, allowNewlines = true) ||
                publication.any { !it.validText(200) } ||
                lists.any { names -> names.size > 20 || names.any { !it.validText(200) } } ->
                R.string.book_edit_invalid_metadata
            else -> null
        }
    }
}

internal data class BookEditSession(
    val draft: BookEditDraft,
    val cloudMetadata: String?,
    val cloudCoverVersion: Long?,
    val retryKeys: Set<String>,
    val pendingCover: Boolean,
)

internal enum class BookEditorOperation { Load, Cover, Fill, Save }

internal data class BookEditorState(
    val bookId: String,
    val open: Boolean = true,
    val initial: BookEditSession? = null,
    val draft: BookEditDraft? = null,
    val operation: BookEditorOperation? = null,
    val candidates: List<DoubanMetadataCandidate>? = null,
    val error: Int? = null,
    val savedMessage: Int? = null,
) {
    val dirty: Boolean get() = draft != initial?.draft
}

internal fun localBookDraft(
    book: LocalBook,
    relations: BookEditRelations,
    embedded: BookBibliography?,
) = BookEditDraft(
    title = book.title,
    authors = relations.authors.asLines().ifEmpty { book.author.orEmpty() },
    translators = if (book.metadataEdited || relations.translators.isNotEmpty()) relations.translators.asLines()
        else embedded?.translators.orEmpty().asLines(),
    isbn = (relations.isbn ?: embedded?.isbn.takeUnless { book.metadataEdited }).orEmpty(),
    publisher = (book.publisher ?: embedded?.publisher.takeUnless { book.metadataEdited }).orEmpty(),
    publicationDate = (book.publicationDate ?: embedded?.publicationDate.takeUnless { book.metadataEdited }).orEmpty(),
    description = (book.description ?: embedded?.description.takeUnless { book.metadataEdited }).orEmpty(),
    series = (book.series ?: embedded?.series.takeUnless { book.metadataEdited }).orEmpty(),
    tags = relations.tags.asLines(),
    shelves = relations.shelves.asLines(),
    readingStatus = book.readingStatus,
)

internal fun BookEditDraft.withCloudMetadata(values: JSONObject, local: Set<String>, useEmbeddedFallback: Boolean): BookEditDraft {
    fun text(key: String, fallback: String): String = when {
        key in local || !values.has(key) -> fallback
        else -> values.optString(key, "").takeUnless { values.isNull(key) }.orEmpty()
            .ifEmpty { if (useEmbeddedFallback) fallback else "" }
    }
    fun names(key: String, fallback: String): String {
        val list = values.optJSONArray(key) ?: return fallback
        return if (key in local) fallback else List(list.length()) { list.getString(it) }.asLines()
    }
    return copy(
        title = text("title", title),
        authors = names("authors", authors),
        translators = names("translators", translators),
        isbn = text("isbn", isbn),
        publisher = text("publisher", publisher),
        publicationDate = text("publicationDate", publicationDate),
        description = text("description", description),
        series = text("series", series),
        tags = names("tags", tags),
        shelves = names("shelves", shelves),
        readingStatus = text("readingStatus", readingStatus),
    )
}

internal fun JSONObject.editableBookValues(): JSONObject = JSONObject().also { data ->
    for (key in bookEditKeys) if (has(key)) data.put(key, get(key))
}

internal fun JSONObject.bookFieldMatches(other: JSONObject, key: String): Boolean {
    val first = opt(key)?.takeUnless { it == JSONObject.NULL }
    val second = other.opt(key)?.takeUnless { it == JSONObject.NULL }
    if (first is JSONArray && second is JSONArray) {
        return first.nameSet() == second.nameSet()
    }
    return first == second
}

internal fun JSONObject.fieldNames(): Set<String> = keys().asSequence().toSet()

internal fun String.normalizedBookIsbn(): String =
    Normalizer.normalize(this, Normalizer.Form.NFKC).filterNot { it.isWhitespace() || it == '-' }.uppercase(Locale.ROOT)

// ISBN-10 and its ISBN-13 equivalent identify the same edition.
internal fun String.bookIsbnMatchKey(): String = when {
    length == 13 && startsWith("978") -> substring(3, 12)
    length == 10 -> dropLast(1)
    else -> this
}

private fun JSONArray.nameSet(): Set<String> = List(length()) { getString(it) }.toSet()

private fun String.validText(limit: Int, allowNewlines: Boolean = false): Boolean =
    length <= limit && none { char ->
        (char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt()) &&
            (!allowNewlines || char !in "\n\r\t")
    }

private fun String.names(): List<String> = lineSequence().map(String::trim).filter(String::isNotEmpty)
    .distinctBy { it.lowercase(Locale.ROOT) }.toList().sorted()

private fun List<String>.asLines(): String = joinToString("\n")
private fun String.trimOrNull(): String? = trim().ifEmpty { null }

private val bookIsbnPattern = Regex("[0-9]{9}[0-9X]|[0-9]{13}")
internal val doubanCoverUrl =
    Regex("""https://img[0-9]?\.doubanio\.com/view/(subject|photo)/[sml]/public/[A-Za-z0-9_.-]+\.(jpe?g|png|webp)""")
private val bookEditKeys = listOf(
    "title", "authors", "translators", "isbn", "publisher", "publicationDate", "description", "series",
    "tags", "shelves", "readingStatus",
)
