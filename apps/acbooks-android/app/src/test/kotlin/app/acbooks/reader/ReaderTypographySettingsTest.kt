package app.acbooks.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM tests; Readium's Theme requires Android and is tested on device. */
class ReaderTypographySettingsTest {
    @Test
    fun newReaderUsesWechatInspiredTypographyWithoutChangingLegacyConstructor() {
        val defaults = defaultReaderTypographySettings()

        assertEquals(ReaderFontFamily.SANS_SERIF, defaults.fontFamily)
        assertEquals(1.3, defaults.fontSize)
        assertEquals(1.5, defaults.lineHeight)
        assertEquals(1.0, defaults.pageMargins)
        assertEquals(2.0, defaults.paragraphIndent)
        assertEquals(0.0, defaults.paragraphSpacing)
        assertEquals(ReaderTextAlignment.JUSTIFY, defaults.textAlignment)
        assertEquals(false, defaults.publisherStyles)
        assertEquals(false, defaults.scroll)
        assertEquals(ReaderAppearance.SYSTEM, defaults.appearance)

        // Old explicit styles and per-book snapshots must still deserialize
        // using the same unspecific constructor as before this change.
        assertEquals(ReaderFontFamily.BOOK, ReaderTypographySettings().fontFamily)
        assertNull(ReaderTypographySettings().fontSize)
    }

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
    fun bookOverrideWinsOverGlobalAndMissingOverrideInherits() {
        val global = ReaderTypographySettings(fontSize = 1.2, appearance = ReaderAppearance.DARK)
        val book = ReaderTypographySettings(fontSize = 1.8, appearance = ReaderAppearance.SEPIA)

        assertEquals(global, ReaderTypographyProfile(global).effective)
        assertEquals(false, ReaderTypographyProfile(global).hasBookOverride)
        assertEquals(book, ReaderTypographyProfile(global, book).effective)
        assertEquals(true, ReaderTypographyProfile(global, book).hasBookOverride)
        // A second book without an override still follows changes to global.
        assertEquals(
            ReaderTypographySettings(fontSize = 1.5),
            ReaderTypographyProfile(ReaderTypographySettings(fontSize = 1.5)).effective,
        )
    }

    @Test
    fun originalLayoutDiscardsConflictingPublisherOverrides() {
        val customized = ReaderTypographySettings(
            fontFamily = ReaderFontFamily.SERIF,
            fontSize = 1.3,
            lineHeight = 1.7,
            paragraphIndent = 2.0,
            paragraphSpacing = 0.3,
            letterSpacing = 0.2,
            textAlignment = ReaderTextAlignment.JUSTIFY,
            pageMargins = 1.5,
            appearance = ReaderAppearance.SEPIA,
            publisherStyles = false,
        )

        val original = customized.withPublisherLayout(original = true)
        assertEquals(true, original.publisherStyles)
        assertEquals(ReaderFontFamily.BOOK, original.fontFamily)
        assertNull(original.lineHeight)
        assertNull(original.paragraphIndent)
        assertNull(original.paragraphSpacing)
        assertNull(original.letterSpacing)
        assertNull(original.textAlignment)
        assertEquals(1.3, original.fontSize)
        assertEquals(1.5, original.pageMargins)
        assertEquals(ReaderAppearance.SEPIA, original.appearance)
        assertEquals(false, original.withPublisherLayout(original = false).publisherStyles)
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
