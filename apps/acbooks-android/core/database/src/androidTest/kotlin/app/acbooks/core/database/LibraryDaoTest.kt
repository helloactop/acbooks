package app.acbooks.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryDaoTest {
    private lateinit var database: LibraryDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        dao = database.library()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun importDistinguishesExistingSourceAndMatchingContent() = runBlocking {
        val first = book()
        val copy = book()
        val firstFile = file(first, "one")
        val replay = firstFile.copy(id = uuid(), bookId = copy.id)
        val secondFile = file(copy, "two")

        assertEquals(BookImportCommit.Added, import(first, firstFile))
        assertEquals(BookImportCommit.Duplicate, import(copy, replay, allowMatching = true))
        assertEquals(BookImportCommit.MatchingContent, import(copy, secondFile))
        assertEquals(listOf(first.id), dao.observeBooks().first().map { it.id })
        assertTrue(dao.hasImported(firstFile.sourceUri, firstFile.sha256, firstFile.format))
        assertTrue(dao.hasContent(firstFile.sha256, firstFile.format))
        assertEquals(BookImportCommit.Added, import(copy, secondFile, allowMatching = true))
        assertEquals(setOf(first.id, copy.id), dao.observeBooks().first().map { it.id }.toSet())
    }

    @Test
    fun importRollsBackANewObjectIfBookInsertionFails() = runBlocking {
        val book = book()
        val existing = file(book, "one")
        assertEquals(BookImportCommit.Added, import(book, existing))

        val conflictingFile = file(book, "two", hash = "b".repeat(64))
        val failure = runCatching { import(book, conflictingFile) }.exceptionOrNull()
        assertTrue(failure is SQLiteConstraintException)
        assertEquals(listOf(existing), dao.allBookFiles(book.id))
        database.openHelper.readableDatabase.query(
            "SELECT 1 FROM file_objects WHERE sha256 = ?", arrayOf(conflictingFile.sha256),
        ).use { assertFalse(it.moveToFirst()) }
    }

    @Test
    fun shelvesIgnoreDuplicateMembershipAndCascadeWithoutDeletingBooks() = runBlocking {
        val book = book()
        assertEquals(BookImportCommit.Added, import(book, file(book, "one")))
        val shelf = LocalShelf(uuid(), "Fiction", "fiction")
        val anotherShelf = LocalShelf(uuid(), "History", "history")
        assertTrue(dao.addShelf(shelf) > 0)
        assertEquals(-1L, dao.addShelf(shelf.copy(id = uuid())))
        dao.addShelf(anotherShelf)
        val member = LocalShelfBook(shelf.id, book.id)
        val otherMember = LocalShelfBook(anotherShelf.id, book.id)
        dao.addMembership(member)
        dao.addMembership(member)
        dao.addMembership(otherMember)

        assertEquals(listOf("Fiction", "History"), dao.observeBookShelves(book.id).first())
        assertEquals(setOf(member, otherMember), dao.observeMemberships().first().toSet())
        dao.removeMembership(shelf.id, book.id)
        dao.deleteShelf(anotherShelf.id)
        assertTrue(dao.observeMemberships().first().isEmpty())
        assertEquals(listOf(shelf), dao.observeShelves().first())
        assertEquals(listOf(book.id), dao.observeBooks().first().map { it.id })
    }

    @Test
    fun readingUpsertAndDeletionCascadeOnlyForTheDeletedBook() = runBlocking {
        val first = book()
        val other = book()
        val firstFile = file(first, "one")
        val otherFile = file(other, "two")
        import(first, firstFile)
        import(other, otherFile, allowMatching = true)
        assertEquals(LocalBookReadingPosition("epub", null), dao.observeBookReadingPosition(first.id).first())
        val shelf = LocalShelf(uuid(), "Fiction", "fiction")
        dao.addShelf(shelf)
        dao.addMembership(LocalShelfBook(shelf.id, first.id))
        val otherMember = LocalShelfBook(shelf.id, other.id)
        dao.addMembership(otherMember)
        dao.saveReadingPosition(LocalReadingPosition(firstFile.id, "original"))
        dao.saveReadingPosition(LocalReadingPosition(firstFile.id, "updated"))
        dao.saveReadingPosition(LocalReadingPosition(otherFile.id, "other"))
        assertEquals(LocalBookReadingPosition("epub", "updated"), dao.observeBookReadingPosition(first.id).first())
        val origin = "https://example.invalid"
        val profile = dao.saveCurrentServerProfile(uuid(), uuid(), uuid(), origin, 1L)
        val sharedOriginId = uuid()
        val otherRemoteId = uuid()
        dao.linkRemoteBook(RemoteBookLink(profile.id, first.id, sharedOriginId))
        dao.linkRemoteBook(RemoteBookLink(profile.id, other.id, otherRemoteId))

        assertEquals(LocalBookRemoval(emptyList(), emptyList()), dao.removeLocalBook(first.id))
        assertNull(dao.removeLocalBook(first.id))
        assertNull(dao.firstBookFile(first.id))
        assertNull(dao.readingPosition(firstFile.id))
        assertNull(dao.remoteBookLink(profile.id, first.id))
        assertEquals(listOf(otherMember), dao.observeMemberships().first())
        assertEquals(otherFile, dao.firstBookFile(other.id))
        assertEquals("other", dao.readingPosition(otherFile.id))
        assertEquals(otherRemoteId, dao.remoteBookLink(profile.id, other.id)?.remoteBookId)
        assertTrue(dao.hasContent(otherFile.sha256, otherFile.format))
        assertEquals(
            LocalBookRemoval(listOf(LocalFileObject(otherFile.sha256, otherFile.format, 0L)), listOf(otherFile.sha256)),
            dao.removeLocalBook(other.id),
        )
        assertFalse(dao.hasContent(otherFile.sha256, otherFile.format))
        assertEquals(listOf(shelf), dao.observeShelves().first())
    }

    @Test
    fun coverRemainsReferencedAcrossFileFormats() = runBlocking {
        val epub = book()
        val pdf = book()
        val epubFile = file(epub, "one")
        val pdfFile = file(pdf, "two").copy(format = "pdf")
        import(epub, epubFile)
        import(pdf, pdfFile)
        assertEquals(
            LocalBookRemoval(listOf(LocalFileObject(epubFile.sha256, "epub", 0L)), emptyList()),
            dao.removeLocalBook(epub.id),
        )
        assertEquals(pdfFile, dao.firstBookFile(pdf.id))
        assertEquals(listOf(pdfFile.sha256), dao.removeLocalBook(pdf.id)?.unreferencedCoverHashes)
    }

    private suspend fun import(book: LocalBook, file: LocalBookFile, allowMatching: Boolean = false): BookImportCommit =
        dao.importBook(book, file, LocalFileObject(file.sha256, file.format, 100L), allowMatching)

    private fun book() = LocalBook(uuid(), "Test book", null, 1L)

    private fun file(book: LocalBook, source: String, hash: String = "a".repeat(64)) =
        LocalBookFile(uuid(), book.id, hash, "epub", "content://acbooks.test/$source", "$source.epub")

    private fun uuid(): String = UUID.randomUUID().toString()
}
