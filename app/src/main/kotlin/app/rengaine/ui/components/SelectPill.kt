package app.rengaine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/** Une option d'un sélecteur : sa valeur et son libellé. */
data class SelectOption<T>(val value: T, val label: String)

/**
 * Sélecteur en pilule (captures `05`, `06` : « Nom ⌄ », « Tous les genres
 * ⌄ ») : affiche le choix courant et ouvre la liste des options.
 */
@Composable
fun <T> SelectPill(
    options: List<SelectOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Nom du sélecteur pour TalkBack (« Tri », « Genre »). */
    label: String,
    /** Champ de formulaire : pas de mise en valeur du choix (Réglages, Aléatoire). */
    field: Boolean = false,
    /** Toute la largeur disponible (champs de Réglages). */
    fill: Boolean = field,
) {
    val colors = Rengaine.colors
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }?.label.orEmpty()
    val active = !field && selected != options.firstOrNull()?.value

    Box(modifier) {
        Row(
            modifier = Modifier
                .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = 44.dp)
                .clip(RengaineShapes.pill)
                .background(if (active) colors.accentSoft else colors.surface)
                .border(1.dp, if (active) colors.accent else colors.line, RengaineShapes.pill)
                .clickable(role = Role.DropdownList, onClickLabel = label) { open = true }
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                current,
                style = Rengaine.type.body,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 10.dp).then(if (fill) Modifier.weight(1f) else Modifier),
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.raised,
            shape = RengaineShapes.card,
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                DropdownMenuItem(
                    text = { Text(option.label, style = Rengaine.type.body, color = colors.text) },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = colors.accent) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(option.value)
                    },
                )
            }
        }
    }
}
