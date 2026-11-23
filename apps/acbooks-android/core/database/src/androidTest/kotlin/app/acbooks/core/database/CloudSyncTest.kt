package app.acbooks.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncTest {
    @Test
    fun cursorAndUploadsSurviveReopenAndOnlyAConfirmedReceiptRemovesAnUpload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cloud-sync-${uuid()}.db"
        fun open() = Room.databaseBuilder(context, LibraryDatabase::class.java, name).configureLibrary().build()
        val book = LocalBook(uuid(), "Offline book", null, 1)
        val file = LocalBookFile(uuid(), book.id, "a".repeat(64), "pdf", "content://acbooks.test/book", "book.pdf")
        val payload = """{"mutationId":"${uuid()}","entityId":"${book.id}"}"""
        try {
            val profile = withDatabase(open()) { dao ->
                val profile = dao.saveCurrentServerProfile(uuid(), uuid(), uuid(), "https://example.invalid", 1)
                dao.importBook(book, file, LocalFileObject(file.sha256, file.format, 100), true, 1, payload)
                dao.applyCloudCatalog(profile.id, null, true, CloudCatalogBatch(15, emptyList(), emptyList(), emptyList()))
                val upload = dao.pendingCloudUploads(profile.id).first()
                dao.recordCloudUploadFailure(profile.id, upload.id, "IO_ERROR")
                profile
            }

            withDatabase(open()) { dao ->
                assertEquals(15L, dao.cloudSyncCursor(profile.id))
                val queued = dao.pendingCloudUploads(profile.id)
                assertEquals(listOf(null, file.id), queued.map { it.fileId })
                assertEquals(payload, queued[0].payload)
                assertEquals("IO_ERROR", queued[0].lastError)
                dao.enqueueCloudUploads(listOf(PendingCloudUpload.createBook(profile.id, book.id, "changed payload")))
                assertEquals(queued, dao.pendingCloudUploads(profile.id))

                assertTrue(runCatching { dao.acknowledgeCloudUpload(queued[0], "invalid remote UUID") }
                    .exceptionOrNull() is SQLiteConstraintException)
                assertEquals(queued, dao.pendingCloudUploads(profile.id))
                assertNull(dao.remoteBookLink(profile.id, book.id))

                dao.acknowledgeCloudUpload(queued[0], book.id)
                assertEquals(RemoteBookLink(profile.id, book.id, book.id), dao.remoteBookLink(profile.id, book.id))
                assertEquals(listOf(queued[1]), dao.pendingCloudUploads(profile.id))
                val remoteFileId = uuid()
                dao.acknowledgeCloudUpload(queued[1], remoteFileId)
                assertEquals(RemoteFileLink(profile.id, file.id, remoteFileId), dao.remoteFileLink(profile.id, file.id))
                assertFalse(dao.hasPendingCloudUploads(profile.id))
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun deltaCommitsWithItsCursorAndAFailedProjectionRollsBackDeletionsAndCursor() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).configureLibrary().build()
        withDatabase(database) { dao ->
            val profile = dao.saveCurrentServerProfile(uuid(), uuid(), uuid(), "https://example.invalid", 1)
            val book = RemoteCatalogBook(profile.id, uuid(), "Cloud book", null, null, true, 1, 1)
            val other = RemoteCatalogBook(profile.id, uuid(), "Other book", null, null, true, 2, 2)
            val file = RemoteCatalogFile(profile.id, uuid(), other.remoteBookId, "pdf", 100, "b".repeat(64),
                RemoteFileStatus.Present, 3)
            val bookVersion = CloudCatalogVersion(profile.id, "book", book.remoteBookId, 1, false)
            val otherVersion = CloudCatalogVersion(profile.id, "book", other.remoteBookId, 2, false)
            val fileVersion = CloudCatalogVersion(profile.id, "book_file", file.remoteFileId, 3, false)

            dao.applyCloudCatalog(profile.id, null, true, CloudCatalogBatch(3, listOf(book, other), listOf(file),
                listOf(bookVersion, otherVersion, fileVersion)))
            val renamed = book.copy(title = "Renamed", cachedAt = 4)
            dao.applyCloudCatalog(profile.id, 3, false, CloudCatalogBatch(4, listOf(renamed), emptyList(),
                listOf(bookVersion.copy(revision = 4))))
            assertEquals(file, dao.remoteCatalogFile(profile.id, file.remoteFileId))
            assertEquals(other, dao.remoteCatalogBook(profile.id, other.remoteBookId))
            assertEquals(renamed, dao.remoteCatalogBook(profile.id, book.remoteBookId))

            val deleted = bookVersion.copy(revision = 5, deleted = true)
            val absentBookId = uuid()
            val newFile = file.copy(remoteFileId = uuid(), remoteBookId = absentBookId)
            val newFileVersion = fileVersion.copy(entityId = newFile.remoteFileId, revision = 6)
            val invalid = CloudCatalogBatch(6, emptyList(), listOf(newFile), listOf(deleted, newFileVersion))
            assertTrue(runCatching { dao.applyCloudCatalog(profile.id, 4, false, invalid) }
                .exceptionOrNull() is SQLiteConstraintException)
            assertEquals(4L, dao.cloudSyncCursor(profile.id))
            assertEquals(renamed, dao.remoteCatalogBook(profile.id, book.remoteBookId))
            assertEquals(book.remoteBookId, dao.book(book.remoteBookId)?.id)
            assertEquals(bookVersion.copy(revision = 4), dao.cloudCatalogVersion(profile.id, "book", book.remoteBookId))

            val parent = other.copy(remoteBookId = absentBookId)
            val parentVersion = otherVersion.copy(entityId = absentBookId, revision = 7)
            dao.applyCloudCatalog(profile.id, 4, false, invalid.copy(cursor = 7, books = listOf(parent),
                versions = invalid.versions + parentVersion))
            assertEquals(7L, dao.cloudSyncCursor(profile.id))
            assertNull(dao.book(book.remoteBookId))
            assertEquals(deleted, dao.cloudCatalogVersion(profile.id, "book", book.remoteBookId))
            assertEquals(newFile, dao.remoteCatalogFile(profile.id, newFile.remoteFileId))

            dao.applyCloudCatalog(profile.id, 7, true, CloudCatalogBatch(2, listOf(other), emptyList(), listOf(otherVersion)))
            assertEquals(2L, dao.cloudSyncCursor(profile.id))
            assertNull(dao.remoteCatalogFile(profile.id, newFile.remoteFileId))
            assertNull(dao.cloudCatalogVersion(profile.id, "book", book.remoteBookId))
            assertEquals(absentBookId, dao.book(absentBookId)?.id)
        }
    }

    private suspend fun <T> withDatabase(database: LibraryDatabase, run: suspend (LibraryDao) -> T): T = try {
        run(database.library())
    } finally {
        database.close()
    }

    private fun uuid() = UUID.randomUUID().toString()
}
