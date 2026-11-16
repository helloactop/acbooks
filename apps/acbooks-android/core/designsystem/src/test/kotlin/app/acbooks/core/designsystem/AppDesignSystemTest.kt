package app.acbooks.core.designsystem

import androidx.compose.ui.graphics.Color
import app.acbooks.core.designsystem.glass.GlassCapability
import app.acbooks.core.designsystem.glass.glassCapability
import app.acbooks.core.designsystem.tokens.DefaultAccent
import app.acbooks.core.designsystem.tokens.appColors
import app.acbooks.core.designsystem.tokens.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDesignSystemTest {
    @Test
    fun customAccentIsPassedThroughUnchanged() {
        val accents = listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue, Color.Yellow)
        for (dark in listOf(false, true)) {
            for (accent in accents) {
                val colors = appColors(dark, accent)
                assertEquals(accent, colors.accent)
                assertEquals(accent, colors.accentText)
                assertEquals(accent, colors.accentBorder)
            }
        }
    }

    @Test
    fun onAccentPicksTheHigherContrastForeground() {
        val accents = listOf(DefaultAccent, Color.Black, Color.White, Color.Red, Color.Green, Color.Blue)
        for (accent in accents) {
            val colors = appColors(dark = false, accent)
            val onBlack = contrast(colors.accent, Color.Black)
            val onWhite = contrast(colors.accent, Color.White)
            val expected = if (onBlack >= onWhite) Color.Black else Color.White
            assertEquals("Foreground for $accent", expected, colors.onAccent)
        }
    }

    @Test
    fun defaultAccentKeepsItsReadableTextBaseline() {
        for (dark in listOf(false, true)) {
            val colors = appColors(dark, DefaultAccent)
            for (background in listOf(colors.background, colors.surface)) {
                assertTrue(
                    "Default accentText on $background",
                    contrast(colors.accentText, background) >= 4.5f,
                )
            }
        }
    }

    @Test
    fun glassCapabilitiesRespectApiHardwareAndPreferences() {
        assertEquals(GlassCapability.Solid, glassCapability(30, true, false, false))
        assertEquals(GlassCapability.Blur, glassCapability(31, true, false, false))
        assertEquals(GlassCapability.Blur, glassCapability(32, true, false, false))
        assertEquals(GlassCapability.Refraction, glassCapability(33, true, false, false))
        assertEquals(GlassCapability.Solid, glassCapability(36, false, false, false))
        assertEquals(GlassCapability.Solid, glassCapability(36, true, true, false))
        assertEquals(GlassCapability.Solid, glassCapability(36, true, false, true))
    }
}
