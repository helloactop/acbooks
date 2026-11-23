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
            assertEquals("Description", dao.observeBookCloudMetadata(card.id).first()?.description)
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

    @Test
    fun remoteShelvesUseServerIdsAndPreserveOfflineAssignments() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).configureLibrary().build()
        try {
            val dao = database.library()
            val account = UUID.randomUUID().toString()
            val profile = dao.saveCurrentServerProfile(
                account, account, UUID.randomUUID().toString(), "https://example.invalid", 1,
            )
            val cloudBook = RemoteCatalogBook(
                profile.id, UUID.randomUUID().toString(), "Cloud book", null, null, true, 1, 1,
            )
            val localBook = LocalBook(UUID.randomUUID().toString(), "Local book", null, 1)
            val oldShelf = LocalShelf(UUID.randomUUID().toString(), "Fiction", "fiction")
            val fiction = RemoteShelf(UUID.randomUUID().toString(), "Fiction")
            val history = RemoteShelf(UUID.randomUUID().toString(), "History")
            val empty = RemoteShelf(UUID.randomUUID().toString(), "Empty")
            dao.addBook(localBook)
            dao.addShelf(oldShelf)
            dao.addMembership(LocalShelfBook(oldShelf.id, localBook.id))
            val remote = RemoteShelfSnapshot(
                listOf(fiction, history, empty),
                mapOf(cloudBook.remoteBookId to setOf(fiction.id, history.id)),
            )
            dao.replaceRemoteCatalog(profile.id, listOf(cloudBook), emptyList())
            dao.replaceRemoteShelves(profile.id, remote)

            val folders = dao.observeLibraryShelves().first().associateBy { it.shelf.serverShelfId }
            assertEquals(setOf(fiction.id, history.id, empty.id), folders.keys)
            assertEquals(setOf(localBook.id, cloudBook.remoteBookId), folders.getValue(fiction.id).bookIds.toSet())
            assertEquals(listOf(cloudBook.remoteBookId), folders.getValue(history.id).bookIds)
            assertEquals(emptyList<String>(), folders.getValue(empty.id).bookIds)

            dao.removeMembership(serverShelfKey(profile.id, fiction.id), cloudBook.remoteBookId)
            val bookId = cloudBook.remoteBookId
            val mutationId = UUID.randomUUID().toString()
            dao.queueBookEdit(PendingBookEdit(
                bookId, profile.id, bookId, mutationId,
                """{"shelves":["History"],"title":"Pending title"}""",
                """{"shelves":["Fiction","History"]}""",
                null, null,
            ))
            dao.replaceRemoteShelves(profile.id, remote, preserveShelfEditsFor = setOf(cloudBook.remoteBookId))
            assertEquals(listOf(serverShelfKey(profile.id, history.id)), dao.shelfIdsForBook(cloudBook.remoteBookId))

            val renamed = history.copy(name = "World history")
            val updated = RemoteShelfSnapshot(listOf(renamed), mapOf(bookId to setOf(history.id)))
            dao.replaceRemoteShelves(profile.id, updated, preserveShelfEditsFor = setOf(bookId))
            val refreshed = dao.observeLibraryShelves().first().associateBy { it.shelf.id }
            val historyKey = serverShelfKey(profile.id, history.id)
            assertEquals(setOf(historyKey), refreshed.keys)
            assertEquals(renamed.name, refreshed.getValue(historyKey).shelf.name)
            assertEquals(emptyList<String>(), dao.shelfIdsForBook(localBook.id))
            assertEquals(localBook, dao.book(localBook.id))
            assertEquals(listOf(historyKey), dao.shelfIdsForBook(bookId))
            val pending = requireNotNull(dao.pendingBookEdit(bookId))
            assertEquals("""{"shelves":["World history"],"title":"Pending title"}""", pending.patchJson)
            assertEquals("""{"shelves":["World history"]}""", pending.baselineJson)
            assertTrue(pending.mutationId != mutationId)
        } finally {
            database.close()
        }
    }
}
