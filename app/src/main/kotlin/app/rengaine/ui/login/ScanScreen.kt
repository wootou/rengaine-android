package app.rengaine.ui.login

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.rengaine.R
import app.rengaine.data.repository.SignInError
import app.rengaine.domain.PairingUri
import app.rengaine.ui.components.ButtonKind
import app.rengaine.ui.components.RengaineButton
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/**
 * Connexion par le QR d'appairage du site (F1). Caméra plein écran avec un
 * cadre de visée ; saisie manuelle de l'adresse et du code en repli.
 *
 * Le scanner signale le même QR à chaque image tant qu'il reste dans le
 * champ : on ne l'envoie qu'une fois. Après un échec (code expiré), il faut
 * toucher « Réessayer » pour qu'un nouveau scan parte, sinon l'app
 * brûlerait les tentatives en boucle sur le même code.
 */
@Composable
fun ScanScreen(
    busy: Boolean,
    error: SignInError?,
    initialAddress: String,
    onClose: () -> Unit,
    onScanned: (PairingUri.Payload) -> Unit,
    onManualCode: (address: String, code: String) -> Unit,
) {
    val context = LocalContext.current
    val colors = Rengaine.colors
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var lastRaw by remember { mutableStateOf<String?>(null) }
    var notPairing by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!granted && !askedOnce) launcher.launch(Manifest.permission.CAMERA)
    }
    // Retour des réglages d'Android : la permission a pu être accordée.
    LifecycleResumeEffect(Unit) {
        granted = context.hasCameraPermission()
        onPauseOrDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (granted) {
            QrScannerView(
                onResult = { raw ->
                    if (busy || raw == lastRaw) return@QrScannerView
                    lastRaw = raw
                    val payload = PairingUri.parse(raw)
                    notPairing = payload == null
                    if (payload != null) onScanned(payload)
                },
            )
            // Cadre de visée.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .border(3.dp, colors.accent, RengaineShapes.panel),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.scan_title),
                    style = Rengaine.type.sectionTitle,
                    color = Color.White,
                    modifier = Modifier.semantics { heading() },
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!granted) {
                    PermissionPanel(
                        permanentlyDenied = askedOnce && !context.shouldShowCameraRationale(),
                        onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                    )
                }
                if (busy) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val message = when {
                    busy -> stringResource(R.string.scan_pairing)
                    error != null -> error.message()
                    notPairing -> stringResource(R.string.scan_not_pairing)
                    else -> stringResource(R.string.scan_hint)
                }
                Text(
                    text = message,
                    style = Rengaine.type.body,
                    color = if (error != null && !busy) Color(0xFFFF8A8A) else Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if ((error != null || notPairing) && !busy) {
                    RengaineButton(
                        text = stringResource(R.string.scan_retry),
                        onClick = {
                            lastRaw = null
                            notPairing = false
                        },
                        kind = ButtonKind.Primary,
                    )
                }
                RengaineButton(
                    text = stringResource(R.string.scan_enter_code),
                    onClick = { manual = true },
                    kind = ButtonKind.Secondary,
                    icon = Icons.Filled.Keyboard,
                    enabled = !busy,
                )
            }
        }
    }

    if (manual) {
        ManualCodeDialog(
            initialAddress = initialAddress,
            onSubmit = { address, code ->
                manual = false
                onManualCode(address, code)
            },
            onDismiss = { manual = false },
        )
    }
}

@Composable
private fun PermissionPanel(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.scan_permission_title),
            style = Rengaine.type.sectionTitle,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.scan_permission_text),
            style = Rengaine.type.body,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            RengaineButton(text = stringResource(R.string.scan_permission_settings), onClick = onOpenSettings)
        } else {
            RengaineButton(text = stringResource(R.string.scan_permission_grant), onClick = onRequest)
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.shouldShowCameraRationale(): Boolean {
    var ctx: Context = this
    while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
    return (ctx as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
}
