package app.acbooks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.icon.glass.ContactsCircle
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.preference.ArrowPreference
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
) {
    val surfaceBackdrop = rememberLayerBackdrop()
    val pageBackdrop = rememberLayerBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    val colors = MiuixTheme.colorScheme
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = stringResource(R.string.settings_title),
                modifier = Modifier.semantics { heading() },
                expandable = true,
                scrollBehavior = scrollBehavior,
                backdrop = pageBackdrop,
                buttonSize = 48.dp,
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(MiuixIcons.Glass.Back, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            Box(
                Modifier.matchParentSize().layerBackdrop(surfaceBackdrop)
                    .background(colors.surface),
            )
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                val shape = GlassShape(GlassDefaults.CornerRadius)
                Column(
                    modifier = Modifier.widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .glassPanel(
                            backdrop = surfaceBackdrop,
                            shape = shape,
                            style = GlassDefaults.style(isDark = colors.background.luminance() < 0.5f),
                            fallback = Modifier.clip(shape).background(colors.surfaceContainer),
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(avatar, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.settings_avatar), style = MiuixTheme.textStyles.title2)
                    }
                    onChangeAvatar?.let { changeAvatar ->
                        ArrowPreference(
                            title = stringResource(R.string.settings_change_avatar),
                            modifier = Modifier.heightIn(min = 48.dp),
                            onClick = changeAvatar,
                        )
                    }
                }
            }
        }
    }
}
