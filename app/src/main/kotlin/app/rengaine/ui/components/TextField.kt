package app.rengaine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/**
 * Champ de saisie du site : étiquette au-dessus (pas d'étiquette flottante
 * Material), boîte à bord fin, rayon 10, hauteur 46 sur l'écran de
 * connexion. Focus : bord braise et halo `accent-soft` de 3.
 *
 * [supporting] s'affiche sous le champ (état du serveur, erreur) ; s'il est
 * une erreur ([isError]), il est annoncé par TalkBack avec le champ.
 */
@Composable
fun RengaineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    minHeight: Dp = 46.dp,
    fieldBackground: Color = Rengaine.colors.surface,
) {
    val colors = Rengaine.colors
    val type = Rengaine.type
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        isError -> colors.danger
        focused -> colors.accent
        else -> colors.line
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = type.fieldLabel, color = colors.muted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focused) Modifier.focusRing(colors.accentSoft, width = 3.dp, gap = 0.dp, radius = 10.dp) else Modifier),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = type.field.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { if (isError && errorText != null) error(errorText) },
                decorationBox = { inner ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = minHeight)
                            .background(fieldBackground, RengaineShapes.card)
                            .border(1.dp, borderColor, RengaineShapes.card)
                            .padding(start = 12.dp, end = if (trailing != null) 2.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, style = type.field, color = colors.faint)
                            }
                            inner()
                        }
                        trailing?.invoke()
                    }
                },
            )
        }
        when {
            isError && errorText != null -> Text(errorText, style = type.secondary, color = colors.danger)
            supporting != null -> supporting()
        }
    }
}

