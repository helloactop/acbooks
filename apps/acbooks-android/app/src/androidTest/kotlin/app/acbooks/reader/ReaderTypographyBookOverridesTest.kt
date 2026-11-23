package app.acbooks.reader

import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTypographyBookOverridesTest {
    @Test
    fun bookOverridesSurviveReopenRemainIsolatedAndCanInheritGlobal(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ReaderTypographyStore(context)
        val originalGlobal = store.settings.first()
        val firstBookId = UUID.randomUUID().toString()
        val secondBookId = UUID.randomUUID().toString()
        val global = ReaderTypographySettings(fontSize = 1.2, appearance = ReaderAppearance.LIGHT)
        val firstBook = ReaderTypographySettings(
            fontSize = 1.8,
            lineHeight = 1.6,
            publisherStyles = false,
            appearance = ReaderAppearance.SEPIA,
        )
        try {
            store.save(global)
            assertEquals(global, store.forBook(firstBookId).first().effective)
            assertEquals(global, store.forBook(secondBookId).first().effective)

            store.saveBook(firstBookId, firstBook)
            // New store instance simulates opening the book again after leaving its screen.
            val reopenedStore = ReaderTypographyStore(context)
            val restored = reopenedStore.forBook(firstBookId).first()
            assertEquals(firstBook, restored.bookOverride)
            assertEquals(firstBook, restored.effective)
            assertTrue(restored.hasBookOverride)
            assertEquals(global, store.forBook(secondBookId).first().effective)

            val updatedGlobal = global.copy(fontSize = 1.5)
            store.save(updatedGlobal)
            assertEquals(firstBook, store.forBook(firstBookId).first().effective)
            assertEquals(updatedGlobal, store.forBook(secondBookId).first().effective)
            assertEquals(updatedGlobal, store.settings.first())

            store.clearBook(firstBookId)
            val inherited = store.forBook(firstBookId).first()
            assertNull(inherited.bookOverride)
            assertFalse(inherited.hasBookOverride)
            assertEquals(updatedGlobal, inherited.effective)
        } finally {
            store.clearBook(firstBookId)
            store.clearBook(secondBookId)
            store.save(originalGlobal)
        }
    }

    @Test
    fun perBookCodecRoundTripsAndIgnoresUnsupportedData() {
        val expected = ReaderTypographySettings(
            fontFamily = ReaderFontFamily.MONOSPACE,
            fontSize = 1.35,
            fontWeight = 1.1,
            paragraphIndent = 2.0,
            paragraphSpacing = 0.5,
            textAlignment = ReaderTextAlignment.JUSTIFY,
            appearance = ReaderAppearance.DARK,
            backgroundArgb = 0xFF112233.toInt(),
            textArgb = 0xFFEEDDCC.toInt(),
            publisherStyles = false,
            scroll = true,
        )
        assertEquals(expected, readerTypographyFromBookOverrideJson(expected.toBookOverrideJson()))
        assertNull(readerTypographyFromBookOverrideJson("{broken json"))
        assertNull(readerTypographyFromBookOverrideJson("{\"version\":99}"))
        assertEquals(
            ReaderTypographySettings(),
            readerTypographyFromBookOverrideJson("{\"version\":1,\"fontSize\":-5,\"appearance\":\"INVALID\"}"),
        )
    }
}
