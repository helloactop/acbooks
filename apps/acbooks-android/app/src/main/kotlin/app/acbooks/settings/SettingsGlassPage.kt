package app.acbooks.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.acbooks.core.designsystem.R as UiR
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsGlassPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.(background: Backdrop, page: Backdrop) -> Unit,
) {
    val background = rememberLayerBackdrop()
    val page = rememberLayerBackdrop()
    val scroll = rememberScrollState()
    val colors = MiuixTheme.colorScheme
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = title,
                isContentScrolled = scroll.value > 0,
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                expandable = false,
                progressiveBlur = true,
                backdrop = page,
                buttonSize = 48.dp,
                navigationIcon = {
                    GlassIconButton(onClick = onBack, backdrop = page, size = 48.dp) {
                        Icon(MiuixIcons.Glass.Back, stringResource(UiR.string.app_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().layerBackdrop(page)) {
            // Panels read the page background while the top bar and popups read the content above it.
            Box(
                Modifier.matchParentSize().layerBackdrop(background).background(
                    Brush.verticalGradient(listOf(colors.surface, colors.surfaceContainer)),
                ),
            )
            Column(
                Modifier.fillMaxSize()
                    .padding(
                        start = padding.calculateStartPadding(layoutDirection),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = padding.calculateBottomPadding(),
                    )
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(scroll)
                    .padding(top = padding.calculateTopPadding())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content(background, page)
            }
        }
    }
}

@Composable
internal fun Modifier.settingsGlassPanel(backdrop: Backdrop): Modifier {
    val shape = GlassShape(CardDefaults.CornerRadius)
    return glassPanel(
        backdrop = backdrop,
        shape = shape,
        material = GlassDialogDefaults.material(),
        stroke = GlassDialogDefaults.stroke(),
        fallback = Modifier.background(MiuixTheme.colorScheme.surfaceContainer, shape),
    )
}
