package app.acbooks.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.acbooks.core.designsystem.icons.AppIcon
import app.acbooks.core.designsystem.icons.ControlIcon
import app.acbooks.core.designsystem.theme.AppShapes
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Groups Miuix preference rows into one card, MIUI style: rows share a single
 * surface instead of one card per row.
 */
@Composable
fun AppMiuixSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        content()
    }
}

/**
 * A Miuix card for arbitrary settings content. Uses Miuix card radius and
 * container color; the caller controls inner padding.
 */
@Composable
fun AppMiuixCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth(), content = content)
}

/**
 * Secondary page whose whole content participates in the Miuix theme. Miuix
 * Overlay components resolve their popup host from [Scaffold], so the page is
 * wrapped in one; the Miuix Scaffold draws no chrome here and its own window
 * insets are dropped because [AppSecondaryPage] already applies safe drawing.
 */
@Composable
fun AppMiuixPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = LocalAppColors.current.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        AppSecondaryPage(title, onBack, content)
    }
}

@Composable
fun AppMiuixSettingsRow(
    title: String,
    icon: AppIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    ArrowPreference(
        title = title,
        modifier = modifier.fillMaxWidth(),
        summary = supportingText,
        startAction = {
            ControlIcon(
                icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = onClick,
    )
}

@Composable
fun AppMiuixProfileCard(
    nickname: String,
    avatarLabel: String,
    avatar: ImageBitmap?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Box(
                Modifier.size(AppSizes.profileAvatar)
                    .clip(AppShapes.circle)
                    .clickable(role = Role.Button, onClickLabel = avatarLabel, onClick = onAvatarClick),
                contentAlignment = Alignment.Center,
            ) {
                AppProfileAvatar(avatar, Modifier.size(AppSizes.profileAvatar))
            }
            Text(
                nickname,
                Modifier.weight(1f),
                style = AppTypography.body,
                color = LocalAppColors.current.textPrimary,
            )
        }
    }
}
