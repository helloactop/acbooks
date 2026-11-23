package app.acbooks.library

import app.acbooks.core.database.LocalBook
import app.acbooks.core.database.RemoteCatalogBook
import app.acbooks.core.database.RemoteCatalogFile
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

internal fun serverOrigin(value: String): String {
    val uri = URI(value.trim())
    require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath in listOf("", "/") &&
        (uri.port == -1 || uri.port in 1..65535)
    )
    val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    return "https://${uri.host.lowercase()}$port"
}

internal class CloudHttpException(val status: Int) : IOException("Server responded with HTTP $status")

internal class LibraryCloudClient(private val origin: String) {
    fun login(username: String, password: String): String = json(
        path = "/auth/device-login",
        method = "POST",
        body = JSONObject().put("username", username).put("password", password).put("deviceName", "acbooks Android"),
    ).getString("accessToken").also { check(it.isNotBlank()) }

    fun accountId(token: String): String = json("/auth/me", token).getString("id").asUuid()

    fun logout(token: String) {
        request("/auth/logout", "POST", token, setOf(204)) { }
    }

    fun createBook(token: String, deviceId: String, book: LocalBook) {
        val name = UUID.nameUUIDFromBytes("acbooks:book.create:${book.id}".toByteArray(UTF_8)).toString()
        val data = JSONObject().put("title", book.title.take(500))
        book.author?.takeIf(String::isNotBlank)?.let { data.put("authors", JSONArray().put(it.take(200))) }
        book.description?.let { data.put("description", it.take(20_000)) }
        book.series?.let { data.put("series", it.take(200)) }
        val mutation = JSONObject().put("mutationId", name).put("entityId", book.id.asUuid())
            .put("baseRevision", 0).put("action", "book.create").put("data", data)
        val response = json("/sync/mutations", token, "POST",
            JSONObject().put("deviceId", deviceId).put("mutations", JSONArray().put(mutation)))
        val receipt = response.getJSONArray("receipts").getJSONObject(0)
        val current = receipt.optJSONObject("current")
        if (receipt.getString("mutationId") != name || receipt.getString("status") != "accepted" ||
            current == null || current.getString("entityId") != book.id || current.getString("operation") != "upsert"
        ) throw IOException("Server did not confirm that the book was created")
    }

    fun upload(token: String, bookId: String, format: String, file: File): JSONObject {
        require(format in setOf("epub", "pdf"))
        val boundary = UUID.randomUUID().toString()
        val head = ("--$boundary\r\nContent-Disposition: form-data; name=\"bookId\"\r\n\r\n${bookId.asUuid()}\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"book.$format\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n").toByteArray(UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(UTF_8)
        return request(
            "/uploads?reuseExisting=true", "POST", token, setOf(200, 201),
            contentType = "multipart/form-data; boundary=$boundary",
            length = head.size + file.length() + tail.size,
            write = { output ->
                output.write(head)
                file.inputStream().use { it.copyTo(output) }
                output.write(tail)
            },
            read = ::readJson,
        )
    }

    fun delete(token: String, bookId: String) {
        request("/books/${bookId.asUuid()}", "DELETE", token, setOf(204)) { }
    }

    fun catalog(token: String, profileId: String): CatalogSnapshot {
        val books = mutableMapOf<String, RemoteCatalogBook>()
        val files = mutableMapOf<String, RemoteCatalogFile>()
        val cachedAt = System.currentTimeMillis()
        val through = pull(token, "snapshot", 0) { record ->
            applyCatalogRecord(record, profileId, cachedAt, books, files)
        }
        pull(token, "changes", through) { record ->
            applyCatalogRecord(record, profileId, cachedAt, books, files)
        }
        return CatalogSnapshot(books.values.toList(), files.values.filter { it.remoteBookId in books })
    }

    private fun pull(token: String, route: String, start: Long, accept: (JSONObject) -> Unit): Long {
        var after = start
        var through: Long? = null
        while (true) {
            val boundary = through?.let { "&through=$it" }.orEmpty()
            val page = json("/sync/$route?after=$after$boundary&limit=100", token)
            val end = page.getLong("through")
            val next = page.getLong("nextCursor")
            val more = page.getBoolean("hasMore")
            if ((through != null && end != through) || next !in after..end || (more && next <= after)) {
                throw IOException("Invalid server catalog page")
            }
            val items = page.getJSONArray("items")
            for (index in 0 until items.length()) accept(items.getJSONObject(index))
            if (!more) return end
            after = next
            through = end
        }
    }

    fun <T> download(token: String, remoteFileId: String, read: (HttpsURLConnection) -> T): T =
        request("/files/${remoteFileId.asUuid()}/content", "GET", token, setOf(200), read = read)

    fun <T> cover(token: String, bookId: String, version: Long, read: (HttpsURLConnection) -> T): T =
        request("/books/${bookId.asUuid()}/cover?v=$version", "GET", token, setOf(200), read = read)

    private fun json(path: String, token: String? = null, method: String = "GET", body: JSONObject? = null): JSONObject {
        val bytes = body?.toString()?.toByteArray(UTF_8)
        return request(path, method, token, setOf(200), "application/json", bytes?.size?.toLong(),
            write = { output -> if (bytes != null) output.write(bytes) }, read = ::readJson)
    }

    private fun <T> request(
        path: String,
        method: String,
        token: String?,
        accepted: Set<Int>,
        contentType: String? = null,
        length: Long? = null,
        write: (OutputStream) -> Unit = {},
        read: (HttpsURLConnection) -> T,
    ): T {
        val connection = URI("$origin/api/v1$path").toURL().openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (length != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(length)
                connection.outputStream.use(write)
            }
            val status = connection.responseCode
            if (status !in accepted) throw CloudHttpException(status)
            return read(connection)
        } finally {
            connection.disconnect()
        }
    }
}

internal data class CatalogSnapshot(val books: List<RemoteCatalogBook>, val files: List<RemoteCatalogFile>)

private fun readJson(connection: HttpsURLConnection): JSONObject =
    connection.inputStream.bufferedReader(UTF_8).use { JSONObject(it.readText()) }

private fun applyCatalogRecord(
    record: JSONObject,
    profileId: String,
    cachedAt: Long,
    books: MutableMap<String, RemoteCatalogBook>,
    files: MutableMap<String, RemoteCatalogFile>,
) {
    val type = record.getString("entityType")
    if (type != "book" && type != "book_file") return
    val id = record.getString("entityId").asUuid()
    when (record.getString("operation")) {
        "delete" -> if (type == "book") books.remove(id) else files.remove(id)
        "upsert" -> {
            val data = record.getJSONObject("data")
            if (data.getString("id").asUuid() != id) throw IOException("Invalid server catalog ID")
            if (type == "book") {
                val authors = data.getJSONArray("authors")
                val author = (0 until authors.length()).joinToString(", ") { authors.getString(it) }
                val updatedAt = data.getLong("updatedAt")
                val coverUpdatedAt = if (data.getBoolean("hasCover")) data.optLong("coverUpdatedAt", updatedAt) else null
                books[id] = RemoteCatalogBook(profileId, id, data.getString("title"), author.takeIf(String::isNotEmpty),
                    data.optionalString("description"), true, updatedAt, cachedAt, coverUpdatedAt)
            } else {
                files[id] = RemoteCatalogFile(profileId, id, data.getString("bookId").asUuid(), data.getString("format"),
                    data.getLong("sizeBytes"), data.optionalString("sha256"), data.getString("fileStatus"), cachedAt)
            }
        }
        else -> throw IOException("Invalid server catalog operation")
    }
}

internal fun String.asUuid(): String = UUID.fromString(this).toString().also { require(it == this) }

private fun JSONObject.optionalString(name: String): String? = if (isNull(name)) null else getString(name)
