package app.acbooks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.acbooks.library.CloudLibraryState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ServerSettingsScreen(
    state: CloudLibraryState,
    onBack: () -> Unit,
    onConnect: (String, String, String) -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    val focus = LocalFocusManager.current
    var address by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val server = state.origin ?: address

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = stringResource(R.string.settings_server),
                backdrop = backdrop,
                expandable = true,
                progressiveBlur = true,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(MiuixIcons.Glass.Back, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().layerBackdrop(backdrop).background(MiuixTheme.colorScheme.surface)
                .consumeWindowInsets(padding).windowInsetsPadding(WindowInsets.ime),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(if (state.connected) R.string.cloud_connected else R.string.cloud_disconnected))
                        state.origin?.let { Text(it, style = MiuixTheme.textStyles.body2) }
                        if (state.busy) Text(stringResource(R.string.cloud_working), style = MiuixTheme.textStyles.body2)
                        state.message?.let { Text(stringResource(it), style = MiuixTheme.textStyles.body2) }
                    }
                }
                if (state.connected) {
                    TextButton(stringResource(R.string.cloud_sync_now), onClick = onSync,
                        modifier = Modifier.fillMaxWidth(), enabled = !state.busy, minHeight = 48.dp)
                    TextButton(stringResource(R.string.cloud_disconnect), onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(), enabled = !state.busy, minHeight = 48.dp)
                } else {
                    GlassTextField(
                        value = server, onValueChange = { address = it }, label = stringResource(R.string.cloud_address),
                        modifier = Modifier.fillMaxWidth(), enabled = !state.busy, readOnly = state.origin != null,
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    GlassTextField(
                        value = username, onValueChange = { username = it }, label = stringResource(R.string.cloud_username),
                        modifier = Modifier.fillMaxWidth(), enabled = !state.busy, singleLine = true,
                    )
                    GlassTextField(
                        value = password, onValueChange = { password = it }, label = stringResource(R.string.cloud_password),
                        modifier = Modifier.fillMaxWidth(), enabled = !state.busy, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Text(stringResource(R.string.cloud_connection_summary), style = MiuixTheme.textStyles.body2)
                    TextButton(
                        text = stringResource(R.string.cloud_connect),
                        onClick = {
                            focus.clearFocus()
                            onConnect(server, username, password)
                            password = ""
                        },
                        modifier = Modifier.fillMaxWidth(), minHeight = 48.dp,
                        enabled = !state.busy && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                    )
                }
            }
        }
    }
}
