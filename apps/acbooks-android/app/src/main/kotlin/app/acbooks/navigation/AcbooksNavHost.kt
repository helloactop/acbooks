package app.acbooks.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.acbooks.R
import app.acbooks.core.datastore.ThemeMode
import app.acbooks.core.designsystem.components.AppBottomDock
import app.acbooks.core.designsystem.components.ButtonKind
import app.acbooks.core.designsystem.components.LiquidGlassButton
import app.acbooks.core.designsystem.components.AppCard
import app.acbooks.core.designsystem.components.AppSecondaryPage
import app.acbooks.core.designsystem.components.AppGlassSelect
import app.acbooks.core.designsystem.components.AppSelectOption
import app.acbooks.core.designsystem.components.AppSettingsEntry
import app.acbooks.core.designsystem.components.AppSettingsProfileCard
import app.acbooks.core.designsystem.components.AppTextField
import app.acbooks.core.designsystem.components.MainTabBody
import app.acbooks.core.designsystem.components.MainTabHeader
import app.acbooks.core.designsystem.glass.AppGlassScene
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.feature.home.HomeHeader
import app.acbooks.library.LibraryBookDetails
import app.acbooks.library.LibraryImportPanel
import app.acbooks.library.LibraryScreen
import app.acbooks.library.LibraryViewModel
import app.acbooks.reader.LocalReaderScreen
import app.acbooks.server.ServerSessions
import app.acbooks.server.ServerLoginScreen
import app.acbooks.server.ServerSettingsScreen
import app.acbooks.server.serverStatusLabel
import java.io.IOException
import kotlinx.coroutines.launch

private const val HOME_ROUTE = "home"
private const val LIBRARY_ROUTE = "library"
private const val STATISTICS_ROUTE = "statistics"
private const val SETTINGS_ROUTE = "settings"
private const val APPEARANCE_SETTINGS_ROUTE = "settings/appearance"
private const val NICKNAME_SETTINGS_ROUTE = "settings/nickname"
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
    library: LibraryViewModel,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSaveNickname: suspend (String) -> Unit,
) {
    val navController = rememberNavController()
    val session by serverSessions.session.collectAsStateWithLifecycle()
    LaunchedEffect(serverSessions) { serverSessions.restore() }

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
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
                onReadBook = { id -> navController.navigate("$READER_ROUTE/$id") { launchSingleTop = true } },
                onOpenDetails = { id ->
                    library.clearShelfError()
                    navController.navigate("$BOOK_ROUTE/$id") { launchSingleTop = true }
                },
            )
        }
        composable("$READER_ROUTE/{$BOOK_KEY}") { entry ->
            val id = entry.arguments?.getString(BOOK_KEY).orEmpty()
            LocalReaderScreen(
                library, id,
                onBack = { navController.returnFrom(entry) },
            )
        }
        composable("$BOOK_ROUTE/{$BOOK_KEY}") { entry ->
            LibraryBookDetails(
                model = library,
                bookId = entry.arguments?.getString(BOOK_KEY).orEmpty(),
                onBack = { navController.returnFrom(entry) },
            )
        }
        composable(SETTINGS_ROUTE) { entry ->
            AppSecondaryPage(stringResource(R.string.settings_title), { navController.returnFrom(entry) }) {
                AppSettingsProfileCard(
                    nickname = nickname ?: stringResource(R.string.settings_default_nickname),
                    editHint = stringResource(R.string.settings_edit_nickname_hint),
                    onClick = { navController.navigate(NICKNAME_SETTINGS_ROUTE) { launchSingleTop = true } },
                )
                AppSettingsEntry(
                    title = stringResource(R.string.settings_appearance),
                    icon = AppIcons.Appearance,
                    onClick = { navController.navigate(APPEARANCE_SETTINGS_ROUTE) { launchSingleTop = true } },
                )
                AppSettingsEntry(
                    title = stringResource(R.string.settings_services),
                    icon = AppIcons.Cloud,
                    onClick = { navController.navigate(SERVICES_ROUTE) { launchSingleTop = true } },
                )
            }
        }
        composable(NICKNAME_SETTINGS_ROUTE) { entry ->
            NicknameSettingsScreen(
                nickname, onSaveNickname,
                onBack = { navController.returnFrom(entry) },
                onSaved = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
            )
        }
        composable(APPEARANCE_SETTINGS_ROUTE) { entry ->
            AppearanceSettingsScreen(themeMode, onThemeModeChange) { navController.returnFrom(entry) }
        }
        composable(SERVICES_ROUTE) { entry ->
            AppSecondaryPage(stringResource(R.string.settings_services), { navController.returnFrom(entry) }) {
                AppSettingsEntry(
                    title = stringResource(R.string.server_title),
                    icon = AppIcons.Cloud,
                    supportingText = stringResource(serverStatusLabel(session)),
                    onClick = { navController.navigate(SERVER_ROUTE) { launchSingleTop = true } },
                )
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
    onOpenSettings: () -> Unit,
    onReadBook: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val selected = dockRoutes.indexOf(entry?.destination?.route).coerceAtLeast(0)
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        library.importBooks(uris)
    }
    fun selectTab(index: Int) {
        val route = dockRoutes[index]
        if (navController.currentDestination?.route == route) return
        if (route == HOME_ROUTE) {
            navController.popBackStack(HOME_ROUTE, inclusive = false, saveState = true)
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    AppGlassScene(
        modifier = Modifier.fillMaxSize(),
        background = {
            NavHost(
                navController = navController,
                startDestination = HOME_ROUTE,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                enterTransition = { pageEnter() },
                exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { pageReturnExit() },
            ) {
                composable(HOME_ROUTE) {
                    MainTabDestination { scroll, modifier -> HomeHeader(scroll, onOpenSettings, modifier) }
                }
                composable(LIBRARY_ROUTE) {
                    LibraryScreen(library, { showAdd = true }, onReadBook, onOpenDetails)
                }
                composable(STATISTICS_ROUTE) {
                    MainTabDestination { scroll, modifier ->
                        MainTabHeader(stringResource(R.string.statistics_title), scroll, modifier)
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            AppBottomDock(
                selected = selected,
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
private fun NicknameSettingsScreen(
    nickname: String?,
    onSaveNickname: suspend (String) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val value = draft ?: nickname.orEmpty()
    val scope = rememberCoroutineScope()

    AppSecondaryPage(stringResource(R.string.settings_edit_nickname_title), onBack) {
        AppTextField(
            value = value,
            onValueChange = {
                draft = it
                saveFailed = false
            },
            label = stringResource(R.string.settings_nickname_label),
            enabled = !saving,
            errorMessage = if (saveFailed) stringResource(R.string.settings_nickname_save_failed) else null,
        )
        LiquidGlassButton(
            label = stringResource(R.string.settings_save_nickname),
            kind = ButtonKind.Primary,
            modifier = Modifier.fillMaxWidth(),
            enabled = value.isNotBlank(),
            loading = saving,
            onClick = {
                if (!saving && value.isNotBlank()) {
                    saving = true
                    saveFailed = false
                    scope.launch {
                        try {
                            onSaveNickname(value)
                            onSaved()
                        } catch (_: IOException) {
                            saveFailed = true
                        } finally {
                            saving = false
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun AppearanceSettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    AppSecondaryPage(stringResource(R.string.settings_appearance), onBack) {
        AppCard(Modifier.fillMaxWidth()) {
            AppGlassSelect(
                label = stringResource(R.string.settings_theme),
                options = listOf(
                    AppSelectOption(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system)),
                    AppSelectOption(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light)),
                    AppSelectOption(ThemeMode.DARK, stringResource(R.string.settings_theme_dark)),
                ),
                value = themeMode,
                onValueChange = onThemeModeChange,
                modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            )
        }
    }
}
