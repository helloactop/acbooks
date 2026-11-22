package app.acbooks.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class LocalBook(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val importedAt: Long,
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
)

@Entity(tableName = "shelves", indices = [Index("nameKey", unique = true)])
data class LocalShelf(@PrimaryKey val id: String, val name: String, val nameKey: String)

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

@Entity(
    tableName = "reading_positions",
    foreignKeys = [ForeignKey(entity = LocalBookFile::class, parentColumns = ["id"], childColumns = ["fileId"],
        onDelete = ForeignKey.CASCADE)],
)
data class LocalReadingPosition(@PrimaryKey val fileId: String, val locator: String)

data class LocalBookReadingPosition(val format: String, val locator: String?)

@Entity(
    tableName = "cloud_book_links",
    foreignKeys = [ForeignKey(
        entity = LocalBook::class,
        parentColumns = ["id"],
        childColumns = ["localBookId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class CloudBookLink(
    @PrimaryKey val localBookId: String,
    val remoteBookId: String,
    val serverOrigin: String,
)

data class LocalBookCard(
    val id: String,
    val title: String,
    val author: String?,
    val importedAt: Long,
    val format: String,
    val sha256: String?,
)

enum class BookImportCommit { Added, Duplicate, MatchingContent }

// Internal book copies are content-addressed and can be shared by several book records.
// This plan lists only files no longer referenced after the database transaction.
data class LocalBookRemoval(
    val unreferencedObjects: List<LocalFileObject>,
    val unreferencedCoverHashes: List<String>,
)

@Dao
abstract class LibraryDao {
    @Query("""
        SELECT b.id, b.title, b.author, b.importedAt,
            COALESCE(f.format, '') AS format, f.sha256
        FROM books b
        LEFT JOIN book_files f ON f.id = (SELECT id FROM book_files WHERE bookId = b.id ORDER BY id LIMIT 1)
        ORDER BY b.importedAt DESC, b.id
    """)
    abstract fun observeBooks(): Flow<List<LocalBookCard>>

    @Query("SELECT * FROM book_files WHERE bookId = :bookId ORDER BY id LIMIT 1")
    abstract suspend fun firstBookFile(bookId: String): LocalBookFile?

    @Query("SELECT remoteBookId FROM cloud_book_links WHERE localBookId = :bookId AND serverOrigin = :serverOrigin")
    abstract suspend fun remoteBookId(bookId: String, serverOrigin: String): String?

    /** Called by the future authenticated sync flow after confirming the remote book identity. */
    @Upsert
    abstract suspend fun bindCloudBook(link: CloudBookLink)

    @Query("SELECT * FROM book_files WHERE bookId = :bookId")
    abstract suspend fun allBookFiles(bookId: String): List<LocalBookFile>

    @Query("DELETE FROM books WHERE id = :bookId")
    abstract suspend fun deleteBookRow(bookId: String): Int

    @Query("""
        DELETE FROM file_objects WHERE sha256 = :sha256 AND format = :format
        AND NOT EXISTS (
            SELECT 1 FROM book_files WHERE sha256 = :sha256 AND format = :format
        )
    """)
    abstract suspend fun deleteObjectIfUnreferenced(sha256: String, format: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM book_files WHERE sha256 = :sha256)")
    abstract suspend fun hasFileWithHash(sha256: String): Boolean

    @Transaction
    open suspend fun removeLocalBook(bookId: String): LocalBookRemoval? {
        val files = allBookFiles(bookId)
        if (deleteBookRow(bookId) == 0) return null

        // Room foreign keys cascade to book_files, reading_positions and shelf_books.
        val unreferencedObjects = files.distinctBy { it.sha256 to it.format }
            .filter { deleteObjectIfUnreferenced(it.sha256, it.format) > 0 }
            .map { LocalFileObject(it.sha256, it.format, 0L) }
        val unreferencedCoverHashes = files.map { it.sha256 }.distinct()
            .filterNot { hasFileWithHash(it) }

        return LocalBookRemoval(unreferencedObjects, unreferencedCoverHashes)
    }

    @Query("SELECT locator FROM reading_positions WHERE fileId = :fileId")
    abstract suspend fun readingPosition(fileId: String): String?

    @Query("""
        SELECT f.format, p.locator FROM book_files f
        LEFT JOIN reading_positions p ON p.fileId = f.id
        WHERE f.bookId = :bookId ORDER BY f.id LIMIT 1
    """)
    abstract fun observeBookReadingPosition(bookId: String): Flow<LocalBookReadingPosition?>

    @Query("SELECT DISTINCT format FROM book_files WHERE bookId = :bookId ORDER BY format")
    abstract fun observeBookFormats(bookId: String): Flow<List<String>>

    @Query("""
        SELECT s.name FROM shelves s JOIN shelf_books sb ON sb.shelfId = s.id
        WHERE sb.bookId = :bookId ORDER BY s.nameKey, s.id
    """)
    abstract fun observeBookShelves(bookId: String): Flow<List<String>>

    @Upsert
    abstract suspend fun saveReadingPosition(position: LocalReadingPosition)

    @Query("SELECT * FROM shelves ORDER BY nameKey, id")
    abstract fun observeShelves(): Flow<List<LocalShelf>>

    @Query("SELECT * FROM shelf_books")
    abstract fun observeMemberships(): Flow<List<LocalShelfBook>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addShelf(shelf: LocalShelf): Long

    @Query("DELETE FROM shelves WHERE id = :id")
    abstract suspend fun deleteShelf(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addMembership(membership: LocalShelfBook)

    @Query("DELETE FROM shelf_books WHERE shelfId = :shelfId AND bookId = :bookId")
    abstract suspend fun removeMembership(shelfId: String, bookId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM book_files WHERE sourceUri = :sourceUri AND sha256 = :sha256 AND format = :format)")
    abstract suspend fun hasImported(sourceUri: String, sha256: String, format: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM book_files WHERE sha256 = :sha256 AND format = :format)")
    abstract suspend fun hasContent(sha256: String, format: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addObject(file: LocalFileObject)

    @Insert
    abstract suspend fun addBook(book: LocalBook)

    @Insert
    abstract suspend fun addFile(file: LocalBookFile)

    @Transaction
    open suspend fun importBook(
        book: LocalBook,
        file: LocalBookFile,
        content: LocalFileObject,
        allowMatchingContent: Boolean,
    ): BookImportCommit {
        if (hasImported(file.sourceUri, file.sha256, file.format)) return BookImportCommit.Duplicate
        if (!allowMatchingContent && hasContent(file.sha256, file.format)) return BookImportCommit.MatchingContent
        addObject(content)
        addBook(book)
        addFile(file)
        return BookImportCommit.Added
    }
}

@Database(
    entities = [
        LocalBook::class, LocalFileObject::class, LocalBookFile::class, LocalShelf::class, LocalShelfBook::class,
        LocalReadingPosition::class,
        CloudBookLink::class,
    ],
    version = 3,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao

    companion object {
        internal val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS reading_positions (fileId TEXT NOT NULL PRIMARY KEY, locator TEXT NOT NULL, " +
                        "FOREIGN KEY(fileId) REFERENCES book_files(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
            }
        }

        internal val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cloud_book_links (" +
                        "localBookId TEXT NOT NULL PRIMARY KEY, remoteBookId TEXT NOT NULL, " +
                        "serverOrigin TEXT NOT NULL, FOREIGN KEY(localBookId) REFERENCES books(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
            }
        }

        fun open(context: Context): LibraryDatabase =
            Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "acbooks-reader.db")
                .addMigrations(migration1To2, migration2To3)
                .build()
    }
}
