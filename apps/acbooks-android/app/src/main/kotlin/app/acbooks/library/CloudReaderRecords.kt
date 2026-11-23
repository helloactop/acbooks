package app.acbooks.library

import app.acbooks.core.database.CloudReaderRecord
import app.acbooks.core.database.LibraryDao
import app.acbooks.core.database.LocalReadingSession
import app.acbooks.core.database.PendingCloudUpload
import java.io.IOException
import java.util.UUID
import org.json.JSONException
import org.json.JSONObject

internal val readerRecordKinds = setOf("reading_session", "bookmark", "annotation")
internal val PendingCloudUpload.isReaderRecord: Boolean get() = id.startsWith("reader:")

internal fun readCloudReaderRecord(profileId: String, record: JSONObject): CloudReaderRecord {
    val kind = record.getString("entityType")
    val id = record.getString("entityId").asUuid()
    val revision = record.getLong("revision")
    if (kind !in readerRecordKinds || revision <= 0) throw IOException("Invalid cloud reading record")
    return when (record.getString("operation")) {
        "delete" -> CloudReaderRecord(profileId, kind, id, revision, null, null)
        "upsert" -> {
            val data = record.getJSONObject("data")
            if (id != data.getString("id")) throw IOException("Invalid cloud reading record ID")
            CloudReaderRecord(profileId, kind, id, revision, data.getString("bookId").asUuid(), data.toString())
        }
        else -> throw IOException("Invalid cloud reading record operation")
    }
}

internal fun CloudReaderRecord.completedSession(localBookId: String): LocalReadingSession? {
    if (kind != "reading_session" || payload == null) return null
    return try {
        val data = JSONObject(payload)
        if (data.isNull("endedAt")) return null
        LocalReadingSession(id, localBookId, null, data.optString("deviceId").takeUnless { it.isBlank() || it == "null" },
            data.getLong("startedAt"), data.getLong("endedAt"), data.getLong("activeSeconds"), data.getString("source"))
    } catch (_: JSONException) {
        null
    }
}

internal suspend fun LibraryDao.enqueueReaderRecords(profileId: String) {
    val pending = mutableListOf<PendingCloudUpload>()
    suspend fun add(kind: String, id: String, bookId: String, localFileId: String?, data: JSONObject) {
        val book = remoteBookLink(profileId, bookId) ?: return
        if (remoteCatalogBook(profileId, book.remoteBookId) == null) return
        if (localFileId != null) {
            val file = cloudReadingFile(profileId, localFileId) ?: return
            if (file.remoteBookId != book.remoteBookId) return
            data.put("bookFileId", file.remoteFileId)
        }
        val mutationId = UUID.nameUUIDFromBytes("acbooks:$kind.create:$id".toByteArray(Charsets.UTF_8))
        val payload = JSONObject().put("action", "$kind.create").put("entityId", id.asUuid())
            .put("bookId", book.remoteBookId).put("baseRevision", 0)
            .put("mutationId", mutationId.toString()).put("data", data)
        pending.add(PendingCloudUpload(profileId, "reader:$kind:$id", bookId, localFileId, payload.toString()))
    }

    for (session in unqueuedReadingSessions(profileId)) {
        if (session.endedAt == null || (session.source == "automatic" && session.fileId == null)) continue
        add("reading_session", session.id, session.bookId, session.fileId,
            JSONObject().put("source", session.source).put("startedAt", session.startedAt)
                .put("endedAt", session.endedAt).put("activeSeconds", session.activeSeconds))
    }
    for (mark in unqueuedBookmarks(profileId)) {
        val data = JSONObject().put("label", mark.label ?: JSONObject.NULL)
        if (mark.locator?.let { !data.putLocator(it) } == true) continue
        if (mark.label.isNullOrBlank() && mark.locator == null) continue
        add("bookmark", mark.id, mark.bookId, mark.fileId, data)
    }
    for (mark in unqueuedAnnotations(profileId)) {
        if (mark.quote.isNullOrBlank() && mark.note.isNullOrBlank()) continue
        val data = JSONObject().put("quote", mark.quote ?: JSONObject.NULL).put("note", mark.note ?: JSONObject.NULL)
        if (mark.locator?.let { !data.putLocator(it) } == true) continue
        add("annotation", mark.id, mark.bookId, mark.fileId, data)
    }
    if (pending.isNotEmpty()) enqueueCloudUploads(pending)
}

private fun JSONObject.putLocator(raw: String): Boolean = try {
    put("locator", JSONObject(raw))
    true
} catch (_: JSONException) {
    false
}
