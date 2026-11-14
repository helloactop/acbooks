package app.acbooks.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import app.acbooks.core.designsystem.glass.AppMaterial
import app.acbooks.core.designsystem.glass.AppSurface
import app.acbooks.core.designsystem.icons.AppIcon
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.icons.ControlIcon
import app.acbooks.core.designsystem.theme.AppShapes
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppMotion
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography

enum class ButtonKind { Primary, Secondary, Destructive }

@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    ButtonSurface(onClick, modifier, kind, enabled, loading, AppShapes.capsule) {
        Text(label, Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm), style = AppTypography.subhead)
    }
}

@Composable
fun AppIconButton(
    icon: AppIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    ButtonSurface(onClick, modifier, kind, enabled, loading, AppShapes.circle) {
        ControlIcon(icon, label)
    }
}

@Composable
fun AppAddButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppIconButton(AppIcons.Add, label, onClick, modifier.size(AppSizes.navigation), kind = ButtonKind.Primary)
}

@Composable
private fun ButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    kind: ButtonKind,
    enabled: Boolean,
    loading: Boolean,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) AppMotion.pressedScale else 1f,
        AppMotion.spring(),
        label = "button press",
    )
    val foreground = when {
        !enabled -> colors.textSecondary
        kind == ButtonKind.Primary -> colors.accentText
        kind == ButtonKind.Destructive -> colors.destructiveText
        else -> colors.textPrimary
    }
    val outline = when {
        !enabled -> colors.divider
        kind == ButtonKind.Primary -> colors.accentBorder
        kind == ButtonKind.Destructive -> colors.destructiveText
        else -> colors.controlBorder
    }
    AppSurface(
        modifier = modifier.defaultMinSize(AppSizes.touch, AppSizes.touch).clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled && !loading,
            role = Role.Button,
            onClick = onClick,
        ),
        material = if (enabled) AppMaterial.Control else AppMaterial.Content,
        shape = shape,
        tint = if (kind == ButtonKind.Primary && enabled) colors.accentSoft else colors.surfaceSecondary,
        foreground = foreground,
        border = outline,
        layerBlock = { scaleX = scale; scaleY = scale },
    ) {
        Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
            Box(Modifier.graphicsLayer { alpha = if (loading) 0f else 1f }) { content() }
            if (loading) CircularProgressIndicator(
                Modifier.size(AppSizes.iconSmall),
                color = foreground,
                strokeWidth = AppSizes.border,
            )
        }
    }
}
