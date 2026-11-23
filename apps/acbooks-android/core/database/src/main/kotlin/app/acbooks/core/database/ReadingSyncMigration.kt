package app.acbooks.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration8To9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reading_positions ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE reading_positions ADD COLUMN cloudRevision INTEGER")
        db.execSQL("""
            CREATE TABLE cloud_reading_positions (
                profileId TEXT NOT NULL, fileId TEXT NOT NULL, sha256 TEXT, revision INTEGER NOT NULL,
                locator TEXT, percent REAL, updatedAt INTEGER NOT NULL,
                PRIMARY KEY(profileId, fileId),
                FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE reading_position_outbox (
                profileId TEXT NOT NULL, localFileId TEXT NOT NULL, remoteFileId TEXT NOT NULL, sha256 TEXT NOT NULL,
                mutationId TEXT NOT NULL, baseRevision INTEGER NOT NULL, localRevision INTEGER NOT NULL,
                locator TEXT NOT NULL, percent REAL,
                PRIMARY KEY(profileId, localFileId),
                FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(localFileId) REFERENCES book_files(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_reading_position_outbox_localFileId ON reading_position_outbox(localFileId)")
        // v8 consumed the change stream without projecting reading positions.
        db.execSQL("DELETE FROM sync_state")
    }
}
