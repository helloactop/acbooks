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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
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
internal fun MainTabs() {
    val items = listOf(
        GlassNavigationItem(MiuixIcons.Glass.HomeOutline, stringResource(R.string.tab_home)),
        GlassNavigationItem(MiuixIcons.Glass.BookOutline, stringResource(R.string.tab_library)),
        GlassNavigationItem(MiuixIcons.Glass.DigitalStatistics, stringResource(R.string.tab_statistics)),
    )
    val searchTitle = stringResource(R.string.tab_search)
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = searchOpen || pager.currentPage != 0) {
        if (searchOpen) {
            searchOpen = false
        } else {
            scope.launch { pager.springAnimateToPage(0) }
        }
    }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = if (searchOpen) searchTitle else items[pager.currentPage].label.orEmpty(),
                modifier = Modifier.semantics { heading() },
                expandable = false,
                isContentScrolled = false,
                backdrop = backdrop,
            )
        },
        bottomBar = {
            val indicator = GlassNavigationBarDefaults.indicatorColor()
            GlassNavigationBar(
                items = items,
                selectedIndex = pager.currentPage,
                onSelect = { index ->
                    if (searchOpen) {
                        pager.requestScrollToPage(index)
                        searchOpen = false
                    } else {
                        scope.launch { pager.springAnimateToPage(index) }
                    }
                },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                indicatorColor = if (searchOpen) Color.Transparent else indicator,
                trailingAction = {
                    GlassIconButton(
                        onClick = { searchOpen = true },
                        modifier = Modifier.semantics {
                            role = Role.Tab
                            selected = searchOpen
                        },
                        size = GlassNavigationBarDefaults.Height,
                        backdrop = backdrop,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Glass.Search,
                            contentDescription = searchTitle,
                            modifier = Modifier
                                .background(if (searchOpen) indicator else Color.Transparent, CircleShape)
                                .padding(6.dp),
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
            val contentModifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            if (searchOpen) {
                Box(contentModifier)
            } else {
                HorizontalPager(
                    state = pager,
                    modifier = contentModifier,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pager,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}
