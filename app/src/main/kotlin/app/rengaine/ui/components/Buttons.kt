package app.rengaine.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/** [Danger] : action destructrice (« Supprimer mon compte… »), en rouge comme sur le site. */
enum class ButtonKind { Primary, Secondary, Ghost, Danger }

/**
 * Bouton pilule (U6). États de la planche « États » de la toile : appuyé =
 * échelle 0,97 et teinte appuyée ; désactivé = opacité 0,4 ; focus clavier =
 * anneau braise ; chargement = indicateur à la place de l'icône, le libellé
 * reste lisible et le bouton ne réagit plus.
 */
@Composable
fun RengaineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 44.dp,
    textStyle: TextStyle = Rengaine.type.button,
    loadingDescription: String? = null,
) {
    val colors = Rengaine.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val active = enabled && !loading

    val (container, content, border) = when (kind) {
        ButtonKind.Primary -> Triple(
            if (pressed) colors.accentHover else colors.accent,
            colors.onAccent,
            null,
        )
        ButtonKind.Secondary -> Triple(
            if (pressed) colors.hover else colors.raised,
            colors.text,
            BorderStroke(1.dp, colors.line),
        )
        ButtonKind.Ghost -> Triple(
            if (pressed) colors.hover else Color.Transparent,
            colors.text,
            null,
        )
        ButtonKind.Danger -> Triple(
            if (pressed) colors.danger.copy(alpha = 0.85f) else colors.danger,
            colors.onDanger,
            null,
        )
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = height)
            .scale(if (pressed && active) 0.97f else 1f)
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (focused) Modifier.focusRing(colors.accent, width = 2.dp, gap = 3.dp, radius = null) else Modifier,
            )
            .background(container, RengaineShapes.pill)
            .then(if (border != null) Modifier.border(border, RengaineShapes.pill) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = active,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (loading && loadingDescription != null) {
                    Modifier.semantics { stateDescription = loadingDescription }
                } else {
                    Modifier
                },
            )
            .padding(PaddingValues(horizontal = 18.dp, vertical = 8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = content,
                    strokeWidth = 2.dp,
                )
                icon != null -> Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            }
            Text(text = text, style = textStyle, color = content, textAlign = TextAlign.Center)
        }
    }
}

/** Lien d'action (`.link-btn` du site) : texte braise, zone tactile de 44. */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Rengaine.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Rengaine.type.bodyStrong,
            color = if (pressed) colors.accentHover else colors.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
        )
    }
}
