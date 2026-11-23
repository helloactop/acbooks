package app.acbooks.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "pending_book_edits",
    primaryKeys = ["bookId", "profileId"],
    foreignKeys = [
        ForeignKey(entity = LocalBook::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PendingBookEdit(
    val bookId: String,
    val profileId: String,
    val remoteBookId: String,
    val mutationId: String,
    val patchJson: String,
    val baselineJson: String?,
    val coverKey: String?,
    val baseCoverVersion: Long?,
)

data class BookEditRelations(
    val authors: List<String>,
    val translators: List<String>,
    val isbn: String?,
    val tags: List<String>,
    val shelves: List<String>,
)

interface BookEditDao : CatalogMetadataDao, ShelfDao, RemoteLibraryDao {
    @Query("""
        SELECT e.* FROM pending_book_edits e
        JOIN server_profiles p ON p.id = e.profileId AND p.currentSlot = 1
        WHERE e.bookId = :bookId
    """)
    suspend fun pendingBookEdit(bookId: String): PendingBookEdit?

    @Query("""
        SELECT e.* FROM pending_book_edits e
        JOIN server_profiles p ON p.id = e.profileId AND p.currentSlot = 1
        WHERE e.bookId = :bookId
    """)
    fun observePendingBookEdit(bookId: String): Flow<PendingBookEdit?>

    @Query("SELECT EXISTS (SELECT 1 FROM pending_book_edits WHERE coverKey = :coverKey)")
    suspend fun hasPendingEditedCover(coverKey: String): Boolean

    @Query("SELECT * FROM pending_book_edits WHERE profileId = :profileId ORDER BY bookId")
    suspend fun pendingBookEdits(profileId: String): List<PendingBookEdit>

    @Upsert
    suspend fun queueBookEdit(edit: PendingBookEdit)

    @Query("DELETE FROM pending_book_edits WHERE bookId = :bookId AND mutationId = :mutationId")
    suspend fun completeBookEdit(bookId: String, mutationId: String)

    @Query("""
        SELECT c.* FROM remote_catalog c
        JOIN remote_book_links l ON l.profileId = c.profileId AND l.remoteBookId = c.remoteBookId
        JOIN server_profiles p ON p.id = l.profileId AND p.currentSlot = 1
        WHERE l.localBookId = :bookId AND c.available = 1
    """)
    fun observeBookCloudMetadata(bookId: String): Flow<RemoteCatalogBook?>

    @Query("""
        SELECT c.id AS contributorId, c.name, bc.role
        FROM book_contributors bc JOIN contributors c ON c.id = bc.contributorId
        WHERE bc.bookId = :bookId ORDER BY bc.role, c.name, c.id
    """)
    suspend fun bookContributors(bookId: String): List<LocalBookContributorName>

    @Query("SELECT * FROM tags WHERE id IN (SELECT tagId FROM book_tags WHERE bookId = :bookId) ORDER BY name, id")
    suspend fun bookTags(bookId: String): List<LocalTag>

    @Query("""
        SELECT s.* FROM shelves s JOIN shelf_books sb ON sb.shelfId = s.id
        WHERE sb.bookId = :bookId
        AND (s.serverProfileId IS NULL OR s.serverProfileId = (SELECT id FROM server_profiles WHERE currentSlot = 1))
        ORDER BY COALESCE(s.serverNameKey, s.nameKey), s.id
    """)
    suspend fun bookShelves(bookId: String): List<LocalShelf>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE ORDER BY id LIMIT 1")
    suspend fun tagByName(name: String): LocalTag?

    @Query("SELECT * FROM contributors WHERE name = :name ORDER BY id LIMIT 1")
    suspend fun contributorByName(name: String): LocalContributor?

    @Query("DELETE FROM book_identifiers WHERE bookId = :bookId AND scheme = 'isbn'")
    suspend fun clearBookIsbn(bookId: String)

    @Transaction
    suspend fun writeBookEdit(
        bookId: String,
        edited: LocalBook,
        relations: BookEditRelations,
        pending: PendingBookEdit?,
    ) {
        val existing = checkNotNull(book(bookId))
        check(updateBook(existing.copy(
            title = edited.title,
            author = edited.author,
            description = edited.description,
            series = edited.series,
            publisher = edited.publisher,
            publicationDate = edited.publicationDate,
            readingStatus = edited.readingStatus,
            editedCoverKey = edited.editedCoverKey,
            metadataEdited = true,
            updatedAt = edited.updatedAt,
        )) == 1)

        val people = bookContributors(bookId)
        for ((role, names) in listOf("author" to relations.authors, "translator" to relations.translators)) {
            val existingPeople = people.filter { it.role == role }.associateBy { it.name }
            for (person in existingPeople.values) {
                if (person.name !in names) deleteBookContributor(bookId, person.contributorId, role)
            }
            for (name in names) {
                if (name in existingPeople) continue
                val person = contributorByName(name) ?: LocalContributor(editId(), name).also { insertContributor(it) }
                insertBookContributor(LocalBookContributor(bookId, person.id, role))
            }
        }

        val isbns = bookIdentifiers(bookId).filter { it.scheme == "isbn" }
        if (isbns.firstOrNull()?.value != relations.isbn) {
            clearBookIsbn(bookId)
            relations.isbn?.let { insertBookIdentifier(LocalBookIdentifier(editId(), bookId, "isbn", it)) }
        }

        val tagIds = relations.tags.map { name ->
            tagByName(name)?.id ?: LocalTag(editId(), name).also { insertTag(it) }.id
        }.toSet()
        val existingTagIds = bookTags(bookId).map { it.id }.toSet()
        for (id in existingTagIds - tagIds) deleteBookTag(bookId, id)
        for (id in tagIds - existingTagIds) insertBookTag(LocalBookTag(bookId, id))

        val assignedShelves = bookShelves(bookId).groupBy { it.name.shelfNameKey() }
        val shelfIds = relations.shelves.flatMap { name ->
            val key = name.shelfNameKey()
            assignedShelves[key]?.map { it.id } ?: listOf(
                shelfByNameKey(key)?.id ?: LocalShelf(editId(), name, key).also { addShelf(it) }.id,
            )
        }.toSet()
        replaceBookShelves(bookId, shelfIds)

        if (pending != null) queueBookEdit(pending)
    }
}

private fun editId(): String = UUID.randomUUID().toString()
