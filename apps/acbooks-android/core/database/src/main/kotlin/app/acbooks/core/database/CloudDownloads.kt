package app.acbooks.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert

@Entity(
    tableName = "cloud_downloads",
    primaryKeys = ["profileId", "bookId"],
    foreignKeys = [
        ForeignKey(entity = ServerProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocalBook::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class PendingCloudDownload(val profileId: String, val bookId: String, val remoteFileId: String)

interface CloudDownloadDao {
    @Upsert
    suspend fun enqueueDownload(download: PendingCloudDownload)

    @Query("SELECT * FROM cloud_downloads WHERE profileId = :profileId ORDER BY bookId")
    suspend fun pendingDownloads(profileId: String): List<PendingCloudDownload>

    @Query("DELETE FROM cloud_downloads WHERE profileId = :profileId AND bookId = :bookId AND remoteFileId = :remoteFileId")
    suspend fun completeDownload(profileId: String, bookId: String, remoteFileId: String): Int
}
