package app.acbooks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal class SettingsCardItem(val key: String, val content: @Composable () -> Unit)

internal fun LazyListScope.groupedCardItems(
    groupKey: String,
    @StringRes title: Int,
    rows: List<SettingsCardItem>,
) {
    if (rows.isEmpty()) return
    item(key = "$groupKey:heading", contentType = "settingsHeading") {
        SmallTitle(
            text = stringResource(title),
            modifier = Modifier.semantics { heading() },
        )
    }
    rows.forEachIndexed { index, row ->
        val last = index == rows.lastIndex
        item(key = "$groupKey:row:${row.key}", contentType = "settingsRow") {
            CardSegment(
                roundedTop = index == 0,
                roundedBottom = last,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = if (last) 6.dp else 0.dp),
                content = row.content,
            )
        }
    }
}

@Composable
internal fun CardSegment(
    roundedTop: Boolean,
    roundedBottom: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val top = if (roundedTop) 16.dp else 0.dp
    val bottom = if (roundedBottom) 16.dp else 0.dp
    Box(
        modifier.fillMaxWidth().squircleSurface(
            color = colors.surfaceContainer,
            topStart = top,
            topEnd = top,
            bottomEnd = bottom,
            bottomStart = bottom,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurfaceContainer, content = content)
    }
}
