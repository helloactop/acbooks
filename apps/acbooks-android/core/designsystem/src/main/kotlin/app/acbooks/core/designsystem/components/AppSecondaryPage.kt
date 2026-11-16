package app.acbooks.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.acbooks.core.designsystem.glass.AppGlassScene
import app.acbooks.core.designsystem.glass.LocalHazeState
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography
import app.acbooks.core.designsystem.tokens.GlassTokens
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

@Composable
fun AppSecondaryPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val minHeaderHeight = AppSpacing.tabHeaderTop + AppSizes.touch
    var headerHeight by remember { mutableIntStateOf(0) }
    val bodyTop = if (headerHeight == 0) minHeaderHeight else with(density) { headerHeight.toDp() }

    AppGlassScene(
        modifier = Modifier.fillMaxSize().background(LocalAppColors.current.background).safeDrawingPadding(),
        sampleButtonBackground = true,
        background = {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll)
                    .padding(horizontal = AppSpacing.page)
                    .padding(top = bodyTop + AppSpacing.md, bottom = AppSpacing.page),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                content = content,
            )
        },
    ) {
        GlassPageHeader(
            scrollOffset = scroll.value,
            modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { headerHeight = it.height },
        ) {
            AppSecondaryTopBar(title, onBack)
        }
    }
}

@Composable
internal fun GlassPageHeader(
    scrollOffset: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val revealDistance = with(LocalDensity.current) { GlassTokens.headerRevealDistance.toPx() }
    val progress = (scrollOffset / revealDistance).coerceIn(0f, 1f)
    val colors = LocalAppColors.current
    val hazeState = LocalHazeState.current
    Box(modifier.fillMaxWidth()) {
        if (hazeState != null && progress > 0f) {
            val blurStyle = remember(progress) {
                HazeBlurStyle { blurRadius(GlassTokens.headerBlur * progress) }
            }
            Box(
                Modifier.matchParentSize()
                    .layout { measurable, constraints ->
                        // Extend the blur below the header without moving the content or its hit targets.
                        val extra = (GlassTokens.headerFadeHeight / 2).roundToPx()
                        val height = constraints.maxHeight
                        val layer = measurable.measure(
                            constraints.copy(minHeight = height + extra, maxHeight = height + extra),
                        )
                        layout(constraints.maxWidth, height) { layer.place(0, 0) }
                    }
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val fade = Brush.verticalGradient(
                            colors = listOf(Color.White, Color.Transparent),
                            startY = (size.height - GlassTokens.headerFadeHeight.toPx()).coerceAtLeast(0f),
                            endY = size.height,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(fade, blendMode = BlendMode.DstIn)
                        }
                    }
                    .hazeBlur(
                        input = HazeInput.Sources(hazeState),
                        style = blurStyle,
                    ),
            ) {
                Box(
                    Modifier.matchParentSize()
                        .background(colors.background.copy(alpha = GlassTokens.headerTintOpacity * progress)),
                )
            }
        }
        content()
    }
}

@Composable
fun AppSecondaryTopBar(title: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Box(Modifier.pageHeaderLayout()) {
        Text(
            title,
            Modifier.align(Alignment.Center).fillMaxWidth()
                .padding(horizontal = AppSizes.touch + AppSpacing.sm)
                .semantics { heading() },
            color = colors.textPrimary,
            style = AppTypography.section,
            textAlign = TextAlign.Center,
        )
        AppBackButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
    }
}
