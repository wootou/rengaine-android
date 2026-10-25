package app.rengaine.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.rengaine.R
import app.rengaine.ui.components.formatDuration
import app.rengaine.ui.theme.Rengaine

/** Hauteur de la forme d'onde dans le lecteur plein écran du téléphone, comme le site. */
private val WAVE_HEIGHT = 64.dp

/**
 * Barre de position du lecteur plein écran : la forme d'onde de la piste
 * (U12) quand on l'a, sinon une barre simple. On suit le doigt pendant le
 * glissement et on ne se déplace dans la piste qu'au relâchement (pas de
 * rafale de requêtes) ; un simple toucher déplace aussi, comme le site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionBar(
    positionMs: Long,
    durationMs: Long,
    waveform: WaveformState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Rengaine.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shown = if (dragging != null) (fraction * duration).toLong() else positionMs
    val elapsed = formatDuration(shown)
    val total = formatDuration(durationMs)
    val positionLabel = stringResource(R.string.player_position, elapsed, total)
    val name = stringResource(R.string.player_seek)

    Column(modifier.fillMaxWidth()) {
        val sliderModifier = Modifier.semantics {
            contentDescription = name
            stateDescription = positionLabel
        }
        val onFinished = {
            dragging?.let { onSeek((it * duration).toLong()) }
            dragging = null
        }
        if (waveform is WaveformState.Plain) {
            Slider(
                value = fraction,
                onValueChange = { dragging = it },
                onValueChangeFinished = onFinished,
                enabled = durationMs > 0,
                colors = SliderDefaults.colors(
                    thumbColor = colors.text,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.line,
                ),
                modifier = sliderModifier,
            )
        } else {
            val bars = (waveform as? WaveformState.Bars)?.heights
            Slider(
                value = fraction,
                onValueChange = { dragging = it },
                onValueChangeFinished = onFinished,
                enabled = durationMs > 0,
                // Tête de lecture : trait de 2 dp couleur du texte, au bord de
                // la partie jouée, et rond de 12 dp au milieu (`.expanded` du
                // site), avec l'ombre --shadow-sm du thème.
                thumb = {
                    Box(Modifier.size(width = 2.dp, height = WAVE_HEIGHT).background(colors.text), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .requiredSize(12.dp)
                                .dropShadow(CircleShape, Shadow(radius = 14.dp, color = if (colors.isDark) Color(0x59000000) else Color(0x1F3C2814), offset = DpOffset(0.dp, 4.dp)))
                                .background(colors.text, CircleShape),
                        )
                    }
                },
                track = { state -> WaveTrack(bars, state, played = colors.accent, unplayed = colors.faint) },
                modifier = sliderModifier,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(elapsed, style = Rengaine.type.numeric, color = colors.muted, modifier = Modifier.weight(1f))
            Text(total, style = Rengaine.type.numeric, color = colors.muted)
        }
    }
}

/**
 * Les barres sont tracées une fois par piste et par taille ; seule la
 * découpe jouée / non jouée suit la position, lue au moment du dessin :
 * l'avance de la lecture redessine sans recomposer ni recalculer la forme
 * (le redessin complet à chaque avance faisait sauter le lecteur web).
 * Pendant le chargement ([bars] nul), la place est gardée, rien n'est dessiné.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveTrack(bars: FloatArray?, state: SliderState, played: Color, unplayed: Color) {
    val draw = remember(bars, played, unplayed) {
        Modifier.drawWithCache {
            val path = Path()
            if (bars != null && bars.isNotEmpty()) {
                val step = size.width / bars.size
                val h = size.height
                bars.forEachIndexed { i, v ->
                    if (v > 0f) {
                        // Barres symétriques, 70 % de leur case, au moins 2 % de haut.
                        val bh = maxOf(0.02f, v) * h
                        path.addRect(Rect((i + 0.15f) * step, (h - bh) / 2, (i + 0.85f) * step, (h + bh) / 2))
                    }
                }
            }
            onDrawBehind {
                val x = state.value.coerceIn(0f, 1f) * size.width
                clipRect(right = x) { drawPath(path, played) }
                clipRect(left = x) { drawPath(path, unplayed) }
            }
        }
    }
    Spacer(Modifier.fillMaxWidth().height(WAVE_HEIGHT).then(draw))
}
