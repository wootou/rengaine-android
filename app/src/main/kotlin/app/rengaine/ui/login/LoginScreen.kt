package app.rengaine.ui.login

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.R
import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.repository.ServerCheck
import app.rengaine.data.repository.SignInError
import app.rengaine.ui.components.ButtonKind
import app.rengaine.ui.components.GroovesBackground
import app.rengaine.ui.components.LinkButton
import app.rengaine.ui.components.LogoMark
import app.rengaine.ui.components.RengaineButton
import app.rengaine.ui.components.RengaineTextField
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/**
 * Écran de connexion à un serveur (F1) : adresse du
 * serveur, e-mail, mot de passe sur un seul écran, et « Scanner un QR
 * code » pour l'autre voie. Structure et valeurs : capture
 * `01-connexion` du site, à laquelle s'ajoutent l'adresse du serveur et le
 * QR (propres à l'app).
 *
 * @param onBack `null` au premier lancement (rien derrière) ; sinon retour
 *   à l'app (ajout d'un compte).
 * @param onOtherAccount reconnexion d'un compte : choisir un autre compte.
 */
@Composable
fun LoginScreen(
    onBack: (() -> Unit)?,
    onOtherAccount: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var scanning by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }

    if (scanning) {
        BackHandler { scanning = false }
        ScanScreen(
            busy = state.pairing,
            error = state.error?.takeIf { state.pairing.not() },
            initialAddress = viewModel.addressForManualCode(),
            onClose = { scanning = false },
            onScanned = viewModel::pairScanned,
            onManualCode = viewModel::pairManually,
        )
        return
    }

    LoginContent(
        state = state,
        onBack = onBack,
        onOtherAccount = onOtherAccount,
        onAddressChange = viewModel::onAddressChange,
        onAddressDone = viewModel::checkNow,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onForgot = viewModel::openForgot,
        onScan = { scanning = true },
        onRename = { renaming = true },
    )

    when (val forgot = state.forgot) {
        ForgotState.Hidden -> Unit
        else -> ForgotPasswordDialog(
            state = forgot,
            onEmailChange = viewModel::onForgotEmailChange,
            onSend = viewModel::sendForgot,
            onDismiss = viewModel::closeForgot,
        )
    }
    if (renaming) {
        DeviceNameDialog(
            current = state.deviceName,
            onSave = {
                viewModel.onDeviceNameChange(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    state.pairingToConfirm?.let { payload ->
        ConfirmPairingDialog(
            server = payload.server,
            onConfirm = viewModel::confirmPendingPairing,
            onDismiss = viewModel::dismissPendingPairing,
        )
    }
}

@Composable
internal fun LoginContent(
    state: LoginUiState,
    onBack: (() -> Unit)?,
    onOtherAccount: () -> Unit,
    onAddressChange: (String) -> Unit,
    onAddressDone: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgot: () -> Unit,
    onScan: () -> Unit,
    onRename: () -> Unit,
) {
    val colors = Rengaine.colors
    val type = Rengaine.type
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val ready = state.ready
    val busy = state.submitting || state.pairing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        GroovesBackground(Modifier.fillMaxSize())
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            val minHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(horizontal = 21.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Brand()

                    state.reauth?.let { account ->
                        Notice(stringResource(R.string.reauth_text, account.serverName.ifBlank { account.address.host }))
                    }

                    RengaineTextField(
                        value = state.address,
                        onValueChange = onAddressChange,
                        label = stringResource(R.string.login_server_label),
                        placeholder = stringResource(R.string.login_server_placeholder),
                        enabled = state.reauth == null && !busy,
                        fieldBackground = colors.bg,
                        isError = (state.server as? ServerStatus.Known)?.check?.problemMessage() != null,
                        errorText = (state.server as? ServerStatus.Known)?.check?.problemMessage(),
                        supporting = { ServerStatusLine(state.server) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                            autoCorrectEnabled = false,
                        ),
                        keyboardActions = KeyboardActions(onNext = {
                            onAddressDone()
                            focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                        }),
                        modifier = Modifier.onFocusChanged { if (!it.isFocused) onAddressDone() },
                    )
                    if (isCleartextAddress(state.address)) Notice(stringResource(R.string.server_cleartext_warning))

                    RengaineTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = stringResource(R.string.login_email_label),
                        enabled = state.reauth == null && !busy,
                        fieldBackground = colors.bg,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                            autoCorrectEnabled = false,
                        ),
                    )

                    RengaineTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = stringResource(R.string.login_password_label),
                        enabled = !busy,
                        fieldBackground = colors.bg,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go,
                            autoCorrectEnabled = false,
                        ),
                        keyboardActions = KeyboardActions(onGo = {
                            focus.clearFocus()
                            onSubmit()
                        }),
                        trailing = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (showPassword) R.string.login_hide_password else R.string.login_show_password,
                                    ),
                                    tint = colors.muted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )

                    state.error?.let { error ->
                        if (error is SignInError.AccountPending) {
                            Notice(error.message())
                        } else if (error !is SignInError.Server) {
                            // Les problèmes de serveur s'affichent sous l'adresse.
                            Text(
                                text = error.message(),
                                style = type.body,
                                color = colors.danger,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }

                    RengaineButton(
                        text = stringResource(R.string.login_submit),
                        onClick = {
                            focus.clearFocus()
                            onSubmit()
                        },
                        enabled = state.canSubmit || state.submitting,
                        loading = state.submitting,
                        loadingDescription = stringResource(R.string.login_signing_in),
                        height = 48.dp,
                        textStyle = type.buttonLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                    )

                    if (ready?.info?.features?.passwordReset == true) {
                        LinkButton(
                            text = stringResource(R.string.login_forgot),
                            onClick = onForgot,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }

                    OrSeparator()

                    RengaineButton(
                        text = stringResource(R.string.login_scan_qr),
                        onClick = onScan,
                        kind = ButtonKind.Secondary,
                        icon = Icons.Filled.QrCodeScanner,
                        enabled = !busy,
                        height = 48.dp,
                        textStyle = type.buttonLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (ready?.info?.registrationOpen == true && state.reauth == null) {
                        LinkButton(
                            text = stringResource(R.string.login_request_account),
                            onClick = {
                                // Pas d'inscription dans l'app : le site s'en charge.
                                val intent = Intent(Intent.ACTION_VIEW, ready.address.root.toUri())
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }

                    if (state.reauth != null) {
                        LinkButton(
                            text = stringResource(R.string.reauth_other_account),
                            onClick = onOtherAccount,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }

                    DeviceNameLine(state.deviceName, onRename)
                }
            }
        }

        if (onBack != null) {
            BackHandler(onBack = onBack)
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.text,
                )
            }
        }
    }
}

@Composable
private fun Brand() {
    val colors = Rengaine.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        LogoMark(size = 64.dp, halo = true)
        Text(
            text = stringResource(R.string.brand_wordmark),
            style = Rengaine.type.logotypeLarge,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.brand_tagline),
            style = Rengaine.type.body,
            color = colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ServerStatusLine(status: ServerStatus) {
    val colors = Rengaine.colors
    val type = Rengaine.type
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        when (status) {
            ServerStatus.Idle -> Unit
            ServerStatus.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = colors.muted,
                )
                Text(stringResource(R.string.server_checking), style = type.secondary, color = colors.muted)
            }
            is ServerStatus.Known -> {
                val ready = status.check as? ServerCheck.Ready ?: return@Row
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.ok, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.server_ready, ready.info.name.ifBlank { ready.address.host }),
                    style = type.secondary,
                    color = colors.muted,
                )
            }
        }
    }
}

/** L'adresse saisie est en `http://` : la connexion ne sera pas chiffrée (D6). */
internal fun isCleartextAddress(input: String): Boolean =
    (ServerAddress.parse(input) as? ServerAddress.ParseResult.Valid)?.address?.isCleartext == true

/** Encadré d'information (`.login-notice` du site) : fond `accent-soft`. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Rengaine.type.body,
        color = Rengaine.colors.text,
        modifier = modifier
            .fillMaxWidth()
            .background(Rengaine.colors.accentSoft, RengaineShapes.cover)
            .padding(horizontal = 13.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun OrSeparator() {
    val colors = Rengaine.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.line)
        Text(stringResource(R.string.login_or), style = Rengaine.type.secondary, color = colors.faint)
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.line)
    }
}

@Composable
private fun DeviceNameLine(name: String, onRename: () -> Unit) {
    val colors = Rengaine.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.login_device_name, name),
            style = Rengaine.type.secondary,
            color = colors.faint,
            textAlign = TextAlign.Center,
        )
        LinkButton(text = stringResource(R.string.login_device_rename), onClick = onRename)
        Spacer(Modifier.size(4.dp))
    }
}
