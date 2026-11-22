package app.acbooks.library

import java.net.HttpURLConnection
import java.net.URL
import app.acbooks.core.database.LibraryDao
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Authenticated connection supplied by the Android server-login flow.
 * Do not persist or log bearer tokens here.
 */
internal data class CloudBookConnection(
    val serverOrigin: String,
    val bearerToken: String,
)

/** Login and sync supply these values; local-only books must not guess a remote id. */
internal interface CloudBookConnectionProvider {
    fun activeConnection(): CloudBookConnection?
    suspend fun remoteBookId(localBookId: String, serverOrigin: String): String?
}

internal interface CloudBookDeletionGateway {
    /** Returns true only after the remote server confirms deletion. */
    suspend fun deleteRemoteBook(localBookId: String): Boolean
}

/**
 * Real acbooks server endpoint: DELETE /api/v1/books/{bookId} (204).
 * No UI prompt, no fallback that silently deletes only the local copy.
 */
internal class HttpCloudBookDeletionGateway(
    private val connectionProvider: CloudBookConnectionProvider,
) : CloudBookDeletionGateway {
    override suspend fun deleteRemoteBook(localBookId: String): Boolean = withContext(Dispatchers.IO) {
        val connection = connectionProvider.activeConnection() ?: return@withContext false
        val remoteId = connectionProvider.remoteBookId(localBookId, connection.serverOrigin)
            ?: return@withContext false
        val validatedId = runCatching { UUID.fromString(remoteId) }.getOrNull() ?: return@withContext false
        val origin = runCatching { URL(connection.serverOrigin.trimEnd('/')) }.getOrNull()
            ?: return@withContext false
        // Do not allow a token to travel over HTTP or to an arbitrary redirect target.
        if (origin.protocol != "https" || origin.host.isBlank() ||
            origin.userInfo != null || origin.query != null || origin.ref != null ||
            origin.path !in listOf("", "/") || connection.bearerToken.isBlank()
        ) return@withContext false
        val url = URL(origin, "/api/v1/books/$validatedId")
        val request = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer ${connection.bearerToken}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            request.responseCode == HttpURLConnection.HTTP_NO_CONTENT
        } finally {
            request.disconnect()
        }
    }
}

/**
 * Android login supplies the active session; credentials are intentionally memory-only.
 * Remote book association is persisted by the sync flow in Room.
 */
internal object CloudBookSessions {
    @Volatile
    private var active: CloudBookConnection? = null

    fun setAuthenticatedConnection(connection: CloudBookConnection?) {
        active = connection
    }

    fun current(): CloudBookConnection? = active
}

internal class RoomCloudBookConnectionProvider(
    private val dao: LibraryDao,
) : CloudBookConnectionProvider {
    override fun activeConnection(): CloudBookConnection? = CloudBookSessions.current()

    override suspend fun remoteBookId(localBookId: String, serverOrigin: String): String? =
        dao.remoteBookId(localBookId, serverOrigin)
}
