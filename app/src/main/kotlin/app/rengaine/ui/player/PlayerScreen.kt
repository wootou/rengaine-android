package app.rengaine.ui.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.rengaine.R
import app.rengaine.playback.MediaItems
import app.rengaine.ui.components.formatDuration
import app.rengaine.ui.components.musicalKeyLabel
import app.rengaine.ui.components.FavoriteButton
import app.rengaine.data.repository.FavoriteKey
import app.rengaine.data.repository.FavoriteKind
import app.rengaine.ui.theme.Rengaine
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * Lecteur plein écran (capture `12`) : réduire ⌄, « EN LECTURE » et l'album,
 * file avec sa pastille ; fond flouté ; grande pochette ; titre, artistes ;
 * « 133 BPM · Do mineur (5A) » ; barre de progression avec temps écoulé et
 * durée ; répétition, précédente, lecture/pause à halo, suivante.
 *
 * Barre de position : forme d'onde de la piste (U12) quand le serveur la
 * donne. Pas encore de volume (D12). Le bouton ∞ change le réglage du
 * compte, comme sur le site.
 */
@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    waveformViewModel: WaveformViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val waveform by waveformViewModel.state.collectAsStateWithLifecycle()
    val infinite by viewModel.infiniteActive.collectAsStateWithLifecycle()
    val volumeBarShown by viewModel.volumeBarShown.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val type = Rengaine.type
    val context = LocalContext.current
    val notSavedText = stringResource(R.string.settings_not_saved)
    LaunchedEffect(Unit) { viewModel.notSaved.collect { Toast.makeText(context, notSavedText, Toast.LENGTH_SHORT).show() } }

    // File vidée pendant qu'on regarde : rien à montrer.
    LaunchedEffect(state.isConnected, state.hasQueue) {
        if (state.isConnected && !state.hasQueue) onClose()
    }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        state.coverUrl?.let {
            AsyncImage(
                model = MediaItems.withSize(it, "s"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.5f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.5f to colors.bg.copy(alpha = 0.55f), 1f to colors.bg),
            ),
        )

        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.player_close), tint = colors.text, modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.player_now_playing).uppercase(), style = type.overline, color = colors.muted)
                    Text(
                        state.album.orEmpty(),
                        style = type.bodyStrong,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                QueueButton(count = state.count, onClick = onOpenQueue)
            }

            Spacer(Modifier.weight(0.5f))
            Cover(
                state.coverUrl,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f),
            )
            Spacer(Modifier.weight(0.5f))

            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.title.orEmpty(), style = type.pageTitle, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(state.artist.orEmpty(), style = type.body, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    analysisLine(state.bpm, state.musicalKey, state.camelot)?.let {
                        Text(it, style = type.secondary, color = colors.muted)
                    }
                }
                // Cœur de la piste en cours (F9), à droite du titre comme sur le site.
                state.trackId?.let { id -> FavoriteButton(FavoriteKey(FavoriteKind.TRACK, id), fromServer = state.favorite) }
            }

            PositionBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                waveform = waveform,
                onSeek = viewModel::seekTo,
                modifier = Modifier.padding(top = 16.dp),
            )

            Controls(
                playing = state.isPlaying,
                repeatMode = state.repeatMode,
                hasNext = state.hasNext || state.repeatMode != Player.REPEAT_MODE_OFF,
                onRepeat = viewModel::cycleRepeat,
                onPrevious = viewModel::skipPrevious,
                onPlayPause = viewModel::togglePlayPause,
                onNext = viewModel::skipNext,
                infinite = infinite,
                onInfinite = viewModel::toggleInfinite,
                modifier = Modifier.padding(top = 12.dp),
            )

            if (volumeBarShown && state.maxVolume > state.minVolume) {
                VolumeRow(
                    volume = state.volume,
                    min = state.minVolume,
                    max = state.maxVolume,
                    muted = state.muted,
                    onVolume = viewModel::setVolume,
                    onMute = viewModel::toggleMute,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
            } else {
                Spacer(Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

/**
 * Volume (D12, capture `12`) : haut-parleur qui coupe et rétablit le son,
 * curseur braise. Il règle le **volume média d'Android** (choix du mainteneur
 * du 2026-10-25) : un seul volume, le même que les boutons du téléphone,
 * qui fait bouger le curseur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VolumeRow(
    volume: Int,
    min: Int,
    max: Int,
    muted: Boolean,
    onVolume: (Int) -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Rengaine.colors
    val silent = muted || volume <= min
    // Valeur suivie pendant le glissement, pour un curseur fluide malgré les crans.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: if (muted) min.toFloat() else volume.toFloat()
    val percent = ((shown - min) / (max - min) * 100).roundToInt()
    val volumeLabel = stringResource(R.string.player_volume)
    val volumeState = stringResource(R.string.player_volume_percent, percent)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMute) {
            Icon(
                if (silent) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(if (muted) R.string.player_unmute else R.string.player_mute),
                tint = colors.muted,
            )
        }
        Slider(
            value = shown,
            onValueChange = {
                dragging = it
                val step = it.roundToInt()
                if (step != volume) onVolume(step)
            },
            onValueChangeFinished = { dragging = null },
            valueRange = min.toFloat()..max.toFloat(),
            // Barre fine et pouce rond de la maquette, pas ceux de Material 3.
            thumb = {
                Box(Modifier.size(18.dp).clip(CircleShape).background(colors.accent))
            },
            track = { sliderState ->
                val range = sliderState.valueRange
                val fraction = ((sliderState.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    val y = size.height / 2
                    val r = size.height / 2
                    drawLine(colors.line, Offset(r, y), Offset(size.width - r, y), size.height, StrokeCap.Round)
                    drawLine(colors.accent, Offset(r, y), Offset(r + (size.width - 2 * r) * fraction, y), size.height, StrokeCap.Round)
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp)
                .semantics {
                    contentDescription = volumeLabel
                    stateDescription = volumeState
                },
        )
    }
}

/** « 133 BPM · Do mineur (5A) » ; `null` si l'analyse n'a pas encore tourné. */
@Composable
private fun analysisLine(bpm: Double?, key: String?, camelot: String?): String? {
    val parts = buildList {
        bpm?.let { add(stringResource(R.string.player_bpm, it.roundToInt())) }
        val keyLabel = musicalKeyLabel(key)
        when {
            keyLabel != null && camelot != null -> add("$keyLabel ($camelot)")
            keyLabel != null -> add(keyLabel)
            camelot != null -> add(camelot)
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
internal fun Controls(
    playing: Boolean,
    repeatMode: Int,
    hasNext: Boolean,
    onRepeat: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    infinite: Boolean,
    onInfinite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Rengaine.colors
    val repeatLabel = stringResource(
        when (repeatMode) {
            Player.REPEAT_MODE_ALL -> R.string.player_repeat_all
            Player.REPEAT_MODE_ONE -> R.string.player_repeat_one
            else -> R.string.player_repeat_off
        },
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onRepeat, modifier = Modifier.semantics { stateDescription = repeatLabel }) {
            Icon(
                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = stringResource(R.string.player_repeat),
                tint = if (repeatMode == Player.REPEAT_MODE_OFF) colors.muted else colors.accent,
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.SkipPrevious, stringResource(R.string.player_previous), tint = colors.text, modifier = Modifier.size(36.dp))
        }
        val playLabel = stringResource(if (playing) R.string.player_pause else R.string.player_play)
        Box(
            modifier = Modifier
                .size(72.dp)
                // Halo braise de la capture `12`.
                .shadow(elevation = 18.dp, shape = CircleShape, ambientColor = colors.accent, spotColor = colors.accent)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(onClickLabel = playLabel, onClick = onPlayPause)
                .semantics { contentDescription = playLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(38.dp),
            )
        }
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(
                Icons.Filled.SkipNext,
                stringResource(R.string.player_next),
                tint = if (hasNext) colors.text else colors.faint,
                modifier = Modifier.size(36.dp),
            )
        }
        // Mode infini (F5) : pastille braise quand il est actif (capture `12`).
        val infiniteState = stringResource(if (infinite) R.string.player_infinite_on else R.string.player_infinite_off)
        IconButton(
            onClick = onInfinite,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (infinite) colors.accentSoft else Color.Transparent)
                .semantics { stateDescription = infiniteState },
        ) {
            Icon(
                Icons.Filled.AllInclusive,
                contentDescription = stringResource(R.string.player_infinite),
                tint = if (infinite) colors.accent else colors.muted,
            )
        }
    }
}
