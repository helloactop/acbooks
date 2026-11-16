package app.acbooks.core.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

val DefaultAccent = Color(0xFF5D92E8)

@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val accent: Color,
    val accentText: Color,
    val accentSoft: Color,
    val accentBorder: Color,
    val onAccent: Color,
    val destructive: Color,
    val onDestructive: Color,
    val destructiveText: Color,
    val controlBorder: Color,
)

fun appColors(dark: Boolean, accent: Color = DefaultAccent): AppColors {
    val light = !dark
    val background = if (light) Color(0xFFF7F7F7) else Color.Black
    val surface = if (light) Color.White else Color(0xFF242424)
    val secondary = if (light) Color(0xFFF2F2F7) else Color(0xFF2C2C2E)
    val text = if (light) Color.Black else Color.White
    val opaqueAccent = accent.copy(alpha = 1f)
    val soft = lerp(surface, opaqueAccent, 0.16f)
    val accentText = if (opaqueAccent == DefaultAccent) {
        if (light) Color(0xFF285FC1) else Color(0xFF8AB4FF)
    } else {
        opaqueAccent
    }
    val destructive = if (light) Color(0xFFD92D3A) else Color(0xFFFF6961)

    return AppColors(
        background, surface, secondary, text,
        if (light) Color(0xFF6E6E73) else Color(0xFF98989D),
        if (light) Color(0xFFE5E5EA) else Color(0xFF38383A),
        opaqueAccent, accentText, soft, opaqueAccent,
        if (contrast(Color.Black, opaqueAccent) >= 4.5f) Color.Black else Color.White,
        destructive,
        if (contrast(Color.Black, destructive) >= 4.5f) Color.Black else Color.White,
        ensureContrast(destructive, text, listOf(surface, secondary), 4.5f),
        ensureContrast(if (light) Color(0xFF6E6E73) else Color(0xFF98989D), text, listOf(surface, secondary), 3f),
    )
}

internal fun contrast(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

internal fun buttonGlassTint(tint: Color, foreground: Color, preferredOpacity: Float): Color {
    val worstBackdrop = if (foreground.luminance() <= tint.luminance()) Color.Black else Color.White
    val glass = tint.copy(alpha = preferredOpacity)
    if (contrast(foreground, glass.compositeOver(worstBackdrop)) >= 4.5f) return glass

    var low = glass.alpha
    var high = 1f
    repeat(16) {
        val opacity = (low + high) / 2f
        if (contrast(foreground, tint.copy(alpha = opacity).compositeOver(worstBackdrop)) >= 4.5f) {
            high = opacity
        } else {
            low = opacity
        }
    }
    return tint.copy(alpha = high)
}

private fun ensureContrast(color: Color, target: Color, backgrounds: List<Color>, minimum: Float): Color {
    if (backgrounds.all { contrast(color, it) >= minimum }) return color
    var low = 0f
    var high = 1f
    repeat(16) {
        val factor = (low + high) / 2f
        if (backgrounds.all { contrast(lerp(color, target, factor), it) >= minimum }) {
            high = factor
        } else {
            low = factor
        }
    }
    return lerp(color, target, high)
}
