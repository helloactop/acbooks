package app.acbooks.library

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.acbooks.core.database.LibraryDao
import app.acbooks.core.database.RemoteBookLink
import app.acbooks.core.database.RemoteFileLink
import app.acbooks.core.database.RemoteFileStatus
import app.acbooks.core.database.ServerProfile
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class CloudIdentityChanged : IOException("The configured server identity changed")

internal class LibraryCloudSync(
    context: Context,
    private val dao: LibraryDao,
    private val library: LibraryRepository,
) {
    private val credentials = CloudCredentialStore(context)
    private val lock = Mutex()

    suspend fun connection(): Pair<String?, Boolean> = serialized {
        val profile = dao.currentServerProfile()
        profile?.origin to (profile != null && credentials.read(profile.id) != null)
    }

    suspend fun searchDoubanMetadata(query: String): List<DoubanMetadataCandidate> = serialized {
        val search = query.trim()
        require(search.length in 1..200)
        val session = verifiedSession() ?: throw IOException("No active server session")
        session.client.searchDoubanMetadata(session.token, search)
    }

    suspend fun login(address: String, username: String, password: String) = serialized {
        val origin = serverOrigin(address)
        val previous = dao.currentServerProfile()
        if (previous != null && previous.origin != origin) throw CloudIdentityChanged()
        val client = LibraryCloudClient(origin)
        val token = client.login(username.trim(), password)
        try {
            val account = client.accountId(token)
            if (previous != null && previous.accountId != account) throw CloudIdentityChanged()
            // acbooks has one persistent administrator per library database.
            val profile = dao.saveCurrentServerProfile(account, account, previous?.deviceId ?: UUID.randomUUID().toString(),
                origin, System.currentTimeMillis())
            credentials.write(profile.id, token)
        } catch (failure: Exception) {
            if (failure !is CancellationException) runCatching { client.logout(token) }
            throw failure
        }
    }

    suspend fun logout() = serialized {
        val profile = dao.currentServerProfile() ?: return@serialized
        val token = credentials.read(profile.id)
        credentials.clear()
        if (token != null) LibraryCloudClient(checkNotNull(profile.origin)).logout(token)
    }

    /** False indicates at least one book still needs to be uploaded. Null indicates a signed-out device. */
    suspend fun sync(): Boolean? = serialized {
        val session = verifiedSession() ?: return@serialized null
        val (profile, client, token) = session
        val initial = client.catalog(token, profile.id)
        val remoteBooks = initial.books.mapTo(mutableSetOf()) { it.remoteBookId }
        val remoteFiles = initial.files.associateBy { it.remoteFileId }
        var complete = true
        var changed = false

        for (book in dao.booksWithOfflineCopies()) {
            try {
                val existingLink = dao.remoteBookLink(profile.id, book.id)
                val link = if (existingLink == null) {
                    client.createBook(token, checkNotNull(profile.deviceId), book)
                    changed = true
                    RemoteBookLink(profile.id, book.id, book.id).also { dao.linkRemoteBook(it) }
                } else if (existingLink.remoteBookId !in remoteBooks) {
                    // The server may have explicitly deleted this book. Keep its local copy.
                    complete = false
                    continue
                } else existingLink
                for (file in dao.allBookFiles(book.id).filterNot { it.localCopyRemoved }) {
                    val fileLink = dao.remoteFileLink(profile.id, file.id)
                    val existing = remoteFiles[fileLink?.remoteFileId]
                    if (existing != null && existing.remoteBookId == link.remoteBookId && existing.sha256 == file.sha256 &&
                        existing.fileStatus == RemoteFileStatus.Present
                    ) continue
                    val remoteFileId = library.uploadBookFile(file) { source ->
                        val receipt = client.upload(token, link.remoteBookId, file.format, source)
                        if (receipt.getString("fileStatus") != RemoteFileStatus.Present ||
                            receipt.getString("format") != file.format || receipt.getLong("sizeBytes") != source.length()
                        ) throw IOException("Server did not confirm the file upload")
                        receipt.getString("id").asUuid()
                    }
                    dao.linkRemoteFile(RemoteFileLink(profile.id, file.id, remoteFileId))
                    changed = true
                }
            } catch (failure: CloudHttpException) {
                if (failure.status == 401 || failure.status == 403 || failure.status == 429 || failure.status >= 500) throw failure
                complete = false
            } catch (_: IOException) {
                complete = false
            } catch (_: SecurityException) {
                complete = false
            }
        }
        val snapshot = if (changed) client.catalog(token, profile.id) else initial
        library.cacheCloudCovers(snapshot.books, client, token)
        dao.replaceRemoteCatalog(profile.id, snapshot.books, snapshot.files)
        complete
    }

    suspend fun openOrDownload(bookId: String): Boolean = serialized {
        if (library.openBook(bookId) != null) return@serialized true
        val session = verifiedSession() ?: return@serialized false
        val remote = dao.cloudFilesForBook(session.profile.id, bookId).firstOrNull {
            it.fileStatus == RemoteFileStatus.Present && it.format in setOf("epub", "pdf")
        } ?: return@serialized false
        library.downloadBookFile(bookId, remote, session.client, session.token)
        true
    }

    suspend fun removeLocal(bookId: String): Boolean = serialized { library.deleteLocalBook(bookId) }

    suspend fun deleteEverywhere(bookId: String): Boolean = serialized {
        val session = verifiedSession() ?: return@serialized false
        val origin = checkNotNull(session.profile.origin)
        val accountId = checkNotNull(session.profile.accountId)
        val instanceId = checkNotNull(session.profile.serverInstanceId)
        val link = dao.verifiedRemoteBookLink(bookId, instanceId, accountId, origin) ?: return@serialized false
        session.client.delete(session.token, link.remoteBookId)
        library.deleteBookAfterCloudConfirmation(link)
    }

    private suspend fun verifiedSession(): CloudSession? {
        val profile = dao.currentServerProfile() ?: return null
        val token = credentials.read(profile.id) ?: return null
        val client = LibraryCloudClient(checkNotNull(profile.origin))
        val account = try {
            client.accountId(token)
        } catch (failure: CloudHttpException) {
            if (failure.status == 401) credentials.clear()
            throw failure
        }
        if (profile.accountId != account || profile.serverInstanceId != account) {
            credentials.clear()
            throw CloudIdentityChanged()
        }
        return CloudSession(profile, client, token)
    }

    private suspend fun <T> serialized(action: suspend () -> T): T =
        withContext(Dispatchers.IO) { lock.withLock { action() } }
}

private class CloudSession(val profile: ServerProfile, val client: LibraryCloudClient, val token: String) {
    operator fun component1() = profile
    operator fun component2() = client
    operator fun component3() = token
}

private class CloudCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_device_session", Context.MODE_PRIVATE)
    private val keyStore by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }
    private val alias = "acbooks-cloud-device-session"

    fun read(profileId: String): String? {
        val encoded = preferences.getString("session", null) ?: return null
        val iv = preferences.getString("iv", null) ?: return null
        return try {
            val key = keyStore.getKey(alias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            cipher.updateAAD(profileId.toByteArray())
            cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)).decodeToString()
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun write(profileId: String, token: String) {
        val key = keyStore.getKey(alias, null) as? SecretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
                generateKey()
            }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(profileId.toByteArray())
        val encrypted = Base64.encodeToString(cipher.doFinal(token.toByteArray()), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        if (!preferences.edit().putString("session", encrypted).putString("iv", iv).commit()) {
            throw IOException("Could not save the device session")
        }
    }

    fun clear() {
        if (!preferences.edit().clear().commit()) throw IOException("Could not remove the device session")
    }
}
