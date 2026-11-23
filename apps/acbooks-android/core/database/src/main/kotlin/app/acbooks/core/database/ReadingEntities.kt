package app.acbooks.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = LocalBook::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocalBookFile::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["bookId", "startedAt", "id"]),
        Index(value = ["fileId"]),
        Index(value = ["startedAt"]),
    ],
)
data class LocalReadingSession(
    @PrimaryKey val id: String,
    val bookId: String,
    val fileId: String?,
    val deviceId: String?,
    val startedAt: Long,
    val endedAt: Long?,
    @ColumnInfo(defaultValue = "0") val activeSeconds: Long = 0,
    val source: String,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = LocalBook::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocalBookFile::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["bookId", "createdAt", "id"]),
        Index(value = ["fileId"]),
    ],
)
data class LocalBookmark(
    @PrimaryKey val id: String,
    val bookId: String,
    val fileId: String?,
    val locator: String?,
    val label: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = LocalBook::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocalBookFile::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["bookId", "createdAt", "id"]),
        Index(value = ["fileId"]),
    ],
)
data class LocalAnnotation(
    @PrimaryKey val id: String,
    val bookId: String,
    val fileId: String?,
    val locator: String?,
    val quote: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
