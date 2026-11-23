package app.acbooks.library

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.acbooks.core.database.LibraryDao
import app.acbooks.core.database.PendingBookEdit
import app.acbooks.core.database.RemoteCatalogBook
import app.acbooks.core.database.RemoteShelf
import app.acbooks.core.database.RemoteShelfSnapshot
import app.acbooks.core.database.PendingCloudUpload
import app.acbooks.core.database.PendingCloudDownload
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

internal class CloudIdentityChanged : IOException("The configured server identity changed")
internal class CloudSignInRequired : IOException("No active server session")
internal data class CloudConnection(val origin: String? = null, val username: String? = null, val hasSession: Boolean = false)
private val cloudSyncLock = Mutex()

internal class LibraryCloudSync(
    private val context: Context,
    private val dao: LibraryDao,
    private val library: LibraryRepository,
) {
    private val credentials = CloudCredentialStore(context)

    suspend fun connection(): CloudConnection = serialized {
        val profile = dao.currentServerProfile()
        val credential = profile?.let { credentials.readSession(it.id) }
        CloudConnection(profile?.origin, credential?.username, credential != null)
    }

    suspend fun testAddress(address: String) = serialized { LibraryCloudClient(serverOrigin(address)).testAddress() }

    suspend fun testConnection(): Boolean = serialized { verifiedSession() != null }

    suspend fun searchDoubanMetadata(query: String): List<DoubanMetadataCandidate> = serialized {
        val search = query.trim()
        require(search.length in 1..200)
        val session = verifiedSession() ?: throw CloudSignInRequired()
        session.client.searchDoubanMetadata(session.token, search)
    }

    suspend fun saveBookEdit(bookId: String, initial: BookEditSession, draft: BookEditDraft): Boolean =
        serialized { library.saveBookEdit(bookId, initial, draft) }

    suspend fun saveBookShelves(bookId: String, shelfIds: Set<String>): Boolean =
        serialized { library.saveBookShelves(bookId, shelfIds) }

    suspend fun saveShelf(id: String?, name: String): Boolean = serialized {
        val previous = id?.let { checkNotNull(dao.libraryShelf(it)) }
        val session = verifiedSession()
        if (session == null) {
            if (previous?.serverShelfId != null) throw CloudSignInRequired()
            return@serialized dao.saveLocalShelf(id, name)
        }
        val (profile, client, token) = session
        val remoteId = previous?.serverShelfId
        val remote = if (remoteId == null) {
            client.createShelf(token, name)
        } else {
            client.renameShelf(token, remoteId, name)
            RemoteShelf(remoteId, name)
        }
        val sameName = dao.shelfByNameKey(remote.name.lowercase(java.util.Locale.ROOT))
        dao.cacheManagedShelf(profile.id, previous ?: sameName?.takeIf { it.serverProfileId == null }, remote)
        true
    }

    suspend fun deleteShelf(id: String) = serialized {
        val previous = dao.libraryShelf(id) ?: return@serialized
        val remoteId = previous.serverShelfId
        if (remoteId == null) {
            dao.deleteShelf(id)
        } else {
            val (profile, client, token) = verifiedSession() ?: throw CloudSignInRequired()
            client.deleteShelf(token, remoteId)
            dao.cacheManagedShelf(profile.id, previous, remote = null)
        }
    }

    suspend fun login(address: String, username: String, password: String, replaceCurrent: Boolean) = serialized {
        val origin = serverOrigin(address)
        val previous = dao.currentServerProfile()
        if (!replaceCurrent && previous != null && previous.origin != origin) throw CloudIdentityChanged()
        val previousToken = previous?.let { credentials.read(it.id) }
        val client = LibraryCloudClient(origin)
        val token = client.login(username.trim(), password)
        var activated = false
        try {
            val account = client.account(token)
            val existing = dao.verifiedServerProfile(origin, account.id)
            if (!replaceCurrent && previous != null && previous.id != existing?.id) throw CloudIdentityChanged()
            val profileId = existing?.id ?: UUID.randomUUID().toString()
            // The API identifies its persistent administrator. Include the origin in new scopes so
            // a copied database at another address never inherits existing associations.
            val instance = existing?.serverInstanceId
                ?: UUID.nameUUIDFromBytes("acbooks:server:$origin:${account.id}".toByteArray()).toString()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                credentials.write(profileId, token, account.username)
                try {
                    if (previous?.id != profileId) {
                        dao.saveCurrentServerProfile(instance, account.id, existing?.deviceId ?: UUID.randomUUID().toString(),
                            origin, System.currentTimeMillis(), replaceCurrent, profileId)
                    }
                    activated = true
                } finally {
                    if (activated) credentials.keepOnly(profileId)
                    else if (previous?.id != profileId) credentials.remove(profileId)
                }
            }
            if (previousToken != null && previousToken != token) {
                previous.origin?.let { runCatching { LibraryCloudClient(it).logout(previousToken, timeout = 3_000) } }
            }
            CloudConnection(origin, account.username, hasSession = true)
        } finally {
            if (!activated) runCatching { client.logout(token, timeout = 3_000) }
        }
    }

    suspend fun logout() = serialized {
        val profile = dao.currentServerProfile() ?: return@serialized
        val token = credentials.read(profile.id)
        credentials.clear()
        if (token != null) {
            try {
                LibraryCloudClient(checkNotNull(profile.origin)).logout(token)
            } catch (failure: CloudHttpException) {
                if (failure.status != 401) throw failure
            }
        }
    }

    /** False indicates that some uploads are pending. Null indicates a signed-out device. */
    suspend fun sync(): Boolean? = serialized {
        val session = verifiedSession() ?: return@serialized null
        val (profile, client, token) = session
        pullCatalog(session)
        publishLocalShelves(session)
        refreshShelves(session, emptySet())
        enqueueExistingUploads(profile.id)
        var changed = false
        val createdBooks = mutableSetOf<String>()

        for (upload in dao.pendingCloudUploads(profile.id)) {
            if (upload.blocked || upload.isReaderRecord) continue
            try {
                if (sendUpload(session, upload, createdBooks)) changed = true
            } catch (failure: CloudMutationRejected) {
                dao.recordCloudUploadFailure(profile.id, upload.id, failure.code, blocked = true)
            } catch (failure: CloudHttpException) {
                dao.recordCloudUploadFailure(profile.id, upload.id, "HTTP_${failure.status}")
                if (failure.status in setOf(401, 403, 429) || failure.status >= 500) throw failure
            } catch (_: IOException) {
                dao.recordCloudUploadFailure(profile.id, upload.id, "IO_ERROR")
            } catch (_: SecurityException) {
                dao.recordCloudUploadFailure(profile.id, upload.id, "SOURCE_PERMISSION_REQUIRED")
            }
        }
        if (changed) pullCatalog(session)
        val completedEdits = uploadBookEdits(session)
        if (completedEdits.isNotEmpty()) pullCatalog(session)
        if (changed || completedEdits.isNotEmpty()) {
            refreshShelves(session, completedEdits.mapTo(mutableSetOf()) { it.mutationId })
        }
        for (edit in completedEdits) dao.completeBookEdit(edit.bookId, edit.mutationId)
        uploadReadingPositions(session)
        uploadReaderRecords(session)
        retryDownloads(session)
        library.cacheCloudCovers(dao.cachedRemoteBooks(profile.id), client, token)
        !dao.hasPendingCloudUploads(profile.id) && dao.dirtyReadingFiles(profile.id).isEmpty() &&
            dao.pendingDownloads(profile.id).isEmpty() && dao.pendingBookEdits(profile.id).isEmpty()
    }

    suspend fun hasRetryableWork(): Boolean {
        val profile = dao.currentServerProfile() ?: return false
        return dao.pendingBookEdits(profile.id).isNotEmpty() || dao.pendingDownloads(profile.id).isNotEmpty() ||
            dao.dirtyReadingFiles(profile.id).isNotEmpty() ||
            dao.pendingCloudUploads(profile.id).any { !it.blocked &&
                it.lastError !in setOf("REMOTE_FILE_CHANGED", "REMOTE_BOOK_NOT_CONFIRMED", "SOURCE_PERMISSION_REQUIRED")
            }
    }

    private suspend fun uploadBookEdits(session: CloudSession): List<PendingBookEdit> {
        val profileId = session.profile.id
        val completed = mutableListOf<PendingBookEdit>()
        for (edit in dao.pendingBookEdits(profileId)) {
            val link = dao.remoteBookLink(profileId, edit.bookId)
            if (link?.remoteBookId != edit.remoteBookId) continue
            val remote = dao.remoteCatalogBook(profileId, edit.remoteBookId) ?: continue
            try {
                if (pushBookEdit(session, edit, remote)) completed.add(edit)
            } catch (failure: CloudHttpException) {
                if (failure.status in setOf(401, 403, 429) || failure.status >= 500) throw failure
            } catch (_: IOException) {
                // Preserve the pending patch for a retry.
            }
        }
        return completed
    }

    private suspend fun publishLocalShelves(session: CloudSession) {
        val (profile, client, token) = session
        for (shelf in dao.allShelves()) {
            if (shelf.serverProfileId != null || ':' in shelf.id) continue
            val remote = try {
                client.createShelf(token, shelf.name)
            } catch (failure: CloudHttpException) {
                if (failure.status in setOf(404, 405)) return
                if (failure.status == 422) continue
                throw failure
            }
            dao.cacheManagedShelf(profile.id, shelf, remote)
        }
    }

    private suspend fun refreshShelves(session: CloudSession, completed: Set<String>) {
        val (profile, client, token) = session
        val directory = client.listShelves(token) ?: return
        val shelfIds = directory.mapTo(mutableSetOf()) { it.id }
        val shelvesByName = directory.groupBy { it.name }
        val memberships = mutableMapOf<String, Set<String>>()
        for (book in dao.cachedRemoteBooks(profile.id)) {
            val bookId = book.remoteBookId
            val details = book.detailsJson?.let(::JSONObject) ?: continue
            val refs = details.optJSONArray("shelfRefs")
            if (refs != null) {
                memberships[bookId] = (0 until refs.length()).map {
                    refs.getJSONObject(it).getString("id").asUuid()
                }.filterTo(mutableSetOf()) { it in shelfIds }
            } else {
                val names = details.optJSONArray("shelves") ?: continue
                val ids = (0 until names.length()).mapNotNull { shelvesByName[names.getString(it)]?.singleOrNull()?.id }
                if (ids.size == names.length()) memberships[bookId] = ids.toSet()
            }
        }
        val pending = dao.pendingBookEdits(profile.id)
            .filter { it.mutationId !in completed && JSONObject(it.patchJson).has("shelves") }
            .mapTo(mutableSetOf()) { it.bookId }
        val snapshot = RemoteShelfSnapshot(directory, memberships)
        dao.replaceRemoteShelves(profile.id, snapshot, pending)
    }

    private suspend fun pushBookEdit(session: CloudSession, edit: PendingBookEdit, remote: RemoteCatalogBook): Boolean {
        val revision = dao.cloudCatalogVersion(edit.profileId, "book", edit.remoteBookId)
            ?.takeUnless { it.deleted }?.revision ?: return false
        val current = remote.detailsJson?.let(::JSONObject) ?: return false
        val baseline = edit.baselineJson?.let(::JSONObject)
        val desired = JSONObject(edit.patchJson)
        val remaining = JSONObject()
        for (key in desired.fieldNames()) {
            if (desired.bookFieldMatches(current, key)) continue
            if (baseline?.has(key) == true && !baseline.bookFieldMatches(current, key)) return false
            remaining.put(key, desired.get(key))
        }

        val (profile, client, token) = session
        val cover = edit.coverKey?.let { key ->
            library.editedCoverFile(key)?.takeIf { it.isFile } ?: throw IOException("The edited book cover is unavailable")
        }
        val coverVersion = remote.coverUpdatedAt
        val coverAlreadyUploaded = if (cover != null && baseline != null && coverVersion != edit.baseCoverVersion) {
            if (coverVersion == null || !client.coverMatches(token, edit.remoteBookId, coverVersion, cover)) return false
            true
        } else false
        if (remaining.length() > 0 && !client.updateBook(
                token, checkNotNull(profile.deviceId), edit.remoteBookId, revision, edit.mutationId, remaining,
            )
        ) return false

        if (cover != null) {
            if (!coverAlreadyUploaded) client.uploadCover(token, edit.remoteBookId, cover)
            dao.queueBookEdit(edit.copy(coverKey = null))
        }
        return true
    }

    suspend fun refreshReadingPosition(fileId: String) = serialized {
        val profile = dao.currentServerProfile() ?: return@serialized
        if (dao.remoteFileLink(profile.id, fileId) == null) return@serialized
        val session = verifiedSession() ?: return@serialized
        pullCatalog(session)
    }

    suspend fun syncReaderData() = serialized {
        val profile = dao.currentServerProfile() ?: return@serialized
        dao.enqueueReaderRecords(profile.id)
        if (dao.dirtyReadingFiles(profile.id).isEmpty() &&
            dao.pendingCloudUploads(profile.id).none { it.isReaderRecord && !it.blocked }
        ) return@serialized
        val session = verifiedSession() ?: return@serialized
        pullCatalog(session)
        uploadReadingPositions(session)
        uploadReaderRecords(session)
    }

    private suspend fun uploadReaderRecords(session: CloudSession) {
        val (profile, client, token) = session
        dao.enqueueReaderRecords(profile.id)
        for (upload in dao.pendingCloudUploads(profile.id).filter { it.isReaderRecord && !it.blocked }) {
            val fileId = upload.fileId
            val payload = org.json.JSONObject(checkNotNull(upload.payload))
            val book = dao.remoteBookLink(profile.id, upload.bookId)
            val file = fileId?.let { dao.cloudReadingFile(profile.id, it) }
            if (book?.remoteBookId != payload.getString("bookId") ||
                (fileId != null && file?.remoteFileId != payload.getJSONObject("data").optString("bookFileId"))
            ) {
                dao.recordCloudUploadFailure(profile.id, upload.id, "REMOTE_FILE_CHANGED", blocked = true)
                continue
            }
            try {
                val record = client.uploadReaderRecord(token, checkNotNull(profile.deviceId), upload)
                dao.acknowledgeReaderUpload(upload, record)
            } catch (failure: CloudMutationRejected) {
                dao.recordCloudUploadFailure(profile.id, upload.id, failure.code, blocked = true)
            }
        }
    }

    private suspend fun uploadReadingPositions(session: CloudSession) {
        val (profile, client, token) = session
        for (fileId in dao.dirtyReadingFiles(profile.id)) {
            val pending = dao.prepareReadingPosition(profile.id, fileId) ?: continue
            try {
                val current = client.saveReadingPosition(token, checkNotNull(profile.deviceId), pending)
                dao.finishReadingPosition(pending, current)
            } catch (_: CloudMutationRejected) {
                // Keep the local position if its legacy locator cannot be submitted.
                dao.discardReadingPositionRequest(pending)
            } catch (_: JSONException) {
                // The request may have succeeded. Keep the exact mutation for a later replay.
            }
        }
    }

    private suspend fun pullCatalog(session: CloudSession) {
        val (profile, client, token) = session
        val cursor = dao.cloudSyncCursor(profile.id)
        var rebuild = cursor == null
        val batch = try {
            client.catalog(token, profile.id, cursor ?: 0L)
        } catch (failure: CloudHttpException) {
            if (failure.status != 422 || cursor == null || cursor == 0L) throw failure
            rebuild = true
            client.catalog(token, profile.id, 0L)
        }
        library.applyCloudCatalog(profile.id, cursor, rebuild, batch)
    }

    private suspend fun enqueueExistingUploads(profileId: String) {
        val books = dao.unqueuedCloudBooks(profileId).map { book ->
            PendingCloudUpload.createBook(profileId, book.id, library.bookUploadPayload(book))
        }
        val files = dao.unqueuedCloudFiles(profileId).map { PendingCloudUpload.uploadFile(profileId, it) }
        dao.enqueueCloudUploads(books + files)
    }

    private suspend fun sendUpload(session: CloudSession, upload: PendingCloudUpload, createdBooks: MutableSet<String>): Boolean {
        val (profile, client, token) = session
        val bookLink = dao.remoteBookLink(profile.id, upload.bookId)
        val fileId = upload.fileId
        if (fileId == null) {
            if (bookLink != null) {
                dao.acknowledgeCloudUpload(upload, bookLink.remoteBookId)
                return false
            }
            if (dao.cloudCatalogVersion(profile.id, "book", upload.bookId)?.deleted == true) {
                throw CloudMutationRejected("REMOTE_BOOK_DELETED")
            }
            if (dao.localBookLinks(profile.id, upload.bookId).isNotEmpty()) {
                throw CloudMutationRejected("REMOTE_BOOK_ID_IN_USE")
            }
            client.createBook(token, checkNotNull(profile.deviceId), upload.bookId, checkNotNull(upload.payload))
            val cover = dao.book(upload.bookId)?.editedCoverKey?.let { key ->
                PendingBookEdit(upload.bookId, profile.id, upload.bookId, UUID.randomUUID().toString(),
                    "{}", null, key, null)
            }
            dao.acknowledgeCloudUpload(upload, upload.bookId, cover)
            createdBooks.add(upload.bookId)
            return true
        }

        val file = dao.bookFile(fileId)
        if (file == null || file.localCopyRemoved) {
            dao.removeCloudUpload(profile.id, upload.id)
            return false
        }
        if (bookLink == null || (upload.bookId !in createdBooks && dao.remoteCatalogBook(profile.id, bookLink.remoteBookId) == null)) {
            dao.recordCloudUploadFailure(profile.id, upload.id, "REMOTE_BOOK_NOT_CONFIRMED")
            return false
        }
        val fileLink = dao.remoteFileLink(profile.id, file.id)
        if (fileLink != null) {
            val remote = dao.remoteCatalogFile(profile.id, fileLink.remoteFileId)
            if (remote == null || remote.remoteBookId != bookLink.remoteBookId || remote.sha256 != file.sha256 ||
                remote.format != file.format
            ) {
                dao.recordCloudUploadFailure(profile.id, upload.id, "REMOTE_FILE_CHANGED")
                return false
            }
            if (remote.fileStatus == RemoteFileStatus.Present) {
                dao.acknowledgeCloudUpload(upload, fileLink.remoteFileId)
                return false
            }
        }
        val remoteFileId = library.uploadBookFile(file) { source ->
            val receipt = client.upload(token, bookLink.remoteBookId, file.format, source)
            if (receipt.getString("fileStatus") != RemoteFileStatus.Present ||
                receipt.getString("format") != file.format || receipt.getLong("sizeBytes") != source.length()
            ) throw IOException("Server did not confirm the file upload")
            receipt.getString("id").asUuid()
        }
        dao.acknowledgeCloudUpload(upload, remoteFileId)
        return true
    }

    suspend fun openOrDownload(bookId: String): Boolean = serialized {
        if (library.openBook(bookId) != null) return@serialized true
        val profile = dao.currentServerProfile() ?: return@serialized false
        if (credentials.read(profile.id) == null) return@serialized false
        val remote = dao.cloudFilesForBook(profile.id, bookId).firstOrNull {
            it.fileStatus == RemoteFileStatus.Present && it.format in setOf("epub", "pdf")
        } ?: return@serialized false
        dao.enqueueDownload(PendingCloudDownload(profile.id, bookId, remote.remoteFileId))
        CloudSyncJobs.request(context)
        val session = verifiedSession() ?: return@serialized false
        library.downloadBookFile(bookId, remote, session.client, session.token)
        true
    }

    private suspend fun retryDownloads(session: CloudSession) {
        val (profile, client, token) = session
        for (pending in dao.pendingDownloads(profile.id)) {
            val remote = dao.cloudFilesForBook(profile.id, pending.bookId).firstOrNull { it.remoteFileId == pending.remoteFileId }
            if (remote == null) {
                if (dao.cloudCatalogVersion(profile.id, "book_file", pending.remoteFileId)?.deleted == true) cancelDownload(pending)
                continue
            }
            if (remote.fileStatus != RemoteFileStatus.Present) continue
            try {
                library.downloadBookFile(pending.bookId, remote, client, token)
            } catch (failure: CloudHttpException) {
                if (failure.status in setOf(401, 403, 429) || failure.status >= 500) throw failure
            } catch (_: IOException) {
                // Keep the request and verified-version partial data for the next attempt.
            }
        }
    }

    private suspend fun cancelDownload(pending: PendingCloudDownload) {
        dao.completeDownload(pending.profileId, pending.bookId, pending.remoteFileId)
        library.removePartialDownloads(pending.profileId, setOf(pending.remoteFileId))
    }

    suspend fun removeLocal(bookId: String): Boolean = serialized {
        dao.currentServerProfile()?.let { profile ->
            dao.pendingDownloads(profile.id).firstOrNull { it.bookId == bookId }?.let { cancelDownload(it) }
        }
        library.deleteLocalBook(bookId)
    }

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
        val credential = credentials.readSession(profile.id) ?: return null
        val client = LibraryCloudClient(checkNotNull(profile.origin))
        val account = try {
            client.account(credential.token)
        } catch (failure: CloudHttpException) {
            if (failure.status == 401) credentials.clear()
            throw failure
        }
        if (profile.accountId != account.id) {
            credentials.clear()
            throw CloudIdentityChanged()
        }
        if (credential.username != account.username) {
            credentials.write(profile.id, credential.token, account.username)
            credentials.keepOnly(profile.id)
        }
        return CloudSession(profile, client, credential.token)
    }

    private suspend fun <T> serialized(action: suspend () -> T): T =
        withContext(Dispatchers.IO) { cloudSyncLock.withLock { action() } }
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

    data class Session(val token: String, val username: String?)

    fun read(profileId: String): String? = readSession(profileId)?.token

    fun readSession(profileId: String): Session? {
        val encoded = preferences.getString("session:$profileId", null) ?: preferences.getString("session", null) ?: return null
        val iv = preferences.getString("iv:$profileId", null) ?: preferences.getString("iv", null) ?: return null
        return try {
            val key = keyStore.getKey(alias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            cipher.updateAAD(profileId.toByteArray())
            val payload = cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)).decodeToString()
            if (payload.startsWith('{')) {
                val session = JSONObject(payload)
                Session(session.getString("token"), session.getString("username"))
            } else {
                Session(payload, null)
            }
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: JSONException) {
            null
        }
    }

    fun write(profileId: String, token: String, username: String) {
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
        val payload = JSONObject().put("token", token).put("username", username).toString()
        val encrypted = Base64.encodeToString(cipher.doFinal(payload.toByteArray()), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        if (!preferences.edit().putString("session:$profileId", encrypted).putString("iv:$profileId", iv).commit()) {
            throw IOException("Could not save the device session")
        }
    }

    fun remove(profileId: String) {
        preferences.edit().remove("session:$profileId").remove("iv:$profileId").apply()
    }

    fun keepOnly(profileId: String) {
        val editor = preferences.edit()
        for (key in preferences.all.keys) if (key != "session:$profileId" && key != "iv:$profileId") editor.remove(key)
        editor.apply()
    }

    fun clear() {
        if (!preferences.edit().clear().commit()) throw IOException("Could not remove the device session")
    }
}
