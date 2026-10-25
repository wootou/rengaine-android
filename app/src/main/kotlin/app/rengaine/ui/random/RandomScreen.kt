package app.rengaine.ui.random

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.R
import app.rengaine.data.remote.dto.AlbumSummaryDto
import app.rengaine.data.remote.dto.GenreDto
import app.rengaine.ui.components.ButtonKind
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.MessageState
import app.rengaine.ui.components.RengaineButton
import app.rengaine.ui.components.SelectOption
import app.rengaine.ui.components.SelectPill
import app.rengaine.ui.components.TrackAction
import app.rengaine.ui.components.TrackRow
import app.rengaine.ui.components.formatDuration
import app.rengaine.ui.player.Cover
import app.rengaine.ui.playlists.AddToPlaylistDialog
import app.rengaine.ui.theme.Rengaine

/**
 * Aléatoire (capture `10`, libellés du site en ligne) : pile de pochettes,
 * « Aléatoire », « 100 pistes · 5:51:49 » ; « Générer une nouvelle
 * sélection », « Tout écouter », « Ajouter à la file » ; nombre de pistes ;
 * « Sélection (résumé) » et son panneau de genres à plat, chacun dépliable
 * sur ses albums (chargés à la demande) ; puis les pistes tirées, sans
 * en-têtes de disque.
 */
@Composable
fun RandomScreen(contentPadding: PaddingValues, viewModel: RandomViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    var addTracks by remember { mutableStateOf<List<Long>?>(null) }
    val playNextLabel = stringResource(R.string.action_play_next)
    val addQueueLabel = stringResource(R.string.action_add_to_queue)
    val addPlaylistLabel = stringResource(R.string.action_add_to_playlist)

    LazyColumn(
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { Header(state) }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RengaineButton(
                    text = stringResource(R.string.random_new_selection),
                    onClick = viewModel::draw,
                    icon = Icons.Filled.Shuffle,
                    loading = state.drawing,
                    enabled = state.genres.isNotEmpty(),
                    height = 52.dp,
                    textStyle = Rengaine.type.buttonLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RengaineButton(
                        text = stringResource(R.string.random_play_all),
                        onClick = viewModel::playAll,
                        kind = ButtonKind.Secondary,
                        icon = Icons.Filled.PlayArrow,
                        enabled = state.tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                    RengaineButton(
                        text = stringResource(R.string.action_add_to_queue),
                        onClick = viewModel::addAll,
                        kind = ButtonKind.Secondary,
                        icon = Icons.Filled.Add,
                        enabled = state.tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Text(stringResource(R.string.random_count), style = Rengaine.type.body, color = colors.text)
                    SelectPill(
                        options = RandomUiState.COUNTS.map { SelectOption(it, it.toString()) },
                        selected = state.count,
                        onSelect = viewModel::setCount,
                        label = stringResource(R.string.random_count),
                        field = true,
                        fill = false,
                    )
                }
            }
        }
        when {
            state.genresLoading -> item { LoadingState() }
            state.genresFailed -> item { ErrorState(onRetry = viewModel::retry) }
            else -> {
                item(key = "toggle") { PanelToggle(state, onClick = viewModel::togglePanel) }
                if (state.panelOpen) selectionPanel(state, viewModel)
                when {
                    state.noGenre -> item { Notice(stringResource(R.string.random_no_genre)) }
                    state.drawFailed -> item { Notice(stringResource(R.string.home_random_failed)) }
                    state.empty -> item { Notice(stringResource(R.string.random_empty)) }
                }
                if (state.excludedFull) item { Notice(stringResource(R.string.random_excluded_full)) }
                if (state.tracks.isNotEmpty()) item(key = "tracks-gap") { Box(Modifier.size(12.dp)) }
                items(state.tracks, key = { "t-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = player.trackId == track.id,
                        isPlaying = player.isPlaying,
                        subtitle = listOfNotNull(track.artistCredit.takeIf { it.isNotBlank() }, track.album?.title).joinToString(" · "),
                        onPlay = { viewModel.play(track) },
                        showCover = true,
                        actions = listOf(
                            TrackAction(playNextLabel, Icons.AutoMirrored.Filled.PlaylistPlay) { viewModel.playNext(track) },
                            TrackAction(addQueueLabel, Icons.AutoMirrored.Filled.PlaylistAdd) { viewModel.addToQueue(track) },
                            TrackAction(addPlaylistLabel, Icons.Filled.Add) { addTracks = listOf(track.id) },
                        ),
                    )
                }
            }
        }
    }
    addTracks?.let { ids -> AddToPlaylistDialog(trackIds = ids, onDismiss = { addTracks = null }) }
}

/** Pile de trois pochettes, titre, « 100 pistes · 5:51:49 ». */
@Composable
private fun Header(state: RandomUiState) {
    val colors = Rengaine.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        val covers = state.tracks.mapNotNull { it.coverUrl }.distinct().take(3)
        Box(Modifier.size(width = 96.dp, height = 88.dp)) {
            covers.reversed().forEachIndexed { i, url ->
                val depth = covers.size - 1 - i
                Cover(
                    url,
                    modifier = Modifier
                        .offset(x = (depth * 10).dp, y = (depth * -6).dp)
                        .rotate(if (depth == 0) 0f else depth * 4f)
                        .size(72.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.tab_random), style = Rengaine.type.pageTitle, color = colors.text, modifier = Modifier.semantics { heading() })
            if (state.tracks.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.track_count, state.tracks.size, state.tracks.size) + " · " + formatDuration(state.durationMs),
                    style = Rengaine.type.body,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun PanelToggle(state: RandomUiState, onClick: () -> Unit) {
    val colors = Rengaine.colors
    FlowRow(
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(if (state.panelOpen) R.string.random_hide_selection else R.string.random_show_selection),
            style = Rengaine.type.bodyStrong,
            color = colors.accent,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Text("(${selectionSummary(state)})", style = Rengaine.type.secondary, color = colors.muted, modifier = Modifier.align(Alignment.CenterVertically))
    }
}

/** « Tous les genres » ou les genres cochés, puis « · 2 albums de côté » (site). */
@Composable
private fun selectionSummary(state: RandomUiState): String {
    val choice = state.choice
    val genres = if (choice.allChecked) {
        stringResource(R.string.random_all_genres)
    } else {
        state.genres.filter { it.id in choice.checked }.joinToString(", ") { it.name }
    }
    val excluded = choice.excludedAlbums.size
    return if (excluded == 0) genres else genres + " · " + pluralStringResource(R.plurals.random_albums_aside, excluded, excluded)
}

/** Panneau : Tout cocher / Tout décocher, aide, genres et, dépliés, leurs albums. */
private fun LazyListScope.selectionPanel(state: RandomUiState, viewModel: RandomViewModel) {
    item(key = "panel-top") {
        val colors = Rengaine.colors
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(colors.surface)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PanelLink(stringResource(R.string.random_check_all), viewModel::checkAll)
                PanelLink(stringResource(R.string.random_uncheck_all), viewModel::uncheckAll)
            }
            Text(stringResource(R.string.random_selection_help), style = Rengaine.type.secondary, color = colors.muted, modifier = Modifier.padding(vertical = 8.dp))
            HorizontalDivider(color = colors.line)
        }
    }
    state.genres.forEach { genre ->
        val checked = genre.id in state.choice.checked
        item(key = "g-${genre.id}") { GenreRow(genre, checked, expanded = genre.id in state.expanded, viewModel) }
        if (genre.id in state.expanded) {
            when (val albums = state.albums[genre.id]) {
                is GenreAlbums.Ready -> items(albums.albums, key = { "g-${genre.id}-a-${it.id}" }) { album ->
                    AlbumRow(album, aside = album.id in state.choice.excludedAlbums, enabled = checked, onToggle = { viewModel.toggleAlbum(album.id) })
                }
                GenreAlbums.Failed -> item(key = "g-${genre.id}-failed") {
                    PanelRow { PanelLink(stringResource(R.string.action_retry)) { viewModel.retryAlbums(genre.id) } }
                }
                else -> item(key = "g-${genre.id}-loading") { PanelRow { LoadingState() } }
            }
        }
    }
    item(key = "panel-bottom") {
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(Rengaine.colors.surface),
        )
    }
}

@Composable
private fun GenreRow(genre: GenreDto, checked: Boolean, expanded: Boolean, viewModel: RandomViewModel) {
    val colors = Rengaine.colors
    PanelRow {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .toggleable(value = checked, role = Role.Checkbox) { viewModel.toggleGenre(genre.id) },
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, colors = checkboxColors())
                    Text(genre.name, style = Rengaine.type.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Text(
                        pluralStringResource(R.plurals.album_count, genre.albumCount, genre.albumCount),
                        style = Rengaine.type.secondary,
                        color = colors.muted,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                IconButton(
                    onClick = { viewModel.toggleExpanded(genre.id) },
                    modifier = Modifier.clip(CircleShape).background(if (expanded) colors.raised else colors.surface),
                ) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        stringResource(if (expanded) R.string.random_collapse_genre else R.string.random_expand_genre, genre.name),
                        tint = colors.text,
                    )
                }
            }
            HorizontalDivider(color = colors.line)
        }
    }
}

/** Album d'un genre déplié : coché = gardé, décoché = mis de côté ; grisé si le genre est décoché. */
@Composable
private fun AlbumRow(album: AlbumSummaryDto, aside: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val colors = Rengaine.colors
    PanelRow {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .toggleable(value = !aside, enabled = enabled, role = Role.Checkbox) { onToggle() }
                .alpha(if (enabled) 1f else 0.4f)
                .padding(start = 28.dp),
        ) {
            Checkbox(checked = !aside, onCheckedChange = null, enabled = enabled, colors = checkboxColors())
            Cover(album.coverUrl, Modifier.padding(start = 12.dp).size(36.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(album.title, style = Rengaine.type.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artistName, style = Rengaine.type.secondary, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Une ligne à l'intérieur du panneau (même fond, mêmes marges). */
@Composable
private fun PanelRow(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Rengaine.colors.surface).padding(horizontal = 16.dp)) { content() }
}

@Composable
private fun PanelLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = Rengaine.type.bodyStrong,
        color = Rengaine.colors.accent,
        modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun Notice(text: String) {
    MessageState(text)
}

@Composable
private fun checkboxColors() = CheckboxDefaults.colors(
    checkedColor = Rengaine.colors.accent,
    uncheckedColor = Rengaine.colors.muted,
    checkmarkColor = Rengaine.colors.onAccent,
)
