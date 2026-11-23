package app.acbooks.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_state",
    foreignKeys = [ForeignKey(entity = ServerProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class CloudSyncState(@PrimaryKey val profileId: String, val cursor: Long)

@Entity(
    tableName = "sync_baselines",
    primaryKeys = ["profileId", "entityType", "entityId"],
    foreignKeys = [ForeignKey(entity = ServerProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class CloudCatalogVersion(
    val profileId: String,
    val entityType: String,
    val entityId: String,
    val revision: Long,
    val deleted: Boolean,
)

data class CloudCatalogBatch(
    val cursor: Long,
    val books: List<RemoteCatalogBook>,
    val files: List<RemoteCatalogFile>,
    val versions: List<CloudCatalogVersion>,
)

@Entity(
    tableName = "sync_outbox",
    primaryKeys = ["profileId", "id"],
    foreignKeys = [
        ForeignKey(entity = ServerProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocalBook::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocalBookFile::class, parentColumns = ["id"], childColumns = ["fileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId"), Index("fileId")],
)
data class PendingCloudUpload(
    val profileId: String,
    val id: String,
    val bookId: String,
    val fileId: String?,
    val payload: String?,
    val lastError: String? = null,
    val blocked: Boolean = false,
) {
    companion object {
        fun createBook(profileId: String, bookId: String, payload: String) =
            PendingCloudUpload(profileId, "book:$bookId", bookId, null, payload)

        fun uploadFile(profileId: String, file: LocalBookFile) =
            PendingCloudUpload(profileId, "file:${file.id}", file.bookId, file.id, null)
    }
}
