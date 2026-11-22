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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HomeTopBar(avatar: ImageBitmap?, backdrop: Backdrop, onOpenSettings: () -> Unit) {
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            val time = LocalDateTime.now()
            value = time
            delay(60_000L - time.second * 1_000L - time.nano / 1_000_000L)
        }
    }
    val greeting = when {
        now.hour < 12 -> R.string.home_good_morning
        now.hour < 18 -> R.string.home_good_afternoon
        else -> R.string.home_good_evening
    }
    val locale = LocalConfiguration.current.locales[0]
    val date = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

    GlassTopAppBar(
        title = stringResource(greeting),
        subtitle = date,
        modifier = Modifier.semantics { heading() },
        expandable = false,
        isContentScrolled = false,
        backdrop = backdrop,
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
internal fun HomeScreen(onOpenLibrary: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 840.dp)
                .fillMaxWidth()
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
