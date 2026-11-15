package app.acbooks.feature.home

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.acbooks.core.designsystem.components.LiquidGlassIconButton
import app.acbooks.core.designsystem.components.MainTabHeader
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.tokens.IconButtonSize

@Composable
fun HomeHeader(scroll: ScrollState, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    MainTabHeader(stringResource(R.string.home_title), scroll, modifier) {
        LiquidGlassIconButton(
            AppIcons.Profile, stringResource(R.string.home_open_settings), onOpenSettings,
            size = IconButtonSize.SM,
        )
    }
}
