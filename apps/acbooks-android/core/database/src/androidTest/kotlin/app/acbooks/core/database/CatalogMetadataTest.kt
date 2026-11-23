package app.acbooks.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogMetadataTest {
    private lateinit var database: LibraryDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun openDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).configureLibrary().build()
        dao = database.library()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun equalTitleIsbnAndHashDoNotMergeBooks() = runBlocking {
        val first = LocalBook(uuid(), "Same title", "Author, Jr.", 1000)
        val second = first.copy(id = uuid())
        val firstFile = file(first.id)
        val secondFile = file(second.id)
        val shared = LocalFileObject("hash", "pdf", 100)
        assertEquals(BookImportCommit.Added, dao.importBook(first, firstFile, shared, false))
        assertEquals(BookImportCommit.Added, dao.importBook(second, secondFile, shared, true))
        val metadata = first.copy(description = "Description", series = "Series", readingStatus = "reading", metadataLocks = 4, updatedAt = 2000)
        assertEquals(1, dao.updateBook(metadata))
        assertEquals(metadata, dao.observeBook(first.id).first())
        assertEquals(second, dao.book(second.id))

        val identifier = LocalBookIdentifier(uuid(), first.id, "isbn", "9780141439518")
        dao.insertBookIdentifier(identifier)
        dao.insertBookIdentifier(identifier.copy(id = uuid(), bookId = second.id))
        assertConstraint { dao.insertBookIdentifier(identifier.copy(id = uuid())) }
        assertEquals(listOf(identifier), dao.observeBookIdentifiers(first.id).first())

        val translator = LocalContributor(uuid(), "Translator")
        dao.insertContributor(translator)
        val relation = LocalBookContributor(first.id, translator.id, "translator")
        dao.insertBookContributor(relation)
        assertConstraint { dao.insertBookContributor(relation) }
        val author = LocalContributor(uuid(), "Coauthor")
        dao.insertContributor(author)
        dao.insertBookContributor(LocalBookContributor(first.id, author.id, "author"))
        assertEquals(
            listOf("Author, Jr." to "author", "Coauthor" to "author", "Translator" to "translator"),
            dao.observeBookContributors(first.id).first().map { it.name to it.role },
        )
        assertEquals("Author, Jr., Coauthor", dao.observeBooks().first().single { it.id == first.id }.author)

        val tag = LocalTag(uuid(), "Fiction")
        dao.insertTag(tag)
        dao.insertBookTag(LocalBookTag(first.id, tag.id))
        dao.insertBookTag(LocalBookTag(second.id, tag.id))
        assertConstraint { dao.insertBookTag(LocalBookTag(first.id, tag.id)) }
        assertEquals(setOf(first.id, second.id), dao.observeBooks().first().map { it.id }.toSet())

        assertEquals(LocalBookRemoval(emptyList(), emptyList()), dao.removeLocalBook(first.id))
        assertEquals(listOf(tag), dao.observeBookTags(second.id).first())
        assertEquals(listOf(secondFile), dao.allBookFiles(second.id))
        assertTrue(dao.bookIdentifiers(first.id).isEmpty())
        assertTrue(dao.observeBookContributors(first.id).first().isEmpty())
    }

    @Test
    fun preferredFileBelongsToTheBookAndFallsBackAfterFileDeletion() = runBlocking {
        val book = LocalBook(uuid(), "Preferred", null, 1000)
        val other = LocalBook(uuid(), "Other", null, 1000)
        val original = file(book.id).copy(id = "00000000-0000-0000-0000-000000000001")
        val alternate = file(book.id).copy(id = "00000000-0000-0000-0000-000000000002")
        val foreign = file(other.id)
        val content = LocalFileObject("hash", "pdf", 100)
        dao.importBook(book, original, content, true)
        dao.importBook(other, foreign, content, true)
        dao.addFile(alternate)
        assertEquals(original, dao.firstBookFile(book.id))
        assertConstraint { dao.updateBook(book.copy(preferredFileId = foreign.id)) }
        assertEquals(1, dao.updateBook(book.copy(preferredFileId = alternate.id)))
        dao.writeReadingPosition(alternate.id, """{"page":2}""", 30.0, 2000)
        assertEquals(alternate, dao.firstBookFile(book.id))
        assertEquals(LocalBookReadingPosition("pdf", """{"page":2}""", 30.0), dao.observeBookReadingPosition(book.id).first())
        database.openHelper.writableDatabase.execSQL("DELETE FROM book_files WHERE id = ?", arrayOf(alternate.id))
        assertNull(dao.book(book.id)?.preferredFileId)
        assertEquals(original, dao.firstBookFile(book.id))
    }

    @Test
    fun locationsRejectCyclesAndLeafDeletionKeepsThePhysicalCopy() = runBlocking {
        val book = LocalBook(uuid(), "Physical", null, 1000)
        dao.addBook(book)
        val room = LocalLocation(uuid(), null, "Room")
        val cabinet = LocalLocation(uuid(), room.id, "Cabinet")
        dao.insertLocation(room)
        dao.insertLocation(cabinet)
        val copy = LocalBookCopy(uuid(), book.id, cabinet.id, 1000, "First edition")
        dao.insertBookCopy(copy)
        assertConstraint { dao.updateLocation(room.copy(parentId = cabinet.id)) }
        assertConstraint { dao.deleteLocation(room.id) }
        assertEquals(1, dao.deleteLocation(cabinet.id))
        assertEquals(listOf(copy.copy(locationId = null)), dao.observeBookCopies(book.id).first())
        assertEquals(listOf(room), dao.locations())
        dao.removeLocalBook(book.id)
        assertNull(dao.bookCopy(copy.id))
    }

    private fun file(bookId: String) =
        LocalBookFile(uuid(), bookId, "hash", "pdf", "content://test/${uuid()}", "test.pdf")

    private suspend fun assertConstraint(write: suspend () -> Unit) {
        val failure = runCatching { write() }.exceptionOrNull()
        assertTrue("Expected a constraint violation, got $failure", failure is SQLiteConstraintException)
    }

    private fun uuid() = UUID.randomUUID().toString()
}
