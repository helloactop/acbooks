package app.acbooks.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPositionDao {
    @Query("SELECT locator FROM reading_positions WHERE fileId = :fileId")
    suspend fun readingPosition(fileId: String): String?

    @Query("SELECT * FROM reading_positions WHERE fileId = :fileId")
    suspend fun readingPositionRecord(fileId: String): LocalReadingPosition?

    @Query("SELECT * FROM reading_positions WHERE fileId = :fileId")
    fun observeReadingPosition(fileId: String): Flow<LocalReadingPosition?>

    @Query("""
        SELECT f.format, p.locator, p.percent FROM books b
        JOIN book_files f ON f.id = COALESCE(b.preferredFileId, (
            SELECT id FROM book_files WHERE bookId = b.id ORDER BY id LIMIT 1
        ))
        LEFT JOIN reading_positions p ON p.fileId = f.id
        WHERE b.id = :bookId
    """)
    fun observeBookReadingPosition(bookId: String): Flow<LocalBookReadingPosition?>

    @Upsert
    suspend fun saveReadingPosition(position: LocalReadingPosition)

    @Query("""
        INSERT INTO reading_positions (fileId, locator, percent, revision, updatedAt, needsSync, cloudRevision)
        VALUES (:fileId, :locator, :percent, 1, :updatedAt, 1, :cloudRevision)
        ON CONFLICT(fileId) DO UPDATE SET
            locator = excluded.locator,
            percent = excluded.percent,
            revision = reading_positions.revision + 1,
            updatedAt = MAX(reading_positions.updatedAt, excluded.updatedAt),
            needsSync = 1,
            cloudRevision = COALESCE(reading_positions.cloudRevision, excluded.cloudRevision)
        WHERE reading_positions.locator <> excluded.locator OR reading_positions.percent IS NOT excluded.percent
    """)
    suspend fun writeReadingPosition(fileId: String, locator: String, percent: Double?, updatedAt: Long, cloudRevision: Long? = null)

    @Query("DELETE FROM reading_positions WHERE fileId = :fileId")
    suspend fun deleteReadingPosition(fileId: String): Int
}
