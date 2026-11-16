package app.acbooks.core.designsystem.tokens

import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * Reads the acbooks palette back out of a Miuix [Colors]. The mapping is the exact
 * inverse of [toMiuixColors], so components written against [AppColors] see the same
 * values whether they read this or the Miuix roles directly.
 *
 * Roles without an acbooks equivalent are stored in otherwise unused Miuix slots:
 * `errorContainer` carries `destructiveText`, `onErrorContainer` carries `controlBorder`.
 */
internal fun Colors.toAppColors(dark: Boolean): AppColors {
    val text = onSurface
    val accent = primary
    val destructive = error
    return AppColors(
        background = background,
        surface = surfaceContainer,
        surfaceSecondary = surfaceVariant,
        textPrimary = text,
        textSecondary = onSurfaceVariantSummary,
        divider = dividerLine,
        accent = accent,
        accentText = onTertiaryContainer,
        accentSoft = tertiaryContainer,
        accentBorder = accent,
        onAccent = onPrimary,
        destructive = destructive,
        onDestructive = onError,
        destructiveText = errorContainer,
        controlBorder = onErrorContainer,
    )
}

internal fun AppColors.toMiuixColors(dark: Boolean): Colors {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryVariant = accent,
        onPrimaryVariant = accentSoft,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        tertiaryContainer = accentSoft,
        onTertiaryContainer = accentText,
        error = destructive,
        onError = onDestructive,
        errorContainer = destructiveText,
        onErrorContainer = controlBorder,
        background = background,
        onBackground = textPrimary,
        onBackgroundVariant = textSecondary,
        surface = surface,
        onSurface = textPrimary,
        onSurfaceSecondary = textSecondary,
        surfaceVariant = surfaceSecondary,
        surfaceContainer = surface,
        onSurfaceContainer = textPrimary,
        surfaceContainerHigh = surfaceSecondary,
        onSurfaceContainerHigh = textPrimary,
        surfaceContainerHighest = surfaceSecondary,
        onSurfaceContainerHighest = textPrimary,
        secondary = surfaceSecondary,
        onSecondary = textPrimary,
        secondaryVariant = surfaceSecondary,
        onSecondaryVariant = textSecondary,
        outline = controlBorder,
        dividerLine = divider,
        disabledPrimary = accentSoft,
        disabledOnPrimary = textSecondary,
        disabledPrimaryButton = accentSoft,
        disabledOnPrimaryButton = textSecondary,
        disabledSecondary = surfaceSecondary,
        disabledOnSecondary = textSecondary,
        disabledSecondaryVariant = surfaceSecondary,
        disabledOnSecondaryVariant = textSecondary,
        disabledOnSurface = textSecondary,
        onSurfaceVariantSummary = textSecondary,
        onSurfaceVariantActions = textSecondary,
        sliderKeyPoint = accent,
        sliderKeyPointForeground = onAccent,
        sliderBackground = surfaceSecondary,
    )
}
