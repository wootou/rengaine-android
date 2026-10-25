package app.rengaine.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rengaine.ui.theme.Rengaine
import kotlin.math.hypot

/**
 * Monogramme Rengaine (U4) : carré arrondi braise, sillon en arc qui revient
 * sur lui-même avec sa flèche, point central. Tracés repris tels quels du
 * logo du site (viewBox 32 × 32).
 */
@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    halo: Boolean = false,
) {
    val colors = Rengaine.colors
    val vector = remember(colors.accent, colors.onAccent) { logoVector(colors.accent, colors.onAccent) }
    val haloColor = colors.accent
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (halo) {
                    // drop-shadow(0 8px 20px accent 35 %) du site.
                    Modifier.drawBehind {
                        val center = Offset(this.size.width / 2, this.size.height / 2 + 8.dp.toPx())
                        val radius = this.size.minDimension / 2 + 20.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to haloColor.copy(alpha = 0.35f),
                                0.55f to haloColor.copy(alpha = 0.18f),
                                1f to Color.Transparent,
                                center = center,
                                radius = radius,
                            ),
                            radius = radius,
                            center = center,
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(imageVector = vector, contentDescription = null, modifier = Modifier.size(size))
    }
}

private fun logoVector(accent: Color, onAccent: Color): ImageVector =
    ImageVector.Builder(
        name = "LogoMark",
        defaultWidth = 32.dp,
        defaultHeight = 32.dp,
        viewportWidth = 32f,
        viewportHeight = 32f,
    ).apply {
        path(fill = SolidColor(accent)) {
            moveTo(9f, 0f)
            lineTo(23f, 0f)
            arcTo(9f, 9f, 0f, false, true, 32f, 9f)
            lineTo(32f, 23f)
            arcTo(9f, 9f, 0f, false, true, 23f, 32f)
            lineTo(9f, 32f)
            arcTo(9f, 9f, 0f, false, true, 0f, 23f)
            lineTo(0f, 9f)
            arcTo(9f, 9f, 0f, false, true, 9f, 0f)
            close()
        }
        addPath(
            pathData = addPathNodes("M24.08 13.06A8.6 8.6 0 1 1 14.51 7.53"),
            fill = null,
            stroke = SolidColor(onAccent),
            strokeLineWidth = 3f,
            strokeLineCap = StrokeCap.Round,
        )
        addPath(pathData = addPathNodes("M19.04 6.73L13.88 3.99l1.25 7.09z"), fill = SolidColor(onAccent))
        addPath(
            pathData = addPathNodes("M16 13.3a2.7 2.7 0 1 1 0 5.4a2.7 2.7 0 1 1 0-5.4z"),
            fill = SolidColor(onAccent),
        )
    }.build()

/**
 * Motif « sillons » (U4) : cercles concentriques en fondu, derrière les
 * écrans de connexion. Reprend le `repeating-radial-gradient` du site :
 * un filet tous les 23 px, opacité 0,6, masque radial plein jusqu'à 5 % puis
 * nul à 55 % d'une boîte 1,4 fois plus grande que l'écran.
 */
@Composable
fun GroovesBackground(modifier: Modifier = Modifier) {
    val line = Rengaine.colors.line
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val reach = 0.7f * hypot(size.width, size.height)
        val fadeStart = 0.05f * reach
        val fadeEnd = 0.55f * reach
        val step = 23.dp.toPx()
        val stroke = Stroke(width = 1.dp.toPx())
        var radius = 22.5f.dp.toPx()
        while (radius < fadeEnd) {
            val mask = when {
                radius <= fadeStart -> 1f
                else -> 1f - (radius - fadeStart) / (fadeEnd - fadeStart)
            }
            drawCircle(color = line.copy(alpha = 0.6f * mask), radius = radius, center = center, style = stroke)
            radius += step
        }
    }
}
