package app.acbooks.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration9To10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE remote_catalog ADD COLUMN detailsJson TEXT")
        db.execSQL("""
            CREATE TABLE cloud_reader_records (
                profileId TEXT NOT NULL, kind TEXT NOT NULL, id TEXT NOT NULL, revision INTEGER NOT NULL,
                remoteBookId TEXT, payload TEXT, PRIMARY KEY(profileId, kind, id),
                FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_cloud_reader_records_profileId_remoteBookId ON cloud_reader_records(profileId, remoteBookId)")
        db.execSQL("""
            CREATE TABLE cloud_downloads (
                profileId TEXT NOT NULL, bookId TEXT NOT NULL, remoteFileId TEXT NOT NULL,
                PRIMARY KEY(profileId, bookId),
                FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_cloud_downloads_bookId ON cloud_downloads(bookId)")
        db.execSQL("DELETE FROM sync_state")
    }
}
