package app.acbooks.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogMetadataDao {
    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun book(bookId: String): LocalBook?

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun observeBook(bookId: String): Flow<LocalBook?>

    @Update
    suspend fun updateBook(book: LocalBook): Int

    @Query("SELECT * FROM contributors WHERE id = :id")
    suspend fun contributor(id: String): LocalContributor?

    @Query("SELECT * FROM contributors ORDER BY name, id")
    suspend fun contributors(): List<LocalContributor>

    @Query("SELECT * FROM contributors ORDER BY name, id")
    fun observeContributors(): Flow<List<LocalContributor>>

    @Insert
    suspend fun insertContributor(item: LocalContributor)

    @Update
    suspend fun updateContributor(item: LocalContributor): Int

    @Query("DELETE FROM contributors WHERE id = :id")
    suspend fun deleteContributor(id: String): Int

    @Query("SELECT * FROM book_identifiers WHERE id = :id")
    suspend fun bookIdentifier(id: String): LocalBookIdentifier?

    @Query("SELECT * FROM book_identifiers WHERE bookId = :bookId ORDER BY scheme, value, id")
    suspend fun bookIdentifiers(bookId: String): List<LocalBookIdentifier>

    @Query("SELECT * FROM book_identifiers WHERE bookId = :bookId ORDER BY scheme, value, id")
    fun observeBookIdentifiers(bookId: String): Flow<List<LocalBookIdentifier>>

    @Insert
    suspend fun insertBookIdentifier(item: LocalBookIdentifier)

    @Update
    suspend fun updateBookIdentifier(item: LocalBookIdentifier): Int

    @Query("DELETE FROM book_identifiers WHERE id = :id")
    suspend fun deleteBookIdentifier(id: String): Int

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun tag(id: String): LocalTag?

    @Query("SELECT * FROM tags ORDER BY name, id")
    suspend fun tags(): List<LocalTag>

    @Query("SELECT * FROM tags ORDER BY name, id")
    fun observeTags(): Flow<List<LocalTag>>

    @Insert
    suspend fun insertTag(item: LocalTag)

    @Update
    suspend fun updateTag(item: LocalTag): Int

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: String): Int

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun location(id: String): LocalLocation?

    @Query("SELECT * FROM locations ORDER BY name, id")
    suspend fun locations(): List<LocalLocation>

    @Query("SELECT * FROM locations ORDER BY name, id")
    fun observeLocations(): Flow<List<LocalLocation>>

    @Insert
    suspend fun insertLocation(item: LocalLocation)

    @Update
    suspend fun updateLocation(item: LocalLocation): Int

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteLocation(id: String): Int

    @Query("SELECT * FROM book_copies WHERE id = :id")
    suspend fun bookCopy(id: String): LocalBookCopy?

    @Query("SELECT * FROM book_copies WHERE bookId = :bookId ORDER BY id")
    suspend fun bookCopies(bookId: String): List<LocalBookCopy>

    @Query("SELECT * FROM book_copies WHERE bookId = :bookId ORDER BY id")
    fun observeBookCopies(bookId: String): Flow<List<LocalBookCopy>>

    @Insert
    suspend fun insertBookCopy(item: LocalBookCopy)

    @Update
    suspend fun updateBookCopy(item: LocalBookCopy): Int

    @Query("DELETE FROM book_copies WHERE id = :id")
    suspend fun deleteBookCopy(id: String): Int

    @Query("""
        SELECT c.id AS contributorId, c.name, bc.role
        FROM book_contributors bc JOIN contributors c ON c.id = bc.contributorId
        WHERE bc.bookId = :bookId ORDER BY bc.role, c.name, c.id
    """)
    fun observeBookContributors(bookId: String): Flow<List<LocalBookContributorName>>

    @Insert
    suspend fun insertBookContributor(item: LocalBookContributor)

    @Query("DELETE FROM book_contributors WHERE bookId = :bookId AND contributorId = :contributorId AND role = :role")
    suspend fun deleteBookContributor(bookId: String, contributorId: String, role: String): Int

    @Query("""
        SELECT * FROM tags WHERE id IN (SELECT tagId FROM book_tags WHERE bookId = :bookId)
        ORDER BY name, id
    """)
    fun observeBookTags(bookId: String): Flow<List<LocalTag>>

    @Insert
    suspend fun insertBookTag(item: LocalBookTag)

    @Query("DELETE FROM book_tags WHERE bookId = :bookId AND tagId = :tagId")
    suspend fun deleteBookTag(bookId: String, tagId: String): Int
}
