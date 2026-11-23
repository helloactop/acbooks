package app.acbooks.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingDataTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun readingRecordsReopenAndFileRemovalPreservesBookLevelRecords() = runBlocking {
        val name = "reading-v5-${uuid()}.db"
        val book = LocalBook(uuid(), "One", null, 1000)
        val other = LocalBook(uuid(), "Two", null, 1000)
        val file = LocalBookFile(uuid(), book.id, "hash", "pdf", "content://test/one", "one.pdf")
        val session = LocalReadingSession(uuid(), book.id, file.id, uuid(), 1000, 5000, 4, "automatic")
        val bookmark = LocalBookmark(uuid(), book.id, file.id, """{"page":1}""", "Bookmark", 1000, 1000)
        val annotation = LocalAnnotation(uuid(), book.id, file.id, """{"page":1}""", "Quote", "Note", 1000, 1000)
        val locator = """{"page":2}"""
        try {
            val opened = Room.databaseBuilder(context, LibraryDatabase::class.java, name).configureLibrary().build()
            try {
                val dao = opened.library()
                dao.addBook(other)
                assertEquals(BookImportCommit.Added, dao.importBook(book, file, LocalFileObject("hash", "pdf", 100), true))
                dao.insertReadingSession(session)
                dao.insertBookmark(bookmark)
                dao.insertAnnotation(annotation)
                dao.writeReadingPosition(file.id, """{"page":1}""", 20.0, 2000)
                dao.writeReadingPosition(file.id, locator, 30.0, 3000)
                dao.writeReadingPosition(file.id, locator, 30.0, 4000)
                assertConstraint { dao.writeReadingPosition(file.id, locator, 101.0, 5000) }
                assertConstraint { dao.insertReadingSession(session.copy(id = uuid(), fileId = null)) }
                assertConstraint { dao.updateReadingSession(session.copy(fileId = null)) }
                assertConstraint { dao.insertReadingSession(session.copy(id = uuid(), activeSeconds = 5)) }
                assertConstraint { dao.insertReadingSession(session.copy(id = uuid(), source = "manual", endedAt = null)) }
                assertConstraint { dao.insertReadingSession(session.copy(id = uuid(), bookId = other.id)) }
                assertConstraint { dao.insertBookmark(bookmark.copy(id = uuid(), bookId = other.id)) }
                assertConstraint { dao.insertAnnotation(annotation.copy(id = uuid(), bookId = other.id)) }
                assertConstraint {
                    opened.openHelper.writableDatabase.execSQL(
                        "UPDATE book_files SET bookId = ? WHERE id = ?", arrayOf(other.id, file.id),
                    )
                }
            } finally {
                opened.close()
            }

            val reopened = Room.databaseBuilder(context, LibraryDatabase::class.java, name).configureLibrary().build()
            try {
                val dao = reopened.library()
                assertEquals(listOf(session), dao.observeReadingSessions(book.id).first())
                assertEquals(listOf(bookmark), dao.bookmarks(book.id))
                assertEquals(listOf(annotation), dao.observeAnnotations(book.id).first())
                assertEquals(LocalReadingPosition(file.id, locator, 30.0, 2, 3000), dao.observeReadingPosition(file.id).first())
                assertEquals(1, dao.updateBookmark(bookmark.copy(label = "Renamed", updatedAt = 2000)))
                assertEquals(1, dao.updateAnnotation(annotation.copy(note = "Edited", updatedAt = 2000)))
                assertEquals(1, dao.updateReadingSession(session.copy(activeSeconds = 3)))
                reopened.openHelper.writableDatabase.execSQL("DELETE FROM book_files WHERE id = ?", arrayOf(file.id))
                assertEquals(session.copy(fileId = null, activeSeconds = 3), dao.readingSession(session.id))
                assertEquals(bookmark.copy(fileId = null, label = "Renamed", updatedAt = 2000), dao.bookmark(bookmark.id))
                assertEquals(annotation.copy(fileId = null, note = "Edited", updatedAt = 2000), dao.annotation(annotation.id))
                assertNull(dao.readingPosition(file.id))
                dao.removeLocalBook(book.id)
                assertNull(dao.readingSession(session.id))
                assertNull(dao.bookmark(bookmark.id))
                assertNull(dao.annotation(annotation.id))
                assertEquals(other, dao.book(other.id))
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }

    @Test
    fun sessionStatisticsExcludeInactiveRowsAndDoNotDoubleCountOverlaps() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).configureLibrary().build()
        try {
            val dao = database.library()
            val book = LocalBook(uuid(), "Statistics", null, 0)
            val start = Instant.parse("2026-01-01T23:59:00Z").toEpochMilli()
            dao.addBook(book)
            assertNull(dao.observeReadingStatistics(book.id, ZoneId.of("UTC")).first())
            dao.insertReadingSession(manualSession(book.id, start, 100, 50))
            dao.insertReadingSession(manualSession(book.id, start + 40_000, 50, 50))
            dao.insertReadingSession(manualSession(book.id, start + 120_000, 10, 10))
            dao.insertReadingSession(manualSession(book.id, start + 130_000, 10, 0))
            val summary = requireNotNull(dao.observeReadingStatistics(book.id, ZoneId.of("UTC")).first())
            assertEquals(85.0, summary.activeSeconds, 0.001)
            assertEquals(3, summary.sessionCount)
            assertEquals(2, summary.readingDays)
            assertEquals(start + 130_000, summary.lastReadAt)
            assertEquals(2, dao.readingSessions(book.id, limit = 2).size)
        } finally {
            database.close()
        }
    }

    private fun manualSession(bookId: String, start: Long, span: Long, active: Long) =
        LocalReadingSession(uuid(), bookId, null, null, start, start + span * 1000, active, "manual")

    private suspend fun assertConstraint(write: suspend () -> Unit) {
        val failure = runCatching { write() }.exceptionOrNull()
        assertTrue("Expected a constraint violation, got $failure", failure is SQLiteConstraintException)
    }

    private fun uuid() = UUID.randomUUID().toString()
}
