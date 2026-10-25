package app.rengaine.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rengaine.R
import app.rengaine.data.remote.dto.DeviceDto
import app.rengaine.ui.components.ButtonKind
import app.rengaine.ui.components.LinkButton
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.RengaineButton
import app.rengaine.ui.components.RengaineTextField
import app.rengaine.ui.components.formatDuration
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// --- Appareils connectés ----------------------------------------------------------------

/**
 * Liste des appareils (`Settings.jsx`) : nom, type, « · cet appareil »,
 * « · vu le … » ; « Déconnecter » sans confirmation pour les autres. Le
 * téléphone lui-même se quitte par « Se déconnecter » dans Compte.
 */
@Composable
fun DevicesList(state: DevicesState, onRevoke: (DeviceDto) -> Unit, onRetry: () -> Unit) {
    val colors = Rengaine.colors
    when {
        state.loading && state.items.isEmpty() -> LoadingState()
        state.failed && state.items.isEmpty() -> LinkButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        else -> Column {
            state.items.forEach { device ->
                val current = device.id == state.currentTokenId
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, style = Rengaine.type.body, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(deviceMeta(device, current), style = Rengaine.type.secondary, color = colors.muted)
                    }
                    if (!current) {
                        RengaineButton(text = stringResource(R.string.devices_revoke), onClick = { onRevoke(device) }, kind = ButtonKind.Secondary)
                    }
                }
                HorizontalDivider(color = colors.line)
            }
        }
    }
}

@Composable
private fun deviceMeta(device: DeviceDto, current: Boolean): String = buildList {
    add(
        stringResource(
            when (device.kind) {
                "web" -> R.string.devices_kind_web
                "agent" -> R.string.devices_kind_agent
                else -> R.string.devices_kind_app
            },
        ),
    )
    if (current) add(stringResource(R.string.devices_this))
    device.lastUsedAt?.let { at ->
        runCatching { Instant.parse(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)) }
            .getOrNull()?.let { add(stringResource(R.string.devices_seen, it)) }
    }
}.joinToString(" · ")

// --- Appairer un appareil ---------------------------------------------------------------

/**
 * « Appairer un appareil » (`PairDevice.jsx`) : QR dessiné ici depuis
 * `qrPayload`, code en clair, serveur, « Valable encore m:ss » ; « Nouveau
 * code » à l'expiration ; « « nom » est appairé. » dès que le nouvel
 * appareil apparaît. Le code vaut pour le compte, pas pour ce téléphone.
 */
@Composable
fun PairingBlock(state: PairingState, server: String, onCreate: () -> Unit, onClose: () -> Unit) {
    val colors = Rengaine.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.pair_help), style = Rengaine.type.secondary, color = colors.muted)
        when (state) {
            PairingState.Closed -> RengaineButton(text = stringResource(R.string.pair_button), onClick = onCreate, kind = ButtonKind.Secondary, icon = Icons.Filled.QrCode2)
            PairingState.Creating -> LoadingState()
            is PairingState.Failed -> {
                Text(stringResource(if (state.tooMany) R.string.pair_too_many else R.string.pair_failed), style = Rengaine.type.body, color = colors.danger)
                RengaineButton(text = stringResource(R.string.pair_button), onClick = onCreate, kind = ButtonKind.Secondary, icon = Icons.Filled.QrCode2)
            }
            is PairingState.Shown -> PairingCard(onClose) {
                val qr = remember(state.code.qrPayload) { qrBitmap(state.code.qrPayload) }
                val qrLabel = stringResource(R.string.pair_qr_label)
                // Fond blanc et marge : un QR clair sur fond sombre se lit mal (piège connu de Musique).
                Image(
                    bitmap = qr,
                    contentDescription = qrLabel,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(240.dp)
                        .background(Color.White, RengaineShapes.card)
                        .padding(12.dp),
                )
                LabeledValue(stringResource(R.string.pair_code), state.code.code)
                LabeledValue(stringResource(R.string.pair_server), server)
                LabeledValue(stringResource(R.string.pair_valid_for), formatDuration(state.remainingS * 1000))
            }
            PairingState.Expired -> PairingCard(onClose) {
                Text(stringResource(R.string.pair_expired), style = Rengaine.type.body, color = colors.text)
                RengaineButton(text = stringResource(R.string.pair_new_code), onClick = onCreate, kind = ButtonKind.Secondary)
            }
            is PairingState.Paired -> PairingCard(onClose) {
                Text(
                    stringResource(R.string.pair_done, state.name),
                    style = Rengaine.type.bodyStrong,
                    color = colors.text,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun PairingCard(onClose: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = Rengaine.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RengaineShapes.panel)
            .border(1.dp, colors.line, RengaineShapes.panel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
        LinkButton(text = stringResource(R.string.pair_close), onClick = onClose)
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = Rengaine.type.secondary, color = Rengaine.colors.muted, modifier = Modifier.weight(0.4f))
        Text(value, style = Rengaine.type.bodyStrong, color = Rengaine.colors.text, modifier = Modifier.weight(0.6f))
    }
}

/** QR en noir sur blanc, un pixel par module (agrandi sans lissage à l'affichage). */
private fun qrBitmap(payload: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
    val bitmap = android.graphics.Bitmap.createBitmap(matrix.width, matrix.height, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
        bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bitmap.asImageBitmap()
}

// --- Mes données ------------------------------------------------------------------------------

@Composable
fun DataBlock(exporting: Boolean, onExport: () -> Unit, onOpenHistory: () -> Unit) {
    val colors = Rengaine.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.data_help), style = Rengaine.type.secondary, color = colors.muted)
        RengaineButton(
            text = stringResource(if (exporting) R.string.data_preparing else R.string.data_download),
            onClick = onExport,
            kind = ButtonKind.Secondary,
            icon = Icons.Filled.Download,
            loading = exporting,
        )
        LinkButton(text = stringResource(R.string.settings_history_link), onClick = onOpenHistory)
    }
}

// --- Supprimer mon compte --------------------------------------------------------------------

/**
 * `DeleteAccount.jsx` : avertissement, puis la liste de ce qui sera
 * effacé et le mot de passe, puis **15 s pour annuler** (compte à rebours,
 * « Annuler » qui reçoit le focus), puis seulement la requête.
 */
@Composable
fun DeleteBlock(state: DeleteState, onStart: () -> Unit, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    val colors = Rengaine.colors
    var password by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            DeleteState.Idle -> {
                Text(stringResource(R.string.delete_help), style = Rengaine.type.secondary, color = colors.muted)
                RengaineButton(text = stringResource(R.string.delete_button), onClick = { password = ""; onStart() }, kind = ButtonKind.Danger)
            }
            is DeleteState.Confirming -> {
                Text(stringResource(R.string.delete_will_erase), style = Rengaine.type.bodyStrong, color = colors.text)
                listOf(R.string.delete_item_music, R.string.delete_item_settings, R.string.delete_item_devices).forEach {
                    Text("• " + stringResource(it), style = Rengaine.type.body, color = colors.text)
                }
                RengaineTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.delete_password),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = state.error != null,
                    errorText = state.error?.let { deleteErrorText(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RengaineButton(
                        text = stringResource(R.string.delete_confirm),
                        onClick = { onConfirm(password) },
                        kind = ButtonKind.Danger,
                        enabled = password.isNotEmpty(),
                    )
                    RengaineButton(text = stringResource(R.string.delete_keep), onClick = onCancel, kind = ButtonKind.Secondary)
                }
            }
            is DeleteState.Countdown -> {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                val announce = stringResource(R.string.delete_countdown_announce)
                Text(
                    pluralStringResource(R.plurals.delete_countdown, state.seconds, state.seconds),
                    style = Rengaine.type.sectionTitle,
                    color = colors.danger,
                    // Annoncé une fois au début, pas à chaque seconde.
                    modifier = Modifier.semantics { contentDescription = announce },
                )
                RengaineButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    kind = ButtonKind.Primary,
                    modifier = Modifier.focusRequester(focus),
                )
            }
            DeleteState.Deleting -> Text(stringResource(R.string.delete_deleting), style = Rengaine.type.body, color = colors.text)
        }
    }
}

@Composable
private fun deleteErrorText(error: DeleteError): String = when (error) {
    DeleteError.WrongPassword -> stringResource(R.string.delete_error_password)
    DeleteError.LastAdmin -> stringResource(R.string.delete_error_last_admin)
    is DeleteError.Api -> error.message.ifBlank { stringResource(R.string.delete_error_offline) }
    DeleteError.Offline -> stringResource(R.string.delete_error_offline)
}
