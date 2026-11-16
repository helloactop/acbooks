package app.acbooks.feature.home

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import app.acbooks.core.designsystem.components.LiquidGlassAvatarButton
import app.acbooks.core.designsystem.components.MainTabHeader

@Composable
fun HomeHeader(
    scroll: ScrollState,
    avatar: ImageBitmap?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MainTabHeader(stringResource(R.string.home_title), scroll, modifier) {
        LiquidGlassAvatarButton(avatar, stringResource(R.string.home_open_settings), onOpenSettings)
    }
}
