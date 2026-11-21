package app.acbooks.server

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.acbooks.R
import app.acbooks.core.designsystem.R as UiR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class ServerAction { SignIn, Test, SignOut }

@StringRes
internal fun serverStatusLabel(session: ServerSession?): Int = when {
    session == null -> R.string.server_signed_out
    session.isExpired() -> R.string.server_session_expired
    else -> R.string.server_signed_in
}

@Composable
internal fun ServerSettingsScreen(
    sessions: ServerSessions,
    session: ServerSession?,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val action = rememberServerAction()
    val connected = session != null && !session.isExpired()

    Scaffold(topBar = {
        SmallTopAppBar(
            title = stringResource(R.string.server_title),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            navigationIcon = {
                IconButton(onBack, minWidth = 48.dp, minHeight = 48.dp) {
                    Icon(MiuixIcons.Back, stringResource(UiR.string.app_back))
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            if (connected) MiuixIcons.Ok else MiuixIcons.CloudFill,
                            contentDescription = null,
                            tint = if (connected) colors.primary else colors.onSurfaceVariantSummary,
                        )
                        Text(stringResource(R.string.server_status), style = MiuixTheme.textStyles.subtitle)
                    }
                    Text(
                        stringResource(if (session == null) R.string.server_disconnected else serverStatusLabel(session)),
                        color = colors.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body1,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        session?.username ?: stringResource(R.string.server_not_configured),
                        color = colors.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (!connected) {
                TextButton(
                    text = stringResource(R.string.server_configure),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = action.pending == null,
                    onClick = onOpenLogin,
                )
            }
            if (session != null) {
                Card {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (connected) {
                            TextButton(
                                text = stringResource(R.string.server_test),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = action.pending == null,
                                onClick = {
                                    action.run(ServerAction.Test) {
                                        sessions.test(session.address.toASCIIString(), session)
                                        action.feedback = R.string.server_verified
                                    }
                                },
                            )
                        }
                        TextButton(
                            text = stringResource(R.string.server_sign_out),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(color = colors.error, textColor = colors.onError),
                            enabled = action.pending == null,
                            onClick = {
                                action.run(ServerAction.SignOut) {
                                    sessions.signOut()
                                    action.feedback = R.string.server_sign_out_done
                                }
                            },
                        )
                    }
                }
            }
            if (action.pending != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            ServerFeedback(action)

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(MiuixIcons.CloudFill, contentDescription = null, tint = colors.onSurfaceVariantSummary)
                        Text(stringResource(R.string.server_title), style = MiuixTheme.textStyles.subtitle)
                    }
                    Text(stringResource(R.string.server_help), color = colors.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
                }
            }
        }
    }
}

@Composable
internal fun ServerLoginScreen(
    sessions: ServerSessions,
    session: ServerSession?,
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val colors = MiuixTheme.colorScheme
    val action = rememberServerAction()
    var addressDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var usernameDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    val address = addressDraft ?: session?.address?.toASCIIString().orEmpty()
    val username = usernameDraft ?: session?.username.orEmpty()

    Scaffold(topBar = {
        SmallTopAppBar(
            title = stringResource(R.string.server_login_title),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            navigationIcon = {
                IconButton(onBack, minWidth = 48.dp, minHeight = 48.dp) {
                    Icon(MiuixIcons.Back, stringResource(UiR.string.app_back))
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val addressLabel = stringResource(R.string.server_address)
                    val usernameLabel = stringResource(R.string.server_username)
                    val passwordLabel = stringResource(R.string.server_password)
                    val addressError = if (action.error == ServerError.Address) {
                        stringResource(errorLabel(ServerError.Address))
                    } else {
                        null
                    }
                    TextField(
                        address, { addressDraft = it; action.clearFeedback() },
                        Modifier.fillMaxWidth().semantics {
                            contentDescription = addressLabel
                            addressError?.let { error(it) }
                        },
                        label = addressLabel,
                        singleLine = true,
                        enabled = action.pending == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    addressError?.let { Text(it, color = colors.error) }
                    TextField(
                        username, { usernameDraft = it; action.clearFeedback() },
                        Modifier.fillMaxWidth().semantics { contentDescription = usernameLabel },
                        label = usernameLabel,
                        singleLine = true,
                        enabled = action.pending == null,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                    )
                    TextField(
                        password, { password = it; action.clearFeedback() },
                        Modifier.fillMaxWidth().semantics { contentDescription = passwordLabel },
                        label = passwordLabel,
                        singleLine = true,
                        enabled = action.pending == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    TextButton(
                        text = stringResource(R.string.server_sign_in),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = action.pending == null &&
                            address.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                        onClick = {
                            focus.clearFocus()
                            action.run(ServerAction.SignIn) {
                                sessions.signIn(address, username, password)
                                password = ""
                                onSignedIn()
                            }
                        },
                    )
                    TextButton(
                        text = stringResource(R.string.server_test),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = action.pending == null && address.isNotBlank(),
                        onClick = {
                            focus.clearFocus()
                            action.run(ServerAction.Test) {
                                sessions.test(address)
                                action.feedback = R.string.server_reachable
                            }
                        },
                    )
                    if (action.pending != null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    ServerFeedback(action, inlineAddressError = true)
                }
            }

            Card {
                Text(
                    stringResource(R.string.server_help),
                    modifier = Modifier.padding(16.dp),
                    color = colors.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }
    }
}

@Composable
private fun rememberServerAction(): ServerActionState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ServerActionState(scope) }
}

private class ServerActionState(private val scope: CoroutineScope) {
    var pending by mutableStateOf<ServerAction?>(null)
        private set
    var error by mutableStateOf<ServerError?>(null)
        private set
    var feedback by mutableStateOf<Int?>(null)

    fun clearFeedback() {
        error = null
        feedback = null
    }

    fun run(action: ServerAction, block: suspend () -> Unit) {
        if (pending != null) return
        clearFeedback()
        pending = action
        scope.launch {
            try {
                block()
            } catch (failure: ServerFailure) {
                error = failure.error
            } finally {
                pending = null
            }
        }
    }
}

@Composable
private fun ServerFeedback(
    action: ServerActionState,
    inlineAddressError: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    val failure = action.error?.takeUnless { inlineAddressError && it == ServerError.Address }
    val message = failure?.let(::errorLabel) ?: action.feedback ?: return
    Text(
        stringResource(message),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = if (failure != null) colors.error else colors.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.body2,
    )
}

@StringRes
private fun errorLabel(error: ServerError): Int = when (error) {
    ServerError.Address -> R.string.server_invalid_address
    ServerError.Credentials -> R.string.server_bad_credentials
    ServerError.Expired -> R.string.server_expired_error
    ServerError.RateLimited -> R.string.server_rate_limited
    ServerError.Tls -> R.string.server_tls_error
    ServerError.Connection -> R.string.server_connection_error
    ServerError.Response -> R.string.server_response_error
    ServerError.Storage -> R.string.server_storage_error
}
