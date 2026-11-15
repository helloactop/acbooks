package app.acbooks.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.acbooks.core.designsystem.components.AppDialogAction
import app.acbooks.core.designsystem.components.AppDialogActionRole
import app.acbooks.core.designsystem.components.orderDialogActions
import app.acbooks.core.designsystem.components.shouldStackDialogActions
import app.acbooks.core.designsystem.tokens.DefaultAccent
import app.acbooks.core.designsystem.tokens.appColors
import app.acbooks.core.designsystem.tokens.contrast
import app.acbooks.core.designsystem.tokens.ButtonSizeTokens
import app.acbooks.core.designsystem.tokens.IconButtonSize
import app.acbooks.core.designsystem.tokens.TextButtonSize
import app.acbooks.core.designsystem.tokens.buttonGlassTint
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDialogActionTest {
    @Test
    fun layoutAccountsForLabelWidthPaddingAndMinimumSize() {
        assertFalse(shouldStackDialogActions(listOf(42, 76), 208, 8, 12, 72))
        assertTrue(shouldStackDialogActions(listOf(42, 77), 208, 8, 12, 72))
        assertTrue(shouldStackDialogActions(listOf(20, 20), 151, 8, 12, 72))
        assertTrue(shouldStackDialogActions(listOf(20, 20, 20), 500, 8, 12, 72))
        assertFalse(shouldStackDialogActions(listOf(300), 208, 8, 12, 72))
    }

    @Test
    fun actionsFollowRoleOrdering() {
        val primary = AppDialogAction("Save", AppDialogActionRole.Primary) {}
        val cancel = AppDialogAction("Cancel", AppDialogActionRole.Cancel) {}
        val secondary = AppDialogAction("Keep changes", AppDialogActionRole.Secondary) {}
        val delete = AppDialogAction("Delete", AppDialogActionRole.Destructive) {}
        assertEquals(listOf(cancel, primary), orderDialogActions(listOf(primary, cancel), stacked = false))
        assertEquals(listOf(cancel, delete), orderDialogActions(listOf(delete, cancel), stacked = false))
        assertEquals(listOf(primary, secondary, cancel), orderDialogActions(listOf(cancel, primary, secondary), stacked = true))
    }

    @Test
    fun enabledActionTextRemainsLegibleOnDarkAndLightBackdrops() {
        for (dark in listOf(false, true)) {
            for (accent in listOf(DefaultAccent, Color(0xFF757575), Color(0xFF00866A), Color.Black, Color.White)) {
                val colors = appColors(dark, accent)
                val treatments = listOf(
                    Triple(colors.accent, colors.onAccent, ButtonSizeTokens.colorTintOpacity),
                    Triple(colors.surfaceSecondary, colors.textPrimary, ButtonSizeTokens.neutralTintOpacity),
                    Triple(colors.destructive, colors.onDestructive, ButtonSizeTokens.colorTintOpacity),
                )
                for ((tint, ink, opacity) in treatments) {
                    val glass = buttonGlassTint(tint, ink, opacity)
                    for (backdrop in listOf(Color.Black, Color.White)) {
                        assertTrue(contrast(ink, glass.compositeOver(backdrop)) >= 4.5f)
                    }
                }
            }
        }
    }

    @Test
    fun buttonTokensCoverAllVisualAndTouchSizes() {
        assertEquals(listOf(32.dp, 36.dp, 44.dp, 48.dp, 56.dp, 64.dp), IconButtonSize.entries.map { it.diameter })
        assertEquals(listOf(18.dp, 17.dp, 20.dp, 20.dp, 24.dp, 28.dp), IconButtonSize.entries.map { it.icon })
        assertEquals(48.dp, ButtonSizeTokens.touch)
        assertEquals(72.dp, ButtonSizeTokens.textMinWidth)
        assertEquals(listOf(32.dp, 44.dp, 48.dp), TextButtonSize.entries.map { it.height })
        assertEquals(listOf(14.sp, 14.sp, 16.sp), TextButtonSize.entries.map { it.textStyle.fontSize })
        assertEquals(8.dp, ButtonSizeTokens.dialogGap)
    }
}
