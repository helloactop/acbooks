package app.acbooks.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions WHERE id = :id")
    suspend fun readingSession(id: String): LocalReadingSession?

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startedAt DESC, id LIMIT :limit OFFSET :offset")
    suspend fun readingSessions(bookId: String, limit: Int = 20, offset: Int = 0): List<LocalReadingSession>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startedAt DESC, id")
    fun observeReadingSessions(bookId: String): Flow<List<LocalReadingSession>>

    @Insert
    suspend fun insertReadingSession(item: LocalReadingSession)

    @Update
    suspend fun updateReadingSession(item: LocalReadingSession): Int

    @Query("DELETE FROM reading_sessions WHERE id = :id")
    suspend fun deleteReadingSession(id: String): Int

    @Query("""
        SELECT * FROM reading_sessions
        WHERE bookId = :bookId AND endedAt IS NOT NULL AND activeSeconds > 0
        ORDER BY startedAt, id
    """)
    fun observeCompletedReadingSessions(bookId: String): Flow<List<LocalReadingSession>>
}
