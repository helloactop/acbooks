package app.acbooks.core.designsystem.tokens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val page = lg
}

object AppRadius {
    val sm = 8.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 22.dp
    val capsule = 28.dp
    val dialog = 32.dp
}

object AppSizes {
    val iconSmall = 18.dp
    val icon = 24.dp
    val touch = 48.dp
    val navigation = 64.dp
    val dialog = 320.dp
    val sheet = 480.dp
    val border = 1.dp
    val switchWidth = 52.dp
}

object AppTypography {
    val display = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)
    val title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    val section = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val subhead = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val tiny = TextStyle(fontSize = 11.sp)
}

object AppMotion {
    const val fast = 120
    const val normal = 200
    const val slow = 300
    const val pressedScale = 0.96f

    fun <T> spring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

internal object GlassTokens {
    const val controlOpacity = 0.55f
    const val overlayOpacity = 0.88f
    const val scrimOpacity = 0.32f
    val controlBlur = 8.dp
    val overlayBlur = 12.dp
    val refractionHeight = 12.dp
    val refractionAmount = 16.dp
}
