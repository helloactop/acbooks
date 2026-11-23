package app.acbooks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.acbooks.library.CloudLibraryState
import app.acbooks.library.ServerConnectionStatus
import app.acbooks.library.ServerOperation
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ServerConnectionScreen(
    state: CloudLibraryState,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onTest: () -> Unit,
    onSync: () -> Unit,
    onChangeServer: () -> Unit,
    onSignOut: () -> Unit,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    val connection = state.connection
    val idle = !state.busy
    SettingsPage(
        title = stringResource(R.string.settings_server),
        onBack = onBack,
        overlay = { backdrop ->
            ServerConfirmation(
                show = confirmSignOut,
                backdrop = backdrop,
                title = R.string.cloud_sign_out_title,
                summary = R.string.cloud_sign_out_summary,
                confirm = R.string.cloud_disconnect,
                onDismiss = { confirmSignOut = false },
                onConfirm = {
                    confirmSignOut = false
                    onSignOut()
                },
            )
        },
    ) { nestedScroll, padding ->
        Column(
            Modifier.widthIn(max = 840.dp).fillMaxWidth().then(nestedScroll)
                .verticalScroll(rememberScrollState()).padding(padding)
                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val status = when (if (state.operation == ServerOperation.Checking) ServerConnectionStatus.Unknown else state.status) {
                ServerConnectionStatus.Unknown -> R.string.cloud_checking
                ServerConnectionStatus.SignedOut -> R.string.cloud_disconnected
                ServerConnectionStatus.Connected -> R.string.cloud_connected
                ServerConnectionStatus.Unavailable -> R.string.cloud_unavailable
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = if (connection.hasSession) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(status),
                        style = MiuixTheme.textStyles.title4,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (connection.hasSession) {
                        ServerConnectionDetail(R.string.cloud_address, connection.origin)
                        ServerConnectionDetail(R.string.cloud_username, connection.username)
                    } else if (state.status == ServerConnectionStatus.SignedOut) {
                        Text(
                            stringResource(R.string.cloud_disconnected_summary),
                            style = MiuixTheme.textStyles.body2,
                            textAlign = TextAlign.Center,
                        )
                        if (connection.origin != null) {
                            Text(connection.origin, style = MiuixTheme.textStyles.body2, textAlign = TextAlign.Center)
                        }
                    }
                    val activity = when (state.operation) {
                        ServerOperation.Checking -> R.string.cloud_checking
                        ServerOperation.Syncing -> R.string.cloud_syncing
                        ServerOperation.SigningOut -> R.string.cloud_signing_out
                        null -> null
                    }
                    activity?.takeUnless { it == status }?.let { ServerFeedback(it) }
                    state.message?.let { ServerFeedback(it) }
                }
            }
            when {
                connection.hasSession -> {
                    TextButton(
                        text = stringResource(R.string.cloud_sync_now),
                        onClick = onSync,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = idle,
                    )
                    TextButton(stringResource(R.string.cloud_test), onClick = onTest, modifier = Modifier.fillMaxWidth(), enabled = idle)
                    TextButton(stringResource(R.string.cloud_change_server), onClick = onChangeServer, modifier = Modifier.fillMaxWidth(), enabled = idle)
                    TextButton(stringResource(R.string.cloud_disconnect), onClick = { confirmSignOut = true },
                        modifier = Modifier.fillMaxWidth(), enabled = idle)
                }
                state.status == ServerConnectionStatus.SignedOut -> TextButton(
                    text = stringResource(R.string.cloud_connect),
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = idle,
                )
            }
        }
    }
}

@Composable
private fun ServerConnectionDetail(@StringRes label: Int, value: String?) {
    Column {
        Text(stringResource(label), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value ?: stringResource(R.string.cloud_account_unavailable))
    }
}

@Composable
internal fun ServerFeedback(@StringRes message: Int) {
    Text(
        text = stringResource(message),
        style = MiuixTheme.textStyles.body2,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
internal fun ServerConfirmation(
    show: Boolean,
    backdrop: Backdrop,
    @StringRes title: Int,
    @StringRes summary: Int,
    @StringRes confirm: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    GlassDialog(
        show = show,
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        title = stringResource(title),
        summary = stringResource(summary),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassDialogButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(android.R.string.cancel), style = MiuixTheme.textStyles.button)
            }
            GlassDialogButton(onClick = onConfirm, modifier = Modifier.weight(1f), role = GlassDialogButtonRole.Destructive) {
                Text(stringResource(confirm), style = MiuixTheme.textStyles.button)
            }
        }
    }
}
