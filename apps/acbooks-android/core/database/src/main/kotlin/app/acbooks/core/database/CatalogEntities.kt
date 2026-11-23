package app.acbooks.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

data class LocalBookContributorName(val contributorId: String, val name: String, val role: String)

@Entity(
    tableName = "contributors",
    indices = [
        Index(value = ["name"]),
    ],
)
data class LocalContributor(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "book_contributors",
    primaryKeys = ["bookId", "contributorId", "role"],
    foreignKeys = [
        ForeignKey(
            entity = LocalBook::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocalContributor::class,
            parentColumns = ["id"],
            childColumns = ["contributorId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["contributorId"]),
        Index(value = ["bookId", "role", "contributorId"]),
    ],
)
data class LocalBookContributor(
    val bookId: String,
    val contributorId: String,
    val role: String,
)

@Entity(
    tableName = "book_identifiers",
    foreignKeys = [
        ForeignKey(
            entity = LocalBook::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["bookId", "scheme", "value"], unique = true),
        Index(value = ["scheme", "value"]),
    ],
)
data class LocalBookIdentifier(
    @PrimaryKey val id: String,
    val bookId: String,
    val scheme: String,
    val value: String,
)

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["name"], unique = true),
    ],
)
data class LocalTag(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "book_tags",
    primaryKeys = ["bookId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = LocalBook::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocalTag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tagId"]),
    ],
)
data class LocalBookTag(
    val bookId: String,
    val tagId: String,
)

@Entity(
    tableName = "locations",
    foreignKeys = [
        ForeignKey(
            entity = LocalLocation::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["parentId"]),
    ],
)
data class LocalLocation(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
)

@Entity(
    tableName = "book_copies",
    foreignKeys = [
        ForeignKey(
            entity = LocalBook::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocalLocation::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["locationId"]),
    ],
)
data class LocalBookCopy(
    @PrimaryKey val id: String,
    val bookId: String,
    val locationId: String?,
    val acquiredAt: Long?,
    val note: String?,
)
