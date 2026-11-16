package app.acbooks.core.designsystem.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
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
    val host = LocalAppMenuHost.current
    val canOpen = enabled && options.isNotEmpty()
    val anchor = remember { mutableStateOf<IntRect?>(null) }
    var standaloneOpen by remember { mutableStateOf(false) }
    val currentOptions by rememberUpdatedState(options)
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentPanel by rememberUpdatedState<@Composable (Boolean, () -> Unit) -> Unit> { active, close ->
        AppMenuPanel(
            items = currentOptions.map { option ->
                AppMenuItem(
                    label = option.label,
                    onClick = { if (option.value != currentValue) currentOnValueChange(option.value) },
                    selected = option.value == currentValue,
                )
            },
            enabled = active,
            role = Role.RadioButton,
            onSelect = { item ->
                close()
                item.onClick()
            },
        )
    }
    val request = remember(host, anchor) {
        host?.let {
            AppMenuRequest(
                anchor = anchor,
                matchAnchorWidth = true,
                alignEnd = true,
                overlapAnchor = true,
                onDismiss = {},
                content = { active, close -> currentPanel(active, close) },
            )
        }
    }
    val expanded = if (host == null) standaloneOpen else request != null && host.popup === request
    DisposableEffect(host, request) {
        onDispose {
            if (host == null) standaloneOpen = false
            else if (request != null && host.popup === request) host.clear()
        }
    }
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
                        if (host == null) standaloneOpen = !standaloneOpen
                        else if (expanded) host.dismiss()
                        else request?.let(host::show)
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
            if (host == null) {
                val visibility = remember { MutableTransitionState(false) }
                visibility.targetState = standaloneOpen && canOpen
                if (visibility.targetState || visibility.currentState || !visibility.isIdle) {
                    val density = LocalDensity.current
                    Popup(
                        popupPositionProvider = rememberMenuPosition(gap = 0, overlapAnchor = true),
                        onDismissRequest = { standaloneOpen = false },
                        properties = PopupProperties(focusable = visibility.targetState),
                    ) {
                        CompositionLocalProvider(LocalBackdrop provides null) {
                            Box(Modifier.width(with(density) { (anchor.value?.width ?: 0).toDp() })) {
                                AnimatedPopupContent(visibility) {
                                    currentPanel(visibility.targetState) { standaloneOpen = false }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
