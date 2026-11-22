package app.acbooks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val theme = remember { ThemeController(colorSchemeMode = ColorSchemeMode.System) }
            MiuixTheme(theme) {
                AcbooksNavigation()
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
}

@Composable
internal fun AcbooksNavigation(
    avatar: ImageBitmap? = null,
    onChangeAvatar: (() -> Unit)? = null,
) {
    val navController = rememberNavController<AcbooksRoute>(AcbooksRoute.Main)
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
                isCurrentDestination = navController.backStack.lastOrNull() == AcbooksRoute.Main,
                onOpenSettings = {
                    if (navController.backStack.lastOrNull() == AcbooksRoute.Main) {
                        navController.push(AcbooksRoute.Settings)
                    }
                },
            )
        }
        entry<AcbooksRoute.Settings> {
            AvatarSettingsScreen(
                avatar = avatar,
                onBack = { navController.pop() },
                onChangeAvatar = onChangeAvatar,
            )
        }
    }
}
