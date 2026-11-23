package app.acbooks.core.database

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileSourceDao {
    @Query("SELECT * FROM file_sources WHERE sha256 = :sha256 AND format = :format ORDER BY kind, location")
    suspend fun fileSources(sha256: String, format: String): List<LocalFileSource>

    @Query("SELECT * FROM file_sources WHERE sha256 = :sha256 AND format = :format ORDER BY kind, location")
    fun observeFileSources(sha256: String, format: String): Flow<List<LocalFileSource>>

    @Query("""
        INSERT INTO file_sources (sha256, format, kind, location, status, updatedAt, folderId)
        VALUES (:sha256, :format, :kind, :location, :status, :updatedAt, :folderId)
        ON CONFLICT(sha256, format, kind, location) DO UPDATE SET
            status = CASE WHEN EXISTS (
                SELECT 1 FROM authorized_folders
                WHERE id = COALESCE(excluded.folderId, file_sources.folderId) AND scanState = 'permission_required'
            ) THEN 'permission_required' ELSE excluded.status END,
            updatedAt = excluded.updatedAt,
            folderId = COALESCE(excluded.folderId, file_sources.folderId)
        WHERE excluded.updatedAt >= file_sources.updatedAt
    """)
    suspend fun recordFileSource(
        sha256: String,
        format: String,
        kind: String,
        location: String,
        status: String,
        updatedAt: Long,
        folderId: String? = null,
    )

    @Query("""
        UPDATE file_sources SET status = :status, updatedAt = :updatedAt
        WHERE sha256 = :sha256 AND format = :format AND location = :location AND kind IN ('managed', 'downloaded')
            AND status <> :status AND updatedAt <= :updatedAt
    """)
    suspend fun setLocalCopyStatus(sha256: String, format: String, location: String, status: String, updatedAt: Long): Int

    @Query("""
        UPDATE file_sources SET status = :status, updatedAt = :updatedAt
        WHERE sha256 = :sha256 AND format = :format AND kind = 'external' AND location = :location
            AND updatedAt <= :updatedAt AND (folderId IS NULL OR NOT EXISTS (
                SELECT 1 FROM authorized_folders WHERE id = file_sources.folderId AND scanState = 'permission_required'
            ))
    """)
    suspend fun updateExternalSourceStatus(sha256: String, format: String, location: String, status: String, updatedAt: Long): Int

    @Query("""
        UPDATE file_sources SET status = 'permission_required', updatedAt = MAX(updatedAt, :updatedAt)
        WHERE folderId = :folderId AND kind = 'external' AND status <> 'permission_required'
    """)
    suspend fun markFolderSourcesUnauthorized(folderId: String, updatedAt: Long)

    @Query("""
        UPDATE file_sources SET status = 'unknown', updatedAt = MAX(updatedAt, :updatedAt)
        WHERE folderId = :folderId AND kind = 'external' AND status = 'permission_required'
    """)
    suspend fun resetFolderSourceAuthorization(folderId: String, updatedAt: Long)

    @Query("""
        UPDATE file_sources SET status = 'missing', updatedAt = :updatedAt
        WHERE folderId = :folderId AND kind = 'external' AND status NOT IN ('missing', 'permission_required')
            AND updatedAt <= :updatedAt AND EXISTS (
                SELECT 1 FROM folder_entries e
                WHERE e.folderId = :folderId AND e.documentUri = file_sources.location AND e.missing = 1
            )
    """)
    suspend fun markMissingFolderSources(folderId: String, updatedAt: Long)
}
