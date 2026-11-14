package app.acbooks.core.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CornerBasedShape
import app.acbooks.core.designsystem.theme.AppShapes
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.GlassTokens
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangularShape

enum class AppMaterial { Content, Control, Overlay }

@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    material: AppMaterial = AppMaterial.Content,
    shape: Shape = AppShapes.large,
    tint: Color = LocalAppColors.current.surface,
    foreground: Color = LocalAppColors.current.textPrimary,
    border: Color = Color.Unspecified,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val capability = LocalGlassCapability.current
    val depth = LocalGlassDepth.current
    val opticalBackdrop = backdrop.takeIf {
        material != AppMaterial.Content && capability != GlassCapability.Solid && depth == 0
    }
    val overlay = material == AppMaterial.Overlay
    val painting = if (opticalBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = opticalBackdrop,
            shape = { shape },
            layerBlock = layerBlock,
            effects = {
                blur((if (overlay) GlassTokens.overlayBlur else GlassTokens.controlBlur).toPx())
                if (!overlay && capability == GlassCapability.Refraction &&
                    (shape is RoundedRectangularShape || shape is CornerBasedShape)
                ) {
                    lens(GlassTokens.refractionHeight.toPx(), GlassTokens.refractionAmount.toPx())
                }
            },
            onDrawSurface = {
                drawRect(tint.copy(alpha = if (overlay) GlassTokens.overlayOpacity else GlassTokens.controlOpacity))
            },
        )
    } else {
        Modifier.background(tint)
    }
    val outline = if (border == Color.Unspecified) Modifier else Modifier.border(AppSizes.border, border, shape)

    CompositionLocalProvider(
        LocalContentColor provides foreground,
        LocalGlassDepth provides if (opticalBackdrop != null) depth + 1 else depth,
    ) {
        val surface = if (opticalBackdrop != null) {
            Modifier.then(painting).clip(shape)
        } else {
            val transform = if (layerBlock == null) Modifier else Modifier.graphicsLayer(layerBlock)
            transform.clip(shape).then(painting)
        }
        Box(modifier.then(surface).then(outline), content = content)
    }
}
