package app.acbooks.core.designsystem.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import app.acbooks.core.designsystem.theme.LocalAppColors
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

enum class GlassCapability { Solid, Blur, Refraction }

fun glassCapability(
    sdk: Int,
    hardwareAccelerated: Boolean,
    reduceTransparency: Boolean,
    eInk: Boolean,
): GlassCapability = when {
    !hardwareAccelerated || reduceTransparency || eInk -> GlassCapability.Solid
    sdk >= 33 -> GlassCapability.Refraction
    sdk >= 31 -> GlassCapability.Blur
    else -> GlassCapability.Solid
}

internal val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalGlassCapability = staticCompositionLocalOf { GlassCapability.Solid }
internal val LocalGlassDepth = staticCompositionLocalOf { 0 }

@Composable
fun AppGlassScene(
    modifier: Modifier = Modifier,
    reduceTransparency: Boolean = false,
    eInk: Boolean = false,
    capabilityLimit: GlassCapability = GlassCapability.Refraction,
    background: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val colors = LocalAppColors.current
    var canvasAccelerated by remember(view) { mutableStateOf<Boolean?>(null) }
    val capability = glassCapability(
        Build.VERSION.SDK_INT,
        canvasAccelerated ?: view.isHardwareAccelerated,
        reduceTransparency,
        eInk,
    ).coerceAtMost(capabilityLimit)
    val backdrop = if (capability != GlassCapability.Solid) rememberLayerBackdrop() else null
    var backgroundReady by remember(backdrop) { mutableStateOf(false) }

    Box(modifier) {
        val capture = if (backdrop == null) Modifier else {
            Modifier.onGloballyPositioned { backgroundReady = it.size.width > 0 && it.size.height > 0 }
                .layerBackdrop(backdrop)
        }
        Box(
            Modifier.matchParentSize()
                .drawWithContent {
                    val accelerated = drawContext.canvas.nativeCanvas.isHardwareAccelerated
                    if (canvasAccelerated != accelerated) canvasAccelerated = accelerated
                    drawContent()
                }
                .then(capture)
                .background(colors.background),
            content = background,
        )
        CompositionLocalProvider(
            LocalBackdrop provides backdrop.takeIf { backgroundReady },
            LocalGlassCapability provides capability,
            LocalGlassDepth provides 0,
        ) {
            content()
        }
    }
}
