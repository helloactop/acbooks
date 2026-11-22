package app.acbooks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BookSearchOverlay(expanded: Boolean, onDismiss: () -> Unit) {
    val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    GlassSearchOverlay(
        expanded = expanded,
        onDismissRequest = onDismiss,
        label = stringResource(R.string.search_query_hint),
        topInsets = topInsets,
        horizontalInsetsModifier = Modifier.windowInsetsPadding(horizontalInsets),
        searchIcon = MiuixIcons.Glass.Search,
        closeIcon = MiuixIcons.Glass.Close,
    ) {
        val searchBarBottom = topInsets.asPaddingValues().calculateTopPadding() +
            GlassSearchOverlayDefaults.BarHeight + GlassSearchOverlayDefaults.VerticalPadding * 2
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = stringResource(R.string.search_no_books),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.widthIn(max = 840.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = searchBarBottom + 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}
