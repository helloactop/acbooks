package app.acbooks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    resizeForKeyboard: Boolean = false,
    overlay: @Composable (Backdrop) -> Unit = {},
    content: @Composable (Modifier, PaddingValues) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    val direction = LocalLayoutDirection.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = title,
                modifier = Modifier.semantics { heading() },
                expandable = true,
                progressiveBlur = true,
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(MiuixIcons.Glass.Back, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop).background(MiuixTheme.colorScheme.surface)
                .padding(start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction))
                .consumeWindowInsets(padding)
                .then(if (resizeForKeyboard) Modifier.windowInsetsPadding(WindowInsets.ime) else Modifier),
            contentAlignment = Alignment.TopCenter,
        ) {
            content(
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            )
        }
        overlay(backdrop)
    }
}
