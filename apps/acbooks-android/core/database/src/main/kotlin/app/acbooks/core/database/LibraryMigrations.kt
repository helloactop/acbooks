package app.acbooks.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reading_positions (fileId TEXT NOT NULL PRIMARY KEY, locator TEXT NOT NULL, " +
                "FOREIGN KEY(fileId) REFERENCES book_files(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
    }
}

internal val migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cloud_book_links (" +
                "localBookId TEXT NOT NULL PRIMARY KEY, remoteBookId TEXT NOT NULL, " +
                "serverOrigin TEXT NOT NULL, FOREIGN KEY(localBookId) REFERENCES books(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
    }
}

internal val libraryMigrations = arrayOf(
    migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8,
    migration8To9, migration9To10,
)
