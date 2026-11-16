package app.acbooks.core.designsystem

import androidx.compose.ui.graphics.Color
import app.acbooks.core.designsystem.tokens.DefaultAccent
import app.acbooks.core.designsystem.tokens.appColors
import app.acbooks.core.designsystem.tokens.toAppColors
import app.acbooks.core.designsystem.tokens.toMiuixColors
import org.junit.Assert.assertEquals
import org.junit.Test

class AppMiuixThemeTest {
    @Test
    fun miuixColorsCarryEveryAcbooksRole() {
        for (dark in listOf(false, true)) {
            for (accent in listOf(DefaultAccent, Color.White, Color.Black, Color.Yellow)) {
                val app = appColors(dark, accent)
                val miuix = app.toMiuixColors(dark)

                assertEquals(app.background, miuix.background)
                assertEquals(app.textPrimary, miuix.onBackground)
                assertEquals(app.surface, miuix.surfaceContainer)
                assertEquals(app.textPrimary, miuix.onSurfaceContainer)
                assertEquals(app.textSecondary, miuix.onSurfaceVariantSummary)
                assertEquals(app.accent, miuix.primary)
                assertEquals(app.onAccent, miuix.onPrimary)
                assertEquals(app.accentText, miuix.onTertiaryContainer)
                assertEquals(app.divider, miuix.dividerLine)
                assertEquals(app.controlBorder, miuix.outline)
                assertEquals(app.destructive, miuix.error)
            }
        }
    }

    @Test
    fun acbooksPaletteRoundTripsThroughMiuixColors() {
        for (dark in listOf(false, true)) {
            for (accent in listOf(DefaultAccent, Color.White, Color.Black, Color.Yellow)) {
                val app = appColors(dark, accent)
                val roundTripped = app.toMiuixColors(dark).toAppColors(dark)

                assertEquals(app.background, roundTripped.background)
                assertEquals(app.surface, roundTripped.surface)
                assertEquals(app.surfaceSecondary, roundTripped.surfaceSecondary)
                assertEquals(app.textPrimary, roundTripped.textPrimary)
                assertEquals(app.textSecondary, roundTripped.textSecondary)
                assertEquals(app.divider, roundTripped.divider)
                assertEquals(app.accent, roundTripped.accent)
                assertEquals(app.accentText, roundTripped.accentText)
                assertEquals(app.accentSoft, roundTripped.accentSoft)
                assertEquals(app.accentBorder, roundTripped.accentBorder)
                assertEquals(app.onAccent, roundTripped.onAccent)
                assertEquals(app.destructive, roundTripped.destructive)
                assertEquals(app.onDestructive, roundTripped.onDestructive)
                assertEquals(app.destructiveText, roundTripped.destructiveText)
                assertEquals(app.controlBorder, roundTripped.controlBorder)
            }
        }
    }
}
