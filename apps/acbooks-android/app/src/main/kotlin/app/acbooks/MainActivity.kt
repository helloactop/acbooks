package app.acbooks

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.core.datastore.AppearancePreferences
import app.acbooks.core.datastore.ThemeMode
import app.acbooks.core.designsystem.theme.AcbooksTheme
import app.acbooks.core.designsystem.tokens.DefaultAccent
import app.acbooks.navigation.AcbooksNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var appearance: AppearancePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by appearance.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val accent by appearance.accent.collectAsStateWithLifecycle(null)
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
                AcbooksNavHost()
            }
        }
    }
}
