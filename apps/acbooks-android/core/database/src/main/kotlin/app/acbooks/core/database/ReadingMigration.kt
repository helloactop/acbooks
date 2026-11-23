package app.acbooks.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reading_positions ADD COLUMN percent REAL")
        db.execSQL("ALTER TABLE reading_positions ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reading_positions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

        db.execSQL("""
            CREATE TABLE reading_sessions (
                id TEXT NOT NULL,
                bookId TEXT NOT NULL,
                fileId TEXT,
                deviceId TEXT,
                startedAt INTEGER NOT NULL,
                endedAt INTEGER,
                activeSeconds INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(fileId) REFERENCES book_files(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_reading_sessions_bookId_startedAt_id ON reading_sessions (bookId, startedAt, id)")
        db.execSQL("CREATE INDEX index_reading_sessions_fileId ON reading_sessions (fileId)")
        db.execSQL("CREATE INDEX index_reading_sessions_startedAt ON reading_sessions (startedAt)")

        db.execSQL("""
            CREATE TABLE bookmarks (
                id TEXT NOT NULL,
                bookId TEXT NOT NULL,
                fileId TEXT,
                locator TEXT,
                label TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(fileId) REFERENCES book_files(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_bookmarks_bookId_createdAt_id ON bookmarks (bookId, createdAt, id)")
        db.execSQL("CREATE INDEX index_bookmarks_fileId ON bookmarks (fileId)")

        db.execSQL("""
            CREATE TABLE annotations (
                id TEXT NOT NULL,
                bookId TEXT NOT NULL,
                fileId TEXT,
                locator TEXT,
                quote TEXT,
                note TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(fileId) REFERENCES book_files(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_annotations_bookId_createdAt_id ON annotations (bookId, createdAt, id)")
        db.execSQL("CREATE INDEX index_annotations_fileId ON annotations (fileId)")

        db.installReadingConstraints()
    }
}
