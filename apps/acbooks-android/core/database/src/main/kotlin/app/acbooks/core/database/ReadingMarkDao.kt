package app.acbooks.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingMarkDao {
    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun bookmark(id: String): LocalBookmark?

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC, id LIMIT :limit OFFSET :offset")
    suspend fun bookmarks(bookId: String, limit: Int = 20, offset: Int = 0): List<LocalBookmark>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC, id")
    fun observeBookmarks(bookId: String): Flow<List<LocalBookmark>>

    @Insert
    suspend fun insertBookmark(item: LocalBookmark)

    @Update
    suspend fun updateBookmark(item: LocalBookmark): Int

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String): Int

    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun annotation(id: String): LocalAnnotation?

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY createdAt DESC, id LIMIT :limit OFFSET :offset")
    suspend fun annotations(bookId: String, limit: Int = 20, offset: Int = 0): List<LocalAnnotation>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY createdAt DESC, id")
    fun observeAnnotations(bookId: String): Flow<List<LocalAnnotation>>

    @Insert
    suspend fun insertAnnotation(item: LocalAnnotation)

    @Update
    suspend fun updateAnnotation(item: LocalAnnotation): Int

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: String): Int
}
