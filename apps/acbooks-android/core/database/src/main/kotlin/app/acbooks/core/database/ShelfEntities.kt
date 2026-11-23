package app.acbooks.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "shelves", indices = [Index("nameKey", unique = true)])
data class LocalShelf(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String,
    val serverNameKey: String? = null,
    val serverProfileId: String? = null,
    val serverShelfId: String? = null,
)

data class LibraryShelf(
    @Embedded val shelf: LocalShelf,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = LocalBook::class,
        associateBy = Junction(LocalShelfBook::class, parentColumn = "shelfId", entityColumn = "bookId"),
        projection = ["id"],
    )
    val bookIds: List<String>,
)

data class RemoteShelf(val id: String, val name: String)

data class RemoteShelfSnapshot(
    val shelves: List<RemoteShelf>,
    val shelfIdsByRemoteBook: Map<String, Set<String>>,
)

@Entity(
    tableName = "shelf_books",
    primaryKeys = ["shelfId", "bookId"],
    foreignKeys = [
        ForeignKey(entity = LocalShelf::class, parentColumns = ["id"], childColumns = ["shelfId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocalBook::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class LocalShelfBook(val shelfId: String, val bookId: String)
