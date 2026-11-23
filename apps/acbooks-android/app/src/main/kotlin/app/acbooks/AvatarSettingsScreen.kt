package app.acbooks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.icon.glass.ContactsCircle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    onOpenServer: () -> Unit,
) {
    val pageBackdrop = rememberLayerBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = stringResource(R.string.settings_title),
                modifier = Modifier.semantics { heading() },
                expandable = true,
                progressiveBlur = true,
                scrollBehavior = scrollBehavior,
                backdrop = pageBackdrop,
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(MiuixIcons.Glass.Back, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize()
                .layerBackdrop(pageBackdrop)
                .background(MiuixTheme.colorScheme.surface)
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                )
                .consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
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
}
