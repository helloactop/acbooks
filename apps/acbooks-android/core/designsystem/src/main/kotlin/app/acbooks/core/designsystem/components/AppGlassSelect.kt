package app.acbooks.core.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.acbooks.core.designsystem.R
import app.acbooks.core.designsystem.glass.AppMaterial
import app.acbooks.core.designsystem.glass.AppSurface
import app.acbooks.core.designsystem.glass.LocalBackdrop
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.icons.ControlIcon
import app.acbooks.core.designsystem.theme.AppShapes
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography
import kotlin.math.roundToInt

data class AppSelectOption<T>(val value: T, val label: String)

internal class SelectOverlayState {
    var popup by mutableStateOf<SelectPopup?>(null)
}

internal class SelectPopup(
    val anchor: State<IntRect?>,
    val content: @Composable () -> Unit,
)

internal val LocalSelectOverlay = staticCompositionLocalOf<SelectOverlayState?> { null }

@Composable
fun <T> AppGlassSelect(
    label: String,
    options: List<AppSelectOption<T>>,
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val overlay = LocalSelectOverlay.current
    val canOpen = enabled && options.isNotEmpty()
    val anchor = remember { mutableStateOf<IntRect?>(null) }
    var popupOpen by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState {
        popupOpen = false
    }
    val menu by rememberUpdatedState<@Composable (() -> Unit) -> Unit> { onDismiss ->
        val active = if (overlay == null) popupOpen else overlay.popup?.anchor === anchor
        SelectOptions(options, value, enabled = active) { selected ->
            onDismiss()
            if (selected != value) onValueChange(selected)
        }
    }
    val popup = remember(overlay, anchor) {
        SelectPopup(anchor) {
            menu { overlay?.popup = null }
        }
    }
    DisposableEffect(overlay, popup, canOpen) {
        onDispose {
            if (overlay == null) {
                popupOpen = false
            } else if (overlay.popup === popup) {
                overlay.popup = null
            }
        }
    }
    val expanded = if (overlay == null) popupOpen else overlay.popup === popup
    val selectedLabel = options.firstOrNull { it.value == value }?.label
        ?: stringResource(R.string.app_select_placeholder)
    val selectState = stringResource(if (expanded) R.string.app_select_expanded else R.string.app_select_collapsed)

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f).padding(end = AppSpacing.md),
            color = colors.textPrimary,
            style = AppTypography.body,
        )
        Box(Modifier.weight(1f, fill = false).widthIn(max = AppSizes.selectWidth).fillMaxWidth()) {
            AppSurface(
                modifier = Modifier.fillMaxWidth()
                    .onGloballyPositioned {
                        val position = it.positionInRoot()
                        val x = position.x.roundToInt()
                        val y = position.y.roundToInt()
                        anchor.value = IntRect(x, y, x + it.size.width, y + it.size.height)
                    }
                    .semantics {
                        contentDescription = label
                        stateDescription = selectState
                    }
                    .alpha(if (expanded) 0f else 1f)
                    .clickable(enabled = canOpen, role = Role.DropdownList) {
                        if (overlay == null) popupOpen = !popupOpen
                        else overlay.popup = if (expanded) null else popup
                    },
                material = if (canOpen) AppMaterial.Control else AppMaterial.Content,
                shape = AppShapes.medium,
                tint = colors.background,
                border = colors.divider,
            ) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = AppSizes.touch)
                        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                ) {
                    Text(
                        selectedLabel,
                        Modifier.align(Alignment.Center).fillMaxWidth()
                            .padding(horizontal = AppSizes.iconSmall + AppSpacing.xs),
                        color = if (canOpen) colors.textPrimary else colors.textSecondary,
                        style = AppTypography.small,
                        textAlign = TextAlign.Center,
                    )
                    ControlIcon(
                        AppIcons.ChevronDown, contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        small = true, tint = colors.textSecondary,
                    )
                }
            }
            val standaloneVisibility = remember { MutableTransitionState(false) }
            standaloneVisibility.targetState = overlay == null && expanded && canOpen
            if (standaloneVisibility.targetState || standaloneVisibility.currentState || !standaloneVisibility.isIdle) {
                val density = LocalDensity.current
                Popup(
                    popupPositionProvider = rememberMenuPosition(gap = 0, overlapAnchor = true),
                    onDismissRequest = dismiss,
                    properties = PopupProperties(focusable = standaloneVisibility.targetState),
                ) {
                    CompositionLocalProvider(LocalBackdrop provides null) {
                        Box(Modifier.width(with(density) { (anchor.value?.width ?: 0).toDp() })) {
                            AnimatedPopupContent(standaloneVisibility) {
                                menu(dismiss)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.AppSelectOverlay(state: SelectOverlayState) {
    val transition = updateTransition(state.popup, label = "select popup")
    val popup = transition.targetState ?: transition.currentState ?: return
    val anchor = popup.anchor.value ?: return
    var origin by remember { mutableStateOf<IntOffset?>(null) }
    val close = { state.popup = null }
    BackHandler(enabled = state.popup != null, onBack = close)
    Box(Modifier.matchParentSize().onGloballyPositioned {
        val offset = it.positionInRoot()
        origin = IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
    }) {
        val start = origin ?: return@Box
        if (state.popup != null) {
            Box(Modifier.matchParentSize().pointerInput(state) { detectTapGestures { state.popup = null } })
        }
        Layout(
            content = {
                Box {
                    transition.AnimatedVisibility(
                        visible = { it != null },
                        enter = popupEnterTransition,
                        exit = popupExitTransition,
                    ) {
                        popup.content()
                    }
                }
            },
            modifier = Modifier.matchParentSize(),
        ) { children, constraints ->
            val anchorTop = anchor.top - start.y
            val roomBelow = (constraints.maxHeight - anchorTop).coerceIn(0, constraints.maxHeight)
            val roomAbove = anchorTop.coerceIn(0, constraints.maxHeight)
            val width = anchor.width.coerceIn(0, constraints.maxWidth)
            val panel = children.single().measure(
                Constraints(minWidth = width, maxWidth = width, maxHeight = maxOf(roomBelow, roomAbove)),
            )
            val x = (anchor.left - start.x).coerceIn(0, constraints.maxWidth - panel.width)
            val y = (if (panel.height <= roomBelow) anchorTop else anchorTop - panel.height)
                .coerceIn(0, constraints.maxHeight - panel.height)
            layout(constraints.maxWidth, constraints.maxHeight) { panel.place(x, y) }
        }
    }
}

@Composable
private fun <T> SelectOptions(options: List<AppSelectOption<T>>, value: T, enabled: Boolean, onSelect: (T) -> Unit) {
    val colors = LocalAppColors.current
    AppSurface(
        Modifier.fillMaxWidth(), AppMaterial.Overlay, AppShapes.extraLarge,
        border = colors.divider, refract = true,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .selectableGroup().padding(AppSpacing.xs),
        ) {
            for (option in options) {
                val selected = option.value == value
                Row(
                    Modifier.fillMaxWidth().heightIn(min = AppSizes.touch)
                        .selectable(selected, enabled = enabled, role = Role.RadioButton) { onSelect(option.value) }
                        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) {
                        ControlIcon(AppIcons.Checkmark, contentDescription = null, small = true)
                    } else {
                        Spacer(Modifier.width(AppSizes.iconSmall))
                    }
                    Text(option.label, Modifier.weight(1f), color = colors.textPrimary, style = AppTypography.small)
                }
            }
        }
    }
}
