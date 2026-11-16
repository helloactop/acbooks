package app.acbooks

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.core.datastore.AppearancePreferences
import app.acbooks.core.datastore.ProfilePreferences
import app.acbooks.core.datastore.ThemeMode
import app.acbooks.core.designsystem.theme.AcbooksTheme
import app.acbooks.core.designsystem.tokens.DefaultAccent
import app.acbooks.navigation.AcbooksNavHost
import app.acbooks.library.LibraryViewModel
import app.acbooks.profile.ProfileAvatars
import app.acbooks.server.ServerSessions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var appearance: AppearancePreferences
    @Inject lateinit var profile: ProfilePreferences
    private val serverSessions by lazy { ServerSessions(applicationContext) }
    private val profileAvatars by lazy { ProfileAvatars(applicationContext, profile, serverSessions) }
    private val library: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val scope = rememberCoroutineScope()
            val themeMode by appearance.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val accent by appearance.accent.collectAsStateWithLifecycle(null)
            val nickname by profile.nickname.collectAsStateWithLifecycle(null)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            AcbooksTheme(darkTheme, accent?.let { Color(it.toColorInt()) } ?: DefaultAccent) {
                AcbooksNavHost(
                    themeMode = themeMode,
                    nickname = nickname,
                    serverSessions = serverSessions,
                    profileAvatars = profileAvatars,
                    library = library,
                    onThemeModeChange = { mode -> scope.launch { appearance.setThemeMode(mode) } },
                )
            }
        }
    }
}
