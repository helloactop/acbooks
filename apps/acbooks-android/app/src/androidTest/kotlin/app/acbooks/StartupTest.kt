package app.acbooks

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.acbooks.feature.home.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appOpensHomeWithoutAnAccount() {
        val title = compose.activity.getString(R.string.home_title)
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun appRendersBothSupportedLanguages() {
        val originalLocales = AppCompatDelegate.getApplicationLocales()
        try {
            setLocales(LocaleListCompat.forLanguageTags("zh-CN"))
            awaitTitle("首页")

            setLocales(LocaleListCompat.forLanguageTags("en"))
            awaitTitle("Home")
        } finally {
            setLocales(originalLocales)
        }
    }

    @Test
    fun unsupportedLocaleFallsBackToEnglish() {
        val activity = compose.activity
        val configuration = Configuration(activity.resources.configuration).apply {
            setLocale(Locale.FRENCH)
        }
        val context = activity.createConfigurationContext(configuration)
        assertEquals("Home", context.getString(R.string.home_title))
    }

    private fun setLocales(locales: LocaleListCompat) {
        if (Build.VERSION.SDK_INT >= 33) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSystemService(LocaleManager::class.java)
                .applicationLocales = LocaleList.forLanguageTags(locales.toLanguageTags())
        } else {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(locales) }
        }
    }

    private fun awaitTitle(title: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(title)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
