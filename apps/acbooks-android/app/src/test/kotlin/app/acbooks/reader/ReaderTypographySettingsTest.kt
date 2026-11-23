package app.acbooks.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM tests; Readium's Theme requires Android and is tested on device. */
class ReaderTypographySettingsTest {
    @Test
    fun defaultsPreservePublisherTypography() {
        val settings = ReaderTypographySettings()
        assertEquals(ReaderFontFamily.BOOK, settings.fontFamily)
        assertNull(settings.fontSize)
        assertNull(settings.lineHeight)
        assertNull(settings.publisherStyles)
        assertEquals(false, settings.scroll)
        assertEquals(ReaderAppearance.SYSTEM, settings.appearance)
    }

    @Test
    fun invalidValuesAreDroppedWithoutChangingOtherFields() {
        val settings = ReaderTypographySettings(
            fontSize = Double.NaN,
            fontWeight = 100.0,
            lineHeight = Double.POSITIVE_INFINITY,
            pageMargins = -1.0,
            paragraphIndent = 2.0,
            appearance = ReaderAppearance.SEPIA,
        ).normalized()

        assertNull(settings.fontSize)
        assertNull(settings.fontWeight)
        assertNull(settings.lineHeight)
        assertNull(settings.pageMargins)
        assertEquals(2.0, settings.paragraphIndent)
        assertEquals(ReaderAppearance.SEPIA, settings.appearance)
    }
}
