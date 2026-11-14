package app.acbooks.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.acbooks.core.designsystem.tokens.AppTypography
import app.acbooks.core.designsystem.tokens.DefaultAccent
import app.acbooks.core.designsystem.tokens.appColors

val LocalAppColors = staticCompositionLocalOf { appColors(dark = false) }

private val TypographyTokens = Typography(
    displaySmall = AppTypography.display,
    headlineMedium = AppTypography.title,
    titleLarge = AppTypography.section,
    titleMedium = AppTypography.headline,
    bodyLarge = AppTypography.body,
    bodyMedium = AppTypography.small,
    labelLarge = AppTypography.subhead,
    labelSmall = AppTypography.caption,
)

@Composable
fun AcbooksTheme(
    darkTheme: Boolean,
    accent: Color = DefaultAccent,
    content: @Composable () -> Unit,
) {
    val colors = remember(darkTheme, accent) { appColors(darkTheme, accent) }
    val scheme = remember(darkTheme, colors) {
        (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
            primary = colors.accentText,
            onPrimary = colors.surface,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accentText,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSecondary,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.controlBorder,
            outlineVariant = colors.divider,
            error = colors.destructiveText,
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = TypographyTokens, content = content)
    }
}
