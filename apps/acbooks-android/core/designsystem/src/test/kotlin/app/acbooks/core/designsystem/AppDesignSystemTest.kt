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
    fun paletteKeepsTextAndControlContrastAcrossThemes() {
        val accents = listOf(DefaultAccent, Color.Black, Color.White, Color.Red, Color.Green, Color.Blue)
        for (dark in listOf(false, true)) {
            for (accent in accents) {
                val colors = appColors(dark, accent)
                assertEquals(accent, colors.accent)
                assertTrue(contrast(colors.accent, colors.onAccent) >= 4.5f)
                for (background in listOf(colors.background, colors.surface, colors.surfaceSecondary, colors.accentSoft)) {
                    assertTrue("Text contrast for $accent on $background", contrast(colors.accentText, background) >= 4.5f)
                    assertTrue("Border contrast for $accent on $background", contrast(colors.accentBorder, background) >= 3f)
                }
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
