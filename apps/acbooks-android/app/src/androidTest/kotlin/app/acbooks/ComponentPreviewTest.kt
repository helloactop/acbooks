package app.acbooks

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.core.os.LocaleListCompat
import app.acbooks.preview.ComponentPreviewActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class ComponentPreviewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentPreviewActivity>()

    @Test
    fun smallToolbarButtonsReserveSeparateTouchTargets() {
        val activity = compose.activity
        val edit = compose.onNodeWithContentDescription(
            activity.getString(app.acbooks.core.designsystem.R.string.app_library_edit),
        )
        val more = compose.onNodeWithContentDescription(
            activity.getString(app.acbooks.core.designsystem.R.string.app_library_more),
        )
        val editBounds = edit.fetchSemanticsNode().boundsInRoot
        val moreBounds = more.fetchSemanticsNode().boundsInRoot
        val minimum = 48 * activity.resources.displayMetrics.density
        for (bounds in listOf(editBounds, moreBounds)) {
            assertTrue(bounds.width >= minimum - 1f)
            assertTrue(bounds.height >= minimum - 1f)
        }
        assertTrue(editBounds.right <= moreBounds.left || moreBounds.right <= editBounds.left)

        edit.performTouchInput { click(Offset(2f, center.y)) }
        val close = activity.getString(R.string.preview_close)
        compose.onNodeWithText(close).assertIsDisplayed().performClick()
        more.performTouchInput { click(Offset(center.x, 2f)) }
        val dialog = activity.getString(R.string.preview_dialog)
        compose.onNode(hasText(dialog) and hasAnyAncestor(isPopup())).assertIsDisplayed()
    }

    @Test
    fun anchoredMenuAndCenteredDialogCloseInSequence() {
        val activity = compose.activity
        val more = activity.getString(app.acbooks.core.designsystem.R.string.app_library_more)
        val dialog = activity.getString(R.string.preview_dialog)
        val close = activity.getString(R.string.preview_close)
        val menuItem = compose.onNode(hasText(dialog) and hasAnyAncestor(isPopup()))

        compose.onNodeWithContentDescription(more).performScrollTo().performClick()
        menuItem.assertIsDisplayed().performClick()
        menuItem.assertDoesNotExist()
        compose.onNodeWithText(close).assertIsDisplayed().performClick()
        compose.onNodeWithText(close).assertDoesNotExist()
        compose.onNodeWithContentDescription(more).assertIsDisplayed()
    }

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

            compose.onNodeWithText("Statistics").performClick().assertIsSelected()
            val libraryTab = hasText("Library") and !SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
            compose.onNode(libraryTab).performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Add").performClick()
            compose.onNodeWithText("Close").assertIsDisplayed().performClick()
            compose.onNode(libraryTab).assertIsSelected()
            compose.onNodeWithContentDescription("Menu").performScrollTo().performClick()
            compose.onNode(hasText("Dialog") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithText("Close").assertIsDisplayed().performClick()
            compose.onNode(libraryTab).assertIsSelected()

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
