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
    val editedCoverKey: String? = null,
)

@Dao
abstract class LibraryDao :
    BookEditDao, ReadingPositionDao, ReadingSessionDao, ReadingMarkDao, FileSourceDao, FolderDao {
    @Upsert
    protected abstract suspend fun upsertServerProfile(profile: ServerProfile)

    /** Accepts identity confirmed by the server. Selecting a connection does not authenticate a session. */
    @Transaction
    open suspend fun saveCurrentServerProfile(
        serverInstanceId: String,
        accountId: String,
        deviceId: String,
        origin: String,
        verifiedAt: Long,
    ): ServerProfile {
        require(isServerOriginSafe(origin)) { "Invalid server origin" }
        val existing = currentServerProfile()
        check(existing == null || (
            existing.serverInstanceId == serverInstanceId && existing.accountId == accountId &&
                existing.origin == origin.trimEnd('/')
            )) { "Server identity changed" }
        val profile = ServerProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            serverInstanceId = serverInstanceId,
            accountId = accountId,
            deviceId = deviceId,
            origin = origin.trimEnd('/'),
            updatedAt = verifiedAt,
            currentSlot = 1,
        )
        upsertServerProfile(profile)
        return profile
    }

    @Query("""
        SELECT b.id,
        CASE WHEN e.bookId IS NOT NULL THEN b.title ELSE COALESCE(c.title, b.title) END AS title,
        CASE WHEN e.bookId IS NOT NULL THEN b.author WHEN c.metadataJson IS NOT NULL THEN c.author
        ELSE COALESCE(c.author, (
            SELECT group_concat(name, ', ') FROM (
                SELECT c.name FROM book_contributors bc
                JOIN contributors c ON c.id = bc.contributorId
                WHERE bc.bookId = b.id AND bc.role = 'author'
                ORDER BY c.name, c.id
            )
        ), b.author) END AS author, b.importedAt,
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
        (c.profileId || '-' || c.remoteBookId || '-' || c.coverUpdatedAt) AS cloudCoverKey,
        CASE WHEN c.remoteBookId IS NULL OR e.coverKey IS NOT NULL THEN b.editedCoverKey END AS editedCoverKey
        FROM books b
        LEFT JOIN server_profiles p ON p.currentSlot = 1
        LEFT JOIN remote_book_links l ON l.profileId = p.id AND l.localBookId = b.id
        LEFT JOIN remote_catalog c ON c.profileId = l.profileId AND c.remoteBookId = l.remoteBookId AND c.available = 1
        LEFT JOIN pending_book_edits e ON e.bookId = b.id AND e.profileId = p.id
        LEFT JOIN book_files f ON f.id = COALESCE(b.preferredFileId, (
            SELECT id FROM book_files WHERE bookId = b.id ORDER BY id LIMIT 1
        ))
        WHERE l.localBookId IS NULL OR c.remoteBookId IS NOT NULL OR EXISTS (
            SELECT 1 FROM book_files bf WHERE bf.bookId = b.id AND bf.localCopyRemoved = 0
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
        val editedCoverKey = book(bookId)?.editedCoverKey
        val files = allBookFiles(bookId)
        if (deleteBookRow(bookId) == 0) return null

        // Room foreign keys cascade to book_files, reading_positions and shelf_books.
        val unreferencedObjects = files.distinctBy { it.sha256 to it.format }
            .filter { deleteObjectIfUnreferenced(it.sha256, it.format) > 0 }
            .map { LocalFileObject(it.sha256, it.format, 0L) }
        val unreferencedCoverHashes = files.map { it.sha256 }.distinct()
            .filterNot { hasFileWithHash(it) }

        return LocalBookRemoval(unreferencedObjects, unreferencedCoverHashes, editedCoverKey)
    }

    @Transaction
    open suspend fun applyConfirmedRemoteBookDeletion(link: RemoteBookLink): LocalBookRemoval? {
        removeCachedRemoteBook(link.profileId, link.remoteBookId)
        // A different association may have been saved while the remote request was in flight.
        if (remoteBookLink(link.profileId, link.localBookId) != link) return null
        return removeLocalBook(link.localBookId)
    }

    @Query("""
        SELECT * FROM books b
        WHERE EXISTS(SELECT 1 FROM book_files f WHERE f.bookId = b.id AND f.localCopyRemoved = 0)
        ORDER BY b.importedAt, b.id
    """)
    abstract suspend fun booksWithOfflineCopies(): List<LocalBook>

    @Transaction
    open suspend fun replaceRemoteCatalog(
        profileId: String,
        books: List<RemoteCatalogBook>,
        files: List<RemoteCatalogFile>,
        shelfSnapshot: RemoteShelfSnapshot? = null,
        preserveShelfEditsFor: Set<String> = emptySet(),
    ) {
        check(currentServerProfile()?.id == profileId)
        require(books.all { it.profileId == profileId } && files.all { it.profileId == profileId })
        clearRemoteCatalog(profileId)
        cacheRemoteBooks(books)
        cacheRemoteFiles(files)
        for (remote in books) {
            if (!remote.available || localBookLinks(profileId, remote.remoteBookId).isNotEmpty()) continue
            // An existing local UUID may be awaiting its idempotent create receipt.
            if (book(remote.remoteBookId) != null) continue
            val id = remote.remoteBookId
            addBook(LocalBook(id, remote.title, remote.author, remote.cachedAt, description = remote.description))
            linkRemoteBook(RemoteBookLink(profileId, id, remote.remoteBookId))
        }
        if (shelfSnapshot != null) replaceRemoteShelves(profileId, shelfSnapshot, preserveShelfEditsFor)
    }

    private suspend fun replaceRemoteShelves(
        profileId: String,
        snapshot: RemoteShelfSnapshot,
        preserveShelfEditsFor: Set<String>,
    ) {
        val existing = allShelves()
        val localByName = existing.filter { it.serverNameKey == null }
            .groupBy { it.name.shelfNameKey() }.toMutableMap()
        val remoteIds = snapshot.shelves.mapTo(mutableSetOf()) { it.id }
        for (remote in snapshot.shelves) {
            val key = remote.name.shelfNameKey()
            // Keep server shelves distinct by ID even if their names compare equal.
            upsertShelf(LocalShelf(remote.id, remote.name, "\u0000${remote.id}", serverNameKey = key))
            for (local in localByName.remove(key).orEmpty()) {
                if (local.id == remote.id || local.id in remoteIds) continue
                copyShelfMemberships(local.id, remote.id)
                deleteShelf(local.id)
            }
        }

        val linksByRemoteBook = remoteBookLinks(profileId).groupBy { it.remoteBookId }
        for ((remoteBookId, shelfIds) in snapshot.shelfIdsByRemoteBook) {
            check(remoteIds.containsAll(shelfIds))
            for (link in linksByRemoteBook[remoteBookId].orEmpty()) {
                if (link.localBookId !in preserveShelfEditsFor) replaceBookShelves(link.localBookId, shelfIds)
            }
        }

        for (old in existing) {
            val key = old.serverNameKey ?: continue
            if (old.id in remoteIds) continue
            if (hasShelfMembers(old.id)) {
                // Retain assignments for local-only books and unsynced edits.
                val replacement = shelfByNameKey(key, excludingId = old.id)
                if (replacement == null) {
                    upsertShelf(old.copy(nameKey = key, serverNameKey = null))
                    continue
                }
                copyShelfMemberships(old.id, replacement.id)
            }
            deleteShelf(old.id)
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
        isbn: String? = null,
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
        isbn?.let { insertBookIdentifier(LocalBookIdentifier(UUID.randomUUID().toString(), book.id, "isbn", it)) }
        addFile(file)
        recordImportedSources(file.sha256, file.format, file.sourceUri, verifiedAt)
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
