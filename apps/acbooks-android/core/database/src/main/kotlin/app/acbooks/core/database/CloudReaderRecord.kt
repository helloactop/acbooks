package app.acbooks.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "cloud_reader_records",
    primaryKeys = ["profileId", "kind", "id"],
    foreignKeys = [ForeignKey(entity = ServerProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["profileId", "remoteBookId"])],
)
data class CloudReaderRecord(
    val profileId: String,
    val kind: String,
    val id: String,
    val revision: Long,
    val remoteBookId: String?,
    val payload: String?,
)
