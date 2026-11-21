package app.acbooks

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.acbooks.feature.home.R
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
        compose.onNode(pageTitle(title)).assertIsDisplayed()
        assertEquals(app.acbooks.R.mipmap.ic_launcher, compose.activity.applicationInfo.icon)
    }

    @Test
    fun serverButtonsFollowTheStoredThemeAccent() {
        val activity = compose.activity
        val appearance = activity.appearance
        val previous = runBlocking { appearance.accent.first() }
        val configure = activity.getString(app.acbooks.R.string.server_configure)
        val signIn = activity.getString(app.acbooks.R.string.server_sign_in)

        fun buttonColor(label: String): Color {
            val pixels = compose.onNodeWithText(label).captureToImage().toPixelMap()
            return pixels[pixels.width / 4, pixels.height / 2]
        }

        try {
            runBlocking { appearance.setAccent(null) }
            compose.onNodeWithContentDescription(activity.getString(R.string.home_open_settings)).performClick()
            compose.onNodeWithText(activity.getString(app.acbooks.R.string.settings_services)).performClick()
            compose.onNodeWithText(activity.getString(app.acbooks.R.string.server_title)).performClick()
            val defaultColor = buttonColor(configure)

            runBlocking { appearance.setAccent("#00866A") }
            compose.waitUntil(10_000) { buttonColor(configure) != defaultColor }
            compose.onNodeWithText(configure).performClick()
            compose.onNodeWithContentDescription(activity.getString(app.acbooks.R.string.server_address))
                .performTextInput("https://books.example.com")
            compose.onNodeWithContentDescription(activity.getString(app.acbooks.R.string.server_username))
                .performTextInput("reader")
            compose.onNodeWithContentDescription(activity.getString(app.acbooks.R.string.server_password))
                .performTextInput("test-password")
            compose.onNodeWithText(signIn).performScrollTo()
            compose.onNodeWithText(signIn).assertIsEnabled()
            val firstAccent = buttonColor(signIn)

            runBlocking { appearance.setAccent("#B94870") }
            compose.waitUntil(10_000) { buttonColor(signIn) != firstAccent }
        } finally {
            runBlocking { appearance.setAccent(previous) }
        }
    }

    @Test
    fun dockNavigatesAndSearchOpensTheLibrary() {
        val library = compose.activity.getString(app.acbooks.R.string.library_title)
        val statistics = compose.activity.getString(app.acbooks.R.string.statistics_title)
        val search = compose.activity.getString(app.acbooks.core.designsystem.R.string.app_library_search)
        val searchTitle = compose.activity.getString(app.acbooks.R.string.library_search)
        val settings = compose.activity.getString(R.string.home_open_settings)
        val appearance = compose.activity.getString(app.acbooks.R.string.settings_appearance)
        val settingsTitle = compose.activity.getString(app.acbooks.R.string.settings_title)
        val themeLabel = compose.activity.getString(app.acbooks.R.string.settings_theme)
        val back = compose.activity.getString(app.acbooks.core.designsystem.R.string.app_back)

        compose.onNodeWithContentDescription(settings).performClick()
        compose.onNode(pageTitle(settingsTitle)).assertIsDisplayed()
        compose.onNodeWithText(themeLabel).assertDoesNotExist()
        compose.onNodeWithText(appearance).performClick()
        compose.onNode(pageTitle(appearance)).assertIsDisplayed()
        compose.onNodeWithText(themeLabel).assertIsDisplayed()
        compose.onNodeWithText(themeLabel).performClick()
        val light = compose.activity.getString(app.acbooks.R.string.settings_theme_light)
        compose.onNodeWithText(light).assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(pageTitle(appearance)).assertIsDisplayed()
        val returnAction = compose.onNodeWithContentDescription(back)
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle {
            returnAction()
            returnAction()
        }
        compose.onNode(pageTitle(settingsTitle)).assertIsDisplayed()
        compose.onNodeWithContentDescription(back).performClick()
        compose.onNodeWithContentDescription(settings).assertIsDisplayed()
        compose.onNodeWithText(library).performTouchInput { click() }
        compose.onNode(pageTitle(library)).assertIsDisplayed()
        compose.onNodeWithContentDescription(settings).assertDoesNotExist()
        compose.onNodeWithText(search).assertDoesNotExist()
        compose.onNodeWithContentDescription(search).performTouchInput { click() }
        compose.onNodeWithText(searchTitle).assertIsDisplayed()
        val done = compose.activity.getString(app.acbooks.R.string.library_done)
        compose.onNodeWithText(done).performTouchInput { click() }
        compose.onNode(hasText(statistics) and !pageTitle(statistics)).performTouchInput { click() }
        compose.onNode(pageTitle(statistics)).assertIsDisplayed()
        compose.onNodeWithContentDescription(settings).assertDoesNotExist()
        compose.onNodeWithContentDescription(search).performTouchInput { click() }
        compose.onNodeWithText(searchTitle).assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNode(hasText(library) and !pageTitle(library)).assertIsSelected()
        compose.onNode(pageTitle(library)).assertIsDisplayed()
        val homeTab = hasText(compose.activity.getString(R.string.home_title)) and
            !pageTitle(compose.activity.getString(R.string.home_title))
        compose.onNode(homeTab).performTouchInput { click() }
        compose.onNodeWithContentDescription(settings).assertIsDisplayed()
        compose.onNode(homeTab).performTouchInput {
            swipe(center, center + Offset(width.toFloat(), 0f))
        }
        compose.onNode(pageTitle(library)).assertIsDisplayed()
    }

    @Test
    fun glassSettingsPersistAppearanceAndDismissTheLanguagePopupBeforeReturning() {
        val activity = compose.activity
        val preferences = activity.appearance
        val originalMode = runBlocking { preferences.themeMode.first() }
        val originalLocales = AppCompatDelegate.getApplicationLocales()
        val back = activity.getString(app.acbooks.core.designsystem.R.string.app_back)
        val avatar = activity.getString(app.acbooks.R.string.settings_change_avatar)
        val title = activity.getString(app.acbooks.R.string.settings_appearance)
        val theme = activity.getString(app.acbooks.R.string.settings_theme)
        val language = activity.getString(app.acbooks.R.string.settings_language)

        fun assertTouchSize(label: String) {
            val bounds = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            val minimum = 48 * activity.resources.displayMetrics.density
            assertTrue(bounds.width >= minimum - 1f && bounds.height >= minimum - 1f)
        }

        try {
            runBlocking { preferences.setThemeMode(app.acbooks.core.datastore.ThemeMode.SYSTEM) }
            compose.onNodeWithContentDescription(activity.getString(R.string.home_open_settings)).performClick()
            assertTouchSize(avatar)
            compose.onNodeWithText(title).performClick()
            assertTouchSize(back)

            compose.onNodeWithText(theme).performClick()
            compose.onNodeWithText(activity.getString(app.acbooks.R.string.settings_theme_dark)).performClick()
            compose.waitUntil(5_000) { runBlocking { preferences.themeMode.first() }.name == "DARK" }

            compose.onNodeWithText(language).performClick()
            val useEnglish = originalLocales.toLanguageTags().startsWith("zh")
            val alternative = activity.getString(
                if (useEnglish) app.acbooks.R.string.settings_language_english
                else app.acbooks.R.string.settings_language_chinese,
            )
            compose.onNodeWithText(alternative).assertIsDisplayed()
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText(alternative).assertDoesNotExist()
            compose.onNode(pageTitle(title)).assertIsDisplayed()

            compose.onNodeWithText(language).performClick()
            compose.onNodeWithText(alternative).performClick()
            val expectedLanguage = if (useEnglish) "en" else "zh-CN"
            compose.waitUntil(5_000) {
                AppCompatDelegate.getApplicationLocales().toLanguageTags() == expectedLanguage
            }
            val currentActivity = compose.activity
            compose.onNodeWithContentDescription(currentActivity.getString(app.acbooks.core.designsystem.R.string.app_back))
                .performClick()
            compose.onNode(pageTitle(currentActivity.getString(app.acbooks.R.string.settings_title))).assertIsDisplayed()
        } finally {
            runBlocking { preferences.setThemeMode(originalMode) }
            setLocales(originalLocales)
        }
    }

    @Test
    fun glassLoginUsesImeNavigationAndKeepsItsDraft() {
        val activity = compose.activity
        val address = compose.onNodeWithContentDescription(activity.getString(app.acbooks.R.string.server_address))
        val username = compose.onNodeWithContentDescription(activity.getString(app.acbooks.R.string.server_username))
        val password = compose.onNodeWithContentDescription(activity.getString(app.acbooks.R.string.server_password))
        val signIn = compose.onNodeWithText(activity.getString(app.acbooks.R.string.server_sign_in))
        val title = activity.getString(app.acbooks.R.string.server_login_title)

        fun imeVisible(): Boolean = compose.runOnIdle {
            ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }

        compose.onNodeWithContentDescription(activity.getString(R.string.home_open_settings)).performClick()
        compose.onNodeWithText(activity.getString(app.acbooks.R.string.settings_services)).performClick()
        compose.onNodeWithText(activity.getString(app.acbooks.R.string.server_title)).performClick()
        compose.onNodeWithText(activity.getString(app.acbooks.R.string.server_configure)).performClick()

        address.performClick()
        address.performTextInput("https://books.example.com")
        address.performImeAction()
        username.assertIsFocused()
        username.performTextInput("reader")
        username.performImeAction()
        password.assertIsFocused()
        password.performTextInput("test-password")
        signIn.performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.waitUntil(5_000) { imeVisible() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !imeVisible() }
        compose.onNode(pageTitle(title)).assertIsDisplayed()
        signIn.performScrollTo().assertIsEnabled()

        compose.onNodeWithContentDescription(activity.getString(app.acbooks.core.designsystem.R.string.app_back)).performClick()
        compose.onNode(pageTitle(activity.getString(app.acbooks.R.string.server_title))).assertIsDisplayed()
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
    fun edgeBackKeepsThePageStillUntilCommit() {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        val settings = compose.activity.getString(R.string.home_open_settings)
        val settingsTitle = compose.activity.getString(app.acbooks.R.string.settings_title)
        val appearance = compose.activity.getString(app.acbooks.R.string.settings_appearance)

        compose.onNodeWithContentDescription(settings).performClick()
        compose.onNodeWithText(appearance).performClick()
        compose.onNode(pageTitle(appearance)).assertIsDisplayed()
        val appearancePage = compose.onNode(pageTitle(appearance))
        val restingBounds = appearancePage.fetchSemanticsNode().boundsInRoot
        sendEdgeGesture(fromLeft = true, cancel = true) {
            assertEquals(restingBounds, appearancePage.fetchSemanticsNode().boundsInRoot)
            compose.onNode(pageTitle(settingsTitle)).assertDoesNotExist()
        }
        compose.onNode(pageTitle(appearance)).assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.onNode(pageTitle(appearance)).assertIsDisplayed()
        sendEdgeGesture(fromLeft = true, cancel = false)
        compose.onNode(pageTitle(settingsTitle)).assertIsDisplayed()
        sendEdgeGesture(fromLeft = false, cancel = false)
        compose.onNodeWithContentDescription(settings).assertIsDisplayed()
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
            compose.onAllNodes(pageTitle(title)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNode(pageTitle(title)).assertIsDisplayed()
    }

    private fun sendEdgeGesture(fromLeft: Boolean, cancel: Boolean, beforeRelease: () -> Unit = {}) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val metrics = compose.activity.resources.displayMetrics
        val startX = if (fromLeft) 1f else metrics.widthPixels - 2f
        val multiplier = if (fromLeft) 1f else -1f
        val y = metrics.heightPixels * 0.6f
        val downTime = SystemClock.uptimeMillis()

        fun send(action: Int, fraction: Float) {
            val x = startX + multiplier * fraction * metrics.widthPixels
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }

        send(MotionEvent.ACTION_DOWN, 0f)
        for (step in 1..12) {
            SystemClock.sleep(20)
            send(MotionEvent.ACTION_MOVE, step * 0.035f)
        }
        try {
            beforeRelease()
        } finally {
            send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, 0.42f)
        }
        compose.waitForIdle()
    }

    private fun pageTitle(title: String): SemanticsMatcher =
        hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
}
