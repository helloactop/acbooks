package app.acbooks.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

internal fun SupportSQLiteDatabase.installRemoteLibraryConstraints() {
    validateRows(
        table = "server_profiles",
        invalid = """
            ${invalidUuid("NEW.id")} OR NEW.verification <> 'verified'
            OR NEW.serverInstanceId IS NULL OR NEW.serverInstanceId = '' OR trim(NEW.serverInstanceId) <> NEW.serverInstanceId
            OR NEW.accountId IS NULL OR ${invalidRemoteUuid("NEW.accountId")}
            OR NEW.deviceId IS NULL OR ${invalidUuid("NEW.deviceId")} OR NEW.legacyOriginFingerprint IS NOT NULL
            OR NEW.currentSlot IS NOT 1
            OR NEW.origin IS NULL OR (
                NEW.origin NOT GLOB 'https://?*' AND NEW.origin NOT GLOB 'http://?*'
            )
            OR substr(rtrim(NEW.origin, '/'), instr(NEW.origin, '://') + 3) GLOB '*[/@?#]*'
            OR rtrim(NEW.origin, '/') IN ('http:', 'https:')
            OR NEW.origin GLOB '* *' OR instr(NEW.origin, char(9)) <> 0
            OR instr(NEW.origin, char(10)) <> 0 OR instr(NEW.origin, char(13)) <> 0
            OR ${invalidTime("NEW.updatedAt")}
        """.trimIndent(),
        invalidOnUpdate = """
            NEW.id IS NOT OLD.id OR NEW.serverInstanceId IS NOT OLD.serverInstanceId
            OR NEW.accountId IS NOT OLD.accountId OR NEW.verification IS NOT OLD.verification
        """.trimIndent(),
    )
    val unverifiedProfile = """
        NOT EXISTS (
            SELECT 1 FROM server_profiles WHERE id = NEW.profileId AND verification = 'verified'
        )
    """.trimIndent()
    validateRows(
        "remote_catalog",
        """
        $unverifiedProfile OR ${invalidRemoteUuid("NEW.remoteBookId")}
        OR NEW.available NOT IN (0, 1) OR ${invalidTime("NEW.updatedAt")} OR ${invalidTime("NEW.cachedAt")}
        OR ${invalidTime("NEW.coverUpdatedAt")}
        """.trimIndent(),
    )
    validateRows(
        "remote_catalog_files",
        """
        $unverifiedProfile OR ${invalidRemoteUuid("NEW.remoteFileId")}
        OR NEW.fileStatus NOT IN ('present', 'missing') OR trim(NEW.format) = '' OR NEW.sizeBytes < 0
        OR (NEW.sha256 IS NOT NULL AND (length(NEW.sha256) <> 64 OR NEW.sha256 GLOB '*[^0-9a-f]*'))
        OR ${invalidTime("NEW.cachedAt")}
        """.trimIndent(),
    )
    validateRows(
        "remote_book_links",
        """
        $unverifiedProfile OR ${invalidRemoteUuid("NEW.remoteBookId")} OR EXISTS (
            SELECT 1 FROM remote_book_links
            WHERE profileId = NEW.profileId AND remoteBookId = NEW.remoteBookId AND localBookId <> NEW.localBookId
        )
        """.trimIndent(),
    )
    validateRows(
        "remote_file_links",
        """
        $unverifiedProfile OR ${invalidRemoteUuid("NEW.remoteFileId")} OR EXISTS (
            SELECT 1 FROM remote_file_links
            WHERE profileId = NEW.profileId AND remoteFileId = NEW.remoteFileId AND localFileId <> NEW.localFileId
        )
        """.trimIndent(),
    )


}

private fun invalidRemoteUuid(column: String): String = "(${invalidUuid(column)} OR lower($column) <> $column)"
