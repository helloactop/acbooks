package app.acbooks.library

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import app.acbooks.core.database.LibraryDao
import app.acbooks.core.database.BookImportCommit
import app.acbooks.core.database.LocalBook
import app.acbooks.core.database.LocalBookFile
import app.acbooks.core.database.LocalFileObject
import app.acbooks.core.database.LocalReadingPosition
import app.acbooks.R
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

internal enum class ImportOutcome { Added, Duplicate, MatchingContent, Unsupported, Failed }

internal data class ReadableBookFile(val id: String, val format: String, val file: File)

internal class LibraryRepository(context: Context, private val dao: LibraryDao) {
    private val resolver = context.contentResolver
    private val objects = File(context.filesDir, "library/objects")
    private val covers = File(context.filesDir, "library/covers")
    private val untitled = context.getString(R.string.library_untitled)
    val books = dao.observeBooks()

    fun coverFile(sha256: String?): File? = sha256?.takeIf(contentHash::matches)?.let { File(covers, "$it.jpg") }

    suspend fun openBook(bookId: String): ReadableBookFile? = withContext(Dispatchers.IO) {
        val entry = dao.firstBookFile(bookId) ?: return@withContext null
        val format = entry.format
        if ((format != "epub" && format != "pdf") || !contentHash.matches(entry.sha256)) return@withContext null
        val file = File(objects, "${entry.sha256}.$format")
        if (!file.isFile) return@withContext null
        ReadableBookFile(entry.id, format, file)
    }

    suspend fun readingPosition(fileId: String) = dao.readingPosition(fileId)

    fun observeBookDetails(bookId: String) = combine(
        dao.observeBookFormats(bookId).distinctUntilChanged(),
        dao.observeBookShelves(bookId).distinctUntilChanged(),
        dao.observeBookReadingPosition(bookId).distinctUntilChanged(),
        flow { emit(readBookDetailsFile(openBook(bookId))) },
    ) { formats, shelves, position, detailsFile ->
        BookDetailsData(
            metadata = detailsFile?.metadata,
            formats = formats,
            shelves = shelves,
            readingLocation = readBookLocation(position, detailsFile?.pdfPageCount),
        )
    }

    suspend fun saveReadingPosition(fileId: String, locator: String) =
        dao.saveReadingPosition(LocalReadingPosition(fileId, locator))

    /** Remove the local book record and owned cached files, but never the source URI. */
    suspend fun deleteLocalBook(bookId: String): Boolean = withContext(Dispatchers.IO) {
        val removal = dao.removeLocalBook(bookId) ?: return@withContext false
        // Different book records can share content-addressed objects; only unlink objects
        // which no longer have a database reference after the transaction.
        for (item in removal.unreferencedObjects) {
            if (contentHash.matches(item.sha256) && (item.format == "epub" || item.format == "pdf")) {
                val path = File(objects, "${item.sha256}.${item.format}")
                if (path.exists() && !path.delete()) Log.w("LibraryDelete", "Could not remove cached book object")
            }
        }
        for (hash in removal.unreferencedCoverHashes) {
            if (contentHash.matches(hash)) {
                val path = File(covers, "$hash.jpg")
                if (path.exists() && !path.delete()) Log.w("LibraryDelete", "Could not remove cached book cover")
            }
        }
        true
    }

    suspend fun import(uri: Uri, allowMatchingContent: Boolean): ImportOutcome = withContext(Dispatchers.IO) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return@withContext ImportOutcome.Unsupported
        try {
            val mime = resolver.getType(uri)
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            val extension = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
            val format = when {
                extension == "epub" || extension == "pdf" -> extension
                mime == "application/epub+zip" -> "epub"
                mime == "application/pdf" -> "pdf"
                else -> return@withContext ImportOutcome.Unsupported
            }
            val title = name?.substringBeforeLast('.')?.trim()?.take(512)?.takeIf(String::isNotEmpty) ?: untitled
            if (!objects.isDirectory && !objects.mkdirs()) throw IOException("Could not create book storage")
            val pending = File.createTempFile("import-", ".part", objects)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                val source = resolver.openInputStream(uri) ?: throw IOException("Could not open document")
                source.use { input ->
                    pending.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            size += read
                        }
                        output.fd.sync()
                    }
                }
                ensureActive()
                val sha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                val objectFile = File(objects, "$sha256.$format")
                val sourceKey = uri.normalizeScheme().toString()
                val alreadyImported = dao.hasImported(sourceKey, sha256, format)
                if (objectFile.isFile && objectFile.length() == size && alreadyImported) {
                    return@withContext ImportOutcome.Duplicate
                }
                if (!alreadyImported && !allowMatchingContent && dao.hasContent(sha256, format)) {
                    return@withContext ImportOutcome.MatchingContent
                }
                val metadata = readBookMetadata(pending, format, title)
                ensureActive()
                if (!objectFile.isFile || objectFile.length() != size) {
                    if (!pending.renameTo(objectFile)) throw IOException("Could not publish book file")
                }
                saveCover(sha256, metadata.cover)
                val book = LocalBook(uuid(), metadata.title, metadata.author, System.currentTimeMillis())
                val file = LocalBookFile(uuid(), book.id, sha256, format, sourceKey, name?.take(512) ?: "$title.$format")
                when (dao.importBook(book, file, LocalFileObject(sha256, format, size), allowMatchingContent)) {
                    BookImportCommit.Added -> ImportOutcome.Added
                    BookImportCommit.Duplicate -> ImportOutcome.Duplicate
                    BookImportCommit.MatchingContent -> ImportOutcome.MatchingContent
                }
            } finally {
                pending.delete()
            }
        } catch (_: IOException) {
            Log.w("LibraryImport", "Could not read or validate the selected book")
            ImportOutcome.Failed
        } catch (_: SecurityException) {
            Log.w("LibraryImport", "The selected book is not readable")
            ImportOutcome.Failed
        } catch (_: android.database.SQLException) {
            Log.w("LibraryImport", "Could not save the selected book")
            ImportOutcome.Failed
        }
    }

    private fun saveCover(sha256: String, bytes: ByteArray?) {
        if (bytes == null) return
        val target = coverFile(sha256) ?: return
        if (target.isFile || (!covers.isDirectory && !covers.mkdirs())) return
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth !in 1..16384 || options.outHeight !in 1..16384) return
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 640) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return
        try {
            val pending = File.createTempFile("cover-", ".part", covers)
            try {
                val encoded = pending.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 84, it) }
                if (encoded) pending.renameTo(target)
            } finally {
                pending.delete()
            }
        } catch (_: IOException) {
            Log.w("LibraryImport", "Could not cache the book cover")
        } finally {
            bitmap.recycle()
        }
    }

    private fun uuid(): String = UUID.randomUUID().toString()
}

private val contentHash = Regex("[0-9a-f]{64}")
