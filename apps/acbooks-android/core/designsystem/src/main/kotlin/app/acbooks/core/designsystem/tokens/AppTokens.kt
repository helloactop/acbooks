package app.acbooks.core.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val page = lg
    val tabHeaderTop = 12.dp
    val libraryToolsGap = 12.dp
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
    val avatar = 40.dp
    val profileAvatar = 56.dp
    val navigation = 64.dp
    val dialog = 320.dp
    val sheet = 480.dp
    val border = 1.dp
    val switchWidth = 52.dp
    val selectWidth = 144.dp
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

enum class IconButtonSize(val diameter: Dp, val icon: Dp) {
    XS(32.dp, 18.dp),
    SM(36.dp, 17.dp),
    MD(44.dp, 20.dp),
    LG(48.dp, 20.dp),
    FAB(56.dp, 24.dp),
    Dock(64.dp, 28.dp),
}

enum class TextButtonSize(val height: Dp, val textStyle: TextStyle) {
    Compact(32.dp, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)),
    Normal(44.dp, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)),
    Dialog(48.dp, AppTypography.body.copy(fontWeight = FontWeight.SemiBold)),
}

object ButtonSizeTokens {
    val touch = AppSizes.touch
    val textMinWidth = 72.dp
    val textPadding = 12.dp
    val textVerticalPadding = AppSpacing.xs
    val dialogGap = AppSpacing.sm
    const val disabledOpacity = 0.48f
    internal const val neutralTintOpacity = 0.55f
    internal const val colorTintOpacity = 0.80f
}

object AppMotion {
    const val popupDuration = 120
    const val transitionDuration = 300
    private val pageDuration = mutableIntStateOf(200)
    var pageDurationMillis: Int
        get() = pageDuration.intValue
        set(value) { pageDuration.intValue = value.coerceAtLeast(0) }
    const val pressedScale = 0.96f
    const val popupScale = 0.96f
    val symmetricEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

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
    val headerRevealDistance = 24.dp
    val headerFadeHeight = 24.dp
    val headerRefractionHeight = 8.dp
    val headerRefractionAmount = 6.dp
    const val headerTintOpacity = 0.38f
}
