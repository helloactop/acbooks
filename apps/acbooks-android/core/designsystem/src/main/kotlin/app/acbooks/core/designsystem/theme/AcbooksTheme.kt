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
import app.acbooks.core.designsystem.tokens.toMiuixColors
import app.acbooks.core.designsystem.tokens.toAppColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles

val LocalAppColors = staticCompositionLocalOf { appColors(dark = false) }

internal val LocalAppDarkTheme = staticCompositionLocalOf { false }

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

/**
 * The single theme entry for the app. [MiuixTheme] holds the only colour source;
 * [LocalAppColors] is derived from it so components written against the original
 * acbooks palette keep working without a second palette.
 */
@Composable
fun AcbooksTheme(
    darkTheme: Boolean,
    accent: Color = DefaultAccent,
    content: @Composable () -> Unit,
) {
    val miuixColors = remember(darkTheme, accent) {
        appColors(darkTheme, accent).toMiuixColors(darkTheme)
    }
    val colors = remember(miuixColors) { miuixColors.toAppColors(darkTheme) }
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

    MiuixTheme(
        colors = miuixColors,
        textStyles = remember { miuixTextStyles() },
    ) {
        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalAppDarkTheme provides darkTheme,
        ) {
            MaterialTheme(colorScheme = scheme, typography = TypographyTokens, content = content)
        }
    }
}

private fun miuixTextStyles() = defaultTextStyles(
    main = AppTypography.body,
    paragraph = AppTypography.body,
    body1 = AppTypography.body,
    body2 = AppTypography.small,
    button = AppTypography.subhead,
    footnote1 = AppTypography.caption,
    footnote2 = AppTypography.tiny,
    headline1 = AppTypography.body,
    headline2 = AppTypography.small,
    subtitle = AppTypography.subhead,
    title1 = AppTypography.display,
    title2 = AppTypography.title,
    title3 = AppTypography.section,
    title4 = AppTypography.headline,
)
