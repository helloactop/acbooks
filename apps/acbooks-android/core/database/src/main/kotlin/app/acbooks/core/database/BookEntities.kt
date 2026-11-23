package app.acbooks.core.database

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = LocalBookFile::class,
            parentColumns = ["id"],
            childColumns = ["preferredFileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("preferredFileId"),
        Index("createdAt", "id"),
        Index("updatedAt", "id"),
        Index("readingStatus", "createdAt", "id"),
    ],
)
data class LocalBook(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val importedAt: Long,
    val description: String? = null,
    val series: String? = null,
    @ColumnInfo(defaultValue = "'unread'") val readingStatus: String = "unread",
    @ColumnInfo(defaultValue = "0") val metadataLocks: Long = 0,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = importedAt,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = importedAt,
    val preferredFileId: String? = null,
)

@Entity(tableName = "file_objects", primaryKeys = ["sha256", "format"])
data class LocalFileObject(val sha256: String, val format: String, val size: Long)

@Entity(
    tableName = "book_files",
    foreignKeys = [
        ForeignKey(entity = LocalBook::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocalFileObject::class, parentColumns = ["sha256", "format"], childColumns = ["sha256", "format"]),
    ],
    indices = [Index("bookId"), Index("sha256", "format"), Index("sourceUri", "sha256", "format", unique = true)],
)
data class LocalBookFile(
    @PrimaryKey val id: String,
    val bookId: String,
    val sha256: String,
    val format: String,
    val sourceUri: String,
    val displayName: String,
    @ColumnInfo(defaultValue = "0") val localCopyRemoved: Boolean = false,
)

data class LocalBookCard(
    val id: String,
    val title: String,
    val author: String?,
    val importedAt: Long,
    val format: String,
    val sha256: String?,
    val hasLocalCopy: Boolean = true,
    val cloudBacked: Boolean = false,
    val canDownload: Boolean = false,
    val cloudCoverKey: String? = null,
)
