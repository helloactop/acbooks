package app.acbooks.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

private const val MaxApiInteger = 9_007_199_254_740_991L
private const val MaxApiSeconds = 9_007_199_254_740L

internal fun SupportSQLiteDatabase.installReadingConstraints() {
    val sessionRules = """
        ${invalidUuid("NEW.id")} OR (NEW.deviceId IS NOT NULL AND (${invalidUuid("NEW.deviceId")}))
        OR ${invalidTime("NEW.startedAt")}
        OR NEW.activeSeconds NOT BETWEEN 0 AND $MaxApiSeconds
        OR NEW.source NOT IN ('automatic', 'manual')
        OR (NEW.source = 'manual' AND NEW.endedAt IS NULL)
        OR (NEW.endedAt IS NOT NULL AND (
            ${invalidTime("NEW.endedAt")} OR NEW.endedAt < NEW.startedAt
            OR NEW.activeSeconds * 1000 > NEW.endedAt - NEW.startedAt
        ))
    """.trimIndent()
    validateRows(
        table = "reading_sessions",
        invalid = "$sessionRules OR ${invalidFileOwner()}",
        invalidOnInsert = "NEW.source = 'automatic' AND NEW.fileId IS NULL",
        invalidOnUpdate = """
            NEW.source = 'automatic' AND NEW.fileId IS NULL AND (
                OLD.source <> 'automatic'
                OR EXISTS (SELECT 1 FROM book_files WHERE id = OLD.fileId)
            )
        """.trimIndent(),
    )

    val markRules = """
        ${invalidUuid("NEW.id")} OR ${invalidFileOwner()}
        OR ${invalidTime("NEW.createdAt")} OR ${invalidTime("NEW.updatedAt")}
        OR length(CAST(NEW.locator AS BLOB)) > 32768
    """.trimIndent()
    validateRows("bookmarks", "$markRules OR length(NEW.label) > 200")
    validateRows("annotations", "$markRules OR length(NEW.quote) > 20000 OR length(NEW.note) > 20000")

    validateRows(
        "reading_positions",
        "NEW.percent NOT BETWEEN 0 AND 100 OR NEW.revision NOT BETWEEN 0 AND $MaxApiInteger OR ${invalidTime("NEW.updatedAt")}",
    )
    execSQL("""
        CREATE TRIGGER IF NOT EXISTS book_file_reading_owner
        BEFORE UPDATE OF bookId ON book_files
        WHEN NEW.bookId <> OLD.bookId AND (
            EXISTS (SELECT 1 FROM reading_sessions WHERE fileId = OLD.id)
            OR EXISTS (SELECT 1 FROM bookmarks WHERE fileId = OLD.id)
            OR EXISTS (SELECT 1 FROM annotations WHERE fileId = OLD.id)
        )
        BEGIN SELECT RAISE(ABORT, 'Book file has reading records'); END
    """.trimIndent())
}

internal fun SupportSQLiteDatabase.installCatalogConstraints() {
    validateRows(
        "books",
        """
        NEW.readingStatus NOT IN ('unread', 'reading', 'done')
        OR NEW.metadataLocks NOT BETWEEN 0 AND $MaxApiInteger
        OR ${invalidTime("NEW.createdAt")} OR ${invalidTime("NEW.updatedAt")}
        OR (NEW.preferredFileId IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM book_files WHERE id = NEW.preferredFileId AND bookId = NEW.id
        ))
        """.trimIndent(),
    )
    execSQL("""
        CREATE TRIGGER IF NOT EXISTS book_file_preferred_owner
        BEFORE UPDATE OF bookId ON book_files
        WHEN NEW.bookId <> OLD.bookId AND EXISTS (
            SELECT 1 FROM books WHERE preferredFileId = OLD.id
        )
        BEGIN SELECT RAISE(ABORT, 'Book file is preferred'); END
    """.trimIndent())

    validateRows("contributors", "${invalidUuid("NEW.id")} OR trim(NEW.name) = ''")
    validateRows("book_contributors", "trim(NEW.role) = ''")
    validateRows("book_identifiers", "${invalidUuid("NEW.id")} OR trim(NEW.scheme) = '' OR trim(NEW.value) = ''")
    validateRows("tags", "${invalidUuid("NEW.id")} OR trim(NEW.name) = ''")
    validateRows(
        "locations",
        """
        ${invalidUuid("NEW.id")} OR trim(NEW.name) = '' OR length(NEW.name) > 200
        OR NEW.id = NEW.parentId
        OR EXISTS (
            WITH RECURSIVE ancestors(id) AS (
                SELECT NEW.parentId
                UNION
                SELECT locations.parentId FROM locations JOIN ancestors ON locations.id = ancestors.id
                WHERE locations.parentId IS NOT NULL
            )
            SELECT 1 FROM ancestors WHERE id = NEW.id
        )
        """.trimIndent(),
    )
    validateRows(
        "book_copies",
        "${invalidUuid("NEW.id")} OR ${invalidTime("NEW.acquiredAt")} OR length(NEW.note) > 2000",
    )
}

internal fun SupportSQLiteDatabase.validateRows(
    table: String,
    invalid: String,
    invalidOnInsert: String? = null,
    invalidOnUpdate: String? = null,
) {
    for (operation in listOf("INSERT", "UPDATE")) {
        val extra = if (operation == "INSERT") invalidOnInsert else invalidOnUpdate
        val condition = if (extra == null) invalid else "($invalid) OR ($extra)"
        execSQL("""
            CREATE TRIGGER IF NOT EXISTS ${table}_validate_${operation.lowercase()}
            BEFORE $operation ON $table
            WHEN $condition
            BEGIN SELECT RAISE(ABORT, 'Invalid $table row'); END
        """.trimIndent())
    }
}

internal fun invalidTime(column: String): String = "$column NOT BETWEEN 0 AND $MaxApiInteger"

private fun invalidFileOwner(): String = """
    (NEW.fileId IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM book_files WHERE id = NEW.fileId AND bookId = NEW.bookId
    ))
""".trimIndent()

internal fun invalidUuid(column: String): String = """
    length($column) <> 36 OR substr($column, 9, 1) <> '-' OR substr($column, 14, 1) <> '-'
    OR substr($column, 19, 1) <> '-' OR substr($column, 24, 1) <> '-'
    OR length(replace($column, '-', '')) <> 32
    OR lower(replace($column, '-', '')) GLOB '*[^0-9a-f]*'
""".trimIndent()

internal fun SupportSQLiteDatabase.installFileSourceConstraints() {
    validateRows(
        "authorized_folders",
        """
        ${invalidUuid("NEW.id")} OR trim(NEW.authority) = '' OR NEW.rootDocumentId = ''
        OR instr(NEW.treeUri, 'content://' || NEW.authority || '/tree/') <> 1
        OR NEW.scanState NOT IN ('idle', 'scanning', 'complete', 'failed', 'permission_required')
        OR ${invalidTime("NEW.lastScanStartedAt")} OR ${invalidTime("NEW.lastScanCompletedAt")}
        OR ${invalidTime("NEW.updatedAt")}
        """.trimIndent(),
    )
    validateRows(
        "file_sources",
        """
        NEW.kind NOT IN ('managed', 'external', 'downloaded')
        OR NEW.status NOT IN ('unknown', 'present', 'missing', 'permission_required')
        OR ${invalidTime("NEW.updatedAt")}
        OR (NEW.kind = 'external' AND instr(NEW.location, 'content://') <> 1)
        OR (NEW.kind <> 'external' AND (
            NEW.folderId IS NOT NULL OR NEW.status = 'permission_required'
            OR NEW.location <> ('library/objects/' || NEW.sha256 || '.' || NEW.format)
            OR instr(NEW.sha256, '/') <> 0 OR instr(NEW.format, '/') <> 0
        ))
        OR (NEW.folderId IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM authorized_folders f
            WHERE f.id = NEW.folderId AND instr(NEW.location, 'content://' || f.authority || '/') = 1
        ))
        """.trimIndent(),
    )
    validateRows(
        "folder_entries",
        """
        NEW.documentId = '' OR trim(NEW.displayName) = ''
        OR NEW.size < 0 OR ${invalidTime("NEW.modifiedAt")} OR ${invalidTime("NEW.lastSeenAt")}
        OR NEW.missing NOT IN (0, 1) OR NEW.ignored NOT IN (0, 1)
        OR NOT EXISTS (
            SELECT 1 FROM authorized_folders f
            WHERE f.id = NEW.folderId AND instr(NEW.documentUri, 'content://' || f.authority || '/') = 1
        )
        """.trimIndent(),
    )
}
