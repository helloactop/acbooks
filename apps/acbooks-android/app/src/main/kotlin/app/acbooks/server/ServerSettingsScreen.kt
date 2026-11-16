package app.acbooks.server

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import app.acbooks.R
import app.acbooks.core.designsystem.components.LiquidGlassButton
import app.acbooks.core.designsystem.components.AppMiuixCard
import app.acbooks.core.designsystem.components.AppMiuixPage
import app.acbooks.core.designsystem.components.AppTextField
import app.acbooks.core.designsystem.components.ButtonKind
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.icons.ControlIcon
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    val colors = LocalAppColors.current
    val action = rememberServerAction()
    val connected = session != null && !session.isExpired()

    AppMiuixPage(stringResource(R.string.server_title), onBack) {
        AppMiuixCard {
            Column(
                Modifier.fillMaxWidth().padding(AppSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    ControlIcon(
                        if (connected) AppIcons.Checkmark else AppIcons.Cloud,
                        contentDescription = null,
                        tint = if (connected) colors.accentText else colors.textSecondary,
                    )
                    Text(stringResource(R.string.server_status), style = AppTypography.subhead)
                }
                Text(
                    stringResource(if (session == null) R.string.server_disconnected else serverStatusLabel(session)),
                    color = colors.textSecondary,
                    style = AppTypography.body,
                    textAlign = TextAlign.Center,
                )
                Text(
                    session?.username ?: stringResource(R.string.server_not_configured),
                    color = colors.textSecondary,
                    style = AppTypography.small,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (!connected) {
            LiquidGlassButton(
                label = stringResource(R.string.server_configure),
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Primary,
                enabled = action.pending == null,
                onClick = onOpenLogin,
            )
        }
        if (session != null) {
            AppMiuixCard {
                Column(
                    Modifier.fillMaxWidth().padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    if (connected) {
                        LiquidGlassButton(
                            label = stringResource(R.string.server_test),
                            modifier = Modifier.fillMaxWidth(),
                            kind = ButtonKind.Secondary,
                            enabled = action.canRun(ServerAction.Test),
                            loading = action.pending == ServerAction.Test,
                            onClick = {
                                action.run(ServerAction.Test) {
                                    sessions.test(session.address.toASCIIString(), session)
                                    action.feedback = R.string.server_verified
                                }
                            },
                        )
                    }
                    LiquidGlassButton(
                        label = stringResource(R.string.server_sign_out),
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.Destructive,
                        enabled = action.canRun(ServerAction.SignOut),
                        loading = action.pending == ServerAction.SignOut,
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
        ServerFeedback(action)

        AppMiuixCard {
            Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    ControlIcon(AppIcons.Cloud, contentDescription = null, tint = colors.textSecondary)
                    Text(stringResource(R.string.server_title), style = AppTypography.subhead)
                }
                Text(stringResource(R.string.server_help), color = colors.textSecondary, style = AppTypography.small)
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
    val colors = LocalAppColors.current
    val action = rememberServerAction()
    var addressDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var usernameDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    val address = addressDraft ?: session?.address?.toASCIIString().orEmpty()
    val username = usernameDraft ?: session?.username.orEmpty()

    AppMiuixPage(stringResource(R.string.server_login_title), onBack) {
        AppMiuixCard {
            Column(
                Modifier.fillMaxWidth().padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                AppTextField(
                    address, { addressDraft = it; action.clearFeedback() },
                    stringResource(R.string.server_address),
                    enabled = action.pending == null,
                    errorMessage = if (action.error == ServerError.Address) {
                        stringResource(errorLabel(ServerError.Address))
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                AppTextField(
                    username, { usernameDraft = it; action.clearFeedback() },
                    stringResource(R.string.server_username),
                    enabled = action.pending == null,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                )
                AppTextField(
                    password, { password = it; action.clearFeedback() },
                    stringResource(R.string.server_password),
                    enabled = action.pending == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    visualTransformation = PasswordVisualTransformation(),
                )
                LiquidGlassButton(
                    label = stringResource(R.string.server_sign_in),
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.Primary,
                    enabled = action.canRun(ServerAction.SignIn) &&
                        address.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                    loading = action.pending == ServerAction.SignIn,
                    onClick = {
                        focus.clearFocus()
                        action.run(ServerAction.SignIn) {
                            sessions.signIn(address, username, password)
                            password = ""
                            onSignedIn()
                        }
                    },
                )
                LiquidGlassButton(
                    label = stringResource(R.string.server_test),
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.Secondary,
                    enabled = action.canRun(ServerAction.Test) && address.isNotBlank(),
                    loading = action.pending == ServerAction.Test,
                    onClick = {
                        focus.clearFocus()
                        action.run(ServerAction.Test) {
                            sessions.test(address)
                            action.feedback = R.string.server_reachable
                        }
                    },
                )
                ServerFeedback(action, inlineAddressError = true)
            }
        }

        AppMiuixCard {
            Text(
                stringResource(R.string.server_help),
                modifier = Modifier.padding(AppSpacing.md),
                color = colors.textSecondary,
                style = AppTypography.small,
            )
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

    fun canRun(action: ServerAction): Boolean = pending == null || pending == action

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
    val colors = LocalAppColors.current
    val failure = action.error?.takeUnless { inlineAddressError && it == ServerError.Address }
    val message = failure?.let(::errorLabel) ?: action.feedback ?: return
    Text(
        stringResource(message),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = if (failure != null) colors.destructiveText else colors.textSecondary,
        style = AppTypography.small,
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
