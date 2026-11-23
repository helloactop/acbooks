package app.acbooks.reader

import org.json.JSONObject

/**
 * Per-book DataStore value, stored under the immutable local book ID.
 * Versioning keeps later reader settings additions compatible with old entries.
 */
internal fun ReaderTypographySettings.toBookOverrideJson(): String {
    val value = normalized()
    return JSONObject().apply {
        put("version", 1)
        put("fontFamily", value.fontFamily.name)
        put("fontSize", value.fontSize ?: JSONObject.NULL)
        put("fontWeight", value.fontWeight ?: JSONObject.NULL)
        put("lineHeight", value.lineHeight ?: JSONObject.NULL)
        put("pageMargins", value.pageMargins ?: JSONObject.NULL)
        put("paragraphIndent", value.paragraphIndent ?: JSONObject.NULL)
        put("paragraphSpacing", value.paragraphSpacing ?: JSONObject.NULL)
        put("letterSpacing", value.letterSpacing ?: JSONObject.NULL)
        put("wordSpacing", value.wordSpacing ?: JSONObject.NULL)
        put("textAlignment", value.textAlignment?.name ?: JSONObject.NULL)
        put("publisherStyles", value.publisherStyles ?: JSONObject.NULL)
        put("scroll", value.scroll)
        put("appearance", value.appearance.name)
        put("backgroundArgb", value.backgroundArgb ?: JSONObject.NULL)
        put("textArgb", value.textArgb ?: JSONObject.NULL)
    }.toString()
}

internal fun readerTypographyFromBookOverrideJson(raw: String): ReaderTypographySettings? =
    runCatching {
        val json = JSONObject(raw)
        if (json.optInt("version", -1) != 1) return@runCatching null
        ReaderTypographySettings(
            fontFamily = json.enumOr("fontFamily", ReaderFontFamily.BOOK),
            fontSize = json.doubleOrNull("fontSize"),
            fontWeight = json.doubleOrNull("fontWeight"),
            lineHeight = json.doubleOrNull("lineHeight"),
            pageMargins = json.doubleOrNull("pageMargins"),
            paragraphIndent = json.doubleOrNull("paragraphIndent"),
            paragraphSpacing = json.doubleOrNull("paragraphSpacing"),
            letterSpacing = json.doubleOrNull("letterSpacing"),
            wordSpacing = json.doubleOrNull("wordSpacing"),
            textAlignment = json.enumOrNull<ReaderTextAlignment>("textAlignment"),
            publisherStyles = json.opt("publisherStyles") as? Boolean,
            scroll = json.opt("scroll") as? Boolean ?: false,
            appearance = json.enumOr("appearance", ReaderAppearance.SYSTEM),
            backgroundArgb = (json.opt("backgroundArgb") as? Number)?.toInt(),
            textArgb = (json.opt("textArgb") as? Number)?.toInt(),
        ).normalized()
    }.getOrNull()

private fun JSONObject.doubleOrNull(key: String): Double? = (opt(key) as? Number)?.toDouble()

private inline fun <reified T : Enum<T>> JSONObject.enumOrNull(key: String): T? =
    enumValues<T>().firstOrNull { it.name == optString(key) }

private inline fun <reified T : Enum<T>> JSONObject.enumOr(key: String, fallback: T): T =
    enumOrNull<T>(key) ?: fallback
