package app.rengaine.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Anneau de focus dessiné **autour** de l'élément, sans changer sa taille
 * ni décaler la mise en page. [radius] `null` = pilule (demi-hauteur).
 *
 * - Champs : halo `accent-soft` de 3, collé (`box-shadow: 0 0 0 3px`).
 * - Boutons : anneau braise de 2 avec 3 d'écart (planche « États »).
 */
fun Modifier.focusRing(color: Color, width: Dp, gap: Dp, radius: Dp?): Modifier = drawBehind {
    val w = width.toPx()
    val offset = gap.toPx() + w / 2
    val base = radius?.toPx() ?: (size.height / 2)
    drawRoundRect(
        color = color,
        topLeft = Offset(-offset, -offset),
        size = Size(size.width + 2 * offset, size.height + 2 * offset),
        cornerRadius = CornerRadius(base + offset),
        style = Stroke(w),
    )
}
