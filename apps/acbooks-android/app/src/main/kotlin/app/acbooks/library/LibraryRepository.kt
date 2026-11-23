package app.acbooks.library

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import app.acbooks.core.database.BookEditRelations
import app.acbooks.core.database.LibraryDao
import app.acbooks.core.database.BookImportCommit
import app.acbooks.core.database.CloudCatalogBatch
import app.acbooks.core.database.FileSourceKind
import app.acbooks.core.database.FileSourceStatus
import app.acbooks.core.database.LocalBook
import app.acbooks.core.database.LocalBookCard
import app.acbooks.core.database.LocalBookFile
import app.acbooks.core.database.LocalBookRemoval
import app.acbooks.core.database.LocalReadingSession
import app.acbooks.core.database.LocalFileObject
import app.acbooks.core.database.LocalFileSource
import app.acbooks.core.database.PendingBookEdit
import app.acbooks.core.database.RemoteBookLink
import app.acbooks.core.database.RemoteCatalogBook
import app.acbooks.core.database.RemoteCatalogFile
import app.acbooks.core.database.coverCacheKey
import app.acbooks.core.database.managedObjectPath
import app.acbooks.core.database.summarizeReadingSessions
import app.acbooks.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal sealed interface ImportOutcome {
    data class Added(val bookId: String, val isbn: String?) : ImportOutcome
    data object Duplicate : ImportOutcome
    data object MatchingContent : ImportOutcome
    data object Unsupported : ImportOutcome
    data object Failed : ImportOutcome
}

internal data class ReadableBookFile(val id: String, val format: String, val file: File)

internal class LibraryRepository(context: Context, private val dao: LibraryDao) {
    private val resolver = context.contentResolver
    private val filesRoot = context.filesDir
    private val objects = File(filesRoot, "library/objects")
    private val covers = File(context.filesDir, "library/covers")
    private val coverDrafts = File(context.cacheDir, "book-cover-drafts")
    private val cloudCovers = File(context.cacheDir, "cloud-covers")
    private val untitled = context.getString(R.string.library_untitled)
    val books = dao.observeBooks()

    fun coverFile(sha256: String?): File? = sha256?.takeIf(contentHash::matches)?.let { File(covers, "$it.jpg") }

    fun coverFile(book: LocalBookCard): File? {
        val cloud = book.cloudCoverKey?.takeIf(cloudCoverName::matches)?.let { File(cloudCovers, it) }
        return editedCoverFile(book.editedCoverKey)?.takeIf(File::isFile)
            ?: cloud?.takeIf(File::isFile) ?: coverFile(book.sha256)
    }

    fun editedCoverFile(key: String?): File? =
        key?.takeIf(editedCoverName::matches)?.let { File(covers, "edit-$it.jpg") }

    suspend fun loadBookEdit(bookId: String): BookEditSession = withContext(Dispatchers.IO) {
        val book = checkNotNull(dao.book(bookId))
        val embedded = if (book.metadataEdited) null else {
            readBookDetailsFile(openBook(bookId)?.takeIf { it.format == "epub" })?.metadata
        }
        val remote = dao.observeBookCloudMetadata(bookId).first()
        val pending = dao.pendingBookEdit(bookId)
        val patchKeys = pending?.let { JSONObject(it.patchJson).fieldNames() }.orEmpty()
        val draft = bookDraft(book, embedded, remote, patchKeys)
        val baseline = remote?.detailsJson?.let { JSONObject(it).editableBookValues().toString() }
        BookEditSession(draft, baseline, remote?.coverUpdatedAt, patchKeys - "shelves", pending?.coverKey != null)
    }

    private suspend fun bookDraft(
        book: LocalBook,
        embedded: BookBibliography?,
        remote: RemoteCatalogBook?,
        localKeys: Set<String>,
    ): BookEditDraft {
        val draft = readLocalBookEdit(book, embedded)
        return remote?.detailsJson?.let {
            draft.withCloudMetadata(JSONObject(it), localKeys, useEmbeddedFallback = !book.metadataEdited)
        } ?: draft.copy(
            title = remote?.title?.takeUnless { "title" in localKeys } ?: draft.title,
            authors = remote?.author?.takeUnless { "authors" in localKeys } ?: draft.authors,
            description = remote?.description?.takeUnless { "description" in localKeys } ?: draft.description,
        )
    }

    suspend fun readLocalBookEdit(book: LocalBook, embedded: BookBibliography? = null): BookEditDraft {
        val people = dao.bookContributors(book.id)
        val relations = BookEditRelations(
            authors = people.filter { it.role == "author" }.map { it.name },
            translators = people.filter { it.role == "translator" }.map { it.name },
            isbn = dao.bookIdentifiers(book.id).firstOrNull { it.scheme == "isbn" }?.value,
            tags = dao.bookTags(book.id).map { it.name },
        )
        return localBookDraft(book, relations, embedded)
    }

    /** Writes the edited book and its outbox entry in one Room transaction. */
    suspend fun saveBookEdit(bookId: String, initial: BookEditSession, draft: BookEditDraft): Boolean =
        withContext(Dispatchers.IO) {
            libraryFilesLock.withLock {
                val book = checkNotNull(dao.book(bookId))
                val profile = dao.currentServerProfile()
                val link = profile?.let { dao.remoteBookLink(it.id, bookId) }
                val previous = dao.pendingBookEdit(bookId)
                val newCoverKey = draft.cover?.let { uuid() }
                val savedCoverKey = newCoverKey ?: book.editedCoverKey
                val target = editedCoverFile(newCoverKey)
                val oldValues = initial.draft.values()
                val newValues = draft.values()
                val cloudValues = initial.cloudMetadata?.let(::JSONObject)
                val patch = JSONObject()
                for (key in newValues.fieldNames()) {
                    val filledFromFile = cloudValues?.has(key) == true && cloudValues.isNull(key) && !newValues.isNull(key)
                    if (key in initial.retryKeys || filledFromFile || !newValues.bookFieldMatches(oldValues, key)) {
                        patch.put(key, newValues.get(key))
                    }
                }
                val baseline = cloudValues ?: JSONObject()
                previous?.let {
                    val previousPatch = JSONObject(it.patchJson)
                    if (previousPatch.has("shelves")) {
                        patch.put("shelves", previousPatch.get("shelves"))
                        baseline.remove("shelves")
                        it.baselineJson?.let(::JSONObject)?.optJSONArray("shelves")?.let { shelves ->
                            baseline.put("shelves", shelves)
                        }
                    }
                }
                val coverToSync = newCoverKey ?: previous?.coverKey
                val remoteId = link?.remoteBookId ?: profile?.let {
                    bookId.takeIf { dao.hasQueuedCloudBook(profile.id, bookId) }
                }
                val pending = if (profile != null && remoteId != null && (patch.length() > 0 || coverToSync != null)) {
                    PendingBookEdit(
                        bookId, profile.id, remoteId, uuid(), patch.toString(),
                        // Only compare a new edit against what the editor displayed.
                        baseline.toString(),
                        coverToSync,
                        initial.cloudCoverVersion,
                    )
                } else null
                val relations = draft.relations()
                withContext(NonCancellable) {
                    try {
                        if (target != null) {
                            val staged = checkNotNull(draft.cover)
                            check(staged.parentFile == coverDrafts && staged.isFile)
                            if (!covers.isDirectory && !covers.mkdirs()) throw IOException("Could not create the cover directory")
                            staged.copyTo(target)
                        }
                        dao.writeBookEdit(
                            bookId,
                            book.copy(
                                title = draft.title.trim(),
                                author = relations.authors.joinToString(", ").ifEmpty { null },
                                description = draft.description.trim().ifEmpty { null },
                                series = draft.series.trim().ifEmpty { null },
                                publisher = draft.publisher.trim().ifEmpty { null },
                                publicationDate = draft.publicationDate.publicationDateOnly().ifEmpty { null },
                                readingStatus = draft.readingStatus,
                                editedCoverKey = savedCoverKey,
                                updatedAt = System.currentTimeMillis(),
                            ),
                            relations,
                            pending,
                        )
                    } catch (failure: Exception) {
                        target?.delete()
                        throw failure
                    }
                    val oldCoverKey = book.editedCoverKey
                    if (newCoverKey != null && oldCoverKey != null && !dao.hasPendingEditedCover(oldCoverKey)) {
                        editedCoverFile(oldCoverKey)?.delete()
                    }
                }
                pending != null
            }
        }

    suspend fun saveBookShelves(bookId: String, shelfIds: Set<String>): Boolean =
        withContext(Dispatchers.IO) {
            libraryFilesLock.withLock {
                val names = shelfIds.map { checkNotNull(dao.libraryShelf(it)).name }
                require(names.size <= 20)
                val previous = dao.pendingBookEdit(bookId)
                if (shelfIds == dao.shelfIdsForBook(bookId).toSet() &&
                    previous?.let { JSONObject(it.patchJson).has("shelves") } != true
                ) return@withLock false
                val profile = dao.currentServerProfile()
                val remoteId = profile?.let { active ->
                    dao.remoteBookLink(active.id, bookId)?.remoteBookId
                        ?: bookId.takeIf { dao.hasQueuedCloudBook(active.id, bookId) }
                }
                val pending = if (profile != null && remoteId != null) {
                    val patch = previous?.let { JSONObject(it.patchJson) } ?: JSONObject()
                    val baseline = previous?.baselineJson?.let(::JSONObject) ?: JSONObject()
                    val remote = dao.remoteCatalogBook(profile.id, remoteId)
                    val remoteValues = remote?.detailsJson?.let(::JSONObject)
                    baseline.remove("shelves")
                    remoteValues?.optJSONArray("shelves")?.let { baseline.put("shelves", it) }
                    patch.put("shelves", JSONArray(names))
                    PendingBookEdit(
                        bookId, profile.id, remoteId, uuid(), patch.toString(), baseline.toString(),
                        previous?.coverKey, previous?.baseCoverVersion,
                    )
                } else null
                dao.writeBookShelves(bookId, shelfIds, pending)
                pending != null
            }
        }

    suspend fun stageBookCover(uri: Uri): File = withContext(Dispatchers.IO) {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val bytes = resolver.openInputStream(uri)?.use(::readCoverBytes) ?: throw IOException("Cannot read cover")
        stageBookCover(bytes)
    }

    suspend fun stageDoubanCover(url: String): File = withContext(Dispatchers.IO) {
        require(doubanCoverUrl.matches(url))
        val connection = URI(url).toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Referer", "https://book.douban.com/")
            connection.setRequestProperty("User-Agent", "acbooks")
            if (connection.responseCode != 200 || connection.contentType?.startsWith("image/") != true) {
                throw IOException("Douban did not return a cover image")
            }
            stageBookCover(connection.inputStream.use(::readCoverBytes))
        } finally {
            connection.disconnect()
        }
    }

    private fun stageBookCover(bytes: ByteArray): File {
        if (!coverDrafts.isDirectory && !coverDrafts.mkdirs()) throw IOException("Could not create cover staging directory")
        val file = File.createTempFile("book-cover-", ".jpg", coverDrafts)
        try {
            if (!encodeCover(bytes, file)) throw IOException("Invalid cover image")
            return file
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    fun cacheCloudCovers(books: List<RemoteCatalogBook>, client: LibraryCloudClient, token: String) {
        if (!cloudCovers.isDirectory && !cloudCovers.mkdirs()) return
        for (book in books) {
            val key = book.coverCacheKey()?.takeIf(cloudCoverName::matches) ?: continue
            val target = File(cloudCovers, key)
            if (target.isFile) continue
            val version = book.coverUpdatedAt ?: continue
            val pending = try {
                File.createTempFile("cloud-cover-", ".part", cloudCovers)
            } catch (_: IOException) {
                return
            }
            try {
                client.cover(token, book.remoteBookId, version) { response ->
                    if (response.contentType?.startsWith("image/") != true) throw IOException("Invalid book cover")
                    response.inputStream.use { input ->
                        pending.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var size = 0
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                size += count
                                if (size > MaxCloudCoverBytes) throw IOException("Book cover is too large")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                if (pending.length() > 0) pending.renameTo(target)
            } catch (_: IOException) {
                // A missing thumbnail leaves the book usable with its local cover or title.
            } finally {
                pending.delete()
            }
        }
        val current = books.mapNotNull(RemoteCatalogBook::coverCacheKey).toSet()
        cloudCovers.listFiles()?.filter { cloudCoverName.matches(it.name) && it.name !in current }?.forEach { it.delete() }
    }

    suspend fun openBook(bookId: String): ReadableBookFile? = withContext(Dispatchers.IO) {
        libraryFilesLock.withLock {
            val preferredId = dao.firstBookFile(bookId)?.id
            val files = dao.allBookFiles(bookId).sortedBy { it.id != preferredId }
            for (entry in files) {
                if (entry.localCopyRemoved) continue
                val file = ownedObject(entry.sha256, entry.format) ?: continue
                val present = file.isFile
                updateLocalCopyStatus(entry.sha256, entry.format, present)
                if (present) return@withLock ReadableBookFile(entry.id, entry.format, file)
            }
            null
        }
    }

    suspend fun <T> uploadBookFile(file: LocalBookFile, send: (File) -> T): T = withContext(Dispatchers.IO) {
        val current = dao.bookFile(file.id)
        check(current != null && !current.localCopyRemoved)
        val managed = ownedObject(current.sha256, current.format)?.takeIf(File::isFile)
        if (managed != null && matchesContent(managed, current.sha256)) return@withContext send(managed)

        val source = dao.fileSources(current.sha256, current.format).firstOrNull {
            it.kind == FileSourceKind.External && it.location == current.sourceUri &&
                it.status in setOf(FileSourceStatus.Present, FileSourceStatus.Unknown)
        } ?: throw IOException("Book file is unavailable")
        // Stage a readable SAF source without modifying the original document.
        val temporary = File.createTempFile("upload-", ".part", filesRoot)
        try {
            val uri = Uri.parse(source.location)
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw IOException("Invalid book source")
            val input = resolver.openInputStream(uri) ?: throw IOException("Book source is unavailable")
            input.use { from -> temporary.outputStream().use { from.copyTo(it) } }
            if (!matchesContent(temporary, current.sha256)) throw IOException("Book source has changed")
            send(temporary)
        } finally {
            temporary.delete()
        }
    }

    suspend fun downloadBookFile(
        bookId: String,
        remote: RemoteCatalogFile,
        client: LibraryCloudClient,
        token: String,
    ): ReadableBookFile = withContext(Dispatchers.IO) {
        if (!objects.isDirectory && !objects.mkdirs() && !objects.isDirectory) throw IOException("Cannot open book storage")
        if (objects.canonicalFile != File(filesRoot.canonicalFile, "library/objects")) throw IOException("Invalid book storage")
        val hash = remote.sha256
        val prefix = "download-${remote.profileId.asUuid()}-${remote.remoteFileId.asUuid()}-"
        val pending = if (hash == null) File.createTempFile("download-", ".part", objects) else {
            require(contentHash.matches(hash) && remote.format in setOf("epub", "pdf"))
            val part = File(objects, "$prefix$hash-${remote.sizeBytes}.part")
            if (part.canonicalFile != File(objects.canonicalFile, part.name)) throw IOException("Invalid download path")
            objects.listFiles { candidate -> candidate.name.startsWith(prefix) && candidate.name.endsWith(".part") }
                ?.filterNot { it == part }?.forEach(File::delete)
            part
        }
        var complete = false
        try {
            val actualHash = downloadContent(pending, remote, client, token)
            libraryFilesLock.withLock {
                val target = ownedObject(actualHash, remote.format) ?: throw IOException("Cannot open book storage")
                val published = !target.isFile || !matchesContent(target, actualHash)
                if (published && !pending.renameTo(target)) throw IOException("Could not publish the book file")
                try {
                    val file = dao.attachDownloadedFile(remote, bookId, actualHash, remote.sizeBytes, System.currentTimeMillis())
                    complete = true
                    ReadableBookFile(file.id, file.format, target)
                } catch (failure: Exception) {
                    if (published && !dao.hasContent(actualHash, remote.format)) target.delete()
                    throw failure
                }
            }
        } finally {
            if (complete || hash == null || pending.length() > remote.sizeBytes) pending.delete()
        }
    }

    private suspend fun downloadContent(part: File, remote: RemoteCatalogFile, client: LibraryCloudClient, token: String): String {
        val hash = remote.sha256
        if (part.length() >= remote.sizeBytes && part.isFile) {
            if (hash != null && part.length() == remote.sizeBytes && matchesContent(part, hash)) return hash
            part.delete()
        }
        val offset = if (hash == null) 0L else part.length()
        return try {
            transferContent(part, remote, client, token, offset)
        } catch (failure: CloudHttpException) {
            if (failure.status != 416 || offset == 0L) throw failure
            part.delete()
            transferContent(part, remote, client, token, 0)
        }
    }

    fun removePartialDownloads(profileId: String, remoteFileIds: Set<String>) {
        if (remoteFileIds.isEmpty()) return
        val prefix = "download-${profileId.asUuid()}-"
        objects.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(".part") &&
            file.name.removePrefix(prefix).take(36) in remoteFileIds
        }
            ?.forEach(File::delete)
    }

    private suspend fun transferContent(
        part: File, remote: RemoteCatalogFile, client: LibraryCloudClient, token: String, offset: Long,
    ): String {
        val context = kotlin.coroutines.coroutineContext
        val expected = client.download(token, remote.remoteFileId, offset, remote.sha256) { response ->
            val responseHash = response.getHeaderField("X-Content-SHA256")
            val hash = remote.sha256 ?: responseHash ?: throw IOException("Book checksum is unavailable")
            val append = response.responseCode == 206
            if (!contentHash.matches(hash) || (responseHash != null && responseHash != hash) ||
                (append && (offset == 0L || response.getHeaderField("ETag") != "\"$hash\"" ||
                    response.getHeaderField("Content-Range") != "bytes $offset-${remote.sizeBytes - 1}/${remote.sizeBytes}"))
            ) {
                part.delete()
                throw IOException("Book version changed")
            }
            var size = if (append) offset else 0L
            response.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        context.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > remote.sizeBytes) {
                            part.delete()
                            throw IOException("Book size changed")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (size != remote.sizeBytes) throw IOException("Incomplete book download")
            hash
        }
        if (!matchesContent(part, expected)) {
            part.delete()
            throw IOException("Book checksum changed")
        }
        return expected
    }

    /** Evict the app-owned copy while keeping the content object and all book references. */
    suspend fun removeLocalCopy(sha256: String, format: String): Boolean = withContext(Dispatchers.IO) {
        libraryFilesLock.withLock {
            val file = ownedObject(sha256, format) ?: return@withLock false
            if (file.exists() && (!file.isFile || !file.delete())) return@withLock false
            updateLocalCopyStatus(sha256, format, present = false)
            true
        }
    }

    suspend fun refreshFolderGrant(folderId: String): Boolean = withContext(Dispatchers.IO) {
        val folder = dao.authorizedFolder(folderId) ?: return@withContext false
        val tree = Uri.parse(folder.treeUri)
        val granted = resolver.persistedUriPermissions.any { it.isReadPermission && it.uri == tree }
        dao.updateFolderGrant(folderId, granted, System.currentTimeMillis())
        granted
    }

    suspend fun refreshExternalSource(source: LocalFileSource): String = withContext(Dispatchers.IO) {
        require(source.kind == FileSourceKind.External)
        val uri = Uri.parse(source.location)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return@withContext FileSourceStatus.Unknown
        val folderId = source.folderId
        if (folderId != null && !refreshFolderGrant(folderId)) {
            return@withContext FileSourceStatus.PermissionRequired
        }
        val checkedAt = System.currentTimeMillis()
        val status = try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { FileSourceStatus.Present } ?: FileSourceStatus.Unknown
        } catch (_: SecurityException) {
            FileSourceStatus.PermissionRequired
        } catch (_: FileNotFoundException) {
            FileSourceStatus.Missing
        } catch (_: IOException) {
            FileSourceStatus.Unknown
        }
        dao.updateExternalSourceStatus(source.sha256, source.format, source.location, status, checkedAt)
        status
    }

    private suspend fun updateLocalCopyStatus(sha256: String, format: String, present: Boolean) {
        dao.setLocalCopyStatus(
            sha256, format, managedObjectPath(sha256, format),
            if (present) FileSourceStatus.Present else FileSourceStatus.Missing,
            System.currentTimeMillis(),
        )
    }

    private fun ownedObject(sha256: String, format: String): File? {
        if (!contentHash.matches(sha256) || format !in setOf("epub", "pdf")) return null
        val relativePath = managedObjectPath(sha256, format)
        val file = File(filesRoot, relativePath)
        return try {
            file.takeIf { it.canonicalFile == File(filesRoot.canonicalFile, relativePath) }
        } catch (_: IOException) {
            null
        }
    }

    suspend fun readingPosition(fileId: String) = dao.readingPosition(fileId)

    fun observeBookDetails(bookId: String) = combine(
        dao.observeBookFormats(bookId).distinctUntilChanged(),
        dao.observeBookShelves(bookId).distinctUntilChanged(),
        dao.observeBookReadingPosition(bookId).distinctUntilChanged(),
        flow { emit(readBookDetailsFile(openBook(bookId))) },
        dao.observeCompletedReadingSessions(bookId).combine(dao.observeCloudReaderRecords(bookId)) { local, cloud ->
            val remote = cloud.mapNotNull { it.completedSession(bookId) }
            val remoteIds = cloud.filter { it.kind == "reading_session" }.mapTo(mutableSetOf()) { it.id }
            summarizeReadingSessions(remote + local.filterNot { it.id in remoteIds }, ZoneId.systemDefault())
        },
    ) { formats, shelves, position, detailsFile, statistics ->
        BookDetailsData(
            metadata = detailsFile?.metadata,
            formats = formats,
            shelves = shelves,
            readingLocation = readBookLocation(position, detailsFile?.pdfPageCount),
            readingStatistics = statistics?.let {
                BookReadingStatistics(it.activeSeconds.toLong(), it.readingDays, it.lastReadAt)
            },
        )
    }.combine(observeEditedBook(bookId)) { details, (book, remote, pending) ->
        if (book == null) return@combine details
        val embedded = details.metadata
        val localKeys = pending?.let { JSONObject(it.patchJson).fieldNames() }.orEmpty()
        val draft = bookDraft(book, embedded, remote, localKeys)
        val relations = draft.relations()
        details.copy(
            metadata = (embedded ?: BookBibliography()).copy(
                description = draft.description.ifBlank { null },
                translators = relations.translators,
                isbn = draft.isbn.ifBlank { null },
                publisher = draft.publisher.ifBlank { null },
                publicationDate = draft.publicationDate.ifBlank { null },
                series = draft.series.ifBlank { null },
            ),
            tags = relations.tags,
            readingStatus = when (draft.readingStatus) {
                "reading" -> BookReadingStatus.Reading
                "done" -> BookReadingStatus.Finished
                else -> BookReadingStatus.Unread
            },
        )
    }

    suspend fun saveReadingSession(session: LocalReadingSession) = dao.insertReadingSession(session)

    suspend fun bookUploadPayload(book: LocalBook): String {
        val bibliography = if (book.metadataEdited) null else {
            readBookDetailsFile(openBook(book.id)?.takeIf { it.format == "epub" })?.metadata
        }
        val relations = readLocalBookEdit(book, bibliography).relations()
        return bookCreatePayload(
            book, bibliography, relations.authors, relations.translators, relations.isbn,
            relations.tags, dao.bookShelves(book.id).map { it.name },
        )
    }

    private fun observeEditedBook(bookId: String) = combine(
        dao.observeBook(bookId), dao.observeBookCloudMetadata(bookId), dao.observePendingBookEdit(bookId),
    ) { book, remote, pending -> Triple(book, remote, pending) }

    suspend fun saveReadingPosition(fileId: String, locator: String, percent: Double? = null) =
        dao.recordReadingPosition(fileId, locator, percent, System.currentTimeMillis())

    /** Remove the local book record and owned cached files, but never the source URI. */
    suspend fun deleteLocalBook(bookId: String): Boolean = withContext(Dispatchers.IO) {
        libraryFilesLock.withLock {
            val profile = dao.currentServerProfile()
            val link = profile?.let { dao.remoteBookLink(it.id, bookId) }
            if (link != null) {
                val copies = dao.allBookFiles(bookId).filterNot { it.localCopyRemoved }
                if (copies.isEmpty()) return@withLock true
                val backedUp = dao.backedUpLocalFileIds(link.profileId, bookId).toSet()
                if (copies.any { it.id !in backedUp }) return@withLock false
                for (copy in copies) {
                    if (!dao.hasOtherOfflineUsers(bookId, copy.sha256, copy.format)) {
                        val path = ownedObject(copy.sha256, copy.format) ?: return@withLock false
                        if (path.exists() && (!path.isFile || !path.delete())) return@withLock false
                        updateLocalCopyStatus(copy.sha256, copy.format, present = false)
                    }
                    dao.setBookFileCopyRemoved(copy.id, true)
                }
                return@withLock true
            }
            val removal = dao.removeLocalBook(bookId) ?: return@withLock false
            removeBookFiles(removal)
            true
        }
    }

    suspend fun deleteBookAfterCloudConfirmation(link: RemoteBookLink): Boolean =
        withContext(Dispatchers.IO) {
            libraryFilesLock.withLock {
                withContext(NonCancellable) cleanup@ {
                    val removal = dao.applyConfirmedRemoteBookDeletion(link) ?: return@cleanup false
                    removeBookFiles(removal)
                    val prefix = "${link.profileId}-${link.remoteBookId}-"
                    cloudCovers.listFiles()?.filter { it.name.startsWith(prefix) && cloudCoverName.matches(it.name) }
                        ?.forEach { it.delete() }
                    true
                }
            }
        }

    suspend fun applyCloudCatalog(profileId: String, expectedCursor: Long?, rebuild: Boolean, batch: CloudCatalogBatch) {
        withContext(Dispatchers.IO) {
            libraryFilesLock.withLock {
                withContext(NonCancellable) {
                    dao.applyCloudCatalog(profileId, expectedCursor, rebuild, batch).forEach(::removeBookFiles)
                    removePartialDownloads(profileId, batch.versions.filter { it.deleted && it.entityType == "book_file" }
                        .mapTo(mutableSetOf()) { it.entityId })
                }
            }
        }
    }

    private fun removeBookFiles(removal: LocalBookRemoval) {
        // Different book records can share content-addressed objects; only unlink objects
        // which no longer have a database reference after the transaction.
        for (item in removal.unreferencedObjects) {
            ownedObject(item.sha256, item.format)?.let { path ->
                if (path.exists() && !path.delete()) Log.w("LibraryDelete", "Could not remove cached book object")
            }
        }
        editedCoverFile(removal.editedCoverKey)?.delete()
        for (hash in removal.unreferencedCoverHashes) {
            if (contentHash.matches(hash)) {
                val path = File(covers, "$hash.jpg")
                if (path.exists() && !path.delete()) Log.w("LibraryDelete", "Could not remove cached book cover")
            }
        }
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
            if (!objects.isDirectory && !objects.mkdirs() && !objects.isDirectory) {
                throw IOException("Could not create book storage")
            }
            if (objects.canonicalFile != File(filesRoot.canonicalFile, "library/objects")) {
                throw IOException("Could not access book storage")
            }
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
                libraryFilesLock.withLock {
                    val objectFile = ownedObject(sha256, format) ?: throw IOException("Could not access book storage")
                    val sourceKey = uri.normalizeScheme().toString()
                    val alreadyImported = dao.hasImported(sourceKey, sha256, format)
                    if (objectFile.isFile && objectFile.length() == size && alreadyImported) {
                        dao.restoreImportedCopy(sourceKey, sha256, format)
                        dao.recordImportedSources(sha256, format, sourceKey, System.currentTimeMillis())
                        return@withLock ImportOutcome.Duplicate
                    }
                    if (!alreadyImported && !allowMatchingContent && dao.hasContent(sha256, format)) {
                        return@withLock ImportOutcome.MatchingContent
                    }
                    val metadata = readBookMetadata(pending, format, title)
                    ensureActive()
                    if (!objectFile.isFile || objectFile.length() != size) {
                        if (!pending.renameTo(objectFile)) throw IOException("Could not publish book file")
                    }
                    saveCover(sha256, metadata.cover)
                    val book = LocalBook(uuid(), metadata.title, metadata.author, System.currentTimeMillis())
                    val file = LocalBookFile(uuid(), book.id, sha256, format, sourceKey, name?.take(512) ?: "$title.$format")
                    val bibliography = if (format == "epub") {
                        readBookDetailsFile(ReadableBookFile(file.id, format, objectFile))?.metadata
                    } else null
                    val commit = dao.importBook(
                        book, file, LocalFileObject(sha256, format, size), allowMatchingContent, book.importedAt,
                        bookUploadPayload = bookCreatePayload(book, bibliography, isbn = metadata.isbn ?: bibliography?.isbn),
                        isbn = metadata.isbn,
                    )
                    when (commit) {
                        BookImportCommit.Added -> ImportOutcome.Added(book.id, metadata.isbn)
                        BookImportCommit.Duplicate -> ImportOutcome.Duplicate
                        BookImportCommit.MatchingContent -> ImportOutcome.MatchingContent
                    }
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
        try {
            val pending = File.createTempFile("cover-", ".part", covers)
            try {
                if (encodeCover(bytes, pending)) pending.renameTo(target)
            } finally {
                pending.delete()
            }
        } catch (_: IOException) {
            Log.w("LibraryImport", "Could not cache the book cover")
        }
    }

    private fun encodeCover(bytes: ByteArray, file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth !in 1..16384 || options.outHeight !in 1..16384) return false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 640) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return false
        return try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 84, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun uuid(): String = UUID.randomUUID().toString()
}

private val contentHash = Regex("[0-9a-f]{64}")
private val editedCoverName = Regex("[0-9a-f-]{36}")
private val cloudCoverName = Regex("[0-9a-f-]{73}-[0-9]{1,16}")
private const val MaxCloudCoverBytes = 5 * 1024 * 1024

private fun readCoverBytes(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > MaxCloudCoverBytes) throw IOException("Book cover is too large")
        output.write(buffer, 0, count)
    }
}

private fun matchesContent(file: File, sha256: String): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().hex() == sha256
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
// Keep file publication and reference commits ordered with deletion across repository instances.
private val libraryFilesLock = Mutex()
