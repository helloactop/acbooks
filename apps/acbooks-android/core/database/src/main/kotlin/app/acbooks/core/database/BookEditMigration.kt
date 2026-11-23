package app.acbooks.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration7To8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN publisher TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN publicationDate TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN editedCoverKey TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN metadataEdited INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE remote_catalog ADD COLUMN revision INTEGER")
        db.execSQL("ALTER TABLE remote_catalog ADD COLUMN metadataJson TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_book_edits (
                bookId TEXT NOT NULL PRIMARY KEY, profileId TEXT NOT NULL, remoteBookId TEXT NOT NULL,
                mutationId TEXT NOT NULL, patchJson TEXT NOT NULL, baselineJson TEXT,
                coverKey TEXT, baseCoverVersion INTEGER,
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}
