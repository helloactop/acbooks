package app.acbooks.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object FileSourceKind {
    const val Managed = "managed"
    const val External = "external"
    const val Downloaded = "downloaded"
}

object FileSourceStatus {
    const val Unknown = "unknown"
    const val Present = "present"
    const val Missing = "missing"
    const val PermissionRequired = "permission_required"
}

object FolderScanState {
    const val Idle = "idle"
    const val Scanning = "scanning"
    const val Complete = "complete"
    const val Failed = "failed"
    const val PermissionRequired = "permission_required"
}

fun managedObjectPath(sha256: String, format: String): String = "library/objects/$sha256.$format"

@Entity(
    tableName = "authorized_folders",
    indices = [Index(value = ["authority", "rootDocumentId"], unique = true), Index(value = ["treeUri"], unique = true)],
)
data class LocalAuthorizedFolder(
    @PrimaryKey val id: String,
    val treeUri: String,
    val authority: String,
    val rootDocumentId: String,
    val displayName: String?,
    val scanState: String,
    val lastScanStartedAt: Long?,
    val lastScanCompletedAt: Long?,
    val updatedAt: Long,
)

@Entity(
    tableName = "file_sources",
    primaryKeys = ["sha256", "format", "kind", "location"],
    foreignKeys = [
        ForeignKey(
            entity = LocalFileObject::class,
            parentColumns = ["sha256", "format"],
            childColumns = ["sha256", "format"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocalAuthorizedFolder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("folderId"), Index("kind", "location")],
)
data class LocalFileSource(
    val sha256: String,
    val format: String,
    val kind: String,
    val location: String,
    val status: String,
    val updatedAt: Long,
    val folderId: String? = null,
)

@Entity(
    tableName = "folder_entries",
    primaryKeys = ["folderId", "documentId"],
    foreignKeys = [
        ForeignKey(
            entity = LocalAuthorizedFolder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocalBookFile::class,
            parentColumns = ["id"],
            childColumns = ["importedFileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("documentId"), Index("importedFileId")],
)
data class LocalFolderEntry(
    val folderId: String,
    val documentId: String,
    val documentUri: String,
    val displayName: String,
    val size: Long?,
    val modifiedAt: Long?,
    val lastSeenAt: Long,
    val missing: Boolean = false,
    val ignored: Boolean = false,
    val importedFileId: String? = null,
)
