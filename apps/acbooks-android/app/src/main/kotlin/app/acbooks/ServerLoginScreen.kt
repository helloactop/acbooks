package app.acbooks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.acbooks.library.CloudConnection
import app.acbooks.library.serverOrigin
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ServerLoginScreen(
    connection: CloudConnection,
    replacing: Boolean,
    onBack: () -> Unit,
    onTest: suspend (String) -> Int,
    onLogin: suspend (String, String, String, Boolean) -> Int?,
    onConnected: () -> Unit,
) {
    var editedAddress by rememberSaveable { mutableStateOf<String?>(null) }
    val address = editedAddress ?: connection.origin.orEmpty()
    var editedUsername by rememberSaveable { mutableStateOf<String?>(null) }
    val username = editedUsername ?: connection.username.orEmpty()
    var password by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Int?>(null) }
    var feedback by remember { mutableStateOf<Int?>(null) }
    var differentAccount by remember { mutableStateOf(false) }
    var confirmReplacement by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val readyToSubmit = pending == null && address.isNotBlank() && username.isNotBlank() && password.isNotEmpty()

    fun logIn(replace: Boolean) {
        if (!readyToSubmit) return
        focus.clearFocus()
        feedback = null
        pending = R.string.cloud_logging_in
        scope.launch {
            try {
                val failure = onLogin(address, username, password, replace)
                if (failure == null) {
                    password = ""
                    onConnected()
                } else {
                    differentAccount = failure == R.string.cloud_confirm_new_identity
                    feedback = failure
                }
            } finally {
                pending = null
            }
        }
    }

    fun submit() {
        if (!readyToSubmit) return
        val origin = try {
            serverOrigin(address)
        } catch (_: IllegalArgumentException) {
            feedback = R.string.cloud_invalid_address
            return
        }
        val identityChanged = connection.origin != null && (
            origin != connection.origin || (connection.username != null && username.trim() != connection.username) ||
                differentAccount
            )
        if (replacing || identityChanged) {
            focus.clearFocus()
            confirmReplacement = true
        } else {
            logIn(replace = false)
        }
    }

    SettingsPage(
        title = stringResource(if (replacing) R.string.cloud_change_server else R.string.cloud_log_in),
        onBack = onBack,
        resizeForKeyboard = true,
        overlay = { backdrop ->
            ServerConfirmation(
                show = confirmReplacement,
                backdrop = backdrop,
                title = R.string.cloud_change_server_title,
                summary = R.string.cloud_change_server_summary,
                confirm = R.string.cloud_change_server,
                onDismiss = { confirmReplacement = false },
                onConfirm = {
                    confirmReplacement = false
                    logIn(replace = true)
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
            GlassTextField(
                value = address,
                onValueChange = {
                    editedAddress = it
                    password = ""
                    differentAccount = false
                    feedback = null
                },
                label = stringResource(R.string.cloud_address),
                modifier = Modifier.fillMaxWidth(),
                enabled = pending == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Text(stringResource(R.string.cloud_address_hint), style = MiuixTheme.textStyles.body2)
            GlassTextField(
                value = username,
                onValueChange = {
                    editedUsername = it
                    differentAccount = false
                    feedback = null
                },
                label = stringResource(R.string.cloud_username),
                modifier = Modifier.fillMaxWidth(),
                enabled = pending == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            GlassTextField(
                value = password,
                onValueChange = {
                    password = it
                    feedback = null
                },
                label = stringResource(R.string.cloud_password),
                modifier = Modifier.fillMaxWidth(),
                enabled = pending == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                visualTransformation = PasswordVisualTransformation(),
            )
            Text(stringResource(R.string.cloud_connection_summary), style = MiuixTheme.textStyles.body2)
            (pending ?: feedback)?.let { ServerFeedback(it) }
            TextButton(
                text = stringResource(R.string.cloud_test),
                onClick = {
                    if (pending == null && address.isNotBlank()) {
                        focus.clearFocus()
                        pending = R.string.cloud_checking
                        scope.launch {
                            try {
                                feedback = onTest(address)
                            } finally {
                                pending = null
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = pending == null && address.isNotBlank(),
            )
            TextButton(
                text = stringResource(R.string.cloud_log_in),
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = readyToSubmit,
            )
        }
    }
}
