package app.acbooks.core.database

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

interface CloudSyncDao {
    @Query("SELECT cursor FROM sync_state WHERE profileId = :profileId")
    suspend fun cloudSyncCursor(profileId: String): Long?

    @Upsert
    suspend fun saveCloudSyncState(state: CloudSyncState)

    @Query("DELETE FROM sync_baselines WHERE profileId = :profileId")
    suspend fun clearCloudCatalogVersions(profileId: String)

    @Upsert
    suspend fun saveCloudCatalogVersions(versions: List<CloudCatalogVersion>)

    @Query("SELECT * FROM sync_baselines WHERE profileId = :profileId AND entityType = :type AND entityId = :entityId")
    suspend fun cloudCatalogVersion(profileId: String, type: String, entityId: String): CloudCatalogVersion?

    @Query("""
        SELECT b.* FROM books b
        WHERE EXISTS(SELECT 1 FROM book_files f WHERE f.bookId = b.id AND f.localCopyRemoved = 0)
          AND NOT EXISTS(SELECT 1 FROM remote_book_links l WHERE l.profileId = :profileId AND l.localBookId = b.id)
          AND NOT EXISTS(SELECT 1 FROM sync_outbox o WHERE o.profileId = :profileId AND o.id = 'book:' || b.id)
    """)
    suspend fun unqueuedCloudBooks(profileId: String): List<LocalBook>

    @Query("""
        SELECT f.* FROM book_files f
        LEFT JOIN remote_book_links b ON b.profileId = :profileId AND b.localBookId = f.bookId
        LEFT JOIN remote_file_links l ON l.profileId = :profileId AND l.localFileId = f.id
        LEFT JOIN remote_catalog_files c ON c.profileId = :profileId AND c.remoteFileId = l.remoteFileId
        WHERE f.localCopyRemoved = 0 AND (
            c.remoteFileId IS NULL OR c.remoteBookId IS NOT b.remoteBookId
            OR c.sha256 IS NOT f.sha256 OR c.format <> f.format OR c.fileStatus <> 'present'
        ) AND NOT EXISTS(SELECT 1 FROM sync_outbox o WHERE o.profileId = :profileId AND o.id = 'file:' || f.id)
    """)
    suspend fun unqueuedCloudFiles(profileId: String): List<LocalBookFile>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueCloudUploads(uploads: List<PendingCloudUpload>)

    @Query("SELECT * FROM sync_outbox WHERE profileId = :profileId ORDER BY fileId IS NOT NULL, bookId, id")
    suspend fun pendingCloudUploads(profileId: String): List<PendingCloudUpload>

    @Query("UPDATE sync_outbox SET lastError = :error, blocked = :blocked WHERE profileId = :profileId AND id = :id")
    suspend fun recordCloudUploadFailure(profileId: String, id: String, error: String, blocked: Boolean = false)

    @Query("DELETE FROM sync_outbox WHERE profileId = :profileId AND id = :id")
    suspend fun removeCloudUpload(profileId: String, id: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE profileId = :profileId)")
    suspend fun hasPendingCloudUploads(profileId: String): Boolean
}
