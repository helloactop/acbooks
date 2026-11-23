package app.acbooks.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object ProfileVerification {
    const val Verified = "verified"
    const val Unverified = "unverified"
}

object RemoteFileStatus {
    const val Present = "present"
    const val Missing = "missing"
}

@Entity(
    tableName = "server_profiles",
    indices = [
        Index(value = ["serverInstanceId", "accountId"], unique = true),
        Index(value = ["currentSlot"], unique = true),
    ],
)
data class ServerProfile(
    @PrimaryKey val id: String,
    val serverInstanceId: String?,
    val accountId: String?,
    val deviceId: String?,
    val origin: String?,
    val updatedAt: Long,
    val verification: String = ProfileVerification.Verified,
    val legacyOriginFingerprint: String? = null,
    val currentSlot: Int? = null,
)

@Entity(
    tableName = "remote_catalog",
    primaryKeys = ["profileId", "remoteBookId"],
    foreignKeys = [
        ForeignKey(entity = ServerProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("profileId", "updatedAt", "remoteBookId")],
)
data class RemoteCatalogBook(
    val profileId: String,
    val remoteBookId: String,
    val title: String,
    val author: String?,
    val description: String?,
    val available: Boolean,
    val updatedAt: Long,
    val cachedAt: Long,
    val coverUpdatedAt: Long? = null,
)

fun RemoteCatalogBook.coverCacheKey(): String? =
    coverUpdatedAt?.let { "$profileId-$remoteBookId-$it" }

@Entity(
    tableName = "remote_catalog_files",
    primaryKeys = ["profileId", "remoteFileId"],
    foreignKeys = [
        ForeignKey(
            entity = RemoteCatalogBook::class,
            parentColumns = ["profileId", "remoteBookId"],
            childColumns = ["profileId", "remoteBookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId", "remoteBookId")],
)
data class RemoteCatalogFile(
    val profileId: String,
    val remoteFileId: String,
    val remoteBookId: String,
    val format: String,
    val sizeBytes: Long,
    val sha256: String?,
    val fileStatus: String,
    val cachedAt: Long,
)

data class RemoteCatalogBookEntry(
    @Embedded val book: RemoteCatalogBook,
    val localBookId: String?,
)

data class RemoteCatalogFileEntry(
    @Embedded val file: RemoteCatalogFile,
    val localFileId: String?,
    val hasLocalCopy: Boolean,
)

@Entity(
    tableName = "remote_book_links",
    primaryKeys = ["profileId", "localBookId"],
    foreignKeys = [
        ForeignKey(entity = ServerProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocalBook::class, parentColumns = ["id"], childColumns = ["localBookId"], onDelete = ForeignKey.CASCADE),
    ],
    // Multiple legacy rows can refer to the same remote book within one origin.
    indices = [Index("localBookId"), Index("profileId", "remoteBookId")],
)
data class RemoteBookLink(val profileId: String, val localBookId: String, val remoteBookId: String)

@Entity(
    tableName = "remote_file_links",
    primaryKeys = ["profileId", "localFileId"],
    foreignKeys = [
        ForeignKey(entity = ServerProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocalBookFile::class, parentColumns = ["id"], childColumns = ["localFileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("localFileId"), Index(value = ["profileId", "remoteFileId"], unique = true)],
)
data class RemoteFileLink(val profileId: String, val localFileId: String, val remoteFileId: String)
