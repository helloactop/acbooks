package app.acbooks.core.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import app.acbooks.core.designsystem.glass.AppMaterial
import app.acbooks.core.designsystem.glass.AppSurface
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.icons.ControlIcon
import app.acbooks.core.designsystem.theme.AppShapes
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography
import kotlin.math.roundToInt

data class AppMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val selected: Boolean? = null,
)

internal class AppMenuRequest(
    val anchor: State<IntRect?>,
    val matchAnchorWidth: Boolean,
    val alignEnd: Boolean,
    val overlapAnchor: Boolean,
    val onDismiss: () -> Unit,
    val content: @Composable (enabled: Boolean, close: () -> Unit) -> Unit,
)

internal class AppMenuHostState {
    internal var popup by mutableStateOf<AppMenuRequest?>(null)

    internal fun show(request: AppMenuRequest) {
        if (popup === request) return
        val previous = popup
        popup = request
        previous?.onDismiss?.invoke()
    }

    internal fun dismiss() {
        val current = popup ?: return
        popup = null
        current.onDismiss()
    }

    internal fun clear() {
        popup = null
    }
}

internal val LocalAppMenuHost = staticCompositionLocalOf<AppMenuHostState?> { null }

@Composable
internal fun BoxScope.AppMenuOverlay(state: AppMenuHostState) {
    val transition = updateTransition(state.popup, label = "app menu")
    val request = transition.targetState ?: transition.currentState ?: return
    val anchor = request.anchor.value ?: return
    var origin by remember { mutableStateOf<IntOffset?>(null) }
    BackHandler(enabled = state.popup != null) { state.dismiss() }
    Box(
        Modifier.matchParentSize().onGloballyPositioned {
            val position = it.positionInRoot()
            origin = IntOffset(position.x.roundToInt(), position.y.roundToInt())
        },
    ) {
        val start = origin ?: return@Box
        if (state.popup != null) {
            Box(Modifier.matchParentSize().pointerInput(state) { detectTapGestures { state.dismiss() } })
        }
        Layout(
            content = {
                Box {
                    transition.AnimatedVisibility(
                        visible = { it != null },
                        enter = popupEnterTransition,
                        exit = popupExitTransition,
                    ) {
                        request.content(state.popup === request) { state.dismiss() }
                    }
                }
            },
            modifier = Modifier.matchParentSize(),
        ) { measurables, constraints ->
            val margin = 8.dp.roundToPx()
            val gap = if (request.overlapAnchor) 0 else AppSpacing.xs.roundToPx()
            val availableWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(1)
            val anchorWidth = anchor.width.coerceAtMost(availableWidth)
            val minWidth = if (request.matchAnchorWidth) anchorWidth
            else AppSizes.navigation.roundToPx().coerceAtMost(availableWidth)
            val maxWidth = if (request.matchAnchorWidth) anchorWidth
            else AppSizes.dialog.roundToPx().coerceAtMost(availableWidth)
            val anchorLeft = anchor.left - start.x
            val anchorTop = anchor.top - start.y
            val anchorRight = anchor.right - start.x
            val anchorBottom = anchor.bottom - start.y
            val roomBelow = (constraints.maxHeight - anchorBottom - gap - margin).coerceAtLeast(1)
            val roomAbove = (anchorTop - gap - margin).coerceAtLeast(1)
            val panel = measurables.single().measure(
                Constraints(
                    minWidth = minWidth,
                    maxWidth = maxWidth,
                    maxHeight = maxOf(roomBelow, roomAbove),
                ),
            )
            val preferredX = if (request.alignEnd) anchorRight - panel.width else anchorLeft
            val x = preferredX.coerceIn(margin, (constraints.maxWidth - margin - panel.width).coerceAtLeast(margin))
            val preferredY = if (panel.height <= roomBelow) anchorBottom + gap else anchorTop - gap - panel.height
            val y = preferredY.coerceIn(margin, (constraints.maxHeight - margin - panel.height).coerceAtLeast(margin))
            layout(constraints.maxWidth, constraints.maxHeight) { panel.place(x, y) }
        }
    }
}

@Composable
internal fun AppMenuPanel(
    items: List<AppMenuItem>,
    enabled: Boolean,
    role: Role,
    modifier: Modifier = Modifier,
    onSelect: (AppMenuItem) -> Unit,
) {
    val colors = LocalAppColors.current
    val hasSelection = items.any { it.selected != null }
    AppSurface(
        modifier,
        AppMaterial.Overlay,
        AppShapes.large,
        border = colors.divider,
        refract = true,
    ) {
        Column(
            Modifier.width(IntrinsicSize.Max)
                .verticalScroll(rememberScrollState())
                .then(if (role == Role.RadioButton) Modifier.selectableGroup() else Modifier)
                .padding(AppSpacing.xs),
        ) {
            items.forEach { item ->
                val selected = item.selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = AppSizes.touch)
                        .then(
                            if (selected != null) Modifier.semantics { this.selected = selected == true }
                            else Modifier,
                        )
                        .clickable(enabled = enabled && item.enabled, role = role) { onSelect(item) }
                        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasSelection) {
                        Box(Modifier.width(AppSizes.iconSmall), contentAlignment = Alignment.CenterStart) {
                            if (selected == true) {
                                ControlIcon(AppIcons.Checkmark, contentDescription = null, small = true)
                            }
                        }
                    }
                    Text(
                        item.label,
                        color = if (item.enabled) colors.textPrimary else colors.textSecondary,
                        style = AppTypography.small,
                    )
                }
            }
        }
    }
}
