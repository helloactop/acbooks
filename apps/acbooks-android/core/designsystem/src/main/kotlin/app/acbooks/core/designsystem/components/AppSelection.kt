package app.acbooks.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import app.acbooks.core.designsystem.glass.AppMaterial
import app.acbooks.core.designsystem.glass.AppSurface
import app.acbooks.core.designsystem.glass.LocalBackdrop
import app.acbooks.core.designsystem.glass.LocalGlassDepth
import app.acbooks.core.designsystem.icons.AppIcon
import app.acbooks.core.designsystem.icons.ControlIcon
import app.acbooks.core.designsystem.theme.AppShapes
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppMotion
import app.acbooks.core.designsystem.tokens.AppRadius
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidToggle

data class AppNavItem(val label: String, val icon: AppIcon)

@Composable
fun AppNavigationBar(
    items: List<AppNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    require(selected in items.indices)
    val backdrop = LocalBackdrop.current
    if (!vertical && backdrop != null && LocalGlassDepth.current == 0) {
        val selection = rememberUpdatedState(selected)
        val callback = rememberUpdatedState(onSelect)
        val currentIndex = remember { { selection.value } }
        val select = remember { { index: Int -> callback.value(index) } }
        LiquidBottomTabs(currentIndex, select, backdrop, items.size, modifier.selectableGroup()) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                LiquidBottomTab(onClick = { onSelect(index) }, selected = active) {
                    ControlIcon(item.icon, contentDescription = null,
                        tint = if (active) LocalAppColors.current.accentText else LocalAppColors.current.textPrimary)
                    SelectionLabel(item.label, active)
                }
            }
        }
        return
    }
    SelectionBar(items.map { it.label }, selected, onSelect, modifier, vertical, navigation = true) { index, active ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ControlIcon(items[index].icon, contentDescription = null, tint = if (active) LocalAppColors.current.accentText else LocalAppColors.current.textPrimary)
            SelectionLabel(items[index].label, active)
        }
    }
}

@Composable
fun AppSegmentedControl(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectionBar(labels, selected, onSelect, modifier) { index, active ->
        SelectionLabel(labels[index], active)
    }
}

@Composable
private fun SelectionLabel(label: String, active: Boolean) {
    val colors = LocalAppColors.current
    Text(label, color = if (active) colors.accentText else colors.textPrimary,
        style = AppTypography.caption, textAlign = TextAlign.Center)
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SelectionBar(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    navigation: Boolean = false,
    item: @Composable (Int, Boolean) -> Unit,
) {
    require(selected in labels.indices)
    val colors = LocalAppColors.current
    val minHeight = if (navigation) AppSizes.navigation else AppSizes.touch
    val latestSelect by rememberUpdatedState(onSelect)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val drag = Modifier.pointerInput(labels.size, vertical, rtl) {
        fun select(x: Float, y: Float) {
            val fraction = if (vertical) y / size.height else x / size.width
            val index = (fraction * labels.size).toInt().coerceIn(labels.indices)
            latestSelect(if (!vertical && rtl) labels.lastIndex - index else index)
        }

        detectDragGestures(orientationLock = if (vertical) Orientation.Vertical else Orientation.Horizontal) { change, _ ->
            select(change.position.x, change.position.y)
        }
    }
    AppSurface(
        modifier.then(drag).selectableGroup(), AppMaterial.Control, AppShapes.capsule,
        tint = colors.surfaceSecondary,
    ) {
        BoxWithConstraints(Modifier.padding(AppSpacing.xs)) {
            val extent = if (vertical) minHeight else maxWidth / labels.size
            val offset by animateDpAsState(extent * selected, AppMotion.spring(), label = "selection")
            Box(Modifier.matchParentSize()) {
                val position = if (vertical) {
                    Modifier.offset { IntOffset(0, offset.roundToPx()) }.fillMaxWidth().height(extent)
                } else {
                    Modifier.offset { IntOffset(offset.roundToPx(), 0) }.width(extent).fillMaxHeight()
                }
                AppSurface(position, shape = AppShapes.inset(AppRadius.capsule, AppSpacing.xs),
                    tint = colors.accentSoft) {}
            }
            if (vertical) {
                Column(Modifier.widthIn(min = AppSizes.navigation)) {
                    labels.indices.forEach { index ->
                        SelectionItem(index, selected, onSelect, Modifier.fillMaxWidth().height(minHeight), item)
                    }
                }
            } else {
                Row {
                    labels.indices.forEach { index ->
                        SelectionItem(
                            index, selected, onSelect,
                            Modifier.weight(1f).heightIn(min = (minHeight - AppSpacing.sm).coerceAtLeast(AppSizes.touch)),
                            item,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionItem(
    index: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    item: @Composable (Int, Boolean) -> Unit,
) {
    Box(
        modifier.defaultMinSize(minWidth = AppSizes.touch)
            .selectable(index == selected, role = Role.Tab, onClick = { onSelect(index) })
            .padding(AppSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        item(index, index == selected)
    }
}

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val backdrop = LocalBackdrop.current
    if (enabled && backdrop != null && LocalGlassDepth.current == 0) {
        val selection = rememberUpdatedState(checked)
        val callback = rememberUpdatedState(onCheckedChange)
        val isSelected = remember { { selection.value } }
        val select = remember { { value: Boolean -> callback.value(value) } }
        LiquidToggle(
            isSelected, select, backdrop,
            modifier.defaultMinSize(minHeight = AppSizes.touch).semantics { contentDescription = label },
        )
        return
    }
    val colors = LocalAppColors.current
    val thumbSize = AppSizes.icon
    val offset by animateDpAsState(
        if (checked) AppSizes.switchWidth - thumbSize - AppSpacing.sm else AppSpacing.xs,
        AppMotion.spring(), label = "switch",
    )
    val latestChange by rememberUpdatedState(onCheckedChange)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val drag = if (enabled) Modifier.pointerInput(rtl) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragEnd = { if (distance != 0f) latestChange((distance > 0f) != rtl) },
        ) { _, delta -> distance += delta }
    } else Modifier
    Box(
        modifier.defaultMinSize(minHeight = AppSizes.touch)
            .then(drag)
            .semantics { contentDescription = label }
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        AppSurface(
            Modifier.width(AppSizes.switchWidth).height(thumbSize + AppSpacing.sm),
            if (enabled) AppMaterial.Control else AppMaterial.Content,
            AppShapes.circle,
            tint = if (checked && enabled) colors.accentSoft else colors.surfaceSecondary,
        ) {
            AppSurface(
                Modifier.align(Alignment.CenterStart).offset { IntOffset(offset.roundToPx(), 0) }
                    .width(thumbSize).height(thumbSize),
                shape = AppShapes.circle,
                tint = if (checked && enabled) colors.accentText else colors.surface,
            ) {}
        }
    }
}
