package app.acbooks.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import app.acbooks.core.designsystem.tokens.ButtonSizeTokens
import app.acbooks.core.designsystem.tokens.TextButtonSize

enum class AppDialogActionRole { Primary, Secondary, Cancel, Destructive }

data class AppDialogAction(
    val label: String,
    val role: AppDialogActionRole,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

internal fun shouldStackDialogActions(
    labelWidths: List<Int>,
    availableWidth: Int,
    gap: Int,
    horizontalPadding: Int,
    minWidth: Int,
): Boolean {
    if (labelWidths.size != 2) return labelWidths.size > 2
    val buttonWidth = (availableWidth - gap) / 2
    return labelWidths.any { maxOf(minWidth, it + 2 * horizontalPadding) > buttonWidth }
}

internal fun orderDialogActions(actions: List<AppDialogAction>, stacked: Boolean): List<AppDialogAction> =
    actions.sortedBy { action ->
        when (action.role) {
            AppDialogActionRole.Primary, AppDialogActionRole.Destructive -> if (stacked) 0 else 2
            AppDialogActionRole.Secondary -> 1
            AppDialogActionRole.Cancel -> if (stacked) 2 else 0
        }
    }

@Composable
internal fun AppDialogActions(actions: List<AppDialogAction>) {
    if (actions.isEmpty()) return
    val tokens = ButtonSizeTokens
    val measurer = rememberTextMeasurer()
    val labels = actions.map { it.label }
    val widths = remember(labels, measurer) {
        labels.map { measurer.measure(it, style = TextButtonSize.Dialog.textStyle, softWrap = false).size.width }
    }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = with(density) {
            shouldStackDialogActions(
                widths, constraints.maxWidth, tokens.dialogGap.roundToPx(),
                tokens.textPadding.roundToPx(), tokens.textMinWidth.roundToPx(),
            )
        }
        val ordered = orderDialogActions(actions, stacked)
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.dialogGap)) {
                ordered.forEach { AppDialogActionButton(it, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.dialogGap)) {
                ordered.forEach { AppDialogActionButton(it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AppDialogActionButton(action: AppDialogAction, modifier: Modifier) {
    LiquidGlassButton(
        label = action.label,
        onClick = action.onClick,
        modifier = modifier,
        kind = when (action.role) {
            AppDialogActionRole.Primary -> ButtonKind.Primary
            AppDialogActionRole.Destructive -> ButtonKind.Destructive
            AppDialogActionRole.Cancel, AppDialogActionRole.Secondary -> ButtonKind.Secondary
        },
        size = TextButtonSize.Dialog,
        enabled = action.enabled,
    )
}
