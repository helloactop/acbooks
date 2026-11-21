package app.acbooks.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.acbooks.R
import app.acbooks.core.datastore.ThemeMode
import app.acbooks.core.designsystem.R as UiR
import app.acbooks.feature.home.HomeHeader
import app.acbooks.library.LibraryBookDetails
import app.acbooks.library.LibraryImportPanel
import app.acbooks.library.LibraryScreen
import app.acbooks.library.LibraryViewModel
import app.acbooks.profile.AvatarSync
import app.acbooks.profile.ProfileAvatars
import app.acbooks.reader.LocalReaderScreen
import app.acbooks.server.ServerLoginScreen
import app.acbooks.server.ServerSessions
import app.acbooks.server.ServerSettingsScreen
import app.acbooks.server.serverStatusLabel
import app.acbooks.settings.SettingsGlassPage
import app.acbooks.settings.settingsGlassPanel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.BookOutline
import top.yukonga.miuix.kmp.icon.glass.Cloud
import top.yukonga.miuix.kmp.icon.glass.ContactsCircle
import top.yukonga.miuix.kmp.icon.glass.DigitalStatistics
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val HOME_ROUTE = "home"
private const val LIBRARY_ROUTE = "library"
private const val STATISTICS_ROUTE = "statistics"
private const val SETTINGS_ROUTE = "settings"
private const val APPEARANCE_SETTINGS_ROUTE = "settings/appearance"
private const val SERVICES_ROUTE = "settings/services"
private const val SERVER_ROUTE = "settings/services/server"
private const val SERVER_LOGIN_ROUTE = "settings/services/server/login"
private const val BOOK_KEY = "bookId"
private const val BOOK_ROUTE = "library/book"
private const val READER_ROUTE = "library/reader"
private val dockRoutes = listOf(HOME_ROUTE, LIBRARY_ROUTE, STATISTICS_ROUTE)

@Composable
internal fun AcbooksNavHost(
    themeMode: ThemeMode,
    nickname: String?,
    serverSessions: ServerSessions,
    profileAvatars: ProfileAvatars,
    library: LibraryViewModel,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val session by serverSessions.session.collectAsStateWithLifecycle()
    val avatar by profileAvatars.image.collectAsStateWithLifecycle()
    val avatarSync by profileAvatars.sync.collectAsStateWithLifecycle()
    LaunchedEffect(serverSessions) { serverSessions.restore() }
    LaunchedEffect(profileAvatars) { profileAvatars.restore() }
    LaunchedEffect(session) { profileAvatars.refresh(session) }

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
        modifier = Modifier.fillMaxSize().clipToBounds().background(MiuixTheme.colorScheme.surface),
        enterTransition = { pageEnter() },
        exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { pageReturnExit() },
    ) {
        composable(HOME_ROUTE) {
            MainTabs(
                library = library,
                avatar = avatar,
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
                onReadBook = { id -> navController.navigate("$READER_ROUTE/$id") { launchSingleTop = true } },
                onOpenDetails = { id ->
                    library.clearShelfError()
                    navController.navigate("$BOOK_ROUTE/$id") { launchSingleTop = true }
                },
            )
        }
        composable("$READER_ROUTE/{$BOOK_KEY}") { entry ->
            LocalReaderScreen(library, entry.arguments?.getString(BOOK_KEY).orEmpty())
        }
        composable("$BOOK_ROUTE/{$BOOK_KEY}") { entry ->
            LibraryBookDetails(
                model = library,
                bookId = entry.arguments?.getString(BOOK_KEY).orEmpty(),
                onBack = { navController.returnFrom(entry) },
            )
        }
        composable(SETTINGS_ROUTE) { entry ->
            SettingsScreen(
                nickname = nickname ?: stringResource(R.string.settings_default_nickname),
                avatar = avatar,
                avatarSync = avatarSync,
                onAvatarPicked = { uri -> scope.launch { profileAvatars.select(uri) } },
                onRefreshAvatar = { scope.launch { profileAvatars.refresh(session) } },
                onBack = { navController.returnFrom(entry) },
                onAppearance = {
                    navController.navigate(APPEARANCE_SETTINGS_ROUTE) { launchSingleTop = true }
                },
                onServices = {
                    navController.navigate(SERVICES_ROUTE) { launchSingleTop = true }
                },
            )
        }
        composable(APPEARANCE_SETTINGS_ROUTE) { entry ->
            AppearanceSettingsScreen(themeMode, onThemeModeChange) { navController.returnFrom(entry) }
        }
        composable(SERVICES_ROUTE) { entry ->
            SettingsGlassPage(stringResource(R.string.settings_services), { navController.returnFrom(entry) }) { background, _ ->
                Column(Modifier.fillMaxWidth().settingsGlassPanel(background)) {
                    ArrowPreference(
                        title = stringResource(R.string.server_title),
                        summary = stringResource(serverStatusLabel(session)),
                        startAction = { Icon(MiuixIcons.Glass.Cloud, null) },
                        onClick = { navController.navigate(SERVER_ROUTE) { launchSingleTop = true } },
                    )
                }
            }
        }
        composable(SERVER_ROUTE) { entry ->
            ServerSettingsScreen(
                serverSessions, session,
                onBack = { navController.returnFrom(entry) },
                onOpenLogin = { navController.navigate(SERVER_LOGIN_ROUTE) { launchSingleTop = true } },
            )
        }
        composable(SERVER_LOGIN_ROUTE) { entry ->
            ServerLoginScreen(
                serverSessions, session,
                onBack = { navController.returnFrom(entry) },
                onSignedIn = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
            )
        }
    }
}

private fun NavHostController.returnFrom(entry: NavBackStackEntry) {
    if (currentBackStackEntry == entry && entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

@Composable
private fun MainTabs(
    library: LibraryViewModel,
    avatar: ImageBitmap?,
    onOpenSettings: () -> Unit,
    onReadBook: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { dockRoutes.size })
    val scope = rememberCoroutineScope()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        library.importBooks(uris)
    }
    fun selectTab(index: Int) {
        if (pagerState.currentPage == index) return
        scope.launch { pagerState.animateScrollToPage(index) }
    }
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    val backdrop = rememberLayerBackdrop()
    val items = listOf(
        GlassNavigationItem(MiuixIcons.Glass.HomeOutline, stringResource(UiR.string.app_dock_home)),
        GlassNavigationItem(MiuixIcons.Glass.BookOutline, stringResource(UiR.string.app_dock_library)),
        GlassNavigationItem(MiuixIcons.Glass.DigitalStatistics, stringResource(UiR.string.app_dock_statistics)),
    )
    val addAction: @Composable () -> Unit = {
        GlassFloatingActionButton(onClick = { showAdd = true }, backdrop = backdrop) {
            Icon(MiuixIcons.Glass.Add, stringResource(UiR.string.app_dock_add))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(
            bottomBar = {
                if (!wide) {
                    GlassNavigationBar(
                        items, pagerState.currentPage, ::selectTab, backdrop,
                        trailingAction = addAction,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if (wide) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        addAction()
                        GlassNavigationRail(
                            items, pagerState.currentPage, ::selectTab, backdrop, showToggle = false,
                            defaultWindowInsetsPadding = false,
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .layerBackdrop(backdrop),
                    key = { dockRoutes[it] },
                ) { page ->
                    when (dockRoutes[page]) {
                        HOME_ROUTE -> HomeHeader(avatar, onOpenSettings)
                        LIBRARY_ROUTE -> LibraryScreen(
                            model = library,
                            onImport = { showAdd = true },
                            onReadBook = onReadBook,
                            onOpenDetails = onOpenDetails,
                            backdrop = backdrop,
                        )
                        else -> TopAppBar(
                            title = stringResource(R.string.statistics_title),
                            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                            defaultWindowInsetsPadding = false,
                        )
                    }
                }
            }
            LibraryImportPanel(
                visible = showAdd,
                model = library,
                backdrop = backdrop,
                onChooseFiles = {
                    picker.launch(arrayOf("application/epub+zip", "application/pdf", "application/octet-stream", "application/zip"))
                },
                onOpenLibrary = {
                    showAdd = false
                    selectTab(dockRoutes.indexOf(LIBRARY_ROUTE))
                },
                onDismiss = { showAdd = false },
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    nickname: String,
    avatar: ImageBitmap?,
    avatarSync: AvatarSync,
    onAvatarPicked: (Uri) -> Unit,
    onRefreshAvatar: () -> Unit,
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onServices: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAvatarPicked(uri)
    }
    LaunchedEffect(Unit) { onRefreshAvatar() }
    SettingsGlassPage(stringResource(R.string.settings_title), onBack) { background, _ ->
        Row(
            Modifier.fillMaxWidth().settingsGlassPanel(background).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                backdrop = background,
                size = 56.dp,
            ) {
                val label = stringResource(R.string.settings_change_avatar)
                if (avatar == null) {
                    Icon(MiuixIcons.Glass.ContactsCircle, label, modifier = Modifier.size(40.dp))
                } else {
                    Image(avatar, label, Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                }
            }
            Text(nickname)
        }
        if (avatarSync == AvatarSync.Failed) {
            Text(stringResource(R.string.settings_avatar_failed), color = MiuixTheme.colorScheme.error)
        }
        Column(Modifier.fillMaxWidth().settingsGlassPanel(background)) {
            ArrowPreference(
                title = stringResource(R.string.settings_appearance),
                startAction = { Icon(MiuixIcons.Glass.SettingsOutline, null) },
                onClick = onAppearance,
            )
            ArrowPreference(
                title = stringResource(R.string.settings_services),
                startAction = { Icon(MiuixIcons.Glass.Cloud, null) },
                onClick = onServices,
            )
        }
    }
}

@Composable
private fun AppearanceSettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val languageTags = listOf("", "en", "zh-CN")
    val currentLanguageTag = if (currentLocales.isEmpty) "" else currentLocales.toLanguageTags()
    SettingsGlassPage(stringResource(R.string.settings_appearance), onBack) { background, page ->
        Column(Modifier.fillMaxWidth().settingsGlassPanel(background)) {
            GlassDropdownPreference(
                backdrop = page,
                modifier = Modifier.heightIn(min = 48.dp),
                items = listOf(
                    stringResource(R.string.settings_theme_system),
                    stringResource(R.string.settings_theme_light),
                    stringResource(R.string.settings_theme_dark),
                ),
                selectedIndex = ThemeMode.entries.indexOf(themeMode),
                title = stringResource(R.string.settings_theme),
                onSelectedIndexChange = { index ->
                    ThemeMode.entries.getOrNull(index)?.let(onThemeModeChange)
                },
            )
            GlassDropdownPreference(
                backdrop = page,
                modifier = Modifier.heightIn(min = 48.dp),
                items = listOf(
                    stringResource(R.string.settings_language_system),
                    stringResource(R.string.settings_language_english),
                    stringResource(R.string.settings_language_chinese),
                ),
                selectedIndex = languageTags.indexOf(currentLanguageTag).coerceAtLeast(0),
                title = stringResource(R.string.settings_language),
                onSelectedIndexChange = { index ->
                    languageTags.getOrNull(index)?.let { tag ->
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                },
            )
        }
    }
}
