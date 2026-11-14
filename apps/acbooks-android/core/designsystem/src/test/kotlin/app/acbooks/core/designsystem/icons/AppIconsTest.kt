package app.acbooks.core.designsystem.icons

import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconsTest {
    @Test
    fun navigationUsesOriginalFilledVectors() {
        for (icon in listOf(AppIcons.Home, AppIcons.Library, AppIcons.Statistics)) {
            assertTrue(icon.filled)
            assertSame(icon.vector, icon.controlVector)
        }
    }

    @Test
    fun regularControlPathsAreThickenedAndCached() {
        for (icon in listOf(AppIcons.Add, AppIcons.Clear, AppIcons.More)) {
            val original = icon.vector.root[0] as VectorPath
            val control = (icon.controlVector.root[0] as VectorGroup)[0] as VectorPath
            assertEquals(original.pathData, control.pathData)
            assertTrue(control.strokeLineWidth > original.strokeLineWidth)
            assertSame(icon.controlVector, icon.controlVector)
        }
    }

    @Test
    fun statePairsSwitchBetweenRegularAndFilled() {
        for (pair in listOf(AppIcons.Favorite, AppIcons.Bookmark)) {
            assertSame(pair.regular, pair.forState(false))
            assertSame(pair.filled, pair.forState(true))
            assertTrue(pair.filled.filled && !pair.regular.filled)
        }
    }
}
