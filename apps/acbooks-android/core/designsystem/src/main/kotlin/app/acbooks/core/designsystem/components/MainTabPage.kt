package app.acbooks.core.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography

@Composable
fun MainTabBody(
    scroll: ScrollState,
    headerHeightPx: Int,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val headerHeight = if (headerHeightPx == 0) AppSpacing.tabHeaderTop + AppSizes.touch
    else with(LocalDensity.current) { headerHeightPx.toDp() }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(scroll)
            .padding(horizontal = AppSpacing.page)
            .padding(top = headerHeight + AppSpacing.md, bottom = AppSizes.navigation + AppSpacing.md + AppSpacing.page),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        content = content,
    )
}

@Composable
fun MainTabHeader(
    title: String,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    MainTabHeader(title, scroll.value, modifier, actions)
}

@Composable
internal fun MainTabHeader(
    title: String,
    scrollOffset: Int,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    GlassPageHeader(scrollOffset, modifier) {
        Row(Modifier.pageHeaderLayout(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                Modifier.weight(1f).semantics { heading() },
                color = LocalAppColors.current.textPrimary,
                style = AppTypography.display,
            )
            actions()
        }
    }
}

internal fun Modifier.pageHeaderLayout(): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = AppSpacing.page)
    .padding(top = AppSpacing.tabHeaderTop)
    .heightIn(min = AppSizes.touch)
