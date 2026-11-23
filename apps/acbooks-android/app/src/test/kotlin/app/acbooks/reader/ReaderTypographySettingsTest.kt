package app.acbooks.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme

class ReaderTypographySettingsTest {
    @Test
    fun defaultsKeepPublisherLayoutAndUseSystemTheme() {
        val settings = ReaderTypographySettings()
        val light = settings.toEpubPreferences(systemDark = false)
        val dark = settings.toEpubPreferences(systemDark = true)

        assertEquals(false, light.scroll)
        assertNull(light.fontFamily)
        assertNull(light.fontSize)
        assertNull(light.lineHeight)
        assertNull(light.publisherStyles)
        assertEquals(Theme.LIGHT, light.theme)
        assertEquals(Theme.DARK, dark.theme)
    }

    @Test
    fun settingsMapToReadiumWithItsNativeUnits() {
        val settings = ReaderTypographySettings(
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
        val prefs = settings.toEpubPreferences(systemDark = true)

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
    }

    @Test
    fun corruptValuesDoNotReachReadium() {
        val prefs = ReaderTypographySettings(
            fontSize = Double.NaN,
            fontWeight = 100.0,
            lineHeight = Double.POSITIVE_INFINITY,
            pageMargins = -1.0,
        ).toEpubPreferences(systemDark = false)

        assertNull(prefs.fontSize)
        assertNull(prefs.fontWeight)
        assertNull(prefs.lineHeight)
        assertNull(prefs.pageMargins)
    }
}
