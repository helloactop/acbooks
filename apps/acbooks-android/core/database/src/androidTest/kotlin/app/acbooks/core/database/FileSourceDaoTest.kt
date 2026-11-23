package app.acbooks.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSourceDaoTest {
    @Test
    fun duplicateImportsShareOneManagedCopyAndKeepDistinctSources() = withDatabase { dao ->
        val first = LocalBook(uuid(), "First", null, 1000)
        val second = LocalBook(uuid(), "Second", null, 1000)
        val firstFile = bookFile(first.id, "content://test/document/one")
        val secondFile = bookFile(second.id, "content://test/document/two")

        assertEquals(BookImportCommit.Added, dao.importBook(first, firstFile, Content, false, verifiedAt = 1000))
        assertEquals(BookImportCommit.Duplicate, dao.importBook(second, firstFile.copy(id = uuid()), Content, true, verifiedAt = 1100))
        assertEquals(BookImportCommit.MatchingContent, dao.importBook(second, secondFile, Content, false))
        assertEquals(BookImportCommit.Added, dao.importBook(second, secondFile, Content, true, verifiedAt = 1200))
        dao.recordFileSource(
            Content.sha256, Content.format, FileSourceKind.Downloaded, managedObjectPath(Content.sha256, Content.format),
            FileSourceStatus.Present, 1300,
        )
        val sources = dao.observeFileSources(Content.sha256, Content.format).first()
        assertEquals(1, sources.count { it.kind == FileSourceKind.Managed })
        assertEquals(1, sources.count { it.kind == FileSourceKind.Downloaded })
        assertEquals(setOf(firstFile.sourceUri, secondFile.sourceUri), sources.filter { it.kind == FileSourceKind.External }.map { it.location }.toSet())
        assertEquals(2, dao.setLocalCopyStatus(Content.sha256, Content.format, managedObjectPath(Content.sha256, Content.format), FileSourceStatus.Missing, 1400))
        assertTrue(dao.fileSources(Content.sha256, Content.format).filter { it.kind == FileSourceKind.External }.all { it.status == FileSourceStatus.Present })
        assertEquals(LocalBookRemoval(emptyList(), emptyList()), dao.removeLocalBook(first.id))
        assertEquals(4, dao.fileSources(Content.sha256, Content.format).size)
        assertNotNull(dao.removeLocalBook(second.id))
        assertTrue(dao.fileSources(Content.sha256, Content.format).isEmpty())
    }

    @Test
    fun scansPreserveIdentityAndPermissionLossDoesNotMarkFilesMissing() = withDatabase { dao ->
        val book = LocalBook(uuid(), "Reading", null, 1000)
        val file = bookFile(book.id, "content://test/document/one")
        dao.importBook(book, file, Content, false, verifiedAt = 1000)
        dao.writeReadingPosition(file.id, """{"page":2}""", 50.0, 1000)
        val outer = folder("root")
        val inner = folder("sub")
        dao.saveAuthorizedFolder(outer)
        dao.saveAuthorizedFolder(inner)
        assertEquals(outer, dao.authorizedFolderByDocument("test", "root"))

        val scanned = ScannedDocument("one", "content://test/tree/root/document/one", "One.pdf", 120, 1100)
        assertEquals(1, dao.startFolderScan(outer.id, 1200))
        assertTrue(dao.completeFolderScan(outer.id, 1200, 1300, listOf(scanned)))
        assertEquals(1, dao.startFolderScan(inner.id, 1200))
        assertTrue(dao.completeFolderScan(inner.id, 1200, 1300, listOf(scanned.copy(documentUri = "content://test/tree/sub/document/one"))))
        assertEquals(2, dao.entriesForDocument("test", "one").size)

        assertFalse(dao.attachVerifiedFolderDocument(outer.id, "one", file.id, "different-hash", "pdf", 1200, 1350))
        assertFalse(dao.attachVerifiedFolderDocument(outer.id, "one", file.id, Content.sha256, Content.format, 1100, 1350))
        assertTrue(dao.attachVerifiedFolderDocument(outer.id, "one", file.id, Content.sha256, Content.format, 1200, 1350))
        dao.setDocumentIgnored(outer.id, "one", true)
        assertEquals(1, dao.startFolderScan(outer.id, 1400))
        assertTrue(dao.completeFolderScan(outer.id, 1400, 1500, listOf(scanned.copy(displayName = "Renamed.pdf"))))
        val entry = dao.observeFolderEntries(outer.id).first().single()
        assertEquals("Renamed.pdf", entry.displayName)
        assertEquals(file.id, entry.importedFileId)
        assertTrue(entry.ignored)
        assertEquals(file.sourceUri, dao.firstBookFile(book.id)?.sourceUri)

        assertEquals(1, dao.startFolderScan(outer.id, 1600))
        dao.updateFolderGrant(outer.id, granted = false, checkedAt = 1700)
        assertEquals(FolderScanState.PermissionRequired, dao.authorizedFolder(outer.id)?.scanState)
        assertEquals(FileSourceStatus.PermissionRequired, sourceFor(dao, outer.id).status)
        assertFalse(dao.completeFolderScan(outer.id, 1600, 1800, emptyList()))
        assertFalse(dao.folderEntry(outer.id, "one")!!.missing)
        assertEquals(FileSourceStatus.Present, dao.fileSources(Content.sha256, Content.format).single { it.kind == FileSourceKind.Managed }.status)

        dao.updateFolderGrant(outer.id, granted = true, checkedAt = 1900)
        assertEquals(FileSourceStatus.Unknown, sourceFor(dao, outer.id).status)
        assertEquals(1, dao.startFolderScan(outer.id, 2000))
        assertEquals(1, dao.setFolderScanFailed(outer.id, 2000, 2050))
        assertFalse(dao.folderEntry(outer.id, "one")!!.missing)
        assertEquals(1, dao.startFolderScan(outer.id, 2100))
        assertTrue(dao.completeFolderScan(outer.id, 2100, 2200, emptyList()))
        assertTrue(dao.folderEntry(outer.id, "one")!!.missing)
        assertEquals(FileSourceStatus.Missing, sourceFor(dao, outer.id).status)
        assertEquals(1, dao.unregisterFolder(outer.id, 2300))
        assertNull(dao.folderEntry(outer.id, "one"))
        val detached = dao.fileSources(Content.sha256, Content.format).single { it.location == scanned.documentUri }
        assertNull(detached.folderId)
        assertEquals(FileSourceStatus.PermissionRequired, detached.status)
        assertEquals("""{"page":2}""", dao.readingPosition(file.id))
        assertNotNull(dao.folderEntry(inner.id, "one"))
    }

    private suspend fun sourceFor(dao: LibraryDao, folderId: String) =
        dao.fileSources(Content.sha256, Content.format).single { it.folderId == folderId }

    private fun withDatabase(test: suspend (LibraryDao) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).configureLibrary().build()
        try {
            test(database.library())
        } finally {
            database.close()
        }
    }

    private fun folder(root: String) =
        LocalAuthorizedFolder(uuid(), "content://test/tree/$root", "test", root, root, FolderScanState.Idle, null, null, 1000)

    private fun bookFile(bookId: String, uri: String) =
        LocalBookFile(uuid(), bookId, Content.sha256, Content.format, uri, "book.pdf")

    private fun uuid() = UUID.randomUUID().toString()

    private companion object {
        val Content = LocalFileObject("a".repeat(64), "pdf", 120)
    }
}
