package app.acbooks

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ContactsCircle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor

@Composable
internal fun ProfileAvatar(avatar: ImageBitmap?, contentDescription: String?, modifier: Modifier) {
    if (avatar == null) {
        Icon(MiuixIcons.Glass.ContactsCircle, contentDescription, modifier = modifier)
    } else {
        Image(avatar, contentDescription, modifier.clip(CircleShape), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun AvatarSettingsScreen(
    avatar: ImageBitmap?,
    onBack: () -> Unit,
    onChangeAvatar: (() -> Unit)?,
    onOpenShelfSettings: () -> Unit,
    onOpenServer: () -> Unit,
) {
    SettingsPage(title = stringResource(R.string.settings_title), onBack = onBack) { nestedScroll, padding ->
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().then(nestedScroll),
            contentPadding = padding,
        ) {
            groupedCardItems(
                groupKey = "profile",
                title = R.string.settings_profile,
                rows = buildList {
                    add(SettingsCardItem("avatar") {
                        BasicComponent(
                            title = stringResource(R.string.settings_avatar),
                            titleColor = BasicComponentDefaults.titleColor(color = LocalContentColor.current),
                            startAction = {
                                ProfileAvatar(avatar, contentDescription = null, modifier = Modifier.size(48.dp))
                            },
                        )
                    })
                    if (onChangeAvatar != null) {
                        add(SettingsCardItem("change-avatar") {
                            ArrowPreference(
                                title = stringResource(R.string.settings_change_avatar),
                                titleColor = BasicComponentDefaults.titleColor(color = LocalContentColor.current),
                                onClick = onChangeAvatar,
                            )
                        })
                    }
                },
            )
            groupedCardItems(
                groupKey = "library",
                title = R.string.tab_library,
                rows = listOf(SettingsCardItem("shelves") {
                    ArrowPreference(
                        title = stringResource(R.string.settings_shelves),
                        titleColor = BasicComponentDefaults.titleColor(color = LocalContentColor.current),
                        onClick = onOpenShelfSettings,
                    )
                }),
            )
            groupedCardItems(
                groupKey = "server",
                title = R.string.settings_services,
                rows = listOf(SettingsCardItem("acbooks-server") {
                    ArrowPreference(
                        title = stringResource(R.string.settings_server),
                        titleColor = BasicComponentDefaults.titleColor(color = LocalContentColor.current),
                        onClick = onOpenServer,
                    )
                }),
            )
        }
    }
}
