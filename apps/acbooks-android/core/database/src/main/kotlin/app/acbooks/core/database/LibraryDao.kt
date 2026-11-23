package app.acbooks.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class BookImportCommit { Added, Duplicate, MatchingContent }

// Internal book copies are content-addressed and can be shared by several book records.
// This plan lists only files no longer referenced after the database transaction.
data class LocalBookRemoval(
    val unreferencedObjects: List<LocalFileObject>,
    val unreferencedCoverHashes: List<String>,
)

@Dao
abstract class LibraryDao :
    ReadingSyncDao(), ShelfDao, ReadingSessionDao, ReadingMarkDao, CatalogMetadataDao,
    FileSourceDao, FolderDao, CloudSyncDao, CloudReaderDao, CloudDownloadDao {
    @Upsert
    protected abstract suspend fun upsertServerProfile(profile: ServerProfile)

    @Query("UPDATE server_profiles SET currentSlot = NULL WHERE id = :profileId")
    protected abstract suspend fun deactivateServerProfile(profileId: String)

    /** Accepts identity confirmed by the server. Selecting a connection does not authenticate a session. */
    @Transaction
    open suspend fun saveCurrentServerProfile(
        serverInstanceId: String,
        accountId: String,
        deviceId: String,
        origin: String,
        verifiedAt: Long,
        replaceCurrent: Boolean = false,
        newProfileId: String = UUID.randomUUID().toString(),
    ): ServerProfile {
        require(isServerOriginSafe(origin)) { "Invalid server origin" }
        val current = currentServerProfile()
        val normalized = origin.trimEnd('/')
        val existing = verifiedServerProfile(normalized, accountId)
        check(existing == null || existing.serverInstanceId == serverInstanceId) { "Server identity changed" }
        if (current != null && current.id != existing?.id) {
            check(replaceCurrent) { "Server identity changed" }
            clearReadingPositionRevisions(current.id, resync = true)
            deactivateServerProfile(current.id)
        }
        val profile = ServerProfile(
            id = existing?.id ?: newProfileId,
            serverInstanceId = serverInstanceId,
            accountId = accountId,
            deviceId = deviceId,
            origin = normalized,
            updatedAt = verifiedAt,
            currentSlot = 1,
        )
        upsertServerProfile(profile)
        return profile
    }

    @Query("""
        SELECT b.id, COALESCE(c.title, b.title) AS title, COALESCE(c.author, (
            SELECT group_concat(name, ', ') FROM (
                SELECT c.name FROM book_contributors bc
                JOIN contributors c ON c.id = bc.contributorId
                WHERE bc.bookId = b.id AND bc.role = 'author'
                ORDER BY c.name, c.id
            )
        ), b.author) AS author, b.importedAt,
        COALESCE(f.format, (
            SELECT rf.format FROM remote_catalog_files rf
            WHERE rf.profileId = c.profileId AND rf.remoteBookId = c.remoteBookId
            ORDER BY rf.fileStatus = 'present' DESC, rf.remoteFileId LIMIT 1
        ), '') AS format, f.sha256,
        EXISTS(
            SELECT 1 FROM book_files bf JOIN file_sources s ON s.sha256 = bf.sha256 AND s.format = bf.format
            WHERE bf.bookId = b.id AND bf.localCopyRemoved = 0
            AND s.kind IN ('managed', 'downloaded') AND s.status IN ('present', 'unknown')
        ) AS hasLocalCopy,
        (c.remoteBookId IS NOT NULL) AS cloudBacked,
        EXISTS(
            SELECT 1 FROM remote_catalog_files rf WHERE rf.profileId = c.profileId AND rf.remoteBookId = c.remoteBookId
            AND rf.fileStatus = 'present' AND rf.format IN ('epub', 'pdf')
        ) AS canDownload,
        (c.profileId || '-' || c.remoteBookId || '-' || c.coverUpdatedAt) AS cloudCoverKey
        FROM books b
        LEFT JOIN server_profiles p ON p.currentSlot = 1
        LEFT JOIN remote_book_links l ON l.profileId = p.id AND l.localBookId = b.id
        LEFT JOIN remote_catalog c ON c.profileId = l.profileId AND c.remoteBookId = l.remoteBookId AND c.available = 1
        LEFT JOIN book_files f ON f.id = COALESCE(b.preferredFileId, (
            SELECT id FROM book_files WHERE bookId = b.id ORDER BY id LIMIT 1
        ))
        WHERE c.remoteBookId IS NOT NULL
           OR EXISTS (SELECT 1 FROM book_files bf WHERE bf.bookId = b.id AND bf.localCopyRemoved = 0)
           OR NOT EXISTS (
               SELECT 1 FROM remote_book_links old JOIN server_profiles owner ON owner.id = old.profileId
               WHERE old.localBookId = b.id AND owner.verification = 'verified'
           )
        ORDER BY b.importedAt DESC, b.id
    """)
    abstract fun observeBooks(): Flow<List<LocalBookCard>>

    @Query("""
        SELECT f.* FROM books b
        JOIN book_files f ON f.id = COALESCE(b.preferredFileId, (
            SELECT id FROM book_files WHERE bookId = b.id ORDER BY id LIMIT 1
        ))
        WHERE b.id = :bookId
    """)
    abstract suspend fun firstBookFile(bookId: String): LocalBookFile?

    @Query("SELECT * FROM book_files WHERE id = :fileId")
    abstract suspend fun bookFile(fileId: String): LocalBookFile?

    @Query("SELECT * FROM book_files WHERE bookId = :bookId")
    abstract suspend fun allBookFiles(bookId: String): List<LocalBookFile>

    @Query("UPDATE book_files SET localCopyRemoved = :removed WHERE id = :fileId")
    abstract suspend fun setBookFileCopyRemoved(fileId: String, removed: Boolean)

    @Query("""
        SELECT EXISTS(SELECT 1 FROM book_files WHERE sha256 = :sha256 AND format = :format
            AND bookId <> :bookId AND localCopyRemoved = 0)
    """)
    abstract suspend fun hasOtherOfflineUsers(bookId: String, sha256: String, format: String): Boolean

    @Query("UPDATE book_files SET localCopyRemoved = 0 WHERE sourceUri = :sourceUri AND sha256 = :sha256 AND format = :format")
    abstract suspend fun restoreImportedCopy(sourceUri: String, sha256: String, format: String)

    @Query("DELETE FROM books WHERE id = :bookId")
    abstract suspend fun deleteBookRow(bookId: String): Int

    @Query("DELETE FROM book_files WHERE id = :fileId")
    protected abstract suspend fun deleteBookFileRow(fileId: String): Int

    @Query("""
        DELETE FROM file_objects WHERE sha256 = :sha256 AND format = :format
        AND NOT EXISTS (
            SELECT 1 FROM book_files WHERE sha256 = :sha256 AND format = :format
        )
    """)
    abstract suspend fun deleteObjectIfUnreferenced(sha256: String, format: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM book_files WHERE sha256 = :sha256)")
    abstract suspend fun hasFileWithHash(sha256: String): Boolean

    @Transaction
    open suspend fun removeLocalBook(bookId: String): LocalBookRemoval? {
        val files = allBookFiles(bookId)
        if (deleteBookRow(bookId) == 0) return null

        return releaseUnreferencedFiles(files)
    }

    private suspend fun releaseUnreferencedFiles(files: List<LocalBookFile>): LocalBookRemoval {
        val unreferencedObjects = files.distinctBy { it.sha256 to it.format }
            .filter { deleteObjectIfUnreferenced(it.sha256, it.format) > 0 }
            .map { LocalFileObject(it.sha256, it.format, 0L) }
        val unreferencedCoverHashes = files.map { it.sha256 }.distinct()
            .filterNot { hasFileWithHash(it) }

        return LocalBookRemoval(unreferencedObjects, unreferencedCoverHashes)
    }

    @Transaction
    open suspend fun applyConfirmedRemoteBookDeletion(link: RemoteBookLink): LocalBookRemoval? {
        removeCachedRemoteBook(link.profileId, link.remoteBookId)
        // A different association may have been saved while the remote request was in flight.
        if (remoteBookLink(link.profileId, link.localBookId) != link) return null
        return removeLocalBook(link.localBookId)
    }

    @Transaction
    open suspend fun applyConfirmedRemoteFileDeletion(link: RemoteFileLink): LocalBookRemoval? {
        removeCachedRemoteFile(link.profileId, link.remoteFileId)
        if (remoteFileLink(link.profileId, link.localFileId) != link) return null
        val file = bookFile(link.localFileId) ?: return null
        if (deleteBookFileRow(file.id) == 0) return null
        return releaseUnreferencedFiles(listOf(file))
    }

    @Transaction
    open suspend fun replaceRemoteCatalog(
        profileId: String,
        books: List<RemoteCatalogBook>,
        files: List<RemoteCatalogFile>,
    ) {
        check(currentServerProfile()?.id == profileId)
        require(books.all { it.profileId == profileId } && files.all { it.profileId == profileId })
        clearRemoteCatalog(profileId)
        cacheCatalogBooks(profileId, books)
        cacheRemoteFiles(files)
    }

    @Transaction
    open suspend fun applyCloudCatalog(
        profileId: String,
        expectedCursor: Long?,
        rebuild: Boolean,
        batch: CloudCatalogBatch,
    ): List<LocalBookRemoval> {
        check(currentServerProfile()?.id == profileId && cloudSyncCursor(profileId) == expectedCursor)
        val after = if (rebuild) 0L else checkNotNull(expectedCursor)
        require(batch.cursor >= after && batch.versions.all { it.profileId == profileId && it.revision in (after + 1)..batch.cursor })
        require(batch.books.all { it.profileId == profileId } && batch.files.all { it.profileId == profileId })
        require(batch.positions.all { it.revision in (after + 1)..batch.cursor })
        require(batch.readerRecords.all { it.profileId == profileId && it.revision in (after + 1)..batch.cursor })
        val positionedFileIds = batch.positions.mapTo(mutableSetOf()) { it.fileId }
        val previousHashes = batch.files.filter { it.remoteFileId in positionedFileIds }.mapNotNull { file ->
            remoteCatalogFile(profileId, file.remoteFileId)?.sha256?.let { file.remoteFileId to it }
        }.toMap()

        if (rebuild) {
            clearRemoteCatalog(profileId)
            clearCloudCatalogVersions(profileId)
            clearCloudReaderRecords(profileId)
        }
        val removals = mutableListOf<LocalBookRemoval>()
        val deletedBookIds = batch.versions.filter { it.deleted && it.entityType == "book" }.map { it.entityId }.toSet()
        if (deletedBookIds.isNotEmpty()) {
            for (link in remoteBookLinks(profileId).filter { it.remoteBookId in deletedBookIds }) {
                applyConfirmedRemoteBookDeletion(link)?.let(removals::add)
            }
            if (!rebuild) for (id in deletedBookIds) removeCachedRemoteBook(profileId, id)
        }
        val deletedFileIds = batch.versions.filter { it.deleted && it.entityType == "book_file" }.map { it.entityId }.toSet()
        if (deletedFileIds.isNotEmpty()) {
            for (link in remoteFileLinks(profileId).filter { it.remoteFileId in deletedFileIds }) {
                applyConfirmedRemoteFileDeletion(link)?.let(removals::add)
            }
            if (!rebuild) for (id in deletedFileIds) removeCachedRemoteFile(profileId, id)
        }

        // A missing parent rolls back the batch and cursor so a later pull can include the parent.
        cacheCatalogBooks(profileId, batch.books)
        cacheRemoteFiles(batch.files.filterNot { it.remoteBookId in deletedBookIds })
        applyReadingChanges(profileId, rebuild, rebuild && expectedCursor != null, batch, previousHashes, deletedFileIds)
        for (record in batch.readerRecords) applyCloudReaderRecord(record)
        saveCloudCatalogVersions(batch.versions)
        saveCloudSyncState(CloudSyncState(profileId, batch.cursor))
        return removals
    }

    @Transaction
    open suspend fun acknowledgeReaderUpload(upload: PendingCloudUpload, record: CloudReaderRecord) {
        require(upload.profileId == record.profileId && upload.id == "reader:${record.kind}:${record.id}")
        applyCloudReaderRecord(record)
    }

    private suspend fun applyCloudReaderRecord(record: CloudReaderRecord) {
        val cached = cloudReaderRecord(record.profileId, record.kind, record.id)
        if (cached == null || record.revision > cached.revision) {
            cacheCloudReaderRecord(record)
            val remoteBookId = record.remoteBookId ?: cached?.remoteBookId
            if (record.payload == null && remoteBookId != null) {
                val localBookId = when (record.kind) {
                    "reading_session" -> readingSession(record.id)?.bookId
                    "bookmark" -> bookmark(record.id)?.bookId
                    "annotation" -> annotation(record.id)?.bookId
                    else -> null
                }
                if (localBookId != null && remoteBookLink(record.profileId, localBookId)?.remoteBookId == remoteBookId) {
                    when (record.kind) {
                        "reading_session" -> deleteReadingSession(record.id)
                        "bookmark" -> deleteBookmark(record.id)
                        "annotation" -> deleteAnnotation(record.id)
                    }
                }
            }
        }
        removeCloudUpload(record.profileId, "reader:${record.kind}:${record.id}")
    }

    private suspend fun cacheCatalogBooks(profileId: String, books: List<RemoteCatalogBook>) {
        cacheRemoteBooks(books)
        for (remote in books) {
            if (!remote.available || localBookLinks(profileId, remote.remoteBookId).isNotEmpty()) continue
            val existing = book(remote.remoteBookId)
            // Let this profile's own queued create confirm its association. A UUID from another
            // profile is not proof of ownership, even when both servers have a book with that ID.
            if (existing != null && hasQueuedCloudBook(profileId, existing.id)) continue
            val id = if (existing == null) remote.remoteBookId else UUID.randomUUID().toString()
            addBook(LocalBook(id, remote.title, remote.author, remote.cachedAt, description = remote.description))
            linkRemoteBook(RemoteBookLink(profileId, id, remote.remoteBookId))
        }
    }

    @Transaction
    open suspend fun acknowledgeCloudUpload(upload: PendingCloudUpload, remoteId: String) {
        if (removeCloudUpload(upload.profileId, upload.id) == 0) return
        if (upload.fileId == null) linkRemoteBook(RemoteBookLink(upload.profileId, upload.bookId, remoteId))
        else {
            val previous = remoteFileLink(upload.profileId, upload.fileId)
            if (previous != null && previous.remoteFileId != remoteId) requeueReadingPosition(upload.fileId)
            linkRemoteFile(RemoteFileLink(upload.profileId, upload.fileId, remoteId))
        }
    }

    @Transaction
    open suspend fun attachDownloadedFile(
        remote: RemoteCatalogFile,
        bookId: String,
        sha256: String,
        size: Long,
        verifiedAt: Long,
    ): LocalBookFile {
        check(remoteBookLink(remote.profileId, bookId)?.remoteBookId == remote.remoteBookId)
        require(size == remote.sizeBytes && (remote.sha256 == null || remote.sha256 == sha256))
        val link = localFileLink(remote.profileId, remote.remoteFileId)
        val existing = link?.let { bookFile(it.localFileId) }
        val file = if (existing != null && existing.bookId == bookId && existing.sha256 == sha256 && existing.format == remote.format) {
            setBookFileCopyRemoved(existing.id, false)
            existing.copy(localCopyRemoved = false)
        } else {
            val source = "acbooks://${remote.profileId}/files/${remote.remoteFileId}"
            val previous = findFileBySource(source, sha256, remote.format)
            if (previous != null) {
                check(previous.bookId == bookId)
                setBookFileCopyRemoved(previous.id, false)
                previous.copy(localCopyRemoved = false)
            } else {
                addObject(LocalFileObject(sha256, remote.format, size))
                LocalBookFile(UUID.randomUUID().toString(), bookId, sha256, remote.format, source, remote.remoteFileId)
                    .also { addFile(it) }
            }
        }
        if (link != null && link.localFileId != file.id) unlinkRemoteFile(remote.profileId, link.localFileId)
        linkRemoteFile(RemoteFileLink(remote.profileId, file.id, remote.remoteFileId))
        recordFileSource(sha256, remote.format, FileSourceKind.Downloaded, managedObjectPath(sha256, remote.format),
            FileSourceStatus.Present, verifiedAt)
        restoreCloudReadingPosition(remote.profileId, file.id)
        completeDownload(remote.profileId, bookId, remote.remoteFileId)
        return file
    }

    @Query("SELECT * FROM book_files WHERE sourceUri = :source AND sha256 = :sha256 AND format = :format")
    protected abstract suspend fun findFileBySource(source: String, sha256: String, format: String): LocalBookFile?

    @Query("""
        SELECT format FROM book_files WHERE bookId = :bookId
        UNION SELECT f.format FROM remote_catalog_files f
        JOIN remote_book_links l ON l.profileId = f.profileId AND l.remoteBookId = f.remoteBookId
        JOIN server_profiles p ON p.id = l.profileId AND p.currentSlot = 1
        WHERE l.localBookId = :bookId ORDER BY format
    """)
    abstract fun observeBookFormats(bookId: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM book_files WHERE sourceUri = :sourceUri AND sha256 = :sha256 AND format = :format)")
    abstract suspend fun hasImported(sourceUri: String, sha256: String, format: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM book_files WHERE sha256 = :sha256 AND format = :format)")
    abstract suspend fun hasContent(sha256: String, format: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addObject(file: LocalFileObject)

    @Insert
    abstract suspend fun addBook(book: LocalBook)

    @Insert
    abstract suspend fun addFile(file: LocalBookFile)

    @Transaction
    open suspend fun recordImportedSources(sha256: String, format: String, sourceUri: String, verifiedAt: Long?) {
        val status = if (verifiedAt == null) FileSourceStatus.Unknown else FileSourceStatus.Present
        val at = verifiedAt ?: 0L
        recordFileSource(sha256, format, FileSourceKind.Managed, managedObjectPath(sha256, format), status, at)
        recordFileSource(sha256, format, FileSourceKind.External, sourceUri, status, at)
    }

    @Transaction
    open suspend fun importBook(
        book: LocalBook,
        file: LocalBookFile,
        content: LocalFileObject,
        allowMatchingContent: Boolean,
        verifiedAt: Long? = null,
        bookUploadPayload: String? = null,
    ): BookImportCommit {
        if (hasImported(file.sourceUri, file.sha256, file.format)) {
            if (verifiedAt != null) {
                restoreImportedCopy(file.sourceUri, file.sha256, file.format)
                recordImportedSources(file.sha256, file.format, file.sourceUri, verifiedAt)
            }
            return BookImportCommit.Duplicate
        }
        if (!allowMatchingContent && hasContent(file.sha256, file.format)) return BookImportCommit.MatchingContent
        addObject(content)
        addBook(book)
        book.author?.takeUnless(String::isBlank)?.let { author ->
            val contributor = LocalContributor(UUID.randomUUID().toString(), author)
            insertContributor(contributor)
            insertBookContributor(LocalBookContributor(book.id, contributor.id, "author"))
        }
        addFile(file)
        recordImportedSources(file.sha256, file.format, file.sourceUri, verifiedAt)
        if (bookUploadPayload != null) currentServerProfile()?.let { profile ->
            enqueueCloudUploads(listOf(
                PendingCloudUpload.createBook(profile.id, book.id, bookUploadPayload),
                PendingCloudUpload.uploadFile(profile.id, file),
            ))
        }
        return BookImportCommit.Added
    }

    @Transaction
    open suspend fun updateFolderGrant(folderId: String, granted: Boolean, checkedAt: Long) {
        if (granted) {
            if (setFolderGrantRestored(folderId, checkedAt) != 0) resetFolderSourceAuthorization(folderId, checkedAt)
        } else {
            setFolderUnauthorized(folderId, checkedAt)
            markFolderSourcesUnauthorized(folderId, checkedAt)
        }
    }

    @Transaction
    open suspend fun unregisterFolder(folderId: String, checkedAt: Long): Int {
        markFolderSourcesUnauthorized(folderId, checkedAt)
        return deleteAuthorizedFolderRow(folderId)
    }

    @Transaction
    open suspend fun completeFolderScan(
        folderId: String,
        startedAt: Long,
        completedAt: Long,
        documents: List<ScannedDocument>,
    ): Boolean {
        require(completedAt >= startedAt)
        if (setFolderScanComplete(folderId, startedAt, completedAt) == 0) return false
        for (document in documents) {
            recordScannedDocument(
                folderId, document.documentId, document.documentUri,
                document.displayName, document.size, document.modifiedAt, startedAt,
            )
        }
        markUnseenDocumentsMissing(folderId, startedAt)
        markMissingFolderSources(folderId, completedAt)
        return true
    }

    @Transaction
    open suspend fun attachVerifiedFolderDocument(
        folderId: String,
        documentId: String,
        fileId: String,
        verifiedSha256: String,
        verifiedFormat: String,
        expectedSeenAt: Long,
        verifiedAt: Long,
    ): Boolean {
        val folder = authorizedFolder(folderId) ?: return false
        if (folder.scanState == FolderScanState.PermissionRequired) return false
        val entry = folderEntry(folderId, documentId)
            ?.takeIf { !it.missing && it.lastSeenAt == expectedSeenAt && verifiedAt >= it.lastSeenAt }
            ?: return false
        val file = bookFile(fileId) ?: return false
        if (file.sha256 != verifiedSha256 || file.format != verifiedFormat) return false
        recordFileSource(
            verifiedSha256, verifiedFormat, FileSourceKind.External, entry.documentUri,
            FileSourceStatus.Present, verifiedAt, folderId,
        )
        return setDocumentImportedFile(folderId, documentId, fileId) == 1
    }
}
