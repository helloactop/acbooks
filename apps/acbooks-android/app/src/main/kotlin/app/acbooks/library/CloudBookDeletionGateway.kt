package app.acbooks.library

/**
 * Integration boundary for a future authenticated server-backed library.
 *
 * The gateway implementation must resolve a local book id to its authoritative remote id
 * and check the signed-in account before enabling cloud deletion. A remote delete must finish
 * successfully before the caller removes the local copy.
 */
internal interface CloudBookDeletionGateway {
    fun canDeleteFromCloud(localBookId: String): Boolean

    /** Returns true only after a successful remote deletion. */
    suspend fun deleteRemoteBook(localBookId: String): Boolean
}

/** Never silently treat an unconfigured cloud endpoint as a successful deletion. */
internal object UnavailableCloudBookDeletionGateway : CloudBookDeletionGateway {
    override fun canDeleteFromCloud(localBookId: String) = false
    override suspend fun deleteRemoteBook(localBookId: String) = false
}
