package app.rengaine.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rengaine.R
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.ui.player.Cover
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/** Une entrée du menu ⋮ d'une piste. */
data class TrackAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Ligne de piste commune (album, playlist, favoris) : numéro ou pochette,
 * titre (braise si en cours), sous-titre, durée, menu ⋮. L'égaliseur
 * remplace le numéro ou la durée pendant la lecture. Une piste
 * indisponible est grisée et ne se lit pas (F2).
 */
@Composable
fun TrackRow(
    track: TrackDto,
    isCurrent: Boolean,
    isPlaying: Boolean,
    subtitle: String?,
    onPlay: () -> Unit,
    actions: List<TrackAction>,
    showCover: Boolean = false,
) {
    val colors = Rengaine.colors
    val type = Rengaine.type
    var menu by remember { mutableStateOf(false) }
    val enabled = track.available
    val titleColor = when {
        !enabled -> colors.faint
        isCurrent -> colors.accent
        else -> colors.text
    }
    val equalizer = isCurrent && isPlaying
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onPlay)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCover) {
            Cover(track.coverUrl, modifier = Modifier.padding(end = 4.dp).size(48.dp))
        } else {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (equalizer) {
                    Icon(Icons.Filled.Equalizer, stringResource(R.string.track_now_playing), tint = colors.accent, modifier = Modifier.size(20.dp))
                } else {
                    Text(track.trackNumber?.toString().orEmpty(), style = type.numeric, color = if (isCurrent) colors.accent else colors.muted)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(track.title, style = type.body, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                !enabled -> Text(stringResource(R.string.track_unavailable), style = type.secondary, color = colors.faint, maxLines = 1)
                !subtitle.isNullOrBlank() ->
                    Text(subtitle, style = type.secondary, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showCover && equalizer) {
            Icon(Icons.Filled.Equalizer, stringResource(R.string.track_now_playing), tint = colors.accent, modifier = Modifier.padding(horizontal = 8.dp).size(20.dp))
        } else {
            Text(
                formatDuration(track.durationMs),
                style = type.numeric,
                color = if (enabled) colors.muted else colors.faint,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (actions.isNotEmpty()) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.track_actions, track.title), tint = colors.muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.raised, shape = RengaineShapes.card) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label, style = type.body, color = colors.text) },
                            leadingIcon = { Icon(action.icon, null, tint = colors.muted) },
                            onClick = {
                                menu = false
                                action.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Pochette d'une playlist : une seule image, ou une mosaïque de quatre (capture `14`). */
@Composable
fun MosaicCover(coverUrls: List<String>, modifier: Modifier = Modifier) {
    if (coverUrls.size < 4) {
        Cover(coverUrls.firstOrNull(), modifier = modifier)
        return
    }
    Column(modifier.clip(RengaineShapes.cover)) {
        Row(Modifier.weight(1f)) {
            Cover(coverUrls[0], Modifier.weight(1f).fillMaxHeight())
            Cover(coverUrls[1], Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.weight(1f)) {
            Cover(coverUrls[2], Modifier.weight(1f).fillMaxHeight())
            Cover(coverUrls[3], Modifier.weight(1f).fillMaxHeight())
        }
    }
}
