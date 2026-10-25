package app.rengaine.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.rengaine.R
import app.rengaine.data.remote.ServerAddress
import app.rengaine.ui.components.RengaineTextField
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

@Composable
private fun RengaineDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissText: String? = stringResource(R.string.action_cancel),
    content: @Composable () -> Unit,
) {
    val colors = Rengaine.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RengaineShapes.dialog,
        containerColor = colors.surface,
        titleContentColor = colors.text,
        textContentColor = colors.muted,
        title = { Text(title, style = Rengaine.type.sectionTitle) },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmText, style = Rengaine.type.button, color = colors.accent)
            }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(onClick = onDismiss) {
                    Text(it, style = Rengaine.type.button, color = colors.muted)
                }
            }
        },
    )
}

@Composable
fun ForgotPasswordDialog(
    state: ForgotState,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        ForgotState.Hidden -> Unit
        is ForgotState.Sent -> RengaineDialog(
            title = stringResource(R.string.forgot_title),
            onDismiss = onDismiss,
            confirmText = stringResource(R.string.action_ok),
            onConfirm = onDismiss,
            dismissText = null,
        ) {
            Text(stringResource(R.string.forgot_sent, state.email), style = Rengaine.type.body)
        }
        is ForgotState.Editing -> RengaineDialog(
            title = stringResource(R.string.forgot_title),
            onDismiss = onDismiss,
            confirmText = stringResource(R.string.forgot_send),
            onConfirm = onSend,
            confirmEnabled = state.email.isNotBlank() && !state.sending,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.forgot_text), style = Rengaine.type.body)
                RengaineTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = stringResource(R.string.login_email_label),
                    isError = state.failed,
                    errorText = if (state.failed) stringResource(R.string.forgot_failed) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun DeviceNameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(current) }
    RengaineDialog(
        title = stringResource(R.string.device_name_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_save),
        onConfirm = { onSave(value) },
        confirmEnabled = value.isNotBlank(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.device_name_text), style = Rengaine.type.body)
            RengaineTextField(
                value = value,
                onValueChange = { value = it.take(DeviceNames.MAX_LENGTH) },
                label = stringResource(R.string.device_name_label),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Lien d'appairage reçu de l'extérieur : on montre où il mène avant de s'y connecter. */
@Composable
fun ConfirmPairingDialog(server: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val shown = (ServerAddress.parse(server) as? ServerAddress.ParseResult.Valid)?.address?.display ?: server
    RengaineDialog(
        title = stringResource(R.string.pair_confirm_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.login_submit),
        onConfirm = onConfirm,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.pair_confirm_text, shown), style = Rengaine.type.body)
            if (isCleartextAddress(server)) Notice(stringResource(R.string.server_cleartext_warning))
        }
    }
}

@Composable
fun ManualCodeDialog(
    initialAddress: String,
    onSubmit: (address: String, code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    var code by rememberSaveable { mutableStateOf("") }
    RengaineDialog(
        title = stringResource(R.string.code_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.login_submit),
        onConfirm = { onSubmit(address, code) },
        confirmEnabled = address.isNotBlank() && code.count { it.isLetterOrDigit() } >= 4,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.code_text), style = Rengaine.type.body)
            RengaineTextField(
                value = address,
                onValueChange = { address = it },
                label = stringResource(R.string.login_server_label),
                placeholder = stringResource(R.string.login_server_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            if (isCleartextAddress(address)) Notice(stringResource(R.string.server_cleartext_warning))
            RengaineTextField(
                value = code,
                onValueChange = { code = it.take(12) },
                label = stringResource(R.string.code_label),
                placeholder = stringResource(R.string.code_placeholder),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
