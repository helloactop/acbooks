package app.acbooks

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.core.database.LocalBookCard
import app.acbooks.library.BookMenuTarget
import app.acbooks.library.LibraryCatalog
import app.acbooks.library.LibraryDeleteDialog
import app.acbooks.library.LibraryImportDialog
import app.acbooks.library.LibraryMenu
import app.acbooks.library.LibraryMenus
import app.acbooks.library.LibraryScreen
import app.acbooks.library.LibraryTopBarActions
import app.acbooks.library.LibraryViewModel
import kotlin.math.abs
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.glassSearchBarTransition
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
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
    catalog: LibraryCatalog,
    library: LibraryViewModel,
    isCurrentDestination: Boolean,
    onOpenSettings: () -> Unit,
    onOpenBook: (String) -> Unit,
    onReadBook: (String) -> Unit,
) {
    val items = listOf(
        GlassNavigationItem(MiuixIcons.Glass.HomeOutline, stringResource(R.string.tab_home)),
        GlassNavigationItem(MiuixIcons.Glass.BookOutline, stringResource(R.string.tab_library)),
        GlassNavigationItem(MiuixIcons.Glass.DigitalStatistics, stringResource(R.string.tab_statistics)),
    )
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navigationBackdrop = rememberLayerBackdrop()
    val menuBackdrop = rememberLayerBackdrop()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchBarAlpha = rememberGlassSearchBarAlpha(searchExpanded)
    val topBarTransition = Modifier.glassSearchBarTransition(searchExpanded) { 1f }
    val displayMode by library.displayMode.collectAsStateWithLifecycle()
    val importProgress by library.progress.collectAsStateWithLifecycle()
    val cloud by library.cloudState.collectAsStateWithLifecycle()
    val displayAnchor = rememberGlassPopupAnchor()
    val moreAnchor = rememberGlassPopupAnchor()
    var libraryMenu by remember { mutableStateOf<LibraryMenu?>(null) }
    var bookMenuTarget by remember { mutableStateOf<BookMenuTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalBookCard?>(null) }
    var deleteDialogShown by remember { mutableStateOf(false) }
    var deletingBook by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), library::importBooks)

    LaunchedEffect(isCurrentDestination, pager.currentPage, searchExpanded) {
        if (!isCurrentDestination || pager.currentPage != 1 || searchExpanded) {
            libraryMenu = null
            deleteDialogShown = false
        }
    }

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
        enabled = isCurrentDestination && !searchExpanded && libraryMenu == null && importProgress == null &&
            (pager.settledPage != 0 || pager.targetPage != 0),
    ) {
        selectTab(0)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
                .layerBackdrop(menuBackdrop)
                .graphicsLayer { alpha = searchBarAlpha.value }
                .then(
                    if (searchExpanded) Modifier.clearAndSetSemantics { hideFromAccessibility() }
                    else Modifier,
                ),
            bottomBar = {
                val isPagerDragged by pager.interactionSource.collectIsDraggedAsState()
                GlassNavigationBar(
                    items = items,
                    selectedIndex = if (isPagerDragged) pager.settledPage else pager.targetPage,
                    onSelect = ::selectTab,
                    backdrop = navigationBackdrop,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    trailingAction = {
                        GlassIconButton(
                            onClick = { searchExpanded = true },
                            size = GlassNavigationBarDefaults.Height,
                            backdrop = navigationBackdrop,
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
        ) { navigationPadding ->
            Box(
                Modifier.fillMaxSize()
                    .layerBackdrop(navigationBackdrop)
                    .background(MiuixTheme.colorScheme.surface),
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !searchExpanded,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pager,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                ) { page ->
                    MainTabPage(
                        navigationPadding = navigationPadding,
                        topBar = { backdrop, scrollBehavior ->
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
                                    progressiveBlur = page == 1,
                                    scrollBehavior = scrollBehavior,
                                    backdrop = backdrop,
                                    actions = {
                                        if (page == 1) {
                                            LibraryTopBarActions(displayAnchor, moreAnchor) { libraryMenu = it }
                                        }
                                    },
                                )
                            }
                        },
                    ) { scrollBehavior, pagePadding ->
                        when (page) {
                            0 -> HomeScreen(
                                scrollBehavior = scrollBehavior,
                                scaffoldPadding = pagePadding,
                                onOpenLibrary = { selectTab(1) },
                            )
                            1 -> LibraryScreen(
                                catalog = catalog,
                                mode = displayMode,
                                scrollBehavior = scrollBehavior,
                                pagePadding = pagePadding,
                                coverFile = library::coverFile,
                                onRead = onReadBook,
                                onDetails = onOpenBook,
                                onShowMenu = { target ->
                                    bookMenuTarget = target
                                    libraryMenu = LibraryMenu.Book
                                },
                            )
                            else -> Box(
                                Modifier.fillMaxSize()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                                    .verticalScroll(rememberScrollState())
                                    .padding(pagePadding),
                            )
                        }
                    }
                }
            }
            LibraryImportDialog(
                progress = importProgress.takeIf { isCurrentDestination },
                backdrop = navigationBackdrop,
                onStop = library::cancelImport,
                onImportMatches = library::importMatches,
                onDismiss = library::dismissImport,
            )
            LibraryDeleteDialog(
                book = deleteTarget,
                show = deleteDialogShown && isCurrentDestination,
                busy = deletingBook,
                cloudConnected = cloud.connected,
                backdrop = navigationBackdrop,
                onDismiss = { if (!deletingBook) deleteDialogShown = false },
                onDismissFinished = { if (!deleteDialogShown) deleteTarget = null },
                onDeleteLocal = {
                    val target = deleteTarget
                    if (!deletingBook && target != null) {
                        deletingBook = true
                        scope.launch {
                            try {
                                if (library.deleteLocalBook(target.id)) {
                                    deleteDialogShown = false
                                } else {
                                    Toast.makeText(context, R.string.library_delete_local_failed, Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                deletingBook = false
                            }
                        }
                    }
                },
                onDeleteEverywhere = {
                    val target = deleteTarget
                    if (!deletingBook && target != null) {
                        deletingBook = true
                        scope.launch {
                            try {
                                if (library.deleteBookEverywhere(target.id)) {
                                    deleteDialogShown = false
                                } else {
                                    Toast.makeText(context, R.string.library_delete_cloud_failed, Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                deletingBook = false
                            }
                        }
                    }
                },
            )
        }
        LibraryMenus(
            activeMenu = libraryMenu,
            displayAnchor = displayAnchor,
            moreAnchor = moreAnchor,
            bookTarget = bookMenuTarget,
            backdrop = menuBackdrop,
            mode = displayMode,
            importing = importProgress?.running == true,
            cloudConnected = cloud.connection.hasSession,
            cloudBusy = cloud.busy,
            onDismiss = { menu -> if (libraryMenu == menu) libraryMenu = null },
            onDisplayMode = library::setDisplayMode,
            onAddBooks = {
                libraryMenu = null
                picker.launch(arrayOf("application/epub+zip", "application/pdf"))
            },
            onSync = {
                libraryMenu = null
                library.syncLibrary()
            },
            onRead = { id ->
                libraryMenu = null
                onReadBook(id)
            },
            onDetails = { id ->
                libraryMenu = null
                onOpenBook(id)
            },
            onDelete = { book ->
                libraryMenu = null
                deleteTarget = book
                deleteDialogShown = true
            },
        )
        BookSearchOverlay(
            expanded = searchExpanded,
            books = catalog.books,
            onDismiss = { searchExpanded = false },
            onOpenBook = onOpenBook,
        )
    }
}

@Composable
private fun MainTabPage(
    navigationPadding: PaddingValues,
    topBar: @Composable (Backdrop, ScrollBehavior) -> Unit,
    content: @Composable (ScrollBehavior, PaddingValues) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val background = MiuixTheme.colorScheme.surface
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { topBar(backdrop, scrollBehavior) },
    ) { topBarPadding ->
        // The page owns its header height. The outer scaffold supplies only the shared safe edges.
        val pagePadding = PaddingValues(
            start = navigationPadding.calculateStartPadding(layoutDirection),
            top = topBarPadding.calculateTopPadding(),
            end = navigationPadding.calculateEndPadding(layoutDirection),
            bottom = navigationPadding.calculateBottomPadding(),
        )
        Box(
            Modifier.fillMaxSize()
                .layerBackdrop(backdrop)
                .background(background)
                .consumeWindowInsets(pagePadding),
        ) {
            content(scrollBehavior, pagePadding)
        }
    }
}
