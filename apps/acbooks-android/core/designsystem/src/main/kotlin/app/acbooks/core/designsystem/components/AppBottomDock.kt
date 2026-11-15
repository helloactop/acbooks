package app.acbooks.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.acbooks.core.designsystem.R
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.tokens.AppSpacing

@Composable
fun AppBottomDock(
    selected: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        AppNavItem(stringResource(R.string.app_dock_home), AppIcons.Home),
        AppNavItem(stringResource(R.string.app_dock_library), AppIcons.Library),
        AppNavItem(stringResource(R.string.app_dock_statistics), AppIcons.Statistics),
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppNavigationBar(tabs, selected, onSelect, Modifier.weight(1f))
        AppAddButton(stringResource(R.string.app_dock_add), onAdd)
    }
}
