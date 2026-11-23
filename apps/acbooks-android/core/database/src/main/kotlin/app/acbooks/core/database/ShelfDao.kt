package app.acbooks.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.util.Locale
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {
    @Query("""
        SELECT s.name FROM shelves s JOIN shelf_books sb ON sb.shelfId = s.id
        WHERE sb.bookId = :bookId
        AND (s.serverProfileId IS NULL OR s.serverProfileId = (SELECT id FROM server_profiles WHERE currentSlot = 1))
        ORDER BY COALESCE(s.serverNameKey, s.nameKey), s.id
    """)
    fun observeBookShelves(bookId: String): Flow<List<String>>

    @Query("SELECT * FROM shelves ORDER BY COALESCE(serverNameKey, nameKey), id")
    fun observeShelves(): Flow<List<LocalShelf>>

    @Transaction
    @Query("""
        SELECT * FROM shelves
        WHERE serverProfileId = (SELECT id FROM server_profiles WHERE currentSlot = 1)
        OR (serverProfileId IS NULL AND EXISTS (SELECT 1 FROM shelf_books WHERE shelfId = shelves.id))
        ORDER BY COALESCE(serverNameKey, nameKey), id
    """)
    fun observeLibraryShelves(): Flow<List<LibraryShelf>>

    @Query("SELECT * FROM shelves")
    suspend fun allShelves(): List<LocalShelf>

    @Query("""
        SELECT * FROM shelves WHERE (nameKey = :key OR serverNameKey = :key)
        AND (:excludingId IS NULL OR id <> :excludingId)
        AND (serverProfileId IS NULL OR serverProfileId = (SELECT id FROM server_profiles WHERE currentSlot = 1))
        ORDER BY serverNameKey IS NULL, id LIMIT 1
    """)
    suspend fun shelfByNameKey(key: String, excludingId: String? = null): LocalShelf?

    @Upsert
    suspend fun upsertShelf(shelf: LocalShelf)

    @Query("""
        INSERT OR IGNORE INTO shelf_books(shelfId, bookId)
        SELECT :toId, bookId FROM shelf_books WHERE shelfId = :fromId
    """)
    suspend fun copyShelfMemberships(fromId: String, toId: String)

    @Query("SELECT EXISTS (SELECT 1 FROM shelf_books WHERE shelfId = :shelfId)")
    suspend fun hasShelfMembers(shelfId: String): Boolean

    @Query("""
        SELECT sb.shelfId FROM shelf_books sb JOIN shelves s ON s.id = sb.shelfId
        WHERE sb.bookId = :bookId
        AND (s.serverProfileId IS NULL OR s.serverProfileId = (SELECT id FROM server_profiles WHERE currentSlot = 1))
    """)
    suspend fun shelfIdsForBook(bookId: String): List<String>

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

internal suspend fun ShelfDao.replaceBookShelves(bookId: String, desired: Set<String>) {
    val existing = shelfIdsForBook(bookId).toSet()
    for (id in existing - desired) removeMembership(id, bookId)
    for (id in desired - existing) addMembership(LocalShelfBook(id, bookId))
}

internal fun String.shelfNameKey(): String = lowercase(Locale.ROOT)
