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
    fun originalLayoutMasksOverridesWithoutErasingSavedOptimizedValues() {
        val customized = defaultReaderTypographySettings().copy(
            fontFamily = ReaderFontFamily.SERIF,
            fontSize = 1.8,
            fontWeight = 1.2,
            lineHeight = 1.7,
            paragraphIndent = 2.0,
            paragraphSpacing = 0.3,
            letterSpacing = 0.2,
            wordSpacing = 0.1,
            textAlignment = ReaderTextAlignment.LEFT,
            pageMargins = 1.5,
            appearance = ReaderAppearance.SEPIA,
            scroll = true,
        )
        val original = customized.withPublisherLayout(original = true)
        assertEquals(customized.copy(publisherStyles = true), original)
        assertEquals(false, original.isOptimizedLayout)

        val rendered = original.forRendering()
        assertEquals(ReaderFontFamily.BOOK, rendered.fontFamily)
        assertNull(rendered.fontSize)
        assertNull(rendered.fontWeight)
        assertNull(rendered.lineHeight)
        assertNull(rendered.pageMargins)
        assertNull(rendered.paragraphIndent)
        assertNull(rendered.paragraphSpacing)
        assertNull(rendered.letterSpacing)
        assertNull(rendered.wordSpacing)
        assertNull(rendered.textAlignment)
        assertEquals(ReaderAppearance.SEPIA, rendered.appearance)
        assertEquals(true, rendered.scroll)

        val restored = original.withPublisherLayout(original = false)
        assertEquals(customized, restored)
        assertEquals(customized, restored.forRendering())
        assertEquals(true, restored.isOptimizedLayout)
        assertEquals(1.9, restored.copy(lineHeight = 1.9).forRendering().lineHeight)
    }

    @Test
    fun switchingOldClearedProfilesBackToOptimizedRestoresMissingDefaults() {
        val old = ReaderTypographySettings(publisherStyles = true, fontSize = 1.6, pageMargins = 1.4)
        val restored = old.withPublisherLayout(original = false)
        assertEquals(1.6, restored.fontSize)
        assertEquals(1.4, restored.pageMargins)
        assertEquals(1.5, restored.lineHeight)
        assertEquals(2.0, restored.paragraphIndent)
        assertEquals(0.0, restored.paragraphSpacing)
        assertEquals(ReaderTextAlignment.JUSTIFY, restored.textAlignment)
        assertEquals(true, restored.isOptimizedLayout)
        assertEquals(false, ReaderTypographySettings().isOptimizedLayout)
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
