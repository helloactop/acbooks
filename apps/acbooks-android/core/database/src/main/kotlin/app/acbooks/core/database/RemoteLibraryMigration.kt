package app.acbooks.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

internal val migration6To7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE book_files ADD COLUMN localCopyRemoved INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            CREATE TABLE server_profiles (
                id TEXT NOT NULL PRIMARY KEY, serverInstanceId TEXT, accountId TEXT, deviceId TEXT,
                origin TEXT, updatedAt INTEGER NOT NULL, verification TEXT NOT NULL, legacyOriginFingerprint TEXT, currentSlot INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX index_server_profiles_serverInstanceId_accountId ON server_profiles(serverInstanceId, accountId)")
        db.execSQL("CREATE UNIQUE INDEX index_server_profiles_currentSlot ON server_profiles(currentSlot)")
        db.execSQL("""
            CREATE TABLE remote_catalog (
                profileId TEXT NOT NULL, remoteBookId TEXT NOT NULL, title TEXT NOT NULL, author TEXT, description TEXT,
                available INTEGER NOT NULL, updatedAt INTEGER NOT NULL, cachedAt INTEGER NOT NULL, coverUpdatedAt INTEGER,
                PRIMARY KEY(profileId, remoteBookId),
                FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_remote_catalog_profileId_updatedAt_remoteBookId ON remote_catalog(profileId, updatedAt, remoteBookId)")
        db.execSQL("""
            CREATE TABLE remote_catalog_files (
                profileId TEXT NOT NULL, remoteFileId TEXT NOT NULL, remoteBookId TEXT NOT NULL, format TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL, sha256 TEXT, fileStatus TEXT NOT NULL, cachedAt INTEGER NOT NULL,
                PRIMARY KEY(profileId, remoteFileId),
                FOREIGN KEY(profileId, remoteBookId) REFERENCES remote_catalog(profileId, remoteBookId)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_remote_catalog_files_profileId_remoteBookId ON remote_catalog_files(profileId, remoteBookId)")
        db.execSQL("""
            CREATE TABLE remote_book_links (
                profileId TEXT NOT NULL, localBookId TEXT NOT NULL, remoteBookId TEXT NOT NULL,
                PRIMARY KEY(profileId, localBookId),
                FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(localBookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_remote_book_links_localBookId ON remote_book_links(localBookId)")
        db.execSQL("CREATE INDEX index_remote_book_links_profileId_remoteBookId ON remote_book_links(profileId, remoteBookId)")
        db.execSQL("""
            CREATE TABLE remote_file_links (
                profileId TEXT NOT NULL, localFileId TEXT NOT NULL, remoteFileId TEXT NOT NULL,
                PRIMARY KEY(profileId, localFileId),
                FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(localFileId) REFERENCES book_files(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_remote_file_links_localFileId ON remote_file_links(localFileId)")
        db.execSQL("CREATE UNIQUE INDEX index_remote_file_links_profileId_remoteFileId ON remote_file_links(profileId, remoteFileId)")
        db.migrateLegacyCloudBookLinks()
        db.installRemoteLibraryConstraints()
    }
}

private fun SupportSQLiteDatabase.migrateLegacyCloudBookLinks() {
    val profileIds = mutableMapOf<String, String>()
    query("SELECT localBookId, remoteBookId, serverOrigin FROM cloud_book_links").use { rows ->
        while (rows.moveToNext()) {
            val legacyOrigin = rows.getString(2)
            val profileId = profileIds.getOrPut(legacyOrigin) {
                val id = UUID.randomUUID().toString()
                val origin = legacyOrigin.takeIf(::isServerOriginSafe)
                val fingerprint = if (origin == null) {
                    MessageDigest.getInstance("SHA-256").digest(legacyOrigin.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                } else null
                execSQL(
                    "INSERT INTO server_profiles (id, origin, updatedAt, verification, legacyOriginFingerprint) VALUES (?, ?, 0, ?, ?)",
                    arrayOf(id, origin, ProfileVerification.Unverified, fingerprint),
                )
                id
            }
            execSQL(
                "INSERT INTO remote_book_links (profileId, localBookId, remoteBookId) VALUES (?, ?, ?)",
                arrayOf(profileId, rows.getString(0), rows.getString(1)),
            )
        }
    }
    query("""
        SELECT (SELECT COUNT(*) FROM cloud_book_links) = (SELECT COUNT(*) FROM remote_book_links)
    """.trimIndent()).use { count ->
        check(count.moveToFirst() && count.getInt(0) == 1) { "Legacy cloud book links were not fully migrated" }
    }
    execSQL("DROP TABLE cloud_book_links")
}

internal fun isServerOriginSafe(origin: String): Boolean {
    val url = try {
        URI(origin)
    } catch (_: java.net.URISyntaxException) {
        return false
    }
    return url.scheme in listOf("https", "http") &&
        url.host != null && url.rawUserInfo == null && url.rawQuery == null && url.rawFragment == null &&
        url.rawPath in listOf("", "/")
}
