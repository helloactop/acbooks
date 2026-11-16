package app.acbooks.server

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

internal class ServerSession(
    val address: URI,
    val username: String,
    val token: String,
    val expiresAt: Long,
) {
    fun isExpired(): Boolean = expiresAt <= System.currentTimeMillis()
}

internal enum class ServerError { Address, Credentials, Expired, RateLimited, Tls, Connection, Response, Storage }

internal data class ServerAccount(val username: String, val avatarUpdatedAt: Long?)

private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024

internal class ServerFailure(val error: ServerError) : IOException()

internal class ServerSessions(context: Context) {
    private val store = ServerSessionStore(context.applicationContext)
    private val mutex = Mutex()
    private val current = MutableStateFlow<ServerSession?>(null)
    val session = current.asStateFlow()

    suspend fun restore() = withContext(Dispatchers.IO) {
        mutex.withLock { current.value = store.read() }
    }

    suspend fun test(address: String, existing: ServerSession? = null) = requestOnIo {
        val base = serverBaseAddress(address) ?: throw ServerFailure(ServerError.Address)
        if (existing == null) {
            val health = request(base, ServerEndpoint.Health, expected = 200)
            if (health.optString("status") != "ok") throw ServerFailure(ServerError.Response)
        } else {
            mutex.withLock {
                try {
                    checkAccount(existing)
                } catch (failure: ServerFailure) {
                    if (failure.error == ServerError.Expired && current.value === existing) {
                        store.clear()
                        current.value = null
                    }
                    throw failure
                }
            }
        }
    }

    suspend fun signIn(address: String, username: String, password: String) = requestOnIo {
        mutex.withLock {
            val base = serverBaseAddress(address) ?: throw ServerFailure(ServerError.Address)
            val response = request(
                base, ServerEndpoint.Login, expected = 200, method = "POST",
                body = JSONObject().put("username", username.trim()).put("password", password)
                    .put("deviceName", "acbooks Android"),
            )
            val token = response.getString("accessToken")
            val expiresAt = response.getLong("expiresAt")
            if (response.getString("tokenType") != "Bearer" || token.isBlank() ||
                token.any { it.code !in 33..126 } || expiresAt <= System.currentTimeMillis()
            ) throw ServerFailure(ServerError.Response)
            val newSession = ServerSession(base, username.trim(), token, expiresAt)
            try {
                val account = checkAccount(newSession)
                val verified = ServerSession(base, account.username, token, expiresAt)
                store.write(verified)
                current.value = verified
            } catch (failure: IOException) {
                runCatching { request(base, ServerEndpoint.Logout, 204, "POST", token = token) }
                throw failure
            }
        }
    }

    suspend fun signOut() = requestOnIo {
        mutex.withLock {
            val existing = current.value ?: return@withLock
            if (!existing.isExpired()) {
                try {
                    request(existing.address, ServerEndpoint.Logout, 204, "POST", token = existing.token)
                } catch (failure: ServerFailure) {
                    if (failure.error != ServerError.Expired) throw failure
                }
            }
            store.clear()
            current.value = null
        }
    }

    private fun checkAccount(session: ServerSession): ServerAccount {
        if (session.isExpired()) throw ServerFailure(ServerError.Expired)
        val account = request(session.address, ServerEndpoint.Account, 200, token = session.token)
        val username = account.optString("username").takeIf { it.isNotBlank() }
            ?: throw ServerFailure(ServerError.Response)
        val revision = if (account.isNull("avatarUpdatedAt")) null else account.optLong("avatarUpdatedAt")
        if (revision != null && revision <= 0) throw ServerFailure(ServerError.Response)
        return ServerAccount(username, revision)
    }

    suspend fun account(session: ServerSession): ServerAccount = requestOnIo { checkAccount(session) }

    suspend fun fetchAvatar(session: ServerSession): ByteArray = requestOnIo {
        if (session.isExpired()) throw ServerFailure(ServerError.Expired)
        val connection = session.address.endpoint(ServerEndpoint.Avatar).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "image/jpeg")
            connection.setRequestProperty("Authorization", "Bearer ${session.token}")
            val status = connection.responseCode
            if (status != 200) {
                throw ServerFailure(
                    when (status) {
                        401 -> ServerError.Expired
                        429 -> ServerError.RateLimited
                        in 500..599 -> ServerError.Connection
                        else -> ServerError.Response
                    },
                )
            }
            if (connection.contentType?.startsWith("image/") != true) {
                throw ServerFailure(ServerError.Response)
            }
            connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_AVATAR_BYTES) throw ServerFailure(ServerError.Response)
                }
                if (output.size() == 0) throw ServerFailure(ServerError.Response)
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun uploadAvatar(session: ServerSession, image: ByteArray): ServerAccount = requestOnIo {
        if (session.isExpired()) throw ServerFailure(ServerError.Expired)
        if (image.isEmpty() || image.size > MAX_AVATAR_BYTES) {
            throw ServerFailure(ServerError.Response)
        }
        val boundary = "acbooks-${java.util.UUID.randomUUID()}"
        val head = (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"avatar.jpg\"\r\n" +
                "Content-Type: image/jpeg\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val connection = session.address.endpoint(ServerEndpoint.Avatar)
            .openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(head.size + image.size + tail.size)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Authorization", "Bearer ${session.token}")
            connection.outputStream.use { stream ->
                stream.write(head)
                stream.write(image)
                stream.write(tail)
            }
            val status = connection.responseCode
            if (status != 200) {
                throw ServerFailure(
                    when (status) {
                        401 -> ServerError.Expired
                        429 -> ServerError.RateLimited
                        in 500..599 -> ServerError.Connection
                        else -> ServerError.Response
                    },
                )
            }
            val account = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                JSONObject(reader.readText())
            }
            val revision = if (account.isNull("avatarUpdatedAt")) null else account.optLong("avatarUpdatedAt")
            ServerAccount(account.optString("username"), revision)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> requestOnIo(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (failure: ServerFailure) {
            throw failure
        } catch (_: SSLException) {
            throw ServerFailure(ServerError.Tls)
        } catch (_: JSONException) {
            throw ServerFailure(ServerError.Response)
        } catch (_: IOException) {
            throw ServerFailure(ServerError.Connection)
        }
    }

    private fun request(
        base: URI,
        endpoint: ServerEndpoint,
        expected: Int,
        method: String = "GET",
        body: JSONObject? = null,
        token: String? = null,
    ): JSONObject {
        val connection = base.endpoint(endpoint).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.useCaches = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status != expected) {
                throw ServerFailure(
                    when (status) {
                        401 -> if (endpoint == ServerEndpoint.Login) ServerError.Credentials else ServerError.Expired
                        429 -> ServerError.RateLimited
                        in 500..599 -> ServerError.Connection
                        else -> ServerError.Response
                    },
                )
            }
            if (status == 204) return JSONObject()
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                val buffer = CharArray(8193)
                var length = 0
                while (length < buffer.size) {
                    val count = it.read(buffer, length, buffer.size - length)
                    if (count < 0) break
                    length += count
                }
                if (length == buffer.size) throw ServerFailure(ServerError.Response)
                JSONObject(String(buffer, 0, length))
            }
        } finally {
            connection.disconnect()
        }
    }
}

private class ServerSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("server_session", Context.MODE_PRIVATE)
    private val alias = "acbooks.server.session"

    fun read(): ServerSession? {
        val saved = preferences.getString("sealed", null) ?: return null
        return try {
            val packed = Base64.decode(saved, Base64.NO_WRAP)
            if (packed.size < 28) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            val data = JSONObject(cipher.doFinal(packed, 12, packed.size - 12).toString(Charsets.UTF_8))
            val address = serverBaseAddress(data.getString("address")) ?: return null
            ServerSession(address, data.getString("username"), data.getString("token"), data.getLong("expiresAt"))
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: ProviderException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: JSONException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    fun write(session: ServerSession) {
        val sealed = try {
            val payload = JSONObject().put("address", session.address.toASCIIString())
                .put("username", session.username).put("token", session.token).put("expiresAt", session.expiresAt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            if (cipher.iv.size != 12) throw ServerFailure(ServerError.Storage)
            Base64.encodeToString(cipher.iv + cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (_: GeneralSecurityException) {
            throw ServerFailure(ServerError.Storage)
        } catch (_: ProviderException) {
            throw ServerFailure(ServerError.Storage)
        } catch (_: IOException) {
            throw ServerFailure(ServerError.Storage)
        }
        if (!preferences.edit().putString("sealed", sealed).commit()) throw ServerFailure(ServerError.Storage)
    }

    fun clear() {
        if (!preferences.edit().remove("sealed").commit()) throw ServerFailure(ServerError.Storage)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build(),
            )
        }.generateKey()
    }
}
