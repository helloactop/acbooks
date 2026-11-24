package app.acbooks.reader

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme

/** Run on an Android device because Readium Theme uses android.graphics.Color. */
class ReaderTypographyIntegrationTest {
    @Test
    fun persistedSettingsRestoreAndMapToReadium(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ReaderTypographyStore(context)
        val original = store.settings.first()
        val expected = ReaderTypographySettings(
            fontFamily = ReaderFontFamily.SERIF,
            fontSize = 1.4,
            lineHeight = 1.6,
            pageMargins = 1.5,
            paragraphIndent = 2.0,
            paragraphSpacing = 0.4,
            textAlignment = ReaderTextAlignment.JUSTIFY,
            publisherStyles = false,
            scroll = true,
            appearance = ReaderAppearance.SEPIA,
        )
        try {
            store.save(expected)
            val restored = store.settings.first()
            assertEquals(expected, restored)

            val prefs = restored.toEpubPreferences(systemDark = true)
            assertEquals(FontFamily.SERIF, prefs.fontFamily)
            assertEquals(1.4, prefs.fontSize)
            assertEquals(1.6, prefs.lineHeight)
            assertEquals(1.5, prefs.pageMargins)
            assertEquals(2.0, prefs.paragraphIndent)
            assertEquals(0.4, prefs.paragraphSpacing)
            assertEquals(TextAlign.JUSTIFY, prefs.textAlign)
            assertEquals(false, prefs.publisherStyles)
            assertEquals(true, prefs.scroll)
            assertEquals(Theme.SEPIA, prefs.theme)
        } finally {
            store.save(original)
        }
    }
    @Test
    fun layoutModeRoundTripPersistsOptimizedValuesAndMasksOriginalPreferences(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ReaderTypographyStore(context)
        val previous = store.settings.first()
        val optimized = defaultReaderTypographySettings().copy(
            fontSize = 1.8, lineHeight = 1.7, pageMargins = 1.5,
            fontFamily = ReaderFontFamily.SERIF, appearance = ReaderAppearance.SEPIA,
        )
        val bookId = "typography-layout-mode-test"
        try {
            val original = optimized.withPublisherLayout(original = true)
            store.save(original)
            store.saveBook(bookId, original)
            val global = store.settings.first()
            val book = store.forBook(bookId).first().effective
            for (saved in listOf(global, book)) {
                assertEquals(original, saved)
                val prefs = saved.toEpubPreferences(systemDark = false)
                assertEquals(true, prefs.publisherStyles)
                assertNull(prefs.fontSize)
                assertNull(prefs.fontFamily)
                assertNull(prefs.lineHeight)
                assertNull(prefs.pageMargins)
                assertEquals(Theme.SEPIA, prefs.theme)

                val restored = saved.withPublisherLayout(original = false)
                assertEquals(optimized, restored)
                assertEquals(1.8, restored.toEpubPreferences(false).fontSize)
                assertEquals(1.7, restored.toEpubPreferences(false).lineHeight)
                assertEquals(1.9, restored.copy(lineHeight = 1.9).toEpubPreferences(false).lineHeight)
            }
        } finally {
            store.clearBook(bookId)
            store.save(previous)
        }
    }
}
