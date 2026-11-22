package app.acbooks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HomeTopBar(
    avatar: ImageBitmap?,
    backdrop: Backdrop,
    scrollBehavior: ScrollBehavior,
    onOpenSettings: () -> Unit,
) {
    val hour by produceState(initialValue = LocalTime.now().hour) {
        while (true) {
            val time = LocalTime.now()
            value = time.hour
            delay(60_000L - time.second * 1_000L - time.nano / 1_000_000L)
        }
    }
    val greetingResource = when {
        hour < 12 -> R.string.home_good_morning
        hour < 18 -> R.string.home_good_afternoon
        else -> R.string.home_good_evening
    }
    val greeting = stringResource(greetingResource)

    GlassTopAppBar(
        title = greeting,
        largeTitle = greeting,
        modifier = Modifier.semantics { heading() },
        expandable = true,
        backdrop = backdrop,
        scrollBehavior = scrollBehavior,
        buttonSize = 48.dp,
        actions = {
            GlassIconButton(onClick = onOpenSettings) {
                ProfileAvatar(
                    avatar = avatar,
                    contentDescription = stringResource(R.string.home_open_settings),
                    modifier = Modifier.size(32.dp),
                )
            }
        },
    )
}

@Composable
internal fun HomeScreen(scrollBehavior: ScrollBehavior, onOpenLibrary: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 840.dp)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.home_continue_reading),
                        modifier = Modifier.semantics { heading() },
                        style = MiuixTheme.textStyles.title2,
                    )
                    Text(stringResource(R.string.home_no_reading_history))
                    TextButton(
                        text = stringResource(R.string.home_open_library),
                        onClick = onOpenLibrary,
                        minHeight = 48.dp,
                    )
                }
            }
        }
    }
}
