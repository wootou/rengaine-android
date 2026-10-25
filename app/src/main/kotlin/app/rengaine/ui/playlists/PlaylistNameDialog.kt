package app.rengaine.ui.playlists

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
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
import app.rengaine.R
import app.rengaine.ui.components.RengaineTextField
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/** Nom d'une playlist, à la création ou pour la renommer (200 caractères au plus, contrat). */
@Composable
fun PlaylistNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Rengaine.colors
    var name by rememberSaveable { mutableStateOf(initial) }
    val valid = name.isNotBlank()
    val submit = {
        if (valid) {
            onConfirm(name.trim())
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RengaineShapes.dialog,
        containerColor = colors.surface,
        title = { Text(title, style = Rengaine.type.sectionTitle, color = colors.text) },
        text = {
            RengaineTextField(
                value = name,
                onValueChange = { name = it.take(200) },
                label = stringResource(R.string.playlist_name),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = valid) {
                Text(stringResource(R.string.action_save), color = if (valid) colors.accent else colors.faint, style = Rengaine.type.button)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.muted, style = Rengaine.type.button) }
        },
    )
}
