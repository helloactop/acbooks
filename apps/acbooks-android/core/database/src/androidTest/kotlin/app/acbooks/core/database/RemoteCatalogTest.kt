package app.acbooks.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCatalogTest {
    @Test
    fun cloudSnapshotAndLocalCopiesRemainIndependent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).configureLibrary().build()
        try {
            val dao = database.library()
            val account = UUID.randomUUID().toString()
            val profile = dao.saveCurrentServerProfile(account, account, UUID.randomUUID().toString(),
                "https://example.invalid", 1)
            assertTrue(runCatching {
                dao.saveCurrentServerProfile("foreign", account, requireNotNull(profile.deviceId), "https://example.invalid", 2)
            }.exceptionOrNull() is IllegalStateException)
            val book = RemoteCatalogBook(profile.id, UUID.randomUUID().toString(), "Cloud book", "Author",
                "Description", true, 1, 1)
            val file = RemoteCatalogFile(profile.id, UUID.randomUUID().toString(), book.remoteBookId, "epub",
                100, "a".repeat(64), RemoteFileStatus.Present, 1)
            dao.replaceRemoteCatalog(profile.id, listOf(book), listOf(file))
            val card = dao.observeBooks().first().single()
            assertEquals(book.remoteBookId, card.id)
            assertEquals("Description", dao.observeCloudDescription(card.id).first())
            assertTrue(card.cloudBacked && card.canDownload)
            assertFalse(card.hasLocalCopy)
            assertNull(dao.firstBookFile(card.id))

            val local = dao.attachDownloadedFile(file, card.id, requireNotNull(file.sha256), file.sizeBytes, 2)
            dao.saveReadingPosition(LocalReadingPosition(local.id, """{"href":"chapter"}"""))
            dao.setBookFileCopyRemoved(local.id, true)
            assertFalse(dao.observeBooks().first().single().hasLocalCopy)
            val renamed = book.copy(title = "Renamed in the cloud", cachedAt = 3)
            dao.replaceRemoteCatalog(profile.id, listOf(renamed), listOf(file.copy(cachedAt = 3)))
            assertEquals(renamed.title, dao.observeBooks().first().single().title)
            val restored = dao.attachDownloadedFile(file, card.id, requireNotNull(file.sha256), file.sizeBytes, 4)
            assertEquals(local.id, restored.id)
            assertEquals("""{"href":"chapter"}""", dao.readingPosition(restored.id))
            assertTrue(dao.observeBooks().first().single().hasLocalCopy)
            assertNull(dao.verifiedRemoteBookLink(card.id, account, UUID.randomUUID().toString(), "https://example.invalid"))

            dao.applyConfirmedRemoteBookDeletion(RemoteBookLink(profile.id, card.id, book.remoteBookId))
            assertTrue(dao.observeBooks().first().isEmpty())
            assertNull(dao.remoteCatalogBook(profile.id, book.remoteBookId))
            assertNull(dao.readingPosition(restored.id))
        } finally {
            database.close()
        }
    }
}
