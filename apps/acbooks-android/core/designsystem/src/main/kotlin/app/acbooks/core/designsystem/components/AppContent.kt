package app.acbooks.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.acbooks.core.designsystem.glass.AppMaterial
import app.acbooks.core.designsystem.glass.AppSurface
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    AppSurface(modifier, content = content)
}

@Composable
fun AppListItem(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = LocalAppColors.current
    AppCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AppSizes.touch).padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AppTypography.body)
                supportingText?.let { Text(it, style = AppTypography.small, color = colors.textSecondary) }
            }
            trailing()
        }
    }
}

@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    leading: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    AppSurface(modifier.fillMaxWidth(), material = if (glass) AppMaterial.Control else AppMaterial.Content) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AppSizes.touch).padding(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            leading()
            Text(title, Modifier.weight(1f), style = AppTypography.section)
            actions()
        }
    }
}

@Composable
fun AppState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier.padding(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Text(title, style = AppTypography.headline, color = LocalAppColors.current.textPrimary)
        message?.let { Text(it, style = AppTypography.body, color = LocalAppColors.current.textSecondary) }
        action()
    }
}

@Composable
fun AppProgress(modifier: Modifier = Modifier, progress: Float? = null) {
    val colors = LocalAppColors.current
    if (progress == null) {
        CircularProgressIndicator(modifier.size(AppSizes.icon), color = colors.accentText)
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            color = colors.accentText,
            trackColor = colors.surfaceSecondary,
        )
    }
}
