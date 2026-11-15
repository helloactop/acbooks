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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.acbooks.feature.home.R
import java.util.Locale
import kotlin.math.abs
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

        fun assertAccent(label: String, color: Color) {
            compose.waitUntil(10_000) {
                val pixels = compose.onNodeWithText(label).captureToImage().toPixelMap()
                val sampled = pixels[pixels.width / 4, pixels.height / 2]
                // The existing backdrop can contribute up to 20% of the sampled color.
                abs(sampled.red - color.red) <= 0.21f &&
                    abs(sampled.green - color.green) <= 0.21f &&
                    abs(sampled.blue - color.blue) <= 0.21f
            }
        }

        try {
            runBlocking { appearance.setAccent(null) }
            compose.onNodeWithContentDescription(activity.getString(R.string.home_open_settings)).performClick()
            compose.onNodeWithText(activity.getString(app.acbooks.R.string.settings_services)).performClick()
            compose.onNodeWithText(activity.getString(app.acbooks.R.string.server_title)).performClick()
            assertAccent(configure, app.acbooks.core.designsystem.tokens.DefaultAccent)

            runBlocking { appearance.setAccent("#00866A") }
            assertAccent(configure, Color(0xFF00866A))
            compose.onNodeWithText(configure).performClick()
            compose.onNodeWithContentDescription(activity.getString(app.acbooks.R.string.server_address))
                .performTextInput("https://books.example.com")
            compose.onNodeWithContentDescription(activity.getString(app.acbooks.R.string.server_username))
                .performTextInput("reader")
            compose.onNodeWithContentDescription(activity.getString(app.acbooks.R.string.server_password))
                .performTextInput("test-password")
            compose.onNodeWithText(signIn).performScrollTo()
            assertAccent(signIn, Color(0xFF00866A))

            runBlocking { appearance.setAccent("#B94870") }
            assertAccent(signIn, Color(0xFFB94870))
        } finally {
            runBlocking { appearance.setAccent(previous) }
        }
    }

    @Test
    fun dockNavigatesAndAddKeepsTheCurrentTab() {
        val library = compose.activity.getString(app.acbooks.R.string.library_title)
        val statistics = compose.activity.getString(app.acbooks.R.string.statistics_title)
        val add = compose.activity.getString(app.acbooks.core.designsystem.R.string.app_dock_add)
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
        compose.onNodeWithContentDescription(themeLabel).performClick()
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
        val search = compose.activity.getString(app.acbooks.core.designsystem.R.string.app_library_search)
        compose.onNodeWithText(search).performTouchInput { click() }
        val done = compose.activity.getString(app.acbooks.R.string.library_done)
        compose.onNodeWithText(done).performTouchInput { click() }
        compose.onNode(hasText(statistics) and !pageTitle(statistics)).performTouchInput { click() }
        compose.onNode(pageTitle(statistics)).assertIsDisplayed()
        compose.onNodeWithContentDescription(settings).assertDoesNotExist()
        compose.onNodeWithContentDescription(add).performTouchInput { click() }
        compose.onNodeWithText(compose.activity.getString(app.acbooks.R.string.add_books_title)).assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNode(hasText(statistics) and !pageTitle(statistics)).assertIsSelected()
        compose.onNodeWithText(library).performTouchInput { click() }
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
