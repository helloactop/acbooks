package app.acbooks.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.acbooks.core.designsystem.R
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.icons.ControlIcon
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography
import app.acbooks.core.designsystem.tokens.IconButtonSize

@Composable
fun AppLibraryHeader(
    title: String,
    scrollOffset: Int,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    moreItems: List<AppMenuItem> = emptyList(),
) {
    var moreOpen by remember { mutableStateOf(false) }
    MainTabHeader(title, scrollOffset, modifier) {
        if (onEdit != null || moreItems.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            if (onEdit != null) {
                LiquidGlassIconButton(AppIcons.Edit, stringResource(R.string.app_library_edit), onEdit, size = IconButtonSize.SM)
            }
            if (moreItems.isNotEmpty()) {
                AppMenu(moreOpen, { moreOpen = false }, moreItems) {
                    LiquidGlassIconButton(
                        AppIcons.MoreVertical, stringResource(R.string.app_library_more),
                        onClick = { moreOpen = true },
                        size = IconButtonSize.SM,
                    )
                }
            }
        }
    }
}

@Composable
fun AppLibraryTools(
    filterLabel: String,
    filterItems: List<AppMenuItem>,
    onSearch: () -> Unit,
    onCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var filtersOpen by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.libraryToolsGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidGlassContentButton(
            content = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.app_library_search), Modifier.weight(1f),
                        style = AppTypography.small, color = colors.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    ControlIcon(AppIcons.Search, contentDescription = null, iconSize = IconButtonSize.MD.icon)
                }
            },
            onClick = onSearch,
            modifier = Modifier.weight(1f),
        )
        LiquidGlassButtonGroup {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppMenu(filtersOpen, { filtersOpen = false }, filterItems) {
                    LiquidGlassButtonSegment(
                        onClick = { filtersOpen = true },
                        enabled = filterItems.isNotEmpty(),
                        role = Role.DropdownList,
                    ) {
                        Row(
                            Modifier.padding(horizontal = AppSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                filterLabel, Modifier.widthIn(max = AppSizes.touch * 2),
                                style = AppTypography.small, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            ControlIcon(AppIcons.ChevronDown, contentDescription = null, iconSize = IconButtonSize.MD.icon)
                        }
                    }
                }
                VerticalDivider(Modifier.height(AppSizes.icon), color = colors.divider)
                LiquidGlassButtonSegment(onClick = onCategories) {
                    ControlIcon(AppIcons.Categories, stringResource(R.string.app_library_categories), iconSize = IconButtonSize.MD.icon)
                }
            }
        }
    }
}
