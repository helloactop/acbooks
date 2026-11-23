package app.acbooks.core.database

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_positions",
    foreignKeys = [ForeignKey(entity = LocalBookFile::class, parentColumns = ["id"], childColumns = ["fileId"],
        onDelete = ForeignKey.CASCADE)],
)
data class LocalReadingPosition(
    @PrimaryKey val fileId: String,
    val locator: String,
    val percent: Double? = null,
    @ColumnInfo(defaultValue = "0") val revision: Long = 0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "1") val needsSync: Boolean = true,
    val cloudRevision: Long? = null,
)

data class LocalBookReadingPosition(val format: String, val locator: String?, val percent: Double? = null)
