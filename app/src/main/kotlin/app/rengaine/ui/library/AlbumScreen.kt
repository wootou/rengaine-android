package app.rengaine.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.R
import app.rengaine.data.repository.FavoriteKey
import app.rengaine.data.repository.FavoriteKind
import app.rengaine.ui.components.FavoriteButton
import app.rengaine.data.remote.dto.AlbumDetailDto
import app.rengaine.data.remote.dto.AlbumKind
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.playback.MediaItems
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.TrackAction
import app.rengaine.ui.components.TrackRow
import app.rengaine.ui.playlists.AddToPlaylistDialog
import app.rengaine.ui.components.formatDuration
import app.rengaine.ui.components.formatLabel
import app.rengaine.ui.player.Cover
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes
import coil3.compose.AsyncImage

/**
 * Page d'un album (capture `07`) : fond flouté tiré de la pochette, pochette
 * centrée, type, titre, « artiste · année · pistes · durée » et format,
 * genres, actions, pistes groupées par disque.
 */
@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    var addTracks by remember { mutableStateOf<List<Long>?>(null) }
    addTracks?.let { ids -> AddToPlaylistDialog(trackIds = ids, onDismiss = { addTracks = null }) }

    when (val s = state) {
        AlbumUiState.Loading -> Box(Modifier.fillMaxSize().padding(contentPadding)) { LoadingState() }
        AlbumUiState.Failed -> Box(Modifier.fillMaxSize().padding(contentPadding)) { ErrorState(onRetry = viewModel::load) }
        is AlbumUiState.Ready -> {
            val album = s.album
            val playNextLabel = stringResource(R.string.action_play_next)
            val addQueueLabel = stringResource(R.string.action_add_to_queue)
            val addPlaylistLabel = stringResource(R.string.action_add_to_playlist)
            val multiDisc = album.tracks.map { it.discNumber }.distinct().size > 1
            LazyColumn(
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Header(album, topPadding = contentPadding.calculateTopPadding(), onBack = onBack, onAddToPlaylist = { addTracks = viewModel.trackIds() }, viewModel = viewModel)
                }
                var lastDisc = -1
                album.tracks.forEach { track ->
                    if (multiDisc && track.discNumber != lastDisc) {
                        lastDisc = track.discNumber
                        val disc = track.discNumber
                        item(key = "disc-$disc") { DiscHeader(disc) }
                    }
                    item(key = track.id) {
                        TrackRow(
                            track = track,
                            isCurrent = player.trackId == track.id,
                            isPlaying = player.isPlaying,
                            subtitle = track.artistCredit.takeIf { album.isCompilation || it != album.artistName },
                            onPlay = { viewModel.play(track) },
                            actions = listOf(
                                TrackAction(playNextLabel, Icons.AutoMirrored.Filled.PlaylistPlay) { viewModel.playNext(listOf(track)) },
                                TrackAction(addQueueLabel, Icons.AutoMirrored.Filled.PlaylistAdd) { viewModel.addToQueue(listOf(track)) },
                                TrackAction(addPlaylistLabel, Icons.Filled.Add) { addTracks = listOf(track.id) },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(album: AlbumDetailDto, topPadding: Dp, onBack: () -> Unit, onAddToPlaylist: () -> Unit, viewModel: AlbumViewModel) {
    val colors = Rengaine.colors
    val type = Rengaine.type
    val trackCount = pluralStringResource(R.plurals.track_count, album.trackCount, album.trackCount)
    val meta = listOfNotNull(
        album.artistName.takeIf { it.isNotBlank() },
        album.year?.toString(),
        trackCount,
        formatDuration(album.durationMs).takeIf { album.durationMs > 0 },
    ).joinToString(" · ")
    val format = formatLabel(album.tracks.firstOrNull()?.formats?.firstOrNull()?.format)
    val overline = when {
        album.kind == AlbumKind.LOOSE -> stringResource(R.string.album_kind_loose)
        album.isCompilation -> stringResource(R.string.album_kind_compilation)
        else -> stringResource(R.string.album_kind_album)
    }

    Box(Modifier.fillMaxWidth()) {
        // Fond : la pochette très floutée, qui se fond dans le fond de page.
        if (album.coverUrl != null) {
            AsyncImage(
                model = MediaItems.withSize(album.coverUrl, "s"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(60.dp).alpha(0.55f),
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.55f to colors.bg.copy(alpha = 0.6f), 1f to colors.bg),
            ),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.text)
                }
            }
            Cover(album.coverUrl, modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(0.66f).aspectRatio(1f))
            Spacer(Modifier.size(20.dp))
            Text(overline.uppercase(), style = type.overline, color = colors.muted)
            Text(
                album.title,
                style = type.pageTitle,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).semantics { heading() },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                Text(meta, style = type.secondary, color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
                if (format != null) FormatBadge(format)
            }
            if (album.genres.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    album.genres.forEach { genre ->
                        Text(
                            genre.name,
                            style = type.secondary,
                            color = colors.text,
                            modifier = Modifier
                                .clip(RengaineShapes.pill)
                                .background(colors.surface)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Actions(viewModel, album, onAddToPlaylist)
        }
    }
}

@Composable
private fun Actions(viewModel: AlbumViewModel, album: AlbumDetailDto, onAddToPlaylist: () -> Unit) {
    val colors = Rengaine.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { viewModel.playNext() }) {
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.action_play_next), tint = colors.text)
        }
        IconButton(onClick = { viewModel.addToQueue() }) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.action_add_to_queue), tint = colors.text)
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(Icons.Filled.Add, stringResource(R.string.action_add_to_playlist), tint = colors.text)
        }
        FavoriteButton(FavoriteKey(FavoriteKind.ALBUM, album.id), fromServer = album.favorite)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = viewModel::shuffle) {
            Icon(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle), tint = colors.text)
        }
        Spacer(Modifier.size(8.dp))
        val playLabel = stringResource(R.string.action_play_album)
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(onClickLabel = playLabel) { viewModel.play() }
                .semantics { contentDescription = playLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun FormatBadge(label: String) {
    val colors = Rengaine.colors
    Text(
        label,
        style = Rengaine.type.small,
        color = colors.muted,
        modifier = Modifier
            .border(1.dp, colors.line, RengaineShapes.cover)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun DiscHeader(disc: Int) {
    Text(
        stringResource(R.string.album_disc, disc).uppercase(),
        style = Rengaine.type.overline,
        color = Rengaine.colors.muted,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp).semantics { heading() },
    )
}
