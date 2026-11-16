package app.acbooks.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.ScrollState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.acbooks.R
import app.acbooks.core.datastore.ThemeMode
import app.acbooks.core.designsystem.components.AppBottomDock
import app.acbooks.core.designsystem.components.AppMiuixCard
import app.acbooks.core.designsystem.components.AppMiuixPage
import app.acbooks.core.designsystem.components.AppMiuixProfileCard
import app.acbooks.core.designsystem.components.AppMiuixSettingsGroup
import app.acbooks.core.designsystem.components.AppMiuixSettingsRow
import app.acbooks.core.designsystem.components.MainTabBody
import app.acbooks.core.designsystem.components.MainTabHeader
import app.acbooks.core.designsystem.glass.AppGlassScene
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography
import app.acbooks.feature.home.HomeHeader
import app.acbooks.library.LibraryBookDetails
import app.acbooks.library.LibraryImportPanel
import app.acbooks.library.LibraryScreen
import app.acbooks.library.LibraryViewModel
import app.acbooks.profile.AvatarSync
import app.acbooks.profile.ProfileAvatars
import app.acbooks.reader.LocalReaderScreen
import app.acbooks.server.ServerSessions
import app.acbooks.server.ServerLoginScreen
import app.acbooks.server.ServerSettingsScreen
import app.acbooks.server.serverStatusLabel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

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
        modifier = Modifier.fillMaxSize().clipToBounds().background(LocalAppColors.current.background),
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
            AppMiuixPage(stringResource(R.string.settings_services), { navController.returnFrom(entry) }) {
                AppMiuixSettingsGroup {
                    AppMiuixSettingsRow(
                        title = stringResource(R.string.server_title),
                        icon = AppIcons.Cloud,
                        supportingText = stringResource(serverStatusLabel(session)),
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

    AppGlassScene(
        modifier = Modifier.fillMaxSize(),
        background = {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { dockRoutes[it] },
            ) { page ->
                when (dockRoutes[page]) {
                    HOME_ROUTE -> MainTabDestination { scroll, modifier ->
                        HomeHeader(scroll, avatar, onOpenSettings, modifier)
                    }
                    LIBRARY_ROUTE -> LibraryScreen(library, { showAdd = true }, onReadBook, onOpenDetails)
                    else -> MainTabDestination { scroll, modifier ->
                        MainTabHeader(stringResource(R.string.statistics_title), scroll, modifier)
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            AppBottomDock(
                selected = pagerState.currentPage,
                onSelect = ::selectTab,
                onAdd = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .widthIn(max = AppSizes.sheet).fillMaxWidth()
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            )
        }
        LibraryImportPanel(
            visible = showAdd,
            model = library,
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

@Composable
private fun MainTabDestination(header: @Composable (ScrollState, Modifier) -> Unit) {
    val scroll = rememberScrollState()
    var headerHeight by remember { mutableIntStateOf(0) }
    AppGlassScene(
        modifier = Modifier.fillMaxSize(),
        background = { MainTabBody(scroll, headerHeight) },
    ) {
        header(
            scroll,
            Modifier.align(Alignment.TopCenter).safeDrawingPadding().onSizeChanged { headerHeight = it.height },
        )
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
    val colors = LocalAppColors.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAvatarPicked(uri)
    }
    LaunchedEffect(Unit) { onRefreshAvatar() }
    AppMiuixPage(stringResource(R.string.settings_title), onBack) {
        AppMiuixProfileCard(
            nickname = nickname,
            avatarLabel = stringResource(R.string.settings_change_avatar),
            avatar = avatar,
            onAvatarClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
        if (avatarSync == AvatarSync.Failed) {
            Text(
                stringResource(R.string.settings_avatar_failed),
                style = AppTypography.small,
                color = colors.textSecondary,
            )
        }
        AppMiuixSettingsGroup {
            AppMiuixSettingsRow(
                title = stringResource(R.string.settings_appearance),
                icon = AppIcons.Appearance,
                onClick = onAppearance,
            )
            AppMiuixSettingsRow(
                title = stringResource(R.string.settings_services),
                icon = AppIcons.Cloud,
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
    AppMiuixPage(stringResource(R.string.settings_appearance), onBack) {
        AppMiuixCard {
            OverlayDropdownPreference(
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
            OverlayDropdownPreference(
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
