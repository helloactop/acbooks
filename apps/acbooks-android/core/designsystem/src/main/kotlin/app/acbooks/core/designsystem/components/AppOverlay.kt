package app.acbooks.core.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import app.acbooks.core.designsystem.glass.AppMaterial
import app.acbooks.core.designsystem.glass.AppSurface
import app.acbooks.core.designsystem.glass.LocalBackdrop
import app.acbooks.core.designsystem.theme.AppShapes
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppMotion
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography
import app.acbooks.core.designsystem.tokens.GlassTokens
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role

data class AppMenuItem(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)

@Composable
fun AppMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<AppMenuItem>,
    modifier: Modifier = Modifier,
    anchor: @Composable () -> Unit,
) {
    Box(modifier) {
        anchor()
        val visibility = remember { MutableTransitionState(false) }
        visibility.targetState = expanded
        if (expanded || visibility.currentState) {
            val gap = with(LocalDensity.current) { AppSpacing.xs.roundToPx() }
            Popup(
                popupPositionProvider = rememberMenuPosition(gap),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = expanded),
            ) {
                // Popup has its own window and cannot sample the scene's coordinates.
                CompositionLocalProvider(LocalBackdrop provides null) {
                    AnimatedVisibility(
                        visibleState = visibility,
                        enter = fadeIn(tween(AppMotion.fast)) + scaleIn(AppMotion.spring(), initialScale = AppMotion.pressedScale),
                        exit = fadeOut(tween(AppMotion.fast)) + scaleOut(AppMotion.spring(), targetScale = AppMotion.pressedScale),
                    ) {
                        val colors = LocalAppColors.current
                        AppSurface(
                            material = AppMaterial.Overlay,
                            shape = AppShapes.large,
                            tint = colors.surface,
                            border = colors.controlBorder,
                        ) {
                            Column(Modifier.widthIn(min = AppSizes.navigation, max = AppSizes.dialog)
                                .verticalScroll(rememberScrollState()).padding(AppSpacing.xs)) {
                                items.forEach { item ->
                                    Box(
                                        Modifier.fillMaxWidth().heightIn(min = AppSizes.touch)
                                            .clickable(enabled = item.enabled, role = Role.Button) {
                                                onDismiss()
                                                item.onClick()
                                            }.padding(AppSpacing.sm),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text(item.label, color = if (item.enabled) colors.textPrimary else colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberMenuPosition(gap: Int) = androidx.compose.runtime.remember(gap) {
    object : androidx.compose.ui.window.PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: androidx.compose.ui.unit.IntRect,
            windowSize: androidx.compose.ui.unit.IntSize,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            popupContentSize: androidx.compose.ui.unit.IntSize,
        ): IntOffset {
            val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) anchorBounds.left
                else anchorBounds.right - popupContentSize.width
            val below = anchorBounds.bottom + gap
            val y = if (below + popupContentSize.height <= windowSize.height) below
                else anchorBounds.top - popupContentSize.height - gap
            return IntOffset(
                x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
            )
        }
    }
}

@Composable
fun BoxScope.AppDialog(visible: Boolean, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AppModal(visible, title, onDismiss, sheet = false, content)
}

@Composable
fun BoxScope.AppSheet(visible: Boolean, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AppModal(visible, title, onDismiss, sheet = true, content)
}

@Composable
private fun BoxScope.AppModal(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    sheet: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(visible, onBack = onDismiss)
    AnimatedVisibility(
        visible,
        Modifier.matchParentSize(),
        enter = fadeIn(tween(AppMotion.fast)),
        exit = fadeOut(tween(AppMotion.fast)),
    ) {
        val colors = LocalAppColors.current
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = GlassTokens.scrimOpacity))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss))
            Box(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(AppSpacing.md),
                contentAlignment = if (sheet) Alignment.BottomCenter else Alignment.Center,
            ) {
                AppSurface(
                    Modifier.widthIn(max = if (sheet) AppSizes.sheet else AppSizes.dialog).fillMaxWidth()
                        .semantics { paneTitle = title }
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                    AppMaterial.Overlay,
                    if (sheet) AppShapes.capsule else AppShapes.dialog,
                    tint = colors.surface,
                    border = colors.controlBorder,
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(title, style = AppTypography.section)
                        content()
                    }
                }
            }
        }
    }
}
