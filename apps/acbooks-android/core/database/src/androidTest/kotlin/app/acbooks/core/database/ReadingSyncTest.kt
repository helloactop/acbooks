package app.acbooks.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSyncTest {
    @Test
    fun restoresBothFormatsAndRetriesTheSameMutationWithoutLosingLaterNavigation() = runBlocking {
        withLibrary { dao, profile ->
            val book = cloudBook(profile)
            val epub = cloudFile(book, "epub", "a".repeat(64))
            val pdf = cloudFile(book, "pdf", "b".repeat(64))
            val epubPosition = ReadingPositionChange(epub.remoteFileId, 9, """{"href":"chapter.xhtml"}""", 50.0, 90)
            val pdfPosition = ReadingPositionChange(pdf.remoteFileId, 10, """{"page":4}""", 30.0, 100)
            dao.applyCloudCatalog(profile.id, null, true, CloudCatalogBatch(10, listOf(book), listOf(epub, pdf),
                listOf(fileVersion(epub, 2), fileVersion(pdf, 3)), listOf(epubPosition, pdfPosition)))
            val localEpub = dao.attachDownloadedFile(epub, book.remoteBookId, requireNotNull(epub.sha256), 100, 110)
            val localPdf = dao.attachDownloadedFile(pdf, book.remoteBookId, requireNotNull(pdf.sha256), 100, 110)
            assertEquals(epubPosition.locator, dao.readingPosition(localEpub.id))
            assertEquals(pdfPosition.locator, dao.readingPosition(localPdf.id))
            assertFalse(requireNotNull(dao.readingPositionRecord(localPdf.id)).needsSync)
            assertTrue(dao.dirtyReadingFiles(profile.id).isEmpty())

            assertTrue(dao.recordReadingPosition(localPdf.id, """{"page":5}""", 35.0, 120))
            val first = requireNotNull(dao.prepareReadingPosition(profile.id, localPdf.id))
            assertEquals(10L, first.baseRevision)
            assertEquals(pdf.remoteFileId, first.remoteFileId)
            assertTrue(dao.recordReadingPosition(localPdf.id, """{"page":6}""", 40.0, 130))
            assertEquals(first, dao.prepareReadingPosition(profile.id, localPdf.id))

            dao.finishReadingPosition(first, pdfPosition.copy(revision = 11, locator = first.locator, percent = first.percent))
            val next = requireNotNull(dao.prepareReadingPosition(profile.id, localPdf.id))
            assertEquals(11L, next.baseRevision)
            assertEquals("""{"page":6}""", next.locator)
            assertNotEquals(first.mutationId, next.mutationId)

            val newerRemote = pdfPosition.copy(revision = 12, locator = """{"page":9}""", percent = 65.0)
            dao.applyCloudCatalog(profile.id, 10, false, CloudCatalogBatch(12, emptyList(), emptyList(), emptyList(),
                listOf(newerRemote)))
            assertEquals(next.locator, dao.readingPosition(localPdf.id))
            dao.finishReadingPosition(next, newerRemote)
            assertEquals(newerRemote.locator, dao.readingPosition(localPdf.id))
            assertTrue(dao.dirtyReadingFiles(profile.id).isEmpty())

            val deletion = ReadingPositionChange(pdf.remoteFileId, 13, null)
            dao.applyCloudCatalog(profile.id, 12, false, CloudCatalogBatch(13, emptyList(), emptyList(), emptyList(),
                listOf(deletion)))
            assertNull(dao.readingPosition(localPdf.id))
            dao.recordReadingPosition(localPdf.id, """{"page":1}""", 10.0, 140)
            assertEquals(13L, dao.prepareReadingPosition(profile.id, localPdf.id)?.baseRevision)
        }
    }

    @Test
    fun aNewContentHashDoesNotInheritTheOldPositions() = runBlocking {
        withLibrary { dao, profile ->
            val book = cloudBook(profile)
            val original = cloudFile(book, "pdf", "a".repeat(64))
            val position = ReadingPositionChange(original.remoteFileId, 5, """{"page":16}""", 90.0, 50)
            dao.applyCloudCatalog(profile.id, null, true, CloudCatalogBatch(5, listOf(book), listOf(original),
                listOf(fileVersion(original, 2)), listOf(position)))
            val old = dao.attachDownloadedFile(original, book.remoteBookId, requireNotNull(original.sha256), 100, 60)
            dao.recordReadingPosition(old.id, """{"page":17}""", 95.0, 70)
            val pending = requireNotNull(dao.prepareReadingPosition(profile.id, old.id))

            val updated = original.copy(sha256 = "c".repeat(64))
            dao.applyCloudCatalog(profile.id, 5, false, CloudCatalogBatch(6, emptyList(), listOf(updated),
                listOf(fileVersion(updated, 6))))
            assertNull(dao.prepareReadingPosition(profile.id, old.id))
            dao.finishReadingPosition(pending, position.copy(revision = 7, locator = pending.locator))
            assertEquals(pending.locator, dao.readingPosition(old.id))

            val replacement = dao.attachDownloadedFile(updated, book.remoteBookId, requireNotNull(updated.sha256), 100, 80)
            assertNotEquals(old.id, replacement.id)
            assertNull(dao.readingPosition(replacement.id))
            dao.applyCloudCatalog(profile.id, 6, true, CloudCatalogBatch(6, listOf(book), listOf(updated),
                listOf(fileVersion(updated, 6)), listOf(position)))
            assertNull(dao.readingPosition(replacement.id))
            dao.recordReadingPosition(replacement.id, """{"page":1}""", 20.0, 90)
            assertEquals(5L, dao.prepareReadingPosition(profile.id, replacement.id)?.baseRevision)
        }
    }

    private suspend fun withLibrary(run: suspend (LibraryDao, ServerProfile) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).configureLibrary().build()
        try {
            val dao = database.library()
            val account = uuid()
            run(dao, dao.saveCurrentServerProfile(account, account, uuid(), "https://example.invalid", 1))
        } finally {
            database.close()
        }
    }

    private fun cloudBook(profile: ServerProfile) =
        RemoteCatalogBook(profile.id, uuid(), "Cloud book", null, null, true, 1, 1)

    private fun cloudFile(book: RemoteCatalogBook, format: String, hash: String) =
        RemoteCatalogFile(book.profileId, uuid(), book.remoteBookId, format, 100, hash, RemoteFileStatus.Present, 1)

    private fun fileVersion(file: RemoteCatalogFile, revision: Long) =
        CloudCatalogVersion(file.profileId, "book_file", file.remoteFileId, revision, false)

    private fun uuid() = UUID.randomUUID().toString()
}
