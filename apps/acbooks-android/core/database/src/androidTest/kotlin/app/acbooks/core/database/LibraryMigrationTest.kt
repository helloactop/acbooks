package app.acbooks.core.database

import androidx.room.Room
import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, LibraryDatabase::class.java)

    @After
    fun removeTestDatabase() {
        context.deleteDatabase(DatabaseName)
    }

    @Test
    fun version1RunsAllMigrationsAndPreservesTheLibrary() {
        migrateAndVerify(startVersion = 1, expectedLocator = null)
    }

    @Test
    fun version2PreservesTheLibraryAndReadingPosition() {
        migrateAndVerify(startVersion = 2, expectedLocator = """{"page":7}""")
    }

    @Test
    fun version3PreservesOpaqueLocatorsAndCloudLinks() {
        migrateAndVerify(startVersion = 3, expectedLocator = "legacy opaque locator")
    }

    @Test
    fun version4PreservesReadingRecordsWhenAddingCatalogRelations() {
        migrateAndVerify(startVersion = 4, expectedLocator = """{"page":7}""")
    }

    @Test
    fun version5KeepsLegacyUrisAndConsolidatesOnlyTheManagedCopy() {
        val otherBook = "63fcc9dd-73dc-44cf-a9ef-270ded89d604"
        val otherFile = "44758823-b86c-4224-9a9c-5382532caf33"
        val otherUri = "content://acbooks.test/Another%2fBook.PDF"
        val locator = """{"page":12}"""
        helper.createDatabase(DatabaseName, 5).use { old ->
            old.execSQL("INSERT INTO books(id,title,importedAt,description) VALUES(?, 'Book', 1000, 'description')", arrayOf(BookId))
            old.execSQL("INSERT INTO books(id,title,importedAt) VALUES(?, 'Other', 1000)", arrayOf(otherBook))
            old.execSQL("INSERT INTO file_objects VALUES (?, 'pdf', 100)", arrayOf(Hash))
            old.execSQL("INSERT INTO book_files VALUES (?, ?, ?, 'pdf', ?, 'old.pdf')", arrayOf(FileId, BookId, Hash, SourceUri))
            old.execSQL("INSERT INTO book_files VALUES (?, ?, ?, 'pdf', ?, 'other.pdf')", arrayOf(otherFile, otherBook, Hash, otherUri))
            old.execSQL("UPDATE books SET preferredFileId = ? WHERE id = ?", arrayOf(FileId, BookId))
            old.execSQL(
                "INSERT INTO reading_positions (fileId, locator, percent, revision, updatedAt) VALUES (?,?,40,8,2000)",
                arrayOf(FileId, locator),
            )
        }
        helper.runMigrationsAndValidate(DatabaseName, 6, true, *libraryMigrations).use { migrated ->
            assertEquals(3, migrated.rowCount("file_sources"))
            assertEquals(0, migrated.rowCount("authorized_folders"))
            assertEquals(0, migrated.rowCount("folder_entries"))
            migrated.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        runBlocking {
            val database = Room.databaseBuilder(context, LibraryDatabase::class.java, DatabaseName)
                .configureLibrary().build()
            try {
                val dao = database.library()
                assertEquals(SourceUri, dao.firstBookFile(BookId)?.sourceUri)
                assertEquals(otherUri, dao.firstBookFile(otherBook)?.sourceUri)
                assertEquals("description", dao.book(BookId)?.description)
                assertEquals(FileId, dao.book(BookId)?.preferredFileId)
                assertEquals(LocalReadingPosition(FileId, locator, 40.0, 8, 2000), dao.readingPositionRecord(FileId))
                val sources = dao.fileSources(Hash, "pdf")
                assertEquals(setOf(SourceUri, otherUri, managedObjectPath(Hash, "pdf")), sources.map { it.location }.toSet())
                assertTrue(sources.all { it.status == FileSourceStatus.Unknown && it.updatedAt == 0L })
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun version6PreservesDuplicateLegacyMappingsAndKeepsThemOutsideTheCurrentProfile() = runBlocking {
        val secondBook = "63fcc9dd-73dc-44cf-a9ef-270ded89d604"
        val thirdBook = "44758823-b86c-4224-9a9c-5382532caf33"
        helper.createDatabase(DatabaseName, 6).use { old ->
            for (id in listOf(BookId, secondBook, thirdBook)) {
                old.execSQL("INSERT INTO books(id, title, importedAt) VALUES (?, 'Legacy', 1000)", arrayOf(id))
            }
            old.execSQL("INSERT INTO cloud_book_links VALUES (?, ?, ?)", arrayOf(BookId, RemoteBookId, ServerOrigin))
            old.execSQL("INSERT INTO cloud_book_links VALUES (?, ?, ?)", arrayOf(secondBook, RemoteBookId, ServerOrigin))
            old.execSQL(
                "INSERT INTO cloud_book_links VALUES (?, ?, ?)",
                arrayOf(thirdBook, "opaque-old-id", "https://example.invalid?access_token=legacy-value"),
            )
            old.execSQL("INSERT INTO file_objects VALUES (?, 'pdf', 123)", arrayOf(Hash))
            old.execSQL("INSERT INTO book_files VALUES (?, ?, ?, 'pdf', ?, 'Legacy.pdf')", arrayOf(FileId, BookId, Hash, SourceUri))
            old.execSQL(
                "INSERT INTO file_sources (sha256, format, kind, location, status, updatedAt) VALUES (?, 'pdf', 'external', ?, 'present', 2000)",
                arrayOf(Hash, SourceUri),
            )
            old.execSQL("INSERT INTO reading_positions VALUES (?, 'legacy opaque locator', 36.5, 3, 2000)", arrayOf(FileId))
        }
        helper.runMigrationsAndValidate(DatabaseName, 7, true, migration6To7).use { upgraded ->
            assertEquals(3, upgraded.rowCount("remote_book_links"))
            assertEquals(2, upgraded.rowCount("server_profiles"))
            upgraded.query("SELECT 1 FROM sqlite_master WHERE name = 'cloud_book_links'").use {
                assertFalse(it.moveToFirst())
            }
            upgraded.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        val database = Room.databaseBuilder(context, LibraryDatabase::class.java, DatabaseName).configureLibrary().build()
        try {
            val dao = database.library()
            assertEquals(BookId, dao.book(BookId)?.id)
            assertEquals(LocalReadingPosition(FileId, "legacy opaque locator", 36.5, 3, 2000), dao.readingPositionRecord(FileId))
            assertEquals(SourceUri, dao.fileSources(Hash, "pdf").single().location)
            assertNull(dao.currentServerProfile())
            val profiles = dao.observeServerProfiles().first()
            val legacy = profiles.single { it.origin == ServerOrigin }
            assertEquals(ProfileVerification.Unverified, legacy.verification)
            assertNull(legacy.serverInstanceId)
            assertNull(legacy.accountId)
            assertNull(legacy.deviceId)
            assertEquals(setOf(BookId, secondBook), dao.localBookLinks(legacy.id, RemoteBookId).map { it.localBookId }.toSet())
            val redacted = profiles.single { it.origin == null }
            assertEquals(64, redacted.legacyOriginFingerprint?.length)
            assertEquals(RemoteBookLink(redacted.id, thirdBook, "opaque-old-id"), dao.remoteBookLink(redacted.id, thirdBook))
            val current = dao.saveCurrentServerProfile("confirmed-instance", RecordId, FileId, ServerOrigin, 3000)
            assertEquals(current.id, dao.currentServerProfile()?.id)
            assertNull(dao.verifiedRemoteBookLink(BookId, "confirmed-instance", RecordId, ServerOrigin))
            assertTrue(dao.observeRemoteCatalog(current.id, 10).first().isEmpty())
            assertTrue(runCatching {
                dao.linkRemoteBook(RemoteBookLink(legacy.id, BookId, RemoteBookId))
            }.exceptionOrNull() is SQLiteConstraintException)
        } finally {
            database.close()
        }
    }

    @Test
    fun version7PreservesCloudLinksAndInitializesSyncStorageOnUpgrade() = runBlocking {
        val profileId = ShelfId
        helper.createDatabase(DatabaseName, 7).use { old ->
            old.execSQL("INSERT INTO books(id, title, importedAt) VALUES (?, 'Existing', 1000)", arrayOf(BookId))
            old.execSQL("INSERT INTO file_objects VALUES (?, 'pdf', 123)", arrayOf(Hash))
            old.execSQL("INSERT INTO book_files VALUES (?, ?, ?, 'pdf', ?, 'book.pdf', 0)", arrayOf(FileId, BookId, Hash, SourceUri))
            old.execSQL("INSERT INTO reading_positions VALUES (?, 'locator', 50, 2, 1000)", arrayOf(FileId))
            old.execSQL("""
                INSERT INTO server_profiles (id,serverInstanceId,accountId,deviceId,origin,updatedAt,verification,currentSlot)
                VALUES (?, ?, ?, ?, ?, 1000, 'verified', 1)
            """.trimIndent(), arrayOf(profileId, RecordId, RecordId, FileId, ServerOrigin))
            old.execSQL("INSERT INTO remote_book_links VALUES (?, ?, ?)", arrayOf(profileId, BookId, RemoteBookId))
            old.execSQL("INSERT INTO remote_file_links VALUES (?, ?, ?)", arrayOf(profileId, FileId, FileId))
            old.execSQL("""
                INSERT INTO remote_catalog (profileId,remoteBookId,title,available,updatedAt,cachedAt)
                VALUES (?, ?, 'Cloud', 1, 1000, 1000)
            """.trimIndent(), arrayOf(profileId, RemoteBookId))
        }
        helper.runMigrationsAndValidate(DatabaseName, 8, true, migration7To8).use { upgraded ->
            assertEquals(0, upgraded.rowCount("sync_state"))
            assertEquals(0, upgraded.rowCount("sync_baselines"))
            assertEquals(0, upgraded.rowCount("sync_outbox"))
            upgraded.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        val database = Room.databaseBuilder(context, LibraryDatabase::class.java, DatabaseName).configureLibrary().build()
        try {
            val dao = database.library()
            assertEquals(profileId, dao.currentServerProfile()?.id)
            assertEquals(RemoteBookLink(profileId, BookId, RemoteBookId), dao.remoteBookLink(profileId, BookId))
            assertEquals(RemoteFileLink(profileId, FileId, FileId), dao.remoteFileLink(profileId, FileId))
            assertEquals("Cloud", dao.remoteCatalogBook(profileId, RemoteBookId)?.title)
            assertEquals(LocalReadingPosition(FileId, "locator", 50.0, 2, 1000), dao.readingPositionRecord(FileId))
            assertNull(dao.cloudSyncCursor(profileId))
        } finally {
            database.close()
        }
    }

    private fun migrateAndVerify(startVersion: Int, expectedLocator: String?) = runBlocking {
        helper.createDatabase(DatabaseName, startVersion).use { old ->
            old.execSQL("INSERT INTO books VALUES (?, ?, ?, ?)", arrayOf<Any>(BookId, "旧版书籍", "作者", 9876L))
            old.execSQL("INSERT INTO file_objects VALUES (?, ?, ?)", arrayOf<Any>(Hash, "pdf", 123L))
            old.execSQL(
                "INSERT INTO book_files VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(FileId, BookId, Hash, "pdf", SourceUri, "old.pdf"),
            )
            old.execSQL("INSERT INTO shelves VALUES (?, ?, ?)", arrayOf(ShelfId, "纸质与电子", "old-shelf"))
            old.execSQL("INSERT INTO shelf_books VALUES (?, ?)", arrayOf(ShelfId, BookId))
            if (expectedLocator != null) {
                old.execSQL("INSERT INTO reading_positions (fileId, locator) VALUES (?, ?)", arrayOf(FileId, expectedLocator))
            }
            if (startVersion >= 3) {
                old.execSQL("INSERT INTO cloud_book_links VALUES (?, ?, ?)", arrayOf(BookId, RemoteBookId, ServerOrigin))
            }
            if (startVersion == 4) {
                old.execSQL("UPDATE reading_positions SET percent = 42.25, revision = 3, updatedAt = 6789")
                old.execSQL(
                    "INSERT INTO reading_sessions VALUES (?, ?, ?, NULL, 1000, 5000, 4, 'automatic')",
                    arrayOf(RecordId, BookId, FileId),
                )
                old.execSQL("INSERT INTO bookmarks VALUES (?, ?, NULL, NULL, 'mark', 1000, 2000)", arrayOf(RecordId, BookId))
                old.execSQL(
                    "INSERT INTO annotations VALUES (?, ?, ?, '{}', 'quote', 'note', 1000, 2000)",
                    arrayOf(RecordId, BookId, FileId),
                )
            }
        }

        if (startVersion == 3) {
            helper.runMigrationsAndValidate(DatabaseName, 4, true, migration3To4).use { version4 ->
                assertEquals(1, version4.rowCount("reading_positions"))
                assertEquals(0, version4.rowCount("reading_sessions"))
            }
        }

        helper.runMigrationsAndValidate(DatabaseName, 8, true, *libraryMigrations).use { upgraded ->
            assertEquals(8, upgraded.version)
            assertEquals(if (startVersion >= 3) 1 else 0, upgraded.rowCount("remote_book_links"))
            upgraded.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            upgraded.query("SELECT sha256, format, size FROM file_objects").use { objects ->
                assertEquals(1, objects.count)
                objects.moveToFirst()
                assertEquals(Hash, objects.getString(0))
                assertEquals("pdf", objects.getString(1))
                assertEquals(123L, objects.getLong(2))
            }
        }

        val database = Room.databaseBuilder(context, LibraryDatabase::class.java, DatabaseName)
            .configureLibrary()
            .build()
        try {
            val dao = database.library()
            assertEquals(listOf(LocalBookCard(BookId, "旧版书籍", "作者", 9876L, "pdf", Hash)), dao.observeBooks().first())
            assertEquals(LocalBookFile(FileId, BookId, Hash, "pdf", SourceUri, "old.pdf"), dao.firstBookFile(BookId))
            assertEquals(listOf(LocalShelf(ShelfId, "纸质与电子", "old-shelf")), dao.observeShelves().first())
            assertEquals(listOf(LocalShelfBook(ShelfId, BookId)), dao.observeMemberships().first())
            assertEquals(expectedLocator, dao.readingPosition(FileId))
            val expectedPosition = expectedLocator?.let { locator ->
                if (startVersion == 4) LocalReadingPosition(FileId, locator, 42.25, 3, 6789)
                else LocalReadingPosition(FileId, locator)
            }
            assertEquals(expectedPosition, dao.readingPositionRecord(FileId))
            val legacy = dao.observeServerProfiles().first().singleOrNull()
            assertEquals(if (startVersion >= 3) ServerOrigin else null, legacy?.origin)
            if (legacy != null) {
                assertEquals(ProfileVerification.Unverified, legacy.verification)
                assertEquals(RemoteBookLink(legacy.id, BookId, RemoteBookId), dao.remoteBookLink(legacy.id, BookId))
            }
            assertEquals(LocalBook(BookId, "旧版书籍", "作者", 9876L), dao.book(BookId))
            assertEquals(listOf(LocalBookContributorName(BookId, "作者", "author")), dao.observeBookContributors(BookId).first())
            if (startVersion == 4) {
                assertEquals(LocalReadingSession(RecordId, BookId, FileId, null, 1000, 5000, 4, "automatic"), dao.readingSession(RecordId))
                assertEquals(LocalBookmark(RecordId, BookId, null, null, "mark", 1000, 2000), dao.bookmark(RecordId))
                assertEquals(LocalAnnotation(RecordId, BookId, FileId, "{}", "quote", "note", 1000, 2000), dao.annotation(RecordId))
            }
            val verified = dao.saveCurrentServerProfile("verified-instance", RecordId, FileId, ServerOrigin, 3000)
            assertNull(dao.verifiedRemoteBookLink(BookId, "verified-instance", RecordId, ServerOrigin))
            dao.linkRemoteBook(RemoteBookLink(verified.id, BookId, RemoteBookId))
            assertEquals(RemoteBookId, dao.verifiedRemoteBookLink(BookId, "verified-instance", RecordId, ServerOrigin)?.remoteBookId)
        } finally {
            database.close()
        }
    }

    private fun SupportSQLiteDatabase.rowCount(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { rows ->
            rows.moveToFirst()
            rows.getInt(0)
        }

    private companion object {
        const val DatabaseName = "library-migration-test.db"
        const val BookId = "baa118ad-cbfd-47a2-8c8c-993af487800f"
        const val FileId = "0d988051-fa0a-49af-bf7e-16d90d6d4301"
        const val ShelfId = "646d6553-0b2f-46ae-b6d7-55abd14721ee"
        const val RemoteBookId = "c6da4079-65cc-441b-af64-caff6ed8f36f"
        const val RecordId = "c6caabda-2e5b-4497-93aa-6a57eb17151a"
        const val SourceUri = "content://acbooks.test/legacy.pdf"
        const val ServerOrigin = "https://example.invalid"
        val Hash = "a".repeat(64)
    }
}
