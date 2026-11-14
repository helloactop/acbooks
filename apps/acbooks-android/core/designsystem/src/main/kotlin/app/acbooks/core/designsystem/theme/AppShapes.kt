package app.acbooks.core.designsystem.theme

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.acbooks.core.designsystem.tokens.AppRadius
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

object AppShapes {
    val small: Shape = RoundedRectangle(AppRadius.sm)
    val medium: Shape = RoundedRectangle(AppRadius.md)
    val large: Shape = RoundedRectangle(AppRadius.lg)
    val extraLarge: Shape = RoundedRectangle(AppRadius.xl)
    val capsule: Shape = RoundedRectangle(AppRadius.capsule)
    val circle: Shape = Capsule()
    val dialog: Shape = RoundedRectangle(AppRadius.dialog)

    fun inset(outerRadius: Dp, inset: Dp): Shape =
        RoundedRectangle((outerRadius - inset).coerceAtLeast(0.dp))
}
