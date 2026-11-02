package app.rengaine.ui.playlists

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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.rengaine.R
import app.rengaine.ui.components.RengaineTextField
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/**
 * « Modifier la playlist » (créateur seulement) : nom (200 caractères au
 * plus), description sur deux lignes (1000 au plus), « Supprimer la
 * playlist » en rouge. Le site montre ces champs dans un formulaire sous
 * l'en-tête ; sur un téléphone, une fenêtre garde la liste des pistes en
 * place.
 */
@Composable
fun PlaylistEditDialog(
    initialName: String,
    initialDescription: String,
    onSave: (name: String, description: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Rengaine.colors
    val type = Rengaine.type
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    val valid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RengaineShapes.dialog,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.playlist_edit), style = type.sectionTitle, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RengaineTextField(
                    value = name,
                    onValueChange = { name = it.take(200) },
                    label = stringResource(R.string.playlist_name_short),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    fieldBackground = colors.bg,
                    modifier = Modifier.fillMaxWidth(),
                )
                RengaineTextField(
                    value = description,
                    onValueChange = { description = it.take(1000) },
                    label = stringResource(R.string.playlist_description),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    fieldBackground = colors.bg,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.playlist_delete_playlist), color = colors.danger, style = type.button)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onSave(name, description) }, enabled = valid) {
                Text(stringResource(R.string.action_save), color = if (valid) colors.accent else colors.faint, style = type.button)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.muted, style = type.button) }
        },
    )
}
