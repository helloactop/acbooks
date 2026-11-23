package app.acbooks.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN series TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN readingStatus TEXT NOT NULL DEFAULT 'unread'")
        db.execSQL("ALTER TABLE books ADD COLUMN metadataLocks INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE books ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE books ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE books ADD COLUMN preferredFileId TEXT REFERENCES book_files(id) ON DELETE SET NULL")
        db.execSQL("UPDATE books SET createdAt = importedAt, updatedAt = importedAt")
        db.execSQL("CREATE INDEX index_books_preferredFileId ON books (preferredFileId)")
        db.execSQL("CREATE INDEX index_books_createdAt_id ON books (createdAt, id)")
        db.execSQL("CREATE INDEX index_books_updatedAt_id ON books (updatedAt, id)")
        db.execSQL("CREATE INDEX index_books_readingStatus_createdAt_id ON books (readingStatus, createdAt, id)")

        db.execSQL("""
            CREATE TABLE contributors (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                PRIMARY KEY(id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_contributors_name ON contributors (name)")

        db.execSQL("""
            CREATE TABLE book_contributors (
                bookId TEXT NOT NULL,
                contributorId TEXT NOT NULL,
                role TEXT NOT NULL,
                PRIMARY KEY(bookId, contributorId, role),
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(contributorId) REFERENCES contributors(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_book_contributors_contributorId ON book_contributors (contributorId)")
        db.execSQL("CREATE INDEX index_book_contributors_bookId_role_contributorId ON book_contributors (bookId, role, contributorId)")

        db.execSQL("""
            CREATE TABLE book_identifiers (
                id TEXT NOT NULL,
                bookId TEXT NOT NULL,
                scheme TEXT NOT NULL,
                value TEXT NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX index_book_identifiers_bookId_scheme_value ON book_identifiers (bookId, scheme, value)")
        db.execSQL("CREATE INDEX index_book_identifiers_scheme_value ON book_identifiers (scheme, value)")

        db.execSQL("""
            CREATE TABLE tags (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                PRIMARY KEY(id)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX index_tags_name ON tags (name)")

        db.execSQL("""
            CREATE TABLE book_tags (
                bookId TEXT NOT NULL,
                tagId TEXT NOT NULL,
                PRIMARY KEY(bookId, tagId),
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_book_tags_tagId ON book_tags (tagId)")

        db.execSQL("""
            CREATE TABLE locations (
                id TEXT NOT NULL,
                parentId TEXT,
                name TEXT NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(parentId) REFERENCES locations(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_locations_parentId ON locations (parentId)")

        db.execSQL("""
            CREATE TABLE book_copies (
                id TEXT NOT NULL,
                bookId TEXT NOT NULL,
                locationId TEXT,
                acquiredAt INTEGER,
                note TEXT,
                PRIMARY KEY(id),
                FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(locationId) REFERENCES locations(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_book_copies_bookId ON book_copies (bookId)")
        db.execSQL("CREATE INDEX index_book_copies_locationId ON book_copies (locationId)")

        // Each legacy string is opaque and belongs to its original book.
        db.execSQL("""
            INSERT INTO contributors (id, name)
            SELECT id, author FROM books WHERE author IS NOT NULL AND trim(author, char(9, 10, 13, 32)) <> ''
        """.trimIndent())
        db.execSQL("""
            INSERT INTO book_contributors (bookId, contributorId, role)
            SELECT id, id, 'author' FROM contributors
        """.trimIndent())
        db.installReadingConstraints()
        db.installCatalogConstraints()
    }
}
