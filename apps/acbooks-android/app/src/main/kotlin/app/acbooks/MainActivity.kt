package app.acbooks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

private const val MAIN_ROUTE = "main"
private const val SETTINGS_ROUTE = "settings"

@Composable
internal fun AcbooksNavigation(
    avatar: ImageBitmap? = null,
    onChangeAvatar: (() -> Unit)? = null,
) {
    val navController = rememberNavController()
    val currentAvatar by rememberUpdatedState(avatar)
    val changeAvatar by rememberUpdatedState(onChangeAvatar)
    NavHost(
        navController = navController,
        startDestination = MAIN_ROUTE,
        enterTransition = { slideIntoContainer(SlideDirection.Left) },
        exitTransition = { slideOutOfContainer(SlideDirection.Left) },
        popEnterTransition = { slideIntoContainer(SlideDirection.Right) },
        popExitTransition = { slideOutOfContainer(SlideDirection.Right) },
    ) {
        composable(MAIN_ROUTE) {
            MainTabs(
                avatar = currentAvatar,
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
            )
        }
        composable(SETTINGS_ROUTE) {
            AvatarSettingsScreen(
                avatar = currentAvatar,
                onBack = { navController.popBackStack(MAIN_ROUTE, inclusive = false) },
                onChangeAvatar = changeAvatar,
            )
        }
    }
}
