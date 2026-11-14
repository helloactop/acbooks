package app.acbooks

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.os.LocaleListCompat
import app.acbooks.preview.ComponentPreviewActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNotNull

class ComponentPreviewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentPreviewActivity>()

    @Test
    fun controlsExerciseThemesCapabilitiesNavigationAndOverlays() {
        val preferences = compose.activity.appearance
        val originalTheme = runBlocking { preferences.themeMode.first() }
        val originalAccent = runBlocking { preferences.accent.first() }
        val originalLocales = AppCompatDelegate.getApplicationLocales()
        try {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
            val preview = compose.onNodeWithText("Component preview")
            preview.assertIsDisplayed()
            compose.onNodeWithText("Auto").performScrollTo().assertIsSelected()
            val detected = listOf("Refraction", "Blur", "Solid").firstOrNull {
                compose.onAllNodes(hasText("Material: $it")).fetchSemanticsNodes().isNotEmpty()
            }
            assertNotNull(detected)
            compose.onNodeWithText("Blur").performScrollTo().performClick().assertIsSelected()
            val limited = if (detected == "Solid") "Solid" else "Blur"
            compose.onNodeWithText("Material: $limited").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Solid").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Material: Solid").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Auto").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Reduce transparency").performScrollTo().performClick()
            compose.onNodeWithText("Material: Solid").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Reduce transparency").performScrollTo().performClick()
            compose.onNodeWithText("Material: $detected").performScrollTo().assertIsDisplayed()

            compose.onNodeWithText("Dark").performScrollTo().performClick()
            compose.waitUntil(5_000) { runBlocking { preferences.themeMode.first() }.name == "DARK" }
            compose.onNodeWithText("#00866A").performScrollTo().performClick()
            compose.waitUntil(5_000) { runBlocking { preferences.accent.first() } == "#00866A" }

            compose.onNodeWithContentDescription("Add to favorites").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
            compose.onNodeWithContentDescription("Add bookmark").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Remove bookmark").assertIsDisplayed()

            compose.onNodeWithText("Library").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Add").performScrollTo().performClick()
            compose.onNodeWithText("Close").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Menu").performScrollTo().performClick()
            compose.onNode(hasText("Dialog") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithText("Close").assertIsDisplayed().performClick()
            compose.onNodeWithText("Library").assertIsSelected()

            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN")) }
            compose.onNodeWithText("组件预览").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("取消收藏").performScrollTo().performClick()
            compose.onNodeWithContentDescription("添加收藏").assertIsDisplayed()
            compose.onNodeWithContentDescription("移除书签").performScrollTo().performClick()
            compose.onNodeWithContentDescription("添加书签").assertIsDisplayed()
        } finally {
            runBlocking {
                preferences.setThemeMode(originalTheme)
                preferences.setAccent(originalAccent)
            }
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(originalLocales) }
        }
    }
}
