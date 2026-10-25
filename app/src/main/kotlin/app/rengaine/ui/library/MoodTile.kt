package app.rengaine.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.rengaine.R
import app.rengaine.data.remote.dto.MoodDto
import app.rengaine.ui.theme.BricolageDisplay
import app.rengaine.ui.theme.Rengaine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Tuiles « Ambiances » d'Explorer, d'après la maquette du site (transmise
 * par l'équipe du serveur, `ambiances.css`) : 2 colonnes sur téléphone, 0,7 rem
 * d'écart ; chaque tuile a son dégradé et son motif, mêmes couleurs en
 * clair et en sombre.
 */
@Composable
fun MoodGrid(moods: List<MoodDto>, onStart: (MoodDto) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        moods.chunked(2).forEach { row ->
            // Même hauteur pour les deux tuiles d'une rangée, comme une grille CSS.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                row.forEach { mood -> MoodTile(mood, onStart = { onStart(mood) }, modifier = Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val GAP = 11.dp // 0,7 rem
private val TILE_SHAPE = RoundedCornerShape(16.dp) // --r-l

/** Couleurs A et B du dégradé (160°) et motif de chaque ambiance, par identifiant de l'API. */
private enum class MoodLook(val a: Color, val b: Color) {
    CALME(Color(0xFF25495A), Color(0xFF14303B)),
    CONCENTRATION(Color(0xFF28503F), Color(0xFF15302A)),
    ENERGIQUE(Color(0xFF9A4016), Color(0xFF5A210E)),
    DANSER(Color(0xFF7A2A5E), Color(0xFF43163A)),
    AUTRE(Color(0xFF3A332D), Color(0xFF26211D));

    companion object {
        fun of(id: String) = when (id) {
            "calme" -> CALME
            "concentration" -> CONCENTRATION
            "energique" -> ENERGIQUE
            "danser" -> DANSER
            else -> AUTRE
        }
    }
}

private val INK = Color(0xFF1A0D04)

@Composable
private fun MoodTile(mood: MoodDto, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val look = MoodLook.of(mood.id)
    val dark = Rengaine.colors.isDark
    val label = stringResource(R.string.explore_mood_play, mood.name)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val descStyle = TextStyle(fontSize = 12.8.sp, lineHeight = 1.4.em, color = Color.White.copy(alpha = 0.84f))
    // 26 caractères au plus (`max-width: 26ch`) : largeur de 26 « 0 » dans ce style.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxDesc = remember(descStyle, density) {
        with(density) { measurer.measure("0".repeat(26), descStyle).size.width.toDp() }
    }

    Box(
        modifier
            .heightIn(min = 152.dp)
            // --shadow-sm du site : 0 4px 14px, plus marquée en sombre.
            .dropShadow(TILE_SHAPE, Shadow(radius = 14.dp, color = if (dark) Color(0x59000000) else Color(0x1F3C2814), offset = DpOffset(0.dp, 4.dp)))
            .clip(TILE_SHAPE)
            .drawWithCache {
                val brush = cssLinearGradient(160f, look.a, look.b, size.width, size.height)
                onDrawBehind {
                    drawRect(brush)
                    drawPattern(look)
                }
            }
            .then(if (focused) Modifier.border(3.dp, Color.White, TILE_SHAPE) else Modifier)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(color = Color.White), role = Role.Button, onClickLabel = label, onClick = onStart)
            // padding: 1rem .9rem .85rem
            .padding(start = 14.4.dp, end = 14.4.dp, top = 16.dp, bottom = 13.6.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                mood.name,
                style = TextStyle(
                    fontFamily = BricolageDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.2.sp, // 1,2 rem
                    lineHeight = 1.05.em,
                    letterSpacing = (-0.02).em,
                    color = Color.White,
                ),
            )
            if (mood.description.isNotBlank()) {
                Text(
                    mood.description,
                    style = descStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = maxDesc),
                )
            }
            // .mood-foot : margin-top auto, padding-top 1rem.
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralStringResource(R.plurals.track_count, mood.trackCount, mood.trackCount),
                    style = TextStyle(fontSize = 13.1.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f)),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(44.dp).dropShadow(CircleShape, Shadow(radius = 18.dp, color = Color(0x4D000000), offset = DpOffset(0.dp, 6.dp))).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = INK, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * `linear-gradient(<angle>deg, a, b)` du CSS : 0° vers le haut, 90° vers la
 * droite ; la ligne du dégradé est assez longue pour que les coins
 * reçoivent exactement a et b.
 */
private fun cssLinearGradient(angleDeg: Float, a: Color, b: Color, w: Float, h: Float): Brush {
    val rad = angleDeg * PI.toFloat() / 180f
    val dx = sin(rad)
    val dy = -cos(rad)
    val half = (abs(w * dx) + abs(h * dy)) / 2f
    val c = Offset(w / 2f, h / 2f)
    return Brush.linearGradient(listOf(a, b), start = c - Offset(dx, dy) * half, end = c + Offset(dx, dy) * half)
}

/** Motifs de `ambiances.css`, au-dessus du dégradé ; 1 px CSS = 1 dp. */
private fun DrawScope.drawPattern(look: MoodLook) {
    val w = size.width
    val h = size.height
    val px = density
    when (look) {
        MoodLook.CALME -> {
            // radial-gradient(130% 70% at 100% Y, blanc 0 → r) : ellipses pleines
            // centrées au bord droit, en bas : trois vagues pleines en bas à droite
            // (centres corrigés le 2026-10-25 : 100 / 115 / 130 %).
            listOf(Triple(1.00f, 0.34f, 0.13f), Triple(1.15f, 0.52f, 0.09f), Triple(1.30f, 0.70f, 0.06f)).forEach { (cy, r, alpha) ->
                val rx = 1.3f * w * r
                val ry = 0.7f * h * r
                drawOval(Color.White.copy(alpha = alpha), topLeft = Offset(w - rx, cy * h - ry), size = Size(2 * rx, 2 * ry))
            }
        }
        MoodLook.CONCENTRATION -> {
            // repeating-linear-gradient(0deg, blanc 0 1px, vide 1px 9px), 42 % de droite :
            // 0deg part du bas.
            val left = w * 0.58f
            var y = h - 1f * px
            while (y > -1f * px) {
                drawRect(Color.White.copy(alpha = 0.08f), topLeft = Offset(left, y), size = Size(w - left, 1f * px))
                y -= 9f * px
            }
        }
        MoodLook.ENERGIQUE -> {
            // repeating-linear-gradient(-58deg, blanc 0 7px, vide 7px 20px), 48 % de droite.
            // Comme CSS : la ligne du dégradé passe par le centre de la zone et
            // commence à la perpendiculaire qui touche le coin de départ.
            val left = w * 0.52f
            val bw = w - left
            val rad = -58f * PI.toFloat() / 180f
            val dx = sin(rad)
            val dy = -cos(rad)
            val length = abs(bw * dx) + abs(h * dy)
            val center = Offset(left + bw / 2f, h / 2f)
            val reach = hypot(bw, h)
            clipRect(left = left) {
                // Axe x tourné le long du dégradé : chaque rayure est un rectangle debout.
                rotate(degrees = atan2(dy, dx) * 180f / PI.toFloat(), pivot = center) {
                    var t = -length / 2f
                    while (t < length / 2f) {
                        drawRect(Color.White.copy(alpha = 0.11f), topLeft = Offset(center.x + t, center.y - reach), size = Size(7f * px, 2 * reach))
                        t += 20f * px
                    }
                }
            }
        }
        MoodLook.DANSER -> {
            // repeating-radial-gradient(circle at 108% 112%, blanc 0 2px, vide 2px 13px).
            val c = Offset(w * 1.08f, h * 1.12f)
            val reach = hypot(c.x, c.y)
            drawCircle(Color.White.copy(alpha = 0.11f), radius = 2f * px, center = c)
            var r = 13f * px
            while (r < reach) {
                drawCircle(Color.White.copy(alpha = 0.11f), radius = r + 1f * px, center = c, style = Stroke(width = 2f * px))
                r += 13f * px
            }
        }
        MoodLook.AUTRE -> Unit
    }
}
