package app.acbooks.core.database

import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.util.UUID

abstract class ReadingSyncDao : ReadingPositionDao, RemoteLibraryDao {
    @Query("""
        SELECT r.* FROM remote_catalog_files r
        JOIN remote_file_links l ON l.profileId = r.profileId AND l.remoteFileId = r.remoteFileId
        JOIN book_files f ON f.id = l.localFileId AND f.sha256 = r.sha256 AND f.format = r.format
        JOIN remote_book_links b ON b.profileId = r.profileId AND b.remoteBookId = r.remoteBookId AND b.localBookId = f.bookId
        WHERE r.profileId = :profileId AND f.id = :localFileId AND f.format IN ('epub', 'pdf')
    """)
    abstract suspend fun cloudReadingFile(profileId: String, localFileId: String): RemoteCatalogFile?

    @Query("""
        SELECT p.fileId FROM reading_positions p
        JOIN remote_file_links l ON l.localFileId = p.fileId AND l.profileId = :profileId
        WHERE p.needsSync = 1 ORDER BY p.updatedAt, p.fileId
    """)
    abstract suspend fun dirtyReadingFiles(profileId: String): List<String>

    @Query("SELECT * FROM cloud_reading_positions WHERE profileId = :profileId AND fileId = :remoteFileId")
    abstract suspend fun cloudReadingPosition(profileId: String, remoteFileId: String): CloudReadingPosition?

    @Upsert
    protected abstract suspend fun cacheReadingPosition(position: CloudReadingPosition)

    @Query("DELETE FROM cloud_reading_positions WHERE profileId = :profileId AND fileId = :remoteFileId")
    protected abstract suspend fun removeCloudReadingPosition(profileId: String, remoteFileId: String)

    @Query("DELETE FROM cloud_reading_positions WHERE profileId = :profileId")
    protected abstract suspend fun clearCloudReadingPositions(profileId: String)

    @Query("""
        UPDATE reading_positions SET cloudRevision = NULL, needsSync = needsSync OR :resync
        WHERE fileId IN (SELECT localFileId FROM remote_file_links WHERE profileId = :profileId)
    """)
    protected abstract suspend fun clearReadingPositionRevisions(profileId: String, resync: Boolean = false)

    @Query("UPDATE reading_positions SET cloudRevision = NULL, needsSync = 1 WHERE fileId = :fileId")
    protected abstract suspend fun requeueReadingPosition(fileId: String)

    @Query("DELETE FROM reading_position_outbox WHERE profileId = :profileId")
    protected abstract suspend fun clearPendingReadingPositions(profileId: String)

    @Query("SELECT * FROM reading_position_outbox WHERE profileId = :profileId AND localFileId = :localFileId")
    protected abstract suspend fun pendingReadingPosition(profileId: String, localFileId: String): PendingReadingPosition?

    @Upsert
    protected abstract suspend fun enqueueReadingPosition(pending: PendingReadingPosition)

    @Query("DELETE FROM reading_position_outbox WHERE profileId = :profileId AND localFileId = :localFileId AND mutationId = :mutationId")
    protected abstract suspend fun removePendingReadingPosition(profileId: String, localFileId: String, mutationId: String): Int

    @Transaction
    open suspend fun recordReadingPosition(fileId: String, locator: String, percent: Double?, updatedAt: Long): Boolean {
        val local = readingPositionRecord(fileId)
        if (local != null && local.locator == locator && local.percent == percent) return false
        val remote = currentServerProfile()?.let { cloudReadingFile(it.id, fileId) }
        val base = remote?.let { cloudReadingPosition(it.profileId, it.remoteFileId)?.revision ?: 0L }
        writeReadingPosition(fileId, locator, percent, updatedAt, base)
        return true
    }

    @Transaction
    open suspend fun prepareReadingPosition(profileId: String, localFileId: String): PendingReadingPosition? {
        val file = cloudReadingFile(profileId, localFileId) ?: return null
        val hash = checkNotNull(file.sha256)
        val local = readingPositionRecord(localFileId)?.takeIf { it.needsSync } ?: return null
        val existing = pendingReadingPosition(profileId, localFileId)
        if (existing != null && existing.remoteFileId == file.remoteFileId && existing.sha256 == hash) return existing
        val cloud = cloudReadingPosition(profileId, file.remoteFileId)
        return PendingReadingPosition(profileId, localFileId, file.remoteFileId, hash, UUID.randomUUID().toString(),
            local.cloudRevision ?: cloud?.revision ?: 0, local.revision, local.locator, local.percent)
            .also { enqueueReadingPosition(it) }
    }

    @Transaction
    open suspend fun finishReadingPosition(pending: PendingReadingPosition, current: ReadingPositionChange?) {
        if (removePendingReadingPosition(pending.profileId, pending.localFileId, pending.mutationId) == 0) return
        val file = cloudReadingFile(pending.profileId, pending.localFileId) ?: return
        if (file.remoteFileId != pending.remoteFileId || file.sha256 != pending.sha256) return
        if (current != null) require(current.fileId == file.remoteFileId)

        val cached = cloudReadingPosition(file.profileId, file.remoteFileId)
        val latest = when {
            current != null && (cached == null || current.revision >= cached.revision) ->
                current.toCloud(file.profileId, file.sha256).also { cacheReadingPosition(it) }
            current == null && cached != null && cached.revision <= pending.baseRevision -> {
                removeCloudReadingPosition(file.profileId, file.remoteFileId)
                null
            }
            else -> cached
        }
        val local = readingPositionRecord(pending.localFileId) ?: return
        if (latest == null || latest.sha256 != file.sha256 || local.revision != pending.localRevision) {
            // Keep a newer local write made while the request was in flight.
            saveReadingPosition(local.copy(cloudRevision = latest?.revision ?: 0))
        } else {
            projectReadingPosition(pending.localFileId, latest, local)
        }
    }

    @Transaction
    open suspend fun discardReadingPositionRequest(pending: PendingReadingPosition) {
        if (removePendingReadingPosition(pending.profileId, pending.localFileId, pending.mutationId) != 0) {
            restoreCloudReadingPosition(pending.profileId, pending.localFileId)
        }
    }

    protected suspend fun restoreCloudReadingPosition(profileId: String, localFileId: String) {
        val file = cloudReadingFile(profileId, localFileId) ?: return
        val remote = cloudReadingPosition(profileId, file.remoteFileId)?.takeIf { it.sha256 == file.sha256 } ?: return
        val pending = pendingReadingPosition(profileId, localFileId)
        if (pending != null && pending.remoteFileId == file.remoteFileId && pending.sha256 == file.sha256) return
        val local = readingPositionRecord(localFileId)
        if (local?.cloudRevision == remote.revision) return
        projectReadingPosition(localFileId, remote, local)
    }

    protected suspend fun applyReadingChanges(
        profileId: String,
        rebuild: Boolean,
        resetVersions: Boolean,
        batch: CloudCatalogBatch,
        previousHashes: Map<String, String>,
        deletedFileIds: Set<String>,
    ) {
        val positionsBeforeRebuild = if (rebuild) batch.positions.mapNotNull { change ->
            cloudReadingPosition(profileId, change.fileId)?.let { change.fileId to it }
        }.toMap() else emptyMap()
        if (rebuild) clearCloudReadingPositions(profileId)
        if (resetVersions) {
            clearPendingReadingPositions(profileId)
            clearReadingPositionRevisions(profileId)
        }
        for (id in deletedFileIds) removeCloudReadingPosition(profileId, id)
        val fileVersions = batch.versions.filter { it.entityType == "book_file" }.associateBy { it.entityId }
        for (change in batch.positions) {
            if (change.fileId in deletedFileIds) continue
            val cached = cloudReadingPosition(profileId, change.fileId)
            if (cached != null && cached.revision >= change.revision) continue
            val file = remoteCatalogFile(profileId, change.fileId)
            val previousHash = previousHashes[change.fileId]
            val fileRevision = fileVersions[change.fileId]?.revision
            val knownPosition = positionsBeforeRebuild[change.fileId]
            val hash = when {
                knownPosition?.sha256 != null && knownPosition.revision == change.revision &&
                    knownPosition.locator == change.locator -> knownPosition.sha256
                previousHash != null && previousHash != file?.sha256 &&
                    fileRevision != null && change.revision < fileRevision -> previousHash
                else -> file?.sha256
            }
            cacheReadingPosition(change.toCloud(profileId, hash))
        }
        for (file in batch.files) {
            val cached = cloudReadingPosition(profileId, file.remoteFileId)
            if (cached != null && cached.sha256 == null && file.sha256 != null) {
                cacheReadingPosition(cached.copy(sha256 = file.sha256))
            }
        }
        val changedFiles = (batch.files.map { it.remoteFileId } + batch.positions.map { it.fileId }).toSet()
        for (remoteId in changedFiles) {
            localFileLink(profileId, remoteId)?.let { restoreCloudReadingPosition(profileId, it.localFileId) }
        }
    }

    private suspend fun projectReadingPosition(fileId: String, remote: CloudReadingPosition, local: LocalReadingPosition?) {
        if (remote.locator == null) {
            deleteReadingPosition(fileId)
        } else {
            saveReadingPosition(LocalReadingPosition(fileId, remote.locator, remote.percent,
                (local?.revision ?: 0) + 1, remote.updatedAt, needsSync = false, cloudRevision = remote.revision))
        }
    }

    private fun ReadingPositionChange.toCloud(profileId: String, sha256: String?) =
        CloudReadingPosition(profileId, fileId, sha256, revision, locator, percent, updatedAt)
}
