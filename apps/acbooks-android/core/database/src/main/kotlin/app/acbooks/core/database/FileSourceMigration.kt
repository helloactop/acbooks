package app.acbooks.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration5To6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE authorized_folders (
                id TEXT NOT NULL,
                treeUri TEXT NOT NULL,
                authority TEXT NOT NULL,
                rootDocumentId TEXT NOT NULL,
                displayName TEXT,
                scanState TEXT NOT NULL,
                lastScanStartedAt INTEGER,
                lastScanCompletedAt INTEGER,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX index_authorized_folders_authority_rootDocumentId ON authorized_folders (authority, rootDocumentId)")
        db.execSQL("CREATE UNIQUE INDEX index_authorized_folders_treeUri ON authorized_folders (treeUri)")

        db.execSQL("""
            CREATE TABLE file_sources (
                sha256 TEXT NOT NULL,
                format TEXT NOT NULL,
                kind TEXT NOT NULL,
                location TEXT NOT NULL,
                status TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                folderId TEXT,
                PRIMARY KEY(sha256, format, kind, location),
                FOREIGN KEY(sha256, format) REFERENCES file_objects(sha256, format) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(folderId) REFERENCES authorized_folders(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_file_sources_folderId ON file_sources (folderId)")
        db.execSQL("CREATE INDEX index_file_sources_kind_location ON file_sources (kind, location)")

        db.execSQL("""
            CREATE TABLE folder_entries (
                folderId TEXT NOT NULL,
                documentId TEXT NOT NULL,
                documentUri TEXT NOT NULL,
                displayName TEXT NOT NULL,
                size INTEGER,
                modifiedAt INTEGER,
                lastSeenAt INTEGER NOT NULL,
                missing INTEGER NOT NULL,
                ignored INTEGER NOT NULL,
                importedFileId TEXT,
                PRIMARY KEY(folderId, documentId),
                FOREIGN KEY(folderId) REFERENCES authorized_folders(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(importedFileId) REFERENCES book_files(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_folder_entries_documentId ON folder_entries (documentId)")
        db.execSQL("CREATE INDEX index_folder_entries_importedFileId ON folder_entries (importedFileId)")

        db.execSQL("""
            INSERT INTO file_sources (sha256, format, kind, location, status, updatedAt)
            SELECT sha256, format, 'managed', 'library/objects/' || sha256 || '.' || format, 'unknown', 0
            FROM file_objects
        """.trimIndent())
        db.execSQL("""
            INSERT INTO file_sources (sha256, format, kind, location, status, updatedAt)
            SELECT DISTINCT sha256, format, 'external', sourceUri, 'unknown', 0 FROM book_files
        """.trimIndent())
        db.installFileSourceConstraints()
    }
}
