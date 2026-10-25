package app.rengaine.ui.settings

import android.text.format.Formatter
import android.widget.Toast
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.BuildConfig
import app.rengaine.R
import app.rengaine.data.remote.dto.ThemeChoice
import app.rengaine.playback.StreamQuality
import app.rengaine.domain.Account
import app.rengaine.ui.accounts.SignOutDialog
import app.rengaine.ui.components.ButtonKind
import app.rengaine.ui.components.RengaineButton
import app.rengaine.ui.components.SelectOption
import app.rengaine.ui.components.SelectPill
import app.rengaine.ui.theme.Rengaine

/**
 * Réglages (F12, captures `17`). Mêmes sections et mêmes libellés que le
 * site, mais seulement ce que l'app applique déjà :
 *
 * - Enchaînement : mode infini (réglage du compte) ;
 * - Apparence : thème (réglage du compte) ;
 * - Sur cet appareil : taille du cache, « Vider le cache » (local) ;
 * - Appareils connectés (avec l'appairage d'un autre appareil), Compte,
 *   Mes données, Supprimer mon compte : comme le site (F13).
 *
 * Volume, fondu enchaîné, qualité du flux et lecture continue viendront
 * avec leurs fonctions (F7, F8, F5) ; l'image de fond et la télécommande
 * partagée ne sont pas prévues dans l'app pour l'instant.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLicences: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
    accountVm: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val devices by accountVm.devices.collectAsStateWithLifecycle()
    val waveformShown by viewModel.waveformShown.collectAsStateWithLifecycle()
    val volumeBarShown by viewModel.volumeBarShown.collectAsStateWithLifecycle()
    val pairing by accountVm.pairing.collectAsStateWithLifecycle()
    val pairingAvailable by accountVm.pairingAvailable.collectAsStateWithLifecycle()
    val deleting by accountVm.delete.collectAsStateWithLifecycle()
    val exporting by accountVm.export.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val context = LocalContext.current
    val notSavedText = stringResource(R.string.settings_not_saved)
    LaunchedEffect(Unit) { viewModel.notSaved.collect { Toast.makeText(context, notSavedText, Toast.LENGTH_SHORT).show() } }
    val accountTexts = mapOf(
        AccountMessage.EXPORTED to stringResource(R.string.data_done),
        AccountMessage.EXPORT_TOO_MANY to stringResource(R.string.data_too_many),
        AccountMessage.EXPORT_FAILED to stringResource(R.string.data_failed),
        AccountMessage.REVOKE_FAILED to stringResource(R.string.devices_revoke_failed),
    )
    LaunchedEffect(Unit) { accountVm.messages.collect { Toast.makeText(context, accountTexts.getValue(it), Toast.LENGTH_SHORT).show() } }
    var confirmSignOut by remember { mutableStateOf<Account?>(null) }

    // Quitter l'écran pendant les 15 s annule la suppression (exigé par le contrat).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { accountVm.cancelDelete() }
    DisposableEffect(Unit) { onDispose { accountVm.cancelDelete() } }

    // Export enregistré tel quel où l'utilisateur le choisit : pas de permission de stockage.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { accountVm.export(context.contentResolver, it) }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.text)
                }
                Text(stringResource(R.string.settings_title), style = Rengaine.type.pageTitle, color = colors.text, modifier = Modifier.semantics { heading() })
            }
        }

        item(key = "volume") { VolumeSection(state, viewModel) }

        item(key = "chaining") {
            Section(stringResource(R.string.settings_chaining)) {
                ToggleRow(
                    text = stringResource(R.string.settings_infinite),
                    checked = state.infiniteEnabled,
                    enabled = state.loaded,
                    onChange = viewModel::setInfinite,
                )
                if (state.showContinuous) ContinuousBlock(state, viewModel)
            }
        }

        if (state.showQuality) {
            item(key = "quality") {
                Section(stringResource(R.string.settings_quality)) {
                    Help(stringResource(R.string.settings_quality_help))
                    val options = StreamQuality.entries.map { SelectOption(it, qualityLabel(it)) }
                    FieldLabel(stringResource(R.string.settings_quality_wifi))
                    SelectPill(
                        options = options,
                        selected = state.qualityWifi,
                        onSelect = viewModel::setQualityWifi,
                        label = stringResource(R.string.settings_quality_wifi),
                        field = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FieldLabel(stringResource(R.string.settings_quality_mobile))
                    SelectPill(
                        options = options,
                        selected = state.qualityMobile,
                        onSelect = viewModel::setQualityMobile,
                        label = stringResource(R.string.settings_quality_mobile),
                        field = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Help(stringResource(R.string.settings_quality_note))
                }
            }
        }

        item(key = "appearance") {
            Section(stringResource(R.string.settings_appearance)) {
                FieldLabel(stringResource(R.string.settings_theme))
                SelectPill(
                    options = listOf(
                        SelectOption(ThemeChoice.AUTO, stringResource(R.string.settings_theme_auto)),
                        SelectOption(ThemeChoice.LIGHT, stringResource(R.string.settings_theme_light)),
                        SelectOption(ThemeChoice.DARK, stringResource(R.string.settings_theme_dark)),
                    ),
                    selected = state.theme,
                    onSelect = viewModel::setTheme,
                    label = stringResource(R.string.settings_theme),
                    field = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "device") {
            Section(stringResource(R.string.settings_device)) {
                waveformShown?.let { shown ->
                    ToggleRow(
                        text = stringResource(R.string.settings_waveform),
                        checked = shown,
                        enabled = true,
                        onChange = viewModel::setWaveformShown,
                    )
                }
                ToggleRow(
                    text = stringResource(R.string.settings_volume_bar),
                    checked = volumeBarShown,
                    enabled = true,
                    onChange = viewModel::setVolumeBarShown,
                )
                FieldLabel(stringResource(R.string.settings_cache_size))
                SelectPill(
                    options = SettingsViewModel.CACHE_SIZES_MB.map { mb ->
                        val label = if (mb >= 1000) stringResource(R.string.size_gb, mb / 1000) else stringResource(R.string.size_mb, mb)
                        SelectOption(mb, label)
                    },
                    selected = state.cacheSizeMb,
                    onSelect = viewModel::setCacheSize,
                    label = stringResource(R.string.settings_cache_size),
                    field = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Help(
                    stringResource(
                        R.string.settings_cache_help,
                        state.cacheUsedBytes?.let { Formatter.formatShortFileSize(context, it) } ?: "…",
                    ),
                )
                RengaineButton(
                    text = stringResource(R.string.settings_cache_clear),
                    onClick = viewModel::clearCache,
                    kind = ButtonKind.Secondary,
                    icon = Icons.Filled.DeleteSweep,
                    enabled = (state.cacheUsedBytes ?: 0) > 0,
                )
            }
        }

        item(key = "devices") {
            Section(stringResource(R.string.devices_title)) {
                DevicesList(devices, onRevoke = accountVm::revoke, onRetry = accountVm::loadDevices)
                if (pairingAvailable) {
                    PairingBlock(
                        state = pairing,
                        server = state.account?.address?.display.orEmpty(),
                        onCreate = accountVm::createPairingCode,
                        onClose = accountVm::closePairing,
                    )
                }
            }
        }

        state.account?.let { account ->
            item(key = "account") {
                Section(stringResource(R.string.settings_account)) {
                    Text(
                        listOf(account.displayName, account.email).filter { it.isNotBlank() }.joinToString(" · "),
                        style = Rengaine.type.body,
                        color = colors.text,
                    )
                    Help(stringResource(R.string.settings_account_server, account.serverName.ifBlank { account.address.host }, account.deviceName))
                    if (account.address.isCleartext) Help(stringResource(R.string.settings_account_cleartext))
                    // FlowRow : à 200 % de texte, les deux boutons passent l'un sous l'autre.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RengaineButton(
                            text = stringResource(R.string.accounts_title),
                            onClick = onOpenAccounts,
                            kind = ButtonKind.Secondary,
                            icon = Icons.Filled.ManageAccounts,
                        )
                        RengaineButton(
                            text = stringResource(R.string.accounts_sign_out),
                            onClick = { confirmSignOut = account },
                            kind = ButtonKind.Secondary,
                        )
                    }
                }
            }
        }

        item(key = "data") {
            Section(stringResource(R.string.data_title)) {
                DataBlock(
                    exporting = exporting == ExportState.PREPARING,
                    onExport = { exportLauncher.launch("rengaine-export-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + ".json") },
                    onOpenHistory = onOpenHistory,
                )
            }
        }

        item(key = "delete") {
            Section(stringResource(R.string.delete_title)) {
                DeleteBlock(
                    state = deleting,
                    onStart = accountVm::startDelete,
                    onConfirm = accountVm::confirmDelete,
                    onCancel = accountVm::cancelDelete,
                )
            }
        }

        item(key = "about") {
            Section(stringResource(R.string.settings_about)) {
                Help(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
                RengaineButton(text = stringResource(R.string.licences_title), onClick = onOpenLicences, kind = ButtonKind.Secondary)
            }
        }
    }

    confirmSignOut?.let { account ->
        SignOutDialog(
            account = account,
            onConfirm = {
                viewModel.signOut(account)
                confirmSignOut = null
            },
            onDismiss = { confirmSignOut = null },
        )
    }
}

/** Section du site : filet au-dessus (sauf la première), titre, contenu. */
@Composable
internal fun Section(title: String, first: Boolean = false, content: @Composable () -> Unit) {
    val colors = Rengaine.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!first) HorizontalDivider(color = colors.line, modifier = Modifier.padding(top = 20.dp))
        Text(
            title,
            style = Rengaine.type.sectionTitle,
            color = colors.text,
            modifier = Modifier.padding(top = if (first) 12.dp else 8.dp).semantics { heading() },
        )
        content()
    }
}

@Composable
private fun qualityLabel(q: StreamQuality): String = stringResource(
    when (q) {
        StreamQuality.ORIGINAL -> R.string.quality_original
        StreamQuality.HIGH -> R.string.quality_high
        StreamQuality.MEDIUM -> R.string.quality_medium
        StreamQuality.LOW -> R.string.quality_low
    },
)

@Composable
internal fun FieldLabel(text: String) {
    Text(text, style = Rengaine.type.body, color = Rengaine.colors.text)
}

@Composable
internal fun Help(text: String) {
    Text(text, style = Rengaine.type.secondary, color = Rengaine.colors.muted)
}

/** Interrupteur à gauche, libellé à droite, comme le site ; toute la ligne se touche. */
@Composable
internal fun ToggleRow(text: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Rengaine.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent,
                checkedThumbColor = colors.bg,
                checkedBorderColor = colors.accent,
                uncheckedTrackColor = colors.surface,
                uncheckedThumbColor = colors.muted,
                uncheckedBorderColor = colors.line,
            ),
        )
        Text(text, style = Rengaine.type.body, color = colors.text, modifier = Modifier.weight(1f))
    }
}
