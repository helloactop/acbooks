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
import app.acbooks.core.designsystem.components.AppMenuHostState
import app.acbooks.core.designsystem.components.AppMenuOverlay
import app.acbooks.core.designsystem.components.LocalAppMenuHost
import app.acbooks.core.designsystem.theme.LocalAppColors
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

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
internal val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
val LocalGlassCapability = staticCompositionLocalOf { GlassCapability.Solid }
internal val LocalGlassDepth = staticCompositionLocalOf { 0 }
internal val LocalButtonBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun AppGlassScene(
    modifier: Modifier = Modifier,
    reduceTransparency: Boolean = false,
    eInk: Boolean = false,
    capabilityLimit: GlassCapability = GlassCapability.Refraction,
    sampleButtonBackground: Boolean = false,
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
    val hazeState = rememberHazeState()
    val hazeCapture = if (capability == GlassCapability.Solid) Modifier else Modifier.hazeSource(hazeState)
    val buttonBackdrop = if (backdrop != null && sampleButtonBackground) rememberLayerBackdrop() else null
    var buttonBackgroundReady by remember(buttonBackdrop) { mutableStateOf(false) }
    var backgroundReady by remember(backdrop) { mutableStateOf(false) }
    val parentMenuHost = LocalAppMenuHost.current
    val menuHost = remember(parentMenuHost) { parentMenuHost ?: AppMenuHostState() }

    CompositionLocalProvider(LocalAppMenuHost provides menuHost) {
        Box(modifier) {
            if (buttonBackdrop != null) {
                Box(
                    Modifier.matchParentSize()
                        .onGloballyPositioned { buttonBackgroundReady = it.size.width > 0 && it.size.height > 0 }
                        .layerBackdrop(buttonBackdrop)
                        .background(colors.background),
                )
            }
            val capture = if (backdrop == null) Modifier else {
                Modifier.onGloballyPositioned { backgroundReady = it.size.width > 0 && it.size.height > 0 }
                    .layerBackdrop(backdrop)
            }
            Box(
                Modifier.matchParentSize()
                    .then(hazeCapture)
                    .drawWithContent {
                        val accelerated = drawContext.canvas.nativeCanvas.isHardwareAccelerated
                        if (canvasAccelerated != accelerated) canvasAccelerated = accelerated
                        drawContent()
                    }
                    .then(capture)
                    .background(colors.background),
            ) {
                CompositionLocalProvider(
                    LocalButtonBackdrop provides buttonBackdrop.takeIf { buttonBackgroundReady },
                    LocalButtonGlassCapability provides capability,
                ) {
                    background()
                }
            }
            CompositionLocalProvider(
                LocalBackdrop provides backdrop.takeIf { backgroundReady },
                LocalHazeState provides hazeState.takeIf {
                    backgroundReady && capability != GlassCapability.Solid
                },
                LocalGlassCapability provides capability,
                LocalGlassDepth provides 0,
            ) {
                content()
                if (parentMenuHost == null) AppMenuOverlay(menuHost)
            }
        }
    }
}

internal val LocalButtonGlassCapability = staticCompositionLocalOf { GlassCapability.Solid }
