package app.acbooks.reader

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.readerTypographyDataStore by preferencesDataStore(name = "reader_typography")

private object Keys {
    val fontFamily = stringPreferencesKey("font_family")
    val fontSize = doublePreferencesKey("font_size")
    val fontWeight = doublePreferencesKey("font_weight")
    val lineHeight = doublePreferencesKey("line_height")
    val pageMargins = doublePreferencesKey("page_margins")
    val paragraphIndent = doublePreferencesKey("paragraph_indent")
    val paragraphSpacing = doublePreferencesKey("paragraph_spacing")
    val letterSpacing = doublePreferencesKey("letter_spacing")
    val wordSpacing = doublePreferencesKey("word_spacing")
    val textAlignment = stringPreferencesKey("text_alignment")
    val publisherStyles = booleanPreferencesKey("publisher_styles")
    val scroll = booleanPreferencesKey("scroll")
    val appearance = stringPreferencesKey("appearance")
    val backgroundArgb = intPreferencesKey("background_argb")
    val textArgb = intPreferencesKey("text_argb")
}

/**
 * One application-scoped DataStore, independent of the server account or a local book.
 * Settings not specified by the user remain absent to preserve EPUB publisher defaults.
 */
internal class ReaderTypographyStore(context: Context) {
    private val dataStore = context.applicationContext.readerTypographyDataStore

    val settings: Flow<ReaderTypographySettings> = dataStore.data
        .catch { failure ->
            if (failure !is IOException) throw failure
            Log.w("Reader", "Could not load typography preferences", failure)
            emit(emptyPreferences())
        }
        .map { saved ->
            ReaderTypographySettings(
                fontFamily = saved[Keys.fontFamily].enumOr(ReaderFontFamily.BOOK),
                fontSize = saved[Keys.fontSize],
                fontWeight = saved[Keys.fontWeight],
                lineHeight = saved[Keys.lineHeight],
                pageMargins = saved[Keys.pageMargins],
                paragraphIndent = saved[Keys.paragraphIndent],
                paragraphSpacing = saved[Keys.paragraphSpacing],
                letterSpacing = saved[Keys.letterSpacing],
                wordSpacing = saved[Keys.wordSpacing],
                textAlignment = saved[Keys.textAlignment].enumOrNull<ReaderTextAlignment>(),
                publisherStyles = saved[Keys.publisherStyles],
                scroll = saved[Keys.scroll] ?: false,
                appearance = saved[Keys.appearance].enumOr(ReaderAppearance.SYSTEM),
                backgroundArgb = saved[Keys.backgroundArgb],
                textArgb = saved[Keys.textArgb],
            ).normalized()
        }
        .distinctUntilChanged()

    suspend fun save(value: ReaderTypographySettings) {
        val saved = value.normalized()
        dataStore.edit { prefs ->
            prefs[Keys.fontFamily] = saved.fontFamily.name
            prefs.setOptional(Keys.fontSize, saved.fontSize)
            prefs.setOptional(Keys.fontWeight, saved.fontWeight)
            prefs.setOptional(Keys.lineHeight, saved.lineHeight)
            prefs.setOptional(Keys.pageMargins, saved.pageMargins)
            prefs.setOptional(Keys.paragraphIndent, saved.paragraphIndent)
            prefs.setOptional(Keys.paragraphSpacing, saved.paragraphSpacing)
            prefs.setOptional(Keys.letterSpacing, saved.letterSpacing)
            prefs.setOptional(Keys.wordSpacing, saved.wordSpacing)
            prefs.setOptional(Keys.textAlignment, saved.textAlignment?.name)
            prefs.setOptional(Keys.publisherStyles, saved.publisherStyles)
            prefs[Keys.scroll] = saved.scroll
            prefs[Keys.appearance] = saved.appearance.name
            prefs.setOptional(Keys.backgroundArgb, saved.backgroundArgb)
            prefs.setOptional(Keys.textArgb, saved.textArgb)
        }
    }
}

private inline fun <reified T : Enum<T>> String?.enumOrNull(): T? =
    enumValues<T>().firstOrNull { it.name == this }

private inline fun <reified T : Enum<T>> String?.enumOr(default: T): T =
    enumOrNull<T>() ?: default

private fun <T> MutablePreferences.setOptional(key: Preferences.Key<T>, value: T?) {
    if (value == null) remove(key) else this[key] = value
}
