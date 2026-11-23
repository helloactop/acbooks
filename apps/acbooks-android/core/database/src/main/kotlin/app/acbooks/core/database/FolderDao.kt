package app.acbooks.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ScannedDocument(
    val documentId: String,
    val documentUri: String,
    val displayName: String,
    val size: Long?,
    val modifiedAt: Long?,
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM authorized_folders WHERE id = :id")
    suspend fun authorizedFolder(id: String): LocalAuthorizedFolder?

    @Query("SELECT * FROM authorized_folders WHERE authority = :authority AND rootDocumentId = :rootDocumentId")
    suspend fun authorizedFolderByDocument(authority: String, rootDocumentId: String): LocalAuthorizedFolder?

    @Query("SELECT * FROM authorized_folders ORDER BY displayName, id")
    fun observeAuthorizedFolders(): Flow<List<LocalAuthorizedFolder>>

    @Upsert
    suspend fun saveAuthorizedFolder(folder: LocalAuthorizedFolder)

    @Query("DELETE FROM authorized_folders WHERE id = :id")
    suspend fun deleteAuthorizedFolderRow(id: String): Int

    @Query("""
        UPDATE authorized_folders SET scanState = 'scanning', lastScanStartedAt = :startedAt, updatedAt = :startedAt
        WHERE id = :folderId AND scanState <> 'permission_required'
            AND (lastScanStartedAt IS NULL OR lastScanStartedAt < :startedAt)
    """)
    suspend fun startFolderScan(folderId: String, startedAt: Long): Int

    @Query("""
        UPDATE authorized_folders SET scanState = 'complete', lastScanCompletedAt = :completedAt, updatedAt = :completedAt
        WHERE id = :folderId AND scanState = 'scanning' AND lastScanStartedAt = :startedAt
    """)
    suspend fun setFolderScanComplete(folderId: String, startedAt: Long, completedAt: Long): Int

    @Query("""
        UPDATE authorized_folders SET scanState = 'failed', updatedAt = :updatedAt
        WHERE id = :folderId AND scanState = 'scanning' AND lastScanStartedAt = :startedAt
    """)
    suspend fun setFolderScanFailed(folderId: String, startedAt: Long, updatedAt: Long): Int

    @Query("""
        UPDATE authorized_folders SET scanState = 'permission_required', updatedAt = MAX(updatedAt, :updatedAt)
        WHERE id = :folderId AND scanState <> 'permission_required'
    """)
    suspend fun setFolderUnauthorized(folderId: String, updatedAt: Long): Int

    @Query("""
        UPDATE authorized_folders SET scanState = 'idle', updatedAt = MAX(updatedAt, :updatedAt)
        WHERE id = :folderId AND scanState = 'permission_required'
    """)
    suspend fun setFolderGrantRestored(folderId: String, updatedAt: Long): Int

    @Query("SELECT * FROM folder_entries WHERE folderId = :folderId ORDER BY displayName, documentId")
    fun observeFolderEntries(folderId: String): Flow<List<LocalFolderEntry>>

    @Query("SELECT * FROM folder_entries WHERE folderId = :folderId AND documentId = :documentId")
    suspend fun folderEntry(folderId: String, documentId: String): LocalFolderEntry?

    @Query("""
        SELECT e.* FROM folder_entries e JOIN authorized_folders f ON f.id = e.folderId
        WHERE f.authority = :authority AND e.documentId = :documentId ORDER BY e.folderId
    """)
    suspend fun entriesForDocument(authority: String, documentId: String): List<LocalFolderEntry>

    @Query("""
        INSERT INTO folder_entries
            (folderId, documentId, documentUri, displayName, size, modifiedAt, lastSeenAt, missing, ignored, importedFileId)
        VALUES (:folderId, :documentId, :documentUri, :displayName, :size, :modifiedAt, :seenAt, 0, 0, NULL)
        ON CONFLICT(folderId, documentId) DO UPDATE SET
            documentUri = excluded.documentUri, displayName = excluded.displayName,
            size = excluded.size, modifiedAt = excluded.modifiedAt, lastSeenAt = excluded.lastSeenAt, missing = 0
        WHERE excluded.lastSeenAt >= folder_entries.lastSeenAt
    """)
    suspend fun recordScannedDocument(
        folderId: String,
        documentId: String,
        documentUri: String,
        displayName: String,
        size: Long?,
        modifiedAt: Long?,
        seenAt: Long,
    )

    @Query("UPDATE folder_entries SET missing = 1 WHERE folderId = :folderId AND lastSeenAt < :seenAt")
    suspend fun markUnseenDocumentsMissing(folderId: String, seenAt: Long)

    @Query("UPDATE folder_entries SET ignored = :ignored WHERE folderId = :folderId AND documentId = :documentId")
    suspend fun setDocumentIgnored(folderId: String, documentId: String, ignored: Boolean): Int

    @Query("UPDATE folder_entries SET importedFileId = :fileId WHERE folderId = :folderId AND documentId = :documentId")
    suspend fun setDocumentImportedFile(folderId: String, documentId: String, fileId: String?): Int
}
