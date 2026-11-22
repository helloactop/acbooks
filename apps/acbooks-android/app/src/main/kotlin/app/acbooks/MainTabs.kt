package app.acbooks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.BookOutline
import top.yukonga.miuix.kmp.icon.glass.DigitalStatistics
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.springAnimateToPage

@Composable
internal fun MainTabs(
    avatar: ImageBitmap?,
    isCurrentDestination: Boolean,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val items = listOf(
        GlassNavigationItem(MiuixIcons.Glass.HomeOutline, stringResource(R.string.tab_home)),
        GlassNavigationItem(MiuixIcons.Glass.BookOutline, stringResource(R.string.tab_library)),
        GlassNavigationItem(MiuixIcons.Glass.DigitalStatistics, stringResource(R.string.tab_statistics)),
    )
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val homeScrollBehavior = MiuixScrollBehavior()

    BackHandler(enabled = isCurrentDestination && pager.currentPage != 0) {
        scope.launch { pager.springAnimateToPage(0) }
    }

    Scaffold(
        topBar = {
            if (pager.currentPage == 0) {
                HomeTopBar(
                    avatar = avatar,
                    backdrop = backdrop,
                    scrollBehavior = homeScrollBehavior,
                    onOpenSettings = onOpenSettings,
                )
            } else {
                GlassTopAppBar(
                    title = items[pager.currentPage].label.orEmpty(),
                    modifier = Modifier.semantics { heading() },
                    expandable = false,
                    isContentScrolled = false,
                    backdrop = backdrop,
                )
            }
        },
        bottomBar = {
            GlassNavigationBar(
                items = items,
                selectedIndex = pager.currentPage,
                onSelect = { index -> scope.launch { pager.springAnimateToPage(index) } },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                trailingAction = {
                    GlassIconButton(
                        onClick = onOpenSearch,
                        size = GlassNavigationBarDefaults.Height,
                        backdrop = backdrop,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Glass.Search,
                            contentDescription = stringResource(R.string.search_title),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize()
                .layerBackdrop(backdrop)
                .background(MiuixTheme.colorScheme.surface),
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = PagerNavigationSpringSpec,
                ),
            ) { page ->
                if (page == 0) {
                    HomeScreen(
                        scrollBehavior = homeScrollBehavior,
                        onOpenLibrary = { scope.launch { pager.springAnimateToPage(1) } },
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}
