package app.acbooks.reader

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign

/** Reader-default migration coverage, independent of shared device DataStore state. */
class ReaderTypographyDefaultTest {
    @Test
    fun anEmptyReaderDataStoreUsesTheNewDefaultTypography() {
        assertEquals(defaultReaderTypographySettings(), emptyPreferences().readGlobalTypography())
    }

    @Test
    fun bookOverridesAloneDoNotCountAsSavedGlobalTypography() {
        val onlyBookOverrides = mutablePreferencesOf(
            stringPreferencesKey("book_override_book-42") to "{\"version\":1}",
        )
        assertEquals(defaultReaderTypographySettings(), onlyBookOverrides.readGlobalTypography())
    }

    @Test
    fun anExistingExplicitOriginalBookStyleIsNotReplaced() {
        // Pre-upgrade save() always wrote these three keys, even with default values.
        val saved = mutablePreferencesOf(
            stringPreferencesKey("font_family") to "BOOK",
            booleanPreferencesKey("scroll") to false,
            stringPreferencesKey("appearance") to "SYSTEM",
        )
        assertEquals(ReaderTypographySettings(), saved.readGlobalTypography())
    }

    @Test
    fun newDefaultMapsDirectlyToReadiumWithoutRecreatingTheNavigator() {
        val settings = defaultReaderTypographySettings()
        val preferences = settings.toEpubPreferences(systemDark = false)

        assertEquals(FontFamily.SANS_SERIF, preferences.fontFamily)
        assertEquals(1.3, preferences.fontSize)
        assertEquals(1.5, preferences.lineHeight)
        assertEquals(2.0, preferences.paragraphIndent)
        assertEquals(0.0, preferences.paragraphSpacing)
        assertEquals(1.0, preferences.pageMargins)
        assertEquals(TextAlign.JUSTIFY, preferences.textAlign)
        assertEquals(false, preferences.publisherStyles)
        assertEquals(false, preferences.scroll)
    }
}
