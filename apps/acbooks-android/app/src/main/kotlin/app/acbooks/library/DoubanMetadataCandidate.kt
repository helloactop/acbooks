package app.acbooks.library

import org.json.JSONArray
import org.json.JSONObject

internal data class DoubanMetadataCandidate(
    val doubanId: String,
    val title: String,
    val authors: List<String>,
    val translators: List<String>,
    val isbn: String?,
    val publisher: String?,
    val publicationDate: String?,
    val series: String?,
    val description: String?,
    val rating: Double?,
    val tags: List<String>,
    val coverUrl: String?,
)

internal fun parseDoubanCandidates(payload: String): List<DoubanMetadataCandidate> {
    val rows = JSONArray(payload)
    return List(rows.length()) { index ->
        val row = rows.getJSONObject(index)
        DoubanMetadataCandidate(
            doubanId = row.getString("doubanId"),
            title = row.getString("title"),
            authors = row.stringList("authors"),
            translators = row.stringList("translators"),
            isbn = row.stringOrNull("isbn"),
            publisher = row.stringOrNull("publisher"),
            publicationDate = row.stringOrNull("publicationDate"),
            series = row.stringOrNull("series"),
            description = row.stringOrNull("description"),
            rating = if (row.isNull("rating")) null else row.getDouble("rating"),
            tags = row.stringList("tags"),
            coverUrl = row.stringOrNull("doubanCoverUrl"),
        )
    }
}

private fun JSONObject.stringOrNull(name: String): String? =
    if (isNull(name)) null else getString(name)

private fun JSONObject.stringList(name: String): List<String> {
    val values = getJSONArray(name)
    return List(values.length()) { values.getString(it) }
}
