package app.acbooks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
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
import top.yukonga.miuix.kmp.glass.glassSearchBarTransition
import top.yukonga.miuix.kmp.glass.rememberGlassSearchBarAlpha
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
) {
    val items = listOf(
        GlassNavigationItem(MiuixIcons.Glass.HomeOutline, stringResource(R.string.tab_home)),
        GlassNavigationItem(MiuixIcons.Glass.BookOutline, stringResource(R.string.tab_library)),
        GlassNavigationItem(MiuixIcons.Glass.DigitalStatistics, stringResource(R.string.tab_statistics)),
    )
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val tabScrollBehaviors = List(items.size) { page ->
        key(page) { MiuixScrollBehavior() }
    }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchBarAlpha = rememberGlassSearchBarAlpha(searchExpanded)
    val topBarTransition = Modifier.glassSearchBarTransition(searchExpanded) { 1f }

    fun selectTab(index: Int) {
        scope.launch {
            if (abs(index - pager.currentPage) <= 1) {
                pager.springAnimateToPage(index)
            } else {
                // Match the priority of Miuix tab animations so a jump can interrupt one.
                with(pager) {
                    scroll(MutatePriority.UserInput) {
                        updateTargetPage(index)
                        updateCurrentPage(index)
                    }
                }
            }
        }
    }

    BackHandler(
        enabled = isCurrentDestination && !searchExpanded && (pager.settledPage != 0 || pager.targetPage != 0),
    ) {
        selectTab(0)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { alpha = searchBarAlpha.value }
                .then(
                    if (searchExpanded) Modifier.clearAndSetSemantics { hideFromAccessibility() }
                    else Modifier,
                ),
            topBar = {
                val page = pager.currentPage
                val scrollBehavior = tabScrollBehaviors[page]
                key(page) {
                    if (page == 0) {
                        HomeTopBar(
                            avatar = avatar,
                            backdrop = backdrop,
                            scrollBehavior = scrollBehavior,
                            onOpenSettings = onOpenSettings,
                            modifier = topBarTransition,
                        )
                    } else {
                        GlassTopAppBar(
                            title = items[page].label.orEmpty(),
                            modifier = Modifier.semantics { heading() },
                            contentModifier = topBarTransition,
                            expandable = true,
                            scrollBehavior = scrollBehavior,
                            backdrop = backdrop,
                        )
                    }
                }
            },
            bottomBar = {
                val isPagerDragged by pager.interactionSource.collectIsDraggedAsState()
                GlassNavigationBar(
                    items = items,
                    selectedIndex = if (isPagerDragged) pager.settledPage else pager.targetPage,
                    onSelect = ::selectTab,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    trailingAction = {
                        GlassIconButton(
                            onClick = { searchExpanded = true },
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
                    userScrollEnabled = !searchExpanded,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pager,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                ) { page ->
                    val scrollBehavior = tabScrollBehaviors[page]
                    if (page == 0) {
                        HomeScreen(
                            scrollBehavior = scrollBehavior,
                            onOpenLibrary = { selectTab(1) },
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
        BookSearchOverlay(expanded = searchExpanded, onDismiss = { searchExpanded = false })
    }
}
