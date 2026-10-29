package app.rengaine.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.R
import app.rengaine.ui.theme.Rengaine
import kotlin.math.roundToInt

/**
 * Haut de Réglages › Enchaînement, dans l'ordre et avec les textes du site
 * (`Settings.jsx`) : fondu enchaîné (0 à 12 s, par pas de 1 s, enregistré
 * au lâcher), fondu dans un même album, calage sur le tempo, préchargement,
 * puis les deux réglages propres à ce téléphone : son simplifié et journal
 * de diagnostic du son. Le mode infini et la lecture continue suivent.
 */
@Composable
internal fun TransitionBlock(state: SettingsUiState, viewModel: SettingsViewModel) {
    val colors = Rengaine.colors
    var dragging by remember { mutableFloatStateOf(Float.NaN) }
    val seconds = if (dragging.isNaN()) (state.crossfadeMs / 1000).toInt() else dragging.roundToInt()
    val value = if (seconds == 0) stringResource(R.string.settings_crossfade_none) else stringResource(R.string.settings_crossfade_seconds, seconds)
    val label = stringResource(R.string.settings_crossfade, value)
    FieldLabel(label)
    Slider(
        value = seconds.toFloat(),
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            if (!dragging.isNaN()) viewModel.setCrossfadeMs(dragging.roundToInt() * 1000L)
            dragging = Float.NaN
        },
        valueRange = 0f..12f,
        steps = 11,
        enabled = state.loaded,
        colors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.line,
            activeTickColor = colors.accent,
            inactiveTickColor = colors.line,
        ),
        modifier = Modifier.semantics {
            contentDescription = label
            stateDescription = value
        },
    )
    val fading = seconds > 0
    Column(Modifier.alpha(if (fading) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleRow(
            text = stringResource(R.string.settings_crossfade_album),
            checked = state.crossfadeWithinAlbum,
            enabled = state.loaded && fading,
            onChange = viewModel::setCrossfadeWithinAlbum,
        )
        if (state.showBeatSync) {
            ToggleRow(
                text = stringResource(R.string.settings_beat_sync),
                checked = state.beatSync,
                enabled = state.loaded && fading,
                onChange = viewModel::setBeatSync,
            )
        }
    }
    ToggleRow(
        text = stringResource(R.string.settings_gapless),
        checked = state.gapless,
        enabled = state.loaded,
        onChange = viewModel::setGapless,
    )

    val simplified by viewModel.simplifiedAudio.collectAsStateWithLifecycle()
    ToggleRow(
        text = stringResource(R.string.settings_simplified_audio),
        checked = simplified,
        enabled = true,
        onChange = viewModel::setSimplifiedAudio,
    )

    val diagnostics by viewModel.diagnosticsEnabled.collectAsStateWithLifecycle()
    val lines by viewModel.diagnosticsLines.collectAsStateWithLifecycle()
    ToggleRow(
        text = stringResource(R.string.settings_diagnostics),
        checked = diagnostics,
        enabled = true,
        onChange = viewModel::setDiagnostics,
    )
    // Le bloc reste tant que le journal est actif ou qu'il a des lignes (site).
    if (diagnostics || lines > 0) DiagnosticsBlock(lines, viewModel)
}

@Composable
private fun DiagnosticsBlock(lines: Int, viewModel: SettingsViewModel) {
    val colors = Rengaine.colors
    val context = LocalContext.current
    var message by remember { mutableStateOf<Int?>(null) }
    val description = stringResource(R.string.diagnostics_label)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Help(pluralStringResource(R.plurals.diagnostics_lines, lines, lines))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                message = runCatching {
                    clipboard!!.setPrimaryClip(ClipData.newPlainText(description, viewModel.diagnosticsText()))
                    R.string.diagnostics_copied
                }.getOrDefault(R.string.diagnostics_copy_refused)
            }) { Text(stringResource(R.string.diagnostics_copy), style = Rengaine.type.button, color = colors.accent) }
            TextButton(onClick = {
                viewModel.clearDiagnostics()
                message = null
            }) { Text(stringResource(R.string.diagnostics_clear), style = Rengaine.type.button, color = colors.accent) }
        }
        message?.let { Help(stringResource(it)) }
    }
}
