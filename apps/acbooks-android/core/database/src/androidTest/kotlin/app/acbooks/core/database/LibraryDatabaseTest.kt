package app.acbooks.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDatabaseTest {
    @Test
    fun importsOncePerSourceAndKeepsBooksWhenShelvesAreRemoved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        try {
            val dao = db.library()
            val objectFile = LocalFileObject("123", "epub", 32L)
            val first = LocalBook("book-1", "Title", "Author", 1L)
            val second = LocalBook("book-2", "Title", "Author", 2L)
            val source = LocalBookFile("file-1", first.id, "123", "epub", "content://test/first", "first.epub")

            assertEquals(BookImportCommit.Added, dao.importBook(first, source, objectFile, false))
            val sameSource = source.copy(id = "file-2", bookId = second.id)
            assertEquals(BookImportCommit.Duplicate, dao.importBook(second, sameSource, objectFile, false))
            val differentSource = sameSource.copy(sourceUri = "content://test/copy")
            assertEquals(BookImportCommit.MatchingContent, dao.importBook(second, differentSource, objectFile, false))
            assertEquals(BookImportCommit.Added, dao.importBook(second, differentSource, objectFile, true))
            assertEquals(listOf(second.id, first.id), dao.observeBooks().first().map { it.id })
            dao.saveReadingPosition(LocalReadingPosition(source.id, "first location"))
            dao.saveReadingPosition(LocalReadingPosition(source.id, "updated location"))
            assertEquals("updated location", dao.readingPosition(source.id))
            assertEquals(null, dao.readingPosition(differentSource.id))

            val shelf = LocalShelf("shelf-1", "Fiction", "fiction")
            assertTrue(dao.addShelf(shelf) != -1L)
            assertEquals(-1L, dao.addShelf(shelf.copy(id = "shelf-2", name = "fiction")))
            val membership = LocalShelfBook(shelf.id, first.id)
            dao.addMembership(membership)
            dao.addMembership(membership)
            assertEquals(listOf(membership), dao.observeMemberships().first())
            dao.removeMembership(shelf.id, first.id)
            assertTrue(dao.observeMemberships().first().isEmpty())
            dao.addMembership(membership)
            dao.deleteShelf(shelf.id)
            assertTrue(dao.observeShelves().first().isEmpty())
            assertTrue(dao.observeMemberships().first().isEmpty())
            assertEquals(2, dao.observeBooks().first().size)
        } finally {
            db.close()
        }
    }
}
