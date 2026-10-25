package app.rengaine.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rengaine.R
import app.rengaine.data.remote.dto.AlbumSummaryDto
import app.rengaine.data.remote.dto.ArtistSummaryDto
import app.rengaine.data.remote.dto.TrackDto
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.player.Cover
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/**
 * Champ de recherche de l'en-tête (capture `09`) : pilule « Artiste,
 * album, titre… », loupe, croix pour effacer. Entrée ouvre la page de
 * tous les résultats.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Rengaine.colors
    val placeholder = stringResource(R.string.search_placeholder)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = Rengaine.type.body.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier.onFocusChanged { onFocusChange(it.isFocused) },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RengaineShapes.pill)
                    .background(colors.surface)
                    .border(1.dp, colors.line, RengaineShapes.pill)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = Rengaine.type.body, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.search_clear), tint = colors.muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
    )
}

/**
 * Liste déroulante sous le champ, pendant la frappe : 6 artistes, albums
 * et pistes au plus, puis « Tous les résultats ».
 */
@Composable
fun SearchSuggestions(
    state: SearchUiState,
    onArtist: (ArtistSummaryDto) -> Unit,
    onAlbum: (AlbumSummaryDto) -> Unit,
    onTrack: (List<TrackDto>, TrackDto) -> Unit,
    onAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Rengaine.colors
    Box(
        modifier
            .clip(RengaineShapes.panel)
            .background(colors.raised)
            .border(1.dp, colors.line, RengaineShapes.panel),
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            when (state) {
                SearchUiState.Idle -> Unit
                SearchUiState.Loading -> item { LoadingState() }
                SearchUiState.Failed -> item { Message(stringResource(R.string.state_load_failed)) }
                is SearchUiState.Ready -> {
                    val r = state.results
                    if (state.isEmpty) item { Message(stringResource(R.string.search_nothing)) }
                    if (r.artists.isNotEmpty()) {
                        item { Group(stringResource(R.string.search_artists)) }
                        items(r.artists, key = { "a-${it.id}" }) { a ->
                            SuggestionRow(title = a.name, subtitle = null, onClick = { onArtist(a) }) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surface), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Person, null, tint = colors.muted, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    if (r.albums.isNotEmpty()) {
                        item { Group(stringResource(R.string.search_albums)) }
                        items(r.albums, key = { "b-${it.id}" }) { b ->
                            val sub = listOfNotNull(b.artistName.takeIf { it.isNotBlank() }, b.year?.toString()).joinToString(" · ")
                            SuggestionRow(title = b.title, subtitle = sub, onClick = { onAlbum(b) }) {
                                Cover(b.coverUrl, Modifier.size(40.dp))
                            }
                        }
                    }
                    if (r.tracks.isNotEmpty()) {
                        item { Group(stringResource(R.string.search_tracks)) }
                        items(r.tracks, key = { "t-${it.id}" }) { t ->
                            val sub = listOfNotNull(t.artistCredit.takeIf { it.isNotBlank() }, t.album?.title).joinToString(" · ")
                            SuggestionRow(title = t.title, subtitle = sub, enabled = t.available, onClick = { onTrack(r.tracks, t) }) {
                                Cover(t.coverUrl, Modifier.size(40.dp))
                            }
                        }
                    }
                    if (!state.isEmpty) {
                        item {
                            HorizontalDivider(color = colors.line, modifier = Modifier.padding(top = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onAll).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(stringResource(R.string.search_all_results), style = Rengaine.type.bodyStrong, color = colors.accent, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(text: String) {
    Text(
        text.uppercase(),
        style = Rengaine.type.overline,
        color = Rengaine.colors.muted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Message(text: String) {
    Text(text, style = Rengaine.type.body, color = Rengaine.colors.muted, modifier = Modifier.padding(16.dp))
}

@Composable
private fun SuggestionRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    leading: @Composable () -> Unit,
) {
    val colors = Rengaine.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = Rengaine.type.body, color = if (enabled) colors.text else colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = Rengaine.type.secondary, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
