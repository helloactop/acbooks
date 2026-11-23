package app.acbooks.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration7To8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_state (
                profileId TEXT NOT NULL PRIMARY KEY, cursor INTEGER NOT NULL,
                FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_baselines (
                profileId TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL,
                revision INTEGER NOT NULL, deleted INTEGER NOT NULL,
                PRIMARY KEY(profileId, entityType, entityId),
                FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_outbox (
                profileId TEXT NOT NULL, id TEXT NOT NULL, bookId TEXT NOT NULL, fileId TEXT,
                payload TEXT, lastError TEXT, blocked INTEGER NOT NULL,
                PRIMARY KEY(profileId, id),
                FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(fileId) REFERENCES book_files(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_bookId ON sync_outbox(bookId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_fileId ON sync_outbox(fileId)")
    }
}
