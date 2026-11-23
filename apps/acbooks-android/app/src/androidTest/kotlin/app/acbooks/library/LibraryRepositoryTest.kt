package app.acbooks.library

import android.content.ContentValues
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.acbooks.core.database.CloudCatalogBatch
import app.acbooks.core.database.CloudCatalogVersion
import app.acbooks.core.database.FileSourceKind
import app.acbooks.core.database.FileSourceStatus
import app.acbooks.core.database.FolderScanState
import app.acbooks.core.database.LibraryDatabase
import app.acbooks.core.database.LocalAuthorizedFolder
import app.acbooks.core.database.LocalBookmark
import app.acbooks.core.database.RemoteBookLink
import app.acbooks.core.database.RemoteCatalogBook
import app.acbooks.core.database.RemoteCatalogFile
import app.acbooks.core.database.RemoteFileLink
import app.acbooks.core.database.RemoteFileStatus
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

class LibraryRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val sources = mutableListOf<Uri>()
    private lateinit var root: File
    private lateinit var database: LibraryDatabase
    private lateinit var repository: LibraryRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory(context.cacheDir.toPath(), "library-room-test-").toFile()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val storageContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = root
        }
        repository = LibraryRepository(storageContext, database.library())
    }

    @After
    fun tearDown() {
        database.close()
        sources.forEach { resolver.delete(it, null, null) }
        root.deleteRecursively()
    }

    @Test
    fun importingAndDeletingSharedBooksKeepsTheLastReferenceReadable() = runBlocking {
        val pdf = pdfBytes()
        val firstSource = addSource(pdf)
        val secondSource = addSource(pdf)
        assertEquals(ImportOutcome.Added, repository.import(firstSource, allowMatchingContent = false))
        val firstBook = repository.books.first().single()
        val firstFile = requireNotNull(repository.openBook(firstBook.id))
        val cover = requireNotNull(repository.coverFile(firstBook.sha256))
        cover.parentFile!!.mkdirs()
        cover.writeText("shared cover")

        assertEquals(ImportOutcome.Duplicate, repository.import(firstSource, allowMatchingContent = true))
        assertEquals(ImportOutcome.MatchingContent, repository.import(secondSource, allowMatchingContent = false))
        assertEquals(listOf(firstBook.id), repository.books.first().map { it.id })
        assertEquals(ImportOutcome.Added, repository.import(secondSource, allowMatchingContent = true))

        val remainingBook = repository.books.first().single { it.id != firstBook.id }
        val remainingFile = requireNotNull(repository.openBook(remainingBook.id))
        UUID.fromString(firstBook.id)
        UUID.fromString(remainingBook.id)
        assertNotEquals(firstFile.id, remainingFile.id)
        assertEquals(firstFile.file, remainingFile.file)
        assertEquals(firstBook.sha256, remainingBook.sha256)
        repository.saveReadingPosition(firstFile.id, """{"page":0}""")
        repository.saveReadingPosition(remainingFile.id, """{"page":1}""")

        assertTrue(repository.deleteLocalBook(firstBook.id))
        assertFalse(repository.deleteLocalBook(firstBook.id))
        assertNull(repository.readingPosition(firstFile.id))
        assertEquals("""{"page":1}""", repository.readingPosition(remainingFile.id))
        assertArrayEquals(pdf, requireNotNull(repository.openBook(remainingBook.id)).file.readBytes())
        assertTrue(cover.isFile)

        assertTrue(repository.deleteLocalBook(remainingBook.id))
        assertFalse(remainingFile.file.exists())
        assertFalse(cover.exists())
        assertTrue(repository.books.first().isEmpty())
        assertArrayEquals(pdf, resolver.openInputStream(firstSource)!!.use { it.readBytes() })
        assertArrayEquals(pdf, resolver.openInputStream(secondSource)!!.use { it.readBytes() })
    }

    @Test
    fun removingBackedUpBooksKeepsCloudCardsAndReadingPositionsWithoutRemovingSharedFilesEarly() = runBlocking {
        val pdf = pdfBytes()
        val source = addSource(pdf)
        val otherSource = addSource(pdf)
        repository.import(source, allowMatchingContent = false)
        val book = repository.books.first().single()
        repository.import(otherSource, allowMatchingContent = true)
        val otherBook = repository.books.first().single { it.id != book.id }
        val local = requireNotNull(repository.openBook(book.id))
        val other = requireNotNull(repository.openBook(otherBook.id))
        assertEquals(local.file, other.file)
        val dao = database.library()
        val accountId = UUID.randomUUID().toString()
        val profile = dao.saveCurrentServerProfile(accountId, accountId, UUID.randomUUID().toString(), "https://example.invalid", 1)
        for ((card, file) in listOf(book to local, otherBook to other)) {
            dao.linkRemoteBook(RemoteBookLink(profile.id, card.id, card.id))
            val remote = RemoteCatalogFile(profile.id, UUID.randomUUID().toString(), card.id, "pdf",
                pdf.size.toLong(), card.sha256, RemoteFileStatus.Present, 2)
            dao.cacheRemoteBooks(listOf(RemoteCatalogBook(profile.id, card.id, card.title, null, null, true, 2, 2)))
            dao.cacheRemoteFiles(listOf(remote))
            dao.linkRemoteFile(RemoteFileLink(profile.id, file.id, remote.remoteFileId))
        }
        repository.saveReadingPosition(local.id, """{"page":1}""", 50.0)
        assertTrue(repository.deleteLocalBook(book.id))
        assertNull(repository.openBook(book.id))
        val cloudCard = repository.books.first().single { it.id == book.id }
        assertTrue(cloudCard.cloudBacked && cloudCard.canDownload && !cloudCard.hasLocalCopy)
        assertArrayEquals(pdf, requireNotNull(repository.openBook(otherBook.id)).file.readBytes())
        assertTrue(repository.deleteLocalBook(otherBook.id))
        assertFalse(local.file.exists())
        assertEquals("""{"page":1}""", repository.readingPosition(local.id))
        assertEquals(2, repository.books.first().size)
        assertArrayEquals(pdf, requireNotNull(resolver.openInputStream(source)).use { it.readBytes() })
    }

    @Test
    fun cloudDeletionsRemoveOnlyLinkedBooksAndFilesAndKeepSharedBytesUntilTheLastReference() = runBlocking {
        val pdf = pdfBytes()
        val firstSource = addSource(pdf)
        val secondSource = addSource(pdf)
        assertEquals(ImportOutcome.Added, repository.import(firstSource, allowMatchingContent = false))
        val firstBook = repository.books.first().single()
        assertEquals(ImportOutcome.Added, repository.import(secondSource, allowMatchingContent = true))
        val secondBook = repository.books.first().single { it.id != firstBook.id }
        val firstFile = requireNotNull(repository.openBook(firstBook.id))
        val secondFile = requireNotNull(repository.openBook(secondBook.id))
        assertEquals(firstFile.file, secondFile.file)

        val dao = database.library()
        val account = UUID.randomUUID().toString()
        val profile = dao.saveCurrentServerProfile(account, account, UUID.randomUUID().toString(), "https://example.invalid", 1)
        val bookLink = RemoteBookLink(profile.id, firstBook.id, UUID.randomUUID().toString())
        val fileLink = RemoteFileLink(profile.id, firstFile.id, UUID.randomUUID().toString())
        dao.linkRemoteBook(bookLink)
        dao.linkRemoteFile(fileLink)
        val cloudBook = RemoteCatalogBook(profile.id, bookLink.remoteBookId, firstBook.title, null, null, true, 2, 2)
        val cloudFile = RemoteCatalogFile(profile.id, fileLink.remoteFileId, bookLink.remoteBookId,
            "pdf", pdf.size.toLong(), firstBook.sha256, RemoteFileStatus.Present, 2)
        repository.applyCloudCatalog(profile.id, null, true, CloudCatalogBatch(3, listOf(cloudBook), listOf(cloudFile), listOf(
            CloudCatalogVersion(profile.id, "book", bookLink.remoteBookId, 2, false),
            CloudCatalogVersion(profile.id, "book_file", fileLink.remoteFileId, 3, false),
        )))
        val locator = """{"page":1}"""
        val bookmark = LocalBookmark(UUID.randomUUID().toString(), firstBook.id, firstFile.id, locator, null, 2, 2)
        dao.insertBookmark(bookmark)
        repository.saveReadingPosition(firstFile.id, locator, 50.0)

        repository.applyCloudCatalog(profile.id, 3, false, CloudCatalogBatch(4, emptyList(), emptyList(), listOf(
            CloudCatalogVersion(profile.id, "book_file", fileLink.remoteFileId, 4, true),
        )))

        assertNull(dao.bookFile(firstFile.id))
        assertNull(dao.remoteFileLink(profile.id, firstFile.id))
        assertTrue(dao.cloudFilesForBook(profile.id, firstBook.id).isEmpty())
        assertNull(repository.readingPosition(firstFile.id))
        assertEquals(bookmark.copy(fileId = null), dao.bookmark(bookmark.id))
        assertEquals(cloudBook, dao.remoteCatalogBook(profile.id, bookLink.remoteBookId))
        assertArrayEquals(pdf, secondFile.file.readBytes())

        repository.applyCloudCatalog(profile.id, 4, false, CloudCatalogBatch(5, emptyList(), emptyList(), listOf(
            CloudCatalogVersion(profile.id, "book", bookLink.remoteBookId, 5, true),
        )))
        repository.applyCloudCatalog(profile.id, 5, false, CloudCatalogBatch(5, emptyList(), emptyList(), emptyList()))
        assertNull(dao.book(firstBook.id))
        assertNull(dao.bookmark(bookmark.id))
        assertEquals(secondBook.id, repository.books.first().single().id)
        assertArrayEquals(pdf, secondFile.file.readBytes())

        val secondLink = RemoteBookLink(profile.id, secondBook.id, UUID.randomUUID().toString())
        dao.linkRemoteBook(secondLink)
        repository.applyCloudCatalog(profile.id, 5, false, CloudCatalogBatch(6, emptyList(), emptyList(), listOf(
            CloudCatalogVersion(profile.id, "book", secondLink.remoteBookId, 6, true),
        )))
        assertTrue(repository.books.first().isEmpty())
        assertFalse(secondFile.file.exists())
        for (source in listOf(firstSource, secondSource)) {
            assertArrayEquals(pdf, requireNotNull(resolver.openInputStream(source)).use { it.readBytes() })
        }
    }

    @Test
    fun anOfflineImportQueuesTheBookRequestAndItsFileBeforeAnyNetworkCall() = runBlocking {
        val dao = database.library()
        val account = UUID.randomUUID().toString()
        val profile = dao.saveCurrentServerProfile(account, account, UUID.randomUUID().toString(), "https://example.invalid", 1)
        val source = addSource(pdfBytes())
        assertEquals(ImportOutcome.Added, repository.import(source, allowMatchingContent = false))
        val book = repository.books.first().single()
        val file = requireNotNull(repository.openBook(book.id))

        val queue = dao.pendingCloudUploads(profile.id)
        assertEquals(2, queue.size)
        val payload = JSONObject(requireNotNull(queue[0].payload))
        assertEquals("book.create", payload.getString("action"))
        assertEquals(book.id, payload.getString("entityId"))
        assertEquals(0, payload.getInt("baseRevision"))
        assertEquals(book.title, payload.getJSONObject("data").getString("title"))
        assertEquals(file.id, queue[1].fileId)
        assertEquals(ImportOutcome.Duplicate, repository.import(source, allowMatchingContent = true))
        assertEquals(queue, dao.pendingCloudUploads(profile.id))
    }

    @Test
    fun deletingTheOriginalDuringImportDoesNotRemoveTheNewReference() = runBlocking {
        val pdf = pdfBytes()
        val originalSource = addSource(pdf)
        val replacementSource = addSource(pdf)
        assertEquals(ImportOutcome.Added, repository.import(originalSource, allowMatchingContent = false))
        val original = repository.books.first().single()

        coroutineScope {
            val importing = async { repository.import(replacementSource, allowMatchingContent = true) }
            val deleting = async { repository.deleteLocalBook(original.id) }
            assertEquals(ImportOutcome.Added, importing.await())
            assertTrue(deleting.await())
        }

        val replacement = repository.books.first().single()
        assertNotEquals(original.id, replacement.id)
        assertArrayEquals(pdf, requireNotNull(repository.openBook(replacement.id)).file.readBytes())
    }

    @Test
    fun lostTreeGrantAndCacheEvictionKeepBookAndReadableSafOriginal() = runBlocking {
        val pdf = pdfBytes()
        val source = addSource(pdf)
        assertEquals(ImportOutcome.Added, repository.import(source, allowMatchingContent = false))
        val book = repository.books.first().single()
        val hash = requireNotNull(book.sha256)
        val originalCopy = requireNotNull(repository.openBook(book.id))
        val locator = """{"page":1}"""
        repository.saveReadingPosition(originalCopy.id, locator, 50.0)
        val dao = database.library()
        val sources = dao.fileSources(hash, "pdf")
        val external = sources.single { it.kind == FileSourceKind.External }
        assertEquals(FileSourceStatus.Present, repository.refreshExternalSource(external))

        val authority = "com.android.externalstorage.documents"
        val documentId = "primary:acbooks-test-${UUID.randomUUID()}"
        val tree = DocumentsContract.buildTreeDocumentUri(authority, documentId)
        val folder = LocalAuthorizedFolder(
            UUID.randomUUID().toString(), tree.toString(), authority, documentId, null, FolderScanState.Idle, null, null, 1000,
        )
        dao.saveAuthorizedFolder(folder)
        val ungrantedUri = DocumentsContract.buildDocumentUriUsingTree(tree, "$documentId/test.pdf").toString()
        dao.recordFileSource(hash, "pdf", FileSourceKind.External, ungrantedUri, FileSourceStatus.Unknown, 1000, folder.id)
        val ungranted = dao.fileSources(hash, "pdf").single { it.location == ungrantedUri }
        assertFalse(repository.refreshFolderGrant(folder.id))
        assertEquals(FileSourceStatus.PermissionRequired, repository.refreshExternalSource(ungranted))
        assertEquals(FolderScanState.PermissionRequired, dao.authorizedFolder(folder.id)?.scanState)
        assertArrayEquals(pdf, requireNotNull(repository.openBook(book.id)).file.readBytes())

        assertTrue(repository.removeLocalCopy(hash, "pdf"))
        assertFalse(originalCopy.file.exists())
        assertNull(repository.openBook(book.id))
        val statuses = dao.fileSources(hash, "pdf")
        assertEquals(FileSourceStatus.Missing, statuses.single { it.kind == FileSourceKind.Managed }.status)
        assertEquals(FileSourceStatus.PermissionRequired, statuses.single { it.location == ungrantedUri }.status)
        assertEquals(FileSourceStatus.Present, statuses.single { it.location == source.toString() }.status)
        assertArrayEquals(pdf, requireNotNull(resolver.openInputStream(source)).use { it.readBytes() })
        assertEquals(locator, repository.readingPosition(originalCopy.id))
        assertEquals(listOf(book.id), repository.books.first().map { it.id })

        assertEquals(ImportOutcome.Duplicate, repository.import(source, allowMatchingContent = true))
        val recovered = requireNotNull(repository.openBook(book.id))
        assertEquals(originalCopy.id, recovered.id)
        assertArrayEquals(pdf, recovered.file.readBytes())
        assertEquals(locator, repository.readingPosition(recovered.id))
    }

    private fun addSource(pdf: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "acbooks-room-${UUID.randomUUID()}.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        sources.add(uri)
        requireNotNull(resolver.openOutputStream(uri)).use { it.write(pdf) }
        val published = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        check(resolver.update(uri, published, null, null) == 1)
        return uri
    }

    private fun pdfBytes(): ByteArray {
        val result = ByteArrayOutputStream()
        val document = PdfDocument()
        try {
            for (pageNumber in 1..2) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(72, 100, pageNumber).create())
                page.canvas.drawColor(Color.WHITE)
                document.finishPage(page)
            }
            document.writeTo(result)
        } finally {
            document.close()
        }
        return result.toByteArray()
    }
}
