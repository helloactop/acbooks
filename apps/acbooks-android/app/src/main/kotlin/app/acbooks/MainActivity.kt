package app.acbooks

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.library.BookDetailsScreen
import app.acbooks.library.LibraryViewModel
import app.acbooks.reader.LocalReaderScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : AppCompatActivity() {
    private val library: LibraryViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        library.syncLibrary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val theme = remember { ThemeController(colorSchemeMode = ColorSchemeMode.System) }
            MiuixTheme(theme) {
                AcbooksNavigation(library = library)
            }
        }
    }
}

@Serializable
private sealed interface AcbooksRoute : NavKey {
    @Serializable
    data object Main : AcbooksRoute

    @Serializable
    data object Settings : AcbooksRoute

    @Serializable
    data object Server : AcbooksRoute

    @Serializable
    data class BookDetails(val bookId: String) : AcbooksRoute

    @Serializable
    data class Reader(val bookId: String) : AcbooksRoute
}

@Composable
internal fun AcbooksNavigation(
    library: LibraryViewModel,
    avatar: ImageBitmap? = null,
    onChangeAvatar: (() -> Unit)? = null,
) {
    val navController = rememberNavController<AcbooksRoute>(AcbooksRoute.Main)
    val catalog by library.catalog.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var downloading by remember { mutableStateOf(false) }
    NavDisplay(
        navController = navController,
        transition = NavTransitions.MiuixDefault,
        effects = NavDisplayEffects(
            cornerClipRadius = rememberNavSystemCornerRadius(),
            blockInputDuringTransition = true,
            backdropColor = MiuixTheme.colorScheme.surface,
        ),
    ) {
        entry<AcbooksRoute.Main> {
            MainTabs(
                avatar = avatar,
                catalog = catalog,
                library = library,
                isCurrentDestination = navController.backStack.lastOrNull() == AcbooksRoute.Main,
                onOpenSettings = {
                    if (navController.backStack.lastOrNull() == AcbooksRoute.Main) {
                        navController.push(AcbooksRoute.Settings)
                    }
                },
                onOpenBook = { id ->
                    if (navController.backStack.lastOrNull() == AcbooksRoute.Main) {
                        navController.push(AcbooksRoute.BookDetails(id))
                    }
                },
                onReadBook = { id ->
                    val book = catalog.books.firstOrNull { it.id == id }
                    if (book != null && navController.backStack.lastOrNull() == AcbooksRoute.Main) {
                        if (book.hasLocalCopy) {
                            navController.push(AcbooksRoute.Reader(id))
                        } else if (!downloading && book.canDownload) {
                            downloading = true
                            Toast.makeText(context, R.string.cloud_downloading, Toast.LENGTH_SHORT).show()
                            scope.launch {
                                try {
                                    val downloaded = library.downloadForReading(id)
                                    if (downloaded && navController.backStack.lastOrNull() == AcbooksRoute.Main) {
                                        navController.push(AcbooksRoute.Reader(id))
                                    } else if (!downloaded) {
                                        Toast.makeText(context, R.string.cloud_download_failed, Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    downloading = false
                                }
                            }
                        } else if (!book.canDownload) {
                            Toast.makeText(context, R.string.cloud_download_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
        entry<AcbooksRoute.Settings> {
            AvatarSettingsScreen(
                avatar = avatar,
                onBack = { navController.pop() },
                onChangeAvatar = onChangeAvatar,
                onOpenServer = {
                    if (navController.backStack.lastOrNull() == AcbooksRoute.Settings) navController.push(AcbooksRoute.Server)
                },
            )
        }
        entry<AcbooksRoute.Server> {
            val state by library.cloudState.collectAsStateWithLifecycle()
            ServerSettingsScreen(
                state = state,
                onBack = { navController.pop() },
                onConnect = library::connectServer,
                onSync = library::syncLibrary,
                onDisconnect = library::disconnectServer,
            )
        }
        entry<AcbooksRoute.BookDetails> { route ->
            val book = catalog.books.firstOrNull { it.id == route.bookId }
            BookDetailsScreen(
                book = book,
                loading = !catalog.loaded,
                library = library,
                onBack = {
                    if (navController.backStack.lastOrNull() == route) navController.pop()
                },
            )
        }
        entry<AcbooksRoute.Reader> { route ->
            LocalReaderScreen(model = library, bookId = route.bookId)
        }
    }
}
