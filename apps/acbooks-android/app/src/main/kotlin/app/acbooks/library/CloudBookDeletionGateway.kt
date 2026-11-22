package app.acbooks.library

/**
 * Integration boundary for authenticated cloud deletion.
 *
 * The caller must delete from the cloud before removing the local book. A remote failure
 * (including an unconfigured service) MUST NOT delete the local book.
 *
 * UI actions remain enabled even if this implementation is not yet connected.
 */
internal interface CloudBookDeletionGateway {
    /** Return true only after the authenticated server confirms the remote deletion. */
    suspend fun deleteRemoteBook(localBookId: String): Boolean
}

internal object UnavailableCloudBookDeletionGateway : CloudBookDeletionGateway {
    override suspend fun deleteRemoteBook(localBookId: String) = false
}
