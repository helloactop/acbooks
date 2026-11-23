package app.acbooks.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

data class ReadingPositionChange(
    val fileId: String,
    val revision: Long,
    val locator: String?,
    val percent: Double? = null,
    val updatedAt: Long = 0,
)

@Entity(
    tableName = "cloud_reading_positions",
    primaryKeys = ["profileId", "fileId"],
    foreignKeys = [ForeignKey(entity = ServerProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class CloudReadingPosition(
    val profileId: String,
    val fileId: String,
    val sha256: String?,
    val revision: Long,
    val locator: String?,
    val percent: Double?,
    val updatedAt: Long,
)

@Entity(
    tableName = "reading_position_outbox",
    primaryKeys = ["profileId", "localFileId"],
    foreignKeys = [
        ForeignKey(entity = ServerProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocalBookFile::class, parentColumns = ["id"], childColumns = ["localFileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("localFileId")],
)
data class PendingReadingPosition(
    val profileId: String,
    val localFileId: String,
    val remoteFileId: String,
    val sha256: String,
    val mutationId: String,
    val baseRevision: Long,
    val localRevision: Long,
    val locator: String,
    val percent: Double?,
)
