package app.acbooks.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {
    @Query("""
        SELECT s.name FROM shelves s JOIN shelf_books sb ON sb.shelfId = s.id
        WHERE sb.bookId = :bookId ORDER BY s.nameKey, s.id
    """)
    fun observeBookShelves(bookId: String): Flow<List<String>>

    @Query("SELECT * FROM shelves ORDER BY nameKey, id")
    fun observeShelves(): Flow<List<LocalShelf>>

    @Query("SELECT * FROM shelf_books")
    fun observeMemberships(): Flow<List<LocalShelfBook>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addShelf(shelf: LocalShelf): Long

    @Query("DELETE FROM shelves WHERE id = :id")
    suspend fun deleteShelf(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMembership(membership: LocalShelfBook)

    @Query("DELETE FROM shelf_books WHERE shelfId = :shelfId AND bookId = :bookId")
    suspend fun removeMembership(shelfId: String, bookId: String)
}
