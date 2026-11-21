package app.acbooks.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ContactsCircle

@Composable
fun HomeHeader(avatar: ImageBitmap?, onOpenSettings: () -> Unit) {
    SmallTopAppBar(
        title = stringResource(R.string.home_title),
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        defaultWindowInsetsPadding = false,
        actions = {
            IconButton(onClick = onOpenSettings, minWidth = 48.dp, minHeight = 48.dp) {
                val label = stringResource(R.string.home_open_settings)
                if (avatar != null) {
                    Image(avatar, label, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(MiuixIcons.Glass.ContactsCircle, label)
                }
            }
        },
    )
}
