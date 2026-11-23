package app.acbooks.library

import app.acbooks.core.database.CloudCatalogBatch
import app.acbooks.core.database.CloudCatalogVersion
import app.acbooks.core.database.CloudReaderRecord
import app.acbooks.core.database.LocalBook
import app.acbooks.core.database.PendingReadingPosition
import app.acbooks.core.database.PendingCloudUpload
import app.acbooks.core.database.ReadingPositionChange
import app.acbooks.core.database.RemoteCatalogBook
import app.acbooks.core.database.RemoteCatalogFile
import app.acbooks.core.database.RemoteShelf
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun serverOrigin(value: String): String {
    val address = value.trim()
    val uri = try {
        URI(if ("://" in address) address else "https://$address")
    } catch (failure: URISyntaxException) {
        throw IllegalArgumentException("Invalid server address", failure)
    }
    require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath in listOf("", "/") &&
        (uri.port == -1 || uri.port in 1..65535)
    )
    val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    return "https://${uri.host.lowercase()}$port"
}

internal class CloudHttpException(val status: Int) : IOException("Server responded with HTTP $status")
internal class CloudMutationRejected(val code: String) : IOException("Server rejected the book creation")
internal class CloudSetupRequired : IOException("Server setup is required")
internal data class CloudAccount(val id: String, val username: String)

internal fun bookCreatePayload(book: LocalBook, bibliography: BookBibliography? = null,
    authors: List<String> = listOfNotNull(book.author), translators: List<String> = bibliography?.translators.orEmpty(),
    isbn: String? = bibliography?.isbn, tags: List<String> = emptyList(), shelves: List<String> = emptyList(),
): String {
    val mutationId = UUID.nameUUIDFromBytes("acbooks:book.create:${book.id}".toByteArray(UTF_8)).toString()
    val data = JSONObject().put("title", book.title.take(500))
    for ((key, names) in mapOf("authors" to authors, "translators" to translators, "tags" to tags, "shelves" to shelves)) {
        val values = names.map { it.trim().take(200) }.filter(String::isNotEmpty).distinct().take(20)
        if (values.isNotEmpty()) data.put(key, JSONArray(values))
    }
    (book.description ?: bibliography?.description)?.takeIf(String::isNotBlank)?.let { data.put("description", it.take(20_000)) }
    book.series?.let { data.put("series", it.take(200)) }
    book.publisher?.let { data.put("publisher", it.take(200)) }
    book.publicationDate?.let { data.put("publicationDate", it.publicationDateOnly().take(80)) }
    isbn?.filterNot { it.isWhitespace() || it == '-' }?.uppercase()
        ?.takeIf { it.matches(Regex("(?:[0-9]{13}|[0-9]{9}[0-9X])")) }?.let { data.put("isbn", it) }
    if (book.readingStatus != "unread") data.put("readingStatus", book.readingStatus)
    return JSONObject().put("mutationId", mutationId).put("entityId", book.id.asUuid())
        .put("baseRevision", 0).put("action", "book.create").put("data", data).toString()
}

internal class LibraryCloudClient(private val origin: String) {
    fun login(username: String, password: String): String = json(
        path = "/auth/device-login",
        method = "POST",
        body = JSONObject().put("username", username).put("password", password).put("deviceName", "acbooks Android"),
    ).getString("accessToken").also { check(it.isNotBlank()) }

    fun account(token: String): CloudAccount {
        val response = json("/auth/me", token)
        return CloudAccount(response.getString("id").asUuid(), response.getString("username").also { check(it.isNotBlank()) })
    }

    fun testAddress() {
        val ready = json("/ready")
        if (ready.getString("status") != "ok" || ready.getString("migration") != "current") {
            throw IOException("Server is not ready")
        }
        if (!json("/setup/status").getBoolean("initialized")) throw CloudSetupRequired()
    }

    fun searchDoubanMetadata(token: String, query: String): List<DoubanMetadataCandidate> {
        val encoded = URLEncoder.encode(query, UTF_8.name())
        return request("/metadata/candidates?source=douban&q=$encoded", "GET", token, setOf(200)) { response ->
            response.inputStream.bufferedReader(UTF_8).use { parseDoubanCandidates(it.readText()) }
        }
    }

    fun logout(token: String, timeout: Int? = null) {
        request("/auth/logout", "POST", token, setOf(204), timeout = timeout) { }
    }

    fun createBook(token: String, deviceId: String, bookId: String, payload: String) {
        val mutation = JSONObject(payload)
        check(mutation.getString("entityId") == bookId)
        val response = json("/sync/mutations", token, "POST",
            JSONObject().put("deviceId", deviceId).put("mutations", JSONArray().put(mutation)))
        val receipt = response.getJSONArray("receipts").getJSONObject(0)
        if (receipt.getString("mutationId") != mutation.getString("mutationId")) throw IOException("Invalid mutation receipt")
        val status = receipt.getString("status")
        if (status == "conflict" || status == "rejected") {
            val code = receipt.optString("code").takeIf { it.matches(Regex("[A-Z_]{1,64}")) }
            throw CloudMutationRejected(code ?: status.uppercase())
        }
        val current = receipt.optJSONObject("current")
        if (status != "accepted" || current == null || current.getString("entityType") != "book" ||
            current.getString("entityId") != bookId || current.getString("operation") != "upsert"
        ) throw IOException("Server did not confirm that the book was created")
    }

    fun updateBook(token: String, deviceId: String, bookId: String, revision: Long, editId: String, data: JSONObject): Boolean {
        val mutationId = UUID.nameUUIDFromBytes("acbooks:book.update:$editId:$revision".toByteArray(UTF_8)).toString()
        val mutation = JSONObject().put("mutationId", mutationId).put("entityId", bookId.asUuid())
            .put("baseRevision", revision).put("action", "book.update").put("data", data)
        val response = json("/sync/mutations", token, "POST",
            JSONObject().put("deviceId", deviceId).put("mutations", JSONArray().put(mutation)))
        val receipt = response.getJSONArray("receipts").getJSONObject(0)
        if (receipt.getString("mutationId") != mutationId) throw IOException("Invalid book update receipt")
        return when (receipt.getString("status")) {
            "accepted" -> {
                val current = receipt.getJSONObject("current")
                if (current.getString("entityId") != bookId || current.getString("operation") != "upsert") {
                    throw IOException("Server did not confirm the book update")
                }
                true
            }
            "conflict" -> false
            else -> throw IOException("Server rejected the book update")
        }
    }

    fun uploadCover(token: String, bookId: String, file: File) {
        val boundary = UUID.randomUUID().toString()
        val head = ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cover.jpg\"\r\n" +
            "Content-Type: image/jpeg\r\n\r\n").toByteArray(UTF_8)
        uploadPart("/books/${bookId.asUuid()}/cover", "PUT", token, file, boundary, head, setOf(200))
    }

    fun upload(token: String, bookId: String, format: String, file: File): JSONObject {
        require(format in setOf("epub", "pdf"))
        val boundary = UUID.randomUUID().toString()
        val head = ("--$boundary\r\nContent-Disposition: form-data; name=\"bookId\"\r\n\r\n${bookId.asUuid()}\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"book.$format\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n").toByteArray(UTF_8)
        return uploadPart("/uploads?reuseExisting=true", "POST", token, file, boundary, head, setOf(200, 201))
    }

    private fun uploadPart(
        path: String, method: String, token: String, file: File, boundary: String, head: ByteArray, accepted: Set<Int>,
    ): JSONObject {
        val tail = "\r\n--$boundary--\r\n".toByteArray(UTF_8)
        return request(
            path, method, token, accepted,
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

    fun uploadReaderRecord(token: String, deviceId: String, upload: PendingCloudUpload): CloudReaderRecord {
        val mutation = JSONObject(checkNotNull(upload.payload))
        val response = json("/sync/mutations", token, "POST",
            JSONObject().put("deviceId", deviceId).put("mutations", JSONArray().put(mutation)))
        val receipt = response.getJSONArray("receipts").getJSONObject(0)
        if (receipt.getString("mutationId") != mutation.getString("mutationId")) throw IOException("Invalid reading record receipt")
        when (receipt.getString("status")) {
            "rejected" -> throw CloudMutationRejected(receipt.optString("code", "REJECTED"))
            "accepted", "conflict" -> {
                val current = receipt.optJSONObject("current") ?: throw IOException("Missing cloud reading record")
                val record = readCloudReaderRecord(upload.profileId, current)
                if (upload.id != "reader:${record.kind}:${record.id}" ||
                    (record.remoteBookId != null && record.remoteBookId != mutation.getString("bookId"))
                ) throw IOException("Invalid cloud reading record receipt")
                return record
            }
            else -> throw IOException("Unknown cloud reading record receipt")
        }
    }

    fun saveReadingPosition(token: String, deviceId: String, pending: PendingReadingPosition): ReadingPositionChange? {
        if (pending.locator.toByteArray(UTF_8).size > 32_768) throw CloudMutationRejected("INVALID_LOCATOR")
        val locator = try {
            JSONObject(pending.locator)
        } catch (_: JSONException) {
            throw CloudMutationRejected("INVALID_LOCATOR")
        }
        val data = JSONObject().put("locator", locator)
            .put("percent", pending.percent ?: JSONObject.NULL)
        val mutation = JSONObject().put("action", "reading_position.save")
            .put("entityId", pending.remoteFileId).put("baseRevision", pending.baseRevision)
            .put("mutationId", pending.mutationId).put("data", data)
        val response = json("/sync/mutations", token, "POST",
            JSONObject().put("deviceId", deviceId).put("mutations", JSONArray().put(mutation)))
        val receipt = response.getJSONArray("receipts").getJSONObject(0)
        if (receipt.getString("mutationId") != pending.mutationId) throw IOException("Invalid reading position receipt")
        val current = receipt.optJSONObject("current")?.let(::readPositionChange)
        if (current != null && current.fileId != pending.remoteFileId) throw IOException("Invalid reading position file")
        return when (receipt.getString("status")) {
            "accepted" -> current?.takeIf { it.locator != null && it.revision > pending.baseRevision }
                ?: throw IOException("Missing accepted reading position")
            "conflict" -> current
            "rejected" -> throw CloudMutationRejected(receipt.optString("code", "REJECTED"))
            else -> throw IOException("Unknown reading position receipt")
        }
    }

    fun catalog(token: String, profileId: String, after: Long): CloudCatalogBatch {
        val books = mutableMapOf<String, RemoteCatalogBook>()
        val files = mutableMapOf<String, RemoteCatalogFile>()
        val versions = mutableMapOf<Pair<String, String>, CloudCatalogVersion>()
        val positions = mutableMapOf<String, ReadingPositionChange>()
        val readerRecords = mutableMapOf<Pair<String, String>, CloudReaderRecord>()
        val cachedAt = System.currentTimeMillis()
        val accept: (JSONObject) -> Unit = { record ->
            when (record.getString("entityType")) {
                "reading_position" -> {
                    val position = readPositionChange(record)
                    positions[position.fileId] = position
                }
                in readerRecordKinds -> {
                    val reading = readCloudReaderRecord(profileId, record)
                    readerRecords[reading.kind to reading.id] = reading
                }
                else -> applyCatalogRecord(record, profileId, cachedAt, books, files, versions)
            }
        }
        // Bootstrapping from the change stream retains historical deletions for existing local links.
        val through = pullChanges(token, after, accept)
        val cursor = if (after == 0L) pullChanges(token, through, accept) else through
        return CloudCatalogBatch(cursor, books.values.toList(), files.values.toList(), versions.values.toList(),
            positions.values.toList(), readerRecords.values.toList())
    }

    fun createShelf(token: String, name: String): RemoteShelf =
        json("/organization/shelf", token, "POST", JSONObject().put("name", name)).toRemoteShelf()

    fun renameShelf(token: String, id: String, name: String) {
        val body = JSONObject().put("name", name).toString().toByteArray(UTF_8)
        request("/organization/shelf/${id.asUuid()}", "PATCH", token, setOf(204), "application/json",
            body.size.toLong(), write = { it.write(body) }) { }
    }

    fun deleteShelf(token: String, id: String) {
        request("/organization/shelf/${id.asUuid()}", "DELETE", token, setOf(204, 404)) { }
    }

    fun listShelves(token: String): List<RemoteShelf>? {
        val shelves = linkedMapOf<String, RemoteShelf>()
        var after: String? = null
        while (true) {
            val cursor = after?.let { "&after=$it" }.orEmpty()
            val page = try {
                json("/books/shelves?limit=100$cursor", token)
            } catch (failure: CloudHttpException) {
                // Older servers route this path to the Book UUID endpoint.
                if (after == null && failure.status in setOf(404, 422)) return null
                throw failure
            }
            val items = page.getJSONArray("items")
            var last = after
            for (index in 0 until items.length()) {
                val shelf = items.getJSONObject(index).toRemoteShelf()
                if (last != null && shelf.id <= last) throw IOException("Invalid server shelf page")
                shelves[shelf.id] = shelf
                last = shelf.id
            }
            val next = page.optionalString("nextAfter") ?: break
            if (items.length() == 0 || next.asUuid() != last) throw IOException("Invalid server shelf cursor")
            after = next
        }

        return shelves.values.toList()
    }

    private fun pullChanges(token: String, start: Long, accept: (JSONObject) -> Unit): Long {
        var after = start
        var through: Long? = null
        while (true) {
            val boundary = through?.let { "&through=$it" }.orEmpty()
            val page = json("/sync/changes?after=$after$boundary&limit=100", token)
            val end = page.getLong("through")
            val next = page.getLong("nextCursor")
            val more = page.getBoolean("hasMore")
            if ((through != null && end != through) || next !in after..end ||
                (more && next <= after) || (!more && next != end)
            ) {
                throw IOException("Invalid server catalog page")
            }
            val items = page.getJSONArray("items")
            for (index in 0 until items.length()) {
                val record = items.getJSONObject(index)
                if (record.getLong("revision") !in (after + 1)..next) throw IOException("Invalid catalog revision")
                accept(record)
            }
            if (!more) return next
            after = next
            through = end
        }
    }

    fun <T> download(token: String, remoteFileId: String, offset: Long, sha256: String?, read: (HttpsURLConnection) -> T): T {
        val headers = if (offset > 0) mapOf("Range" to "bytes=$offset-", "If-Range" to "\"${checkNotNull(sha256)}\"")
            else emptyMap()
        return request("/files/${remoteFileId.asUuid()}/content", "GET", token, setOf(200, 206), headers = headers, read = read)
    }

    fun <T> cover(token: String, bookId: String, version: Long, read: (HttpsURLConnection) -> T): T =
        request("/books/${bookId.asUuid()}/cover?v=$version", "GET", token, setOf(200), read = read)

    fun coverMatches(token: String, bookId: String, version: Long, expected: File): Boolean =
        cover(token, bookId, version) { response ->
            if (response.contentLengthLong >= 0 && response.contentLengthLong != expected.length()) return@cover false
            response.inputStream.use { stream ->
                val bytes = expected.readBytes()
                var offset = 0
                val buffer = ByteArray(8192)
                while (offset < bytes.size) {
                    val length = stream.read(buffer, 0, minOf(buffer.size, bytes.size - offset))
                    if (length < 0 || (0 until length).any { buffer[it] != bytes[offset + it] }) return@cover false
                    offset += length
                }
                stream.read() == -1
            }
        }

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
        headers: Map<String, String> = emptyMap(),
        timeout: Int? = null,
        read: (HttpsURLConnection) -> T,
    ): T {
        val connection = URI("$origin/api/v1$path").toURL().openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeout ?: 10_000
            connection.readTimeout = timeout ?: 30_000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("Accept-Encoding", "identity")
            for ((name, value) in headers) connection.setRequestProperty(name, value)
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

private fun readJson(connection: HttpsURLConnection): JSONObject =
    connection.inputStream.bufferedReader(UTF_8).use { JSONObject(it.readText()) }

private fun readPositionChange(record: JSONObject): ReadingPositionChange {
    if (record.getString("entityType") != "reading_position") throw IOException("Invalid reading position type")
    val fileId = record.getString("entityId").asUuid()
    val revision = record.getLong("revision")
    if (revision <= 0) throw IOException("Invalid reading position revision")
    return when (record.getString("operation")) {
        "delete" -> ReadingPositionChange(fileId, revision, null)
        "upsert" -> {
            val data = record.getJSONObject("data")
            if (data.getString("bookFileId") != fileId) throw IOException("Invalid reading position file")
            val percent = if (data.isNull("percent")) null else data.getDouble("percent")
            val updatedAt = data.getLong("updatedAt")
            if ((percent != null && percent !in 0.0..100.0) || updatedAt !in 0..9_007_199_254_740_991L) {
                throw IOException("Invalid reading position data")
            }
            ReadingPositionChange(fileId, revision, data.getJSONObject("locator").toString(), percent, updatedAt)
        }
        else -> throw IOException("Invalid reading position operation")
    }
}

private fun applyCatalogRecord(
    record: JSONObject,
    profileId: String,
    cachedAt: Long,
    books: MutableMap<String, RemoteCatalogBook>,
    files: MutableMap<String, RemoteCatalogFile>,
    versions: MutableMap<Pair<String, String>, CloudCatalogVersion>,
) {
    val type = record.getString("entityType")
    if (type != "book" && type != "book_file") return
    val id = record.getString("entityId").asUuid()
    val operation = record.getString("operation")
    when (operation) {
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
                    data.optionalString("description"), true, updatedAt, cachedAt, coverUpdatedAt, data.toString())
            } else {
                files[id] = RemoteCatalogFile(profileId, id, data.getString("bookId").asUuid(), data.getString("format"),
                    data.getLong("sizeBytes"), data.optionalString("sha256"), data.getString("fileStatus"), cachedAt)
            }
        }
        else -> throw IOException("Invalid server catalog operation")
    }
    versions[type to id] = CloudCatalogVersion(profileId, type, id, record.getLong("revision"), operation == "delete")
}

internal fun String.asUuid(): String = UUID.fromString(this).toString().also { require(it == this) }

private fun JSONObject.optionalString(name: String): String? = if (isNull(name)) null else getString(name)

internal fun JSONObject.toRemoteShelf(): RemoteShelf =
    RemoteShelf(getString("id").asUuid(), getString("name"))
