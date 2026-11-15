package app.acbooks.core.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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

internal val popupEnterTransition =
    fadeIn(tween(AppMotion.popupDuration, easing = AppMotion.symmetricEasing)) +
        scaleIn(tween(AppMotion.popupDuration, easing = AppMotion.symmetricEasing), initialScale = AppMotion.popupScale)

internal val popupExitTransition =
    fadeOut(tween(AppMotion.popupDuration, easing = AppMotion.symmetricEasing)) +
        scaleOut(tween(AppMotion.popupDuration, easing = AppMotion.symmetricEasing), targetScale = AppMotion.popupScale)

@Composable
internal fun AnimatedPopupContent(visibility: MutableTransitionState<Boolean>, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visibleState = visibility,
        enter = popupEnterTransition,
        exit = popupExitTransition,
    ) {
        content()
    }
}

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
        if (expanded || visibility.currentState || !visibility.isIdle) {
            val gap = with(LocalDensity.current) { AppSpacing.xs.roundToPx() }
            Popup(
                popupPositionProvider = rememberMenuPosition(gap),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = expanded),
            ) {
                // Popup has its own window and cannot sample the scene's coordinates.
                CompositionLocalProvider(LocalBackdrop provides null) {
                    AnimatedPopupContent(visibility) {
                        val colors = LocalAppColors.current
                        AppSurface(
                            material = AppMaterial.Overlay,
                            shape = AppShapes.large,
                            tint = colors.surface,
                        ) {
                            Column(Modifier.widthIn(min = AppSizes.navigation, max = AppSizes.dialog)
                                .verticalScroll(rememberScrollState()).padding(AppSpacing.xs)) {
                                items.forEach { item ->
                                    Box(
                                        Modifier.fillMaxWidth().heightIn(min = AppSizes.touch)
                                            .clickable(enabled = expanded && item.enabled, role = Role.Button) {
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
internal fun rememberMenuPosition(gap: Int, overlapAnchor: Boolean = false) = remember(gap, overlapAnchor) {
    object : androidx.compose.ui.window.PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: androidx.compose.ui.unit.IntRect,
            windowSize: androidx.compose.ui.unit.IntSize,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            popupContentSize: androidx.compose.ui.unit.IntSize,
        ): IntOffset {
            val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) anchorBounds.left
                else anchorBounds.right - popupContentSize.width
            val below = if (overlapAnchor) anchorBounds.top else anchorBounds.bottom + gap
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
fun BoxScope.AppDialog(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    actions: List<AppDialogAction> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    AppModal(visible, title, onDismiss, actions, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    actions: List<AppDialogAction> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            if (mounted && sheetState.hasExpandedState) sheetState.show()
            mounted = true
        } else if (mounted) {
            sheetState.hide()
            mounted = false
        }
    }
    if (!mounted && !visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = AppSizes.sheet + AppSpacing.md * 2,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = GlassTokens.scrimOpacity),
        dragHandle = null,
    ) {
        AppSurface(
            Modifier.fillMaxWidth()
                .padding(horizontal = AppSpacing.md)
                .padding(bottom = AppSpacing.md)
                .semantics { paneTitle = title },
            AppMaterial.Overlay,
            AppShapes.capsule,
        ) {
            ModalContent(title, actions, content)
        }
    }
}

@Composable
private fun BoxScope.AppModal(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    actions: List<AppDialogAction>,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(tween(AppMotion.transitionDuration, easing = AppMotion.symmetricEasing)),
        exit = fadeOut(tween(AppMotion.transitionDuration, easing = AppMotion.symmetricEasing)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = constraints.maxHeight
            val offscreenOffset: (Int) -> Int = { height -> (viewportHeight + height) / 2 }
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = GlassTokens.scrimOpacity))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss))
            Box(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(AppSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                AppSurface(
                    Modifier.widthIn(max = AppSizes.dialog).fillMaxWidth()
                        .animateEnterExit(
                            enter = slideInVertically(
                                tween(AppMotion.transitionDuration, easing = AppMotion.symmetricEasing), offscreenOffset,
                            ),
                            exit = slideOutVertically(
                                tween(AppMotion.transitionDuration, easing = AppMotion.symmetricEasing), offscreenOffset,
                            ),
                        )
                        .semantics { paneTitle = title }
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                    AppMaterial.Overlay,
                    AppShapes.dialog,
                ) {
                    ModalContent(title, actions, content)
                }
            }
        }
    }
}

@Composable
private fun ModalContent(
    title: String,
    actions: List<AppDialogAction>,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Text(title, style = AppTypography.section)
        content()
        AppDialogActions(actions)
    }
}
