package app.acbooks.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration10To11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN publisher TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN publicationDate TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN editedCoverKey TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN metadataEdited INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE shelves ADD COLUMN serverNameKey TEXT")
        db.execSQL("ALTER TABLE shelves ADD COLUMN serverProfileId TEXT")
        db.execSQL("ALTER TABLE shelves ADD COLUMN serverShelfId TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_book_edits (
                bookId TEXT NOT NULL, profileId TEXT NOT NULL, remoteBookId TEXT NOT NULL,
                mutationId TEXT NOT NULL, patchJson TEXT NOT NULL, baselineJson TEXT,
                coverKey TEXT, baseCoverVersion INTEGER, PRIMARY KEY(bookId, profileId),
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}
