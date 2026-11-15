package app.acbooks.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.acbooks.core.designsystem.R
import app.acbooks.core.designsystem.glass.AppMaterial
import app.acbooks.core.designsystem.glass.AppSurface
import app.acbooks.core.designsystem.glass.GlassCapability
import app.acbooks.core.designsystem.glass.LocalBackdrop
import app.acbooks.core.designsystem.glass.LocalButtonBackdrop
import app.acbooks.core.designsystem.glass.LocalButtonGlassCapability
import app.acbooks.core.designsystem.glass.LocalGlassCapability
import app.acbooks.core.designsystem.glass.LocalGlassDepth
import app.acbooks.core.designsystem.icons.AppIcon
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.icons.ControlIcon
import app.acbooks.core.designsystem.theme.AppShapes
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppMotion
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.ButtonSizeTokens
import app.acbooks.core.designsystem.tokens.IconButtonSize
import app.acbooks.core.designsystem.tokens.TextButtonSize
import app.acbooks.core.designsystem.tokens.buttonGlassTint
import com.kyant.backdrop.catalog.components.LiquidButton

enum class ButtonKind { Primary, Secondary, Destructive }

@Composable
fun LiquidGlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    size: TextButtonSize = TextButtonSize.Normal,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val minWidth = if (size == TextButtonSize.Compact) ButtonSizeTokens.touch else ButtonSizeTokens.textMinWidth
    LiquidGlassContentButton(
        onClick = onClick,
        modifier = modifier.widthIn(min = minWidth),
        kind = kind,
        size = size,
        enabled = enabled,
        loading = loading,
    ) {
        Text(label, style = size.textStyle, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun LiquidGlassContentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    size: TextButtonSize = TextButtonSize.Normal,
    enabled: Boolean = true,
    loading: Boolean = false,
    content: @Composable () -> Unit,
) {
    GlassButtonSurface(
        modifier = modifier.heightIn(min = ButtonSizeTokens.touch),
        onClick = onClick,
        kind = kind,
        enabled = enabled,
        loading = loading,
        visualHeight = size.height,
        touchInsetX = 0.dp,
        touchInsetY = touchInset(size.height),
        paddingX = ButtonSizeTokens.textPadding,
        paddingY = ButtonSizeTokens.textVerticalPadding,
        content = content,
    )
}

@Composable
internal fun LiquidGlassButtonSegment(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Button,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale by buttonPressScale(interaction)
    Box(
        modifier.widthIn(min = ButtonSizeTokens.touch).heightIn(min = ButtonSizeTokens.touch)
            .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { content() }
    }
}

@Composable
internal fun LiquidGlassButtonGroup(content: @Composable BoxScope.() -> Unit) {
    val backdrop = LocalBackdrop.current
    val groupBackdrop = backdrop ?: LocalButtonBackdrop.current
    val capability = if (backdrop != null) LocalGlassCapability.current else LocalButtonGlassCapability.current
    CompositionLocalProvider(LocalBackdrop provides groupBackdrop, LocalGlassCapability provides capability) {
        AppSurface(material = AppMaterial.Control, shape = AppShapes.circle, content = content)
    }
}

@Composable
fun LiquidGlassIconButton(
    icon: AppIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    size: IconButtonSize = IconButtonSize.MD,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val inset = touchInset(size.diameter)
    GlassButtonSurface(
        modifier = modifier.size(size.diameter + inset * 2),
        onClick = onClick,
        kind = kind,
        enabled = enabled,
        loading = loading,
        visualHeight = size.diameter,
        touchInsetX = inset,
        touchInsetY = inset,
        paddingX = 0.dp,
        paddingY = 0.dp,
    ) {
        ControlIcon(icon, label, iconSize = size.icon)
    }
}

@Composable
fun AppAddButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidGlassIconButton(
        AppIcons.Add, label, onClick, modifier,
        kind = ButtonKind.Primary, size = IconButtonSize.Dock,
    )
}

@Composable
fun AppBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidGlassIconButton(
        AppIcons.Back, stringResource(R.string.app_back), onClick, modifier, size = IconButtonSize.LG,
    )
}

private fun touchInset(visualSize: Dp): Dp = (ButtonSizeTokens.touch - visualSize).coerceAtLeast(0.dp) / 2

@Composable
private fun GlassButtonSurface(
    modifier: Modifier,
    onClick: () -> Unit,
    kind: ButtonKind,
    enabled: Boolean,
    loading: Boolean,
    visualHeight: Dp,
    touchInsetX: Dp,
    touchInsetY: Dp,
    paddingX: Dp,
    paddingY: Dp,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val (tint, foreground) = when (kind) {
        ButtonKind.Primary -> colors.accent to colors.onAccent
        ButtonKind.Secondary -> colors.surfaceSecondary to colors.textPrimary
        ButtonKind.Destructive -> colors.destructive to colors.onDestructive
    }
    val opacity = if (kind == ButtonKind.Secondary) ButtonSizeTokens.neutralTintOpacity else ButtonSizeTokens.colorTintOpacity
    val surface = modifier.alpha(if (enabled) 1f else ButtonSizeTokens.disabledOpacity)
    val interactive = enabled && !loading
    val sceneBackdrop = LocalBackdrop.current
    val backdrop = sceneBackdrop ?: LocalButtonBackdrop.current
    val capability = if (sceneBackdrop != null) LocalGlassCapability.current else LocalButtonGlassCapability.current
    if (backdrop != null && capability != GlassCapability.Solid && LocalGlassDepth.current <= 1) {
        val glassTint = remember(tint, foreground, opacity) { buttonGlassTint(tint, foreground, opacity) }
        CompositionLocalProvider(LocalContentColor provides foreground) {
            LiquidButton(
                onClick = onClick,
                backdrop = backdrop,
                modifier = surface,
                isInteractive = interactive,
                enabled = interactive,
                surfaceColor = glassTint,
                horizontalPadding = paddingX,
                verticalPadding = paddingY,
                minHeight = visualHeight,
                touchInsetX = touchInsetX,
                touchInsetY = touchInsetY,
                refract = capability == GlassCapability.Refraction,
            ) {
                ButtonContent(loading, content)
            }
        }
    } else {
        val interaction = remember { MutableInteractionSource() }
        val scale by buttonPressScale(interaction)
        AppSurface(
            modifier = surface
                .clickable(interaction, indication = null, enabled = interactive, role = Role.Button, onClick = onClick)
                .padding(horizontal = touchInsetX, vertical = touchInsetY)
                .heightIn(min = visualHeight),
            shape = AppShapes.circle,
            tint = tint,
            foreground = foreground,
            layerBlock = { scaleX = scale; scaleY = scale },
        ) {
            Box(
                Modifier.align(Alignment.Center).padding(horizontal = paddingX, vertical = paddingY),
                contentAlignment = Alignment.Center,
            ) {
                ButtonContent(loading, content)
            }
        }
    }
}

@Composable
private fun buttonPressScale(interaction: MutableInteractionSource) = animateFloatAsState(
    if (interaction.collectIsPressedAsState().value) AppMotion.pressedScale else 1f,
    AppMotion.spring(),
    label = "button press",
)

@Composable
private fun ButtonContent(loading: Boolean, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.alpha(if (loading) 0f else 1f)) { content() }
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(AppSizes.iconSmall),
                color = LocalContentColor.current,
                strokeWidth = AppSizes.border,
            )
        }
    }
}
