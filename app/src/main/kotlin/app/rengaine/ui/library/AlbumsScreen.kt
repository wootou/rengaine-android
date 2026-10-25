package app.rengaine.ui.library

import androidx.compose.foundation.clickable
import app.rengaine.ui.components.formatMinutes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.R
import app.rengaine.data.remote.dto.AlbumSummaryDto
import app.rengaine.data.repository.AlbumSort
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.LinkButton
import app.rengaine.ui.components.MessageState
import app.rengaine.ui.components.SelectOption
import app.rengaine.ui.components.SelectPill
import app.rengaine.ui.player.Cover
import app.rengaine.ui.theme.Rengaine

/**
 * Onglet Albums (capture `05`) : titre et nombre, sélecteurs tri / genre /
 * décennie, grille de pochettes. Deux colonnes sur un téléphone, davantage
 * sur une tablette (U11).
 */
@Composable
fun AlbumsScreen(
    onOpenAlbum: (Long) -> Unit,
    contentPadding: PaddingValues,
    /** Liste ouverte depuis Explorer : son titre (« Pop ») et le retour. */
    title: String? = null,
    onBack: (() -> Unit)? = null,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val asList by viewModel.asList.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val gridState = rememberLazyGridState()

    // Page suivante quand il reste moins d'une douzaine d'albums à voir.
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 12
        }
    }
    LaunchedEffect(nearEnd, state.albums.size) { if (nearEnd) viewModel.loadMore() }

    // Retour en haut quand les filtres changent.
    LaunchedEffect(state.filter) { gridState.scrollToItem(0) }

    LazyVerticalGrid(
        // En liste : une colonne sur un téléphone, deux sur une tablette (U11).
        columns = if (asList) GridCells.Adaptive(minSize = 360.dp) else GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(if (asList) 4.dp else 18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.text)
                        }
                    }
                    Text(
                        title ?: stringResource(R.string.albums_title),
                        style = Rengaine.type.pageTitle,
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    state.total?.let {
                        Text(it.toString(), style = Rengaine.type.numeric, color = colors.muted, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    // Grille ou liste : l'icône montre l'affichage qu'on obtient en touchant.
                    IconButton(onClick = { viewModel.setAsList(!asList) }) {
                        Icon(
                            if (asList) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                            stringResource(if (asList) R.string.albums_show_grid else R.string.albums_show_list),
                            tint = colors.text,
                        )
                    }
                }
                Filters(state, viewModel)
            }
        }

        when {
            state.failed -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorState(onRetry = viewModel::retry) }
            state.albums.isEmpty() && state.loading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingState() }
            state.albums.isEmpty() && state.total == 0 -> item(span = { GridItemSpan(maxLineSpan) }) {
                MessageState(stringResource(if (state.filter.genreId != null || state.filter.decade != null) R.string.albums_none_filtered else R.string.albums_none))
            }
            else -> {
                items(state.albums, key = { it.id }) { album ->
                    if (asList) {
                        AlbumRow(album, onClick = { onOpenAlbum(album.id) })
                    } else {
                        AlbumCard(album, onClick = { onOpenAlbum(album.id) })
                    }
                }
                if (state.moreFailed) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LinkButton(text = stringResource(R.string.action_retry), onClick = viewModel::retry)
                    }
                } else if (state.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) { LoadingState() }
                }
            }
        }
    }
}

/** Sélecteurs sur une ligne qui défile : jamais un `Row` nu (convention du projet). */
@Composable
private fun Filters(state: AlbumsUiState, viewModel: AlbumsViewModel) {
    val sortOptions = listOf(
        SelectOption(AlbumSort.NAME, stringResource(R.string.albums_sort_name)),
        SelectOption(AlbumSort.ADDED, stringResource(R.string.albums_sort_added)),
        SelectOption(AlbumSort.YEAR, stringResource(R.string.albums_sort_year)),
        SelectOption(AlbumSort.ARTIST, stringResource(R.string.albums_sort_artist)),
    )
    val genreOptions = listOf(SelectOption<Long?>(null, stringResource(R.string.albums_all_genres))) +
        state.genres.map { SelectOption<Long?>(it.id, it.name) }
    val decadeOptions = listOf(SelectOption<Int?>(null, stringResource(R.string.albums_all_years))) +
        state.decades.map { SelectOption<Int?>(it.decade, stringResource(R.string.decade_label, it.decade)) }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SelectPill(sortOptions, state.filter.sort, viewModel::setSort, label = stringResource(R.string.albums_sort_label))
        if (state.genres.isNotEmpty()) {
            SelectPill(genreOptions, state.filter.genreId, viewModel::setGenre, label = stringResource(R.string.albums_genre_label))
        }
        if (state.decades.isNotEmpty()) {
            SelectPill(decadeOptions, state.filter.decade, viewModel::setDecade, label = stringResource(R.string.albums_year_label))
        }
    }
}

/** Carte d'album : pochette carrée, titre, « artiste · année ». */
@Composable
fun AlbumCard(album: AlbumSummaryDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Rengaine.colors
    val subtitle = listOfNotNull(album.artistName.takeIf { it.isNotBlank() }, album.year?.toString()).joinToString(" · ")
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Cover(album.coverUrl, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
            album.title,
            style = Rengaine.type.cardTitle,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = Rengaine.type.secondary, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Ligne d'album (affichage en liste) : petite pochette, titre, « artiste ·
 * année » ; à droite le nombre de pistes et la durée.
 */
@Composable
fun AlbumRow(album: AlbumSummaryDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Rengaine.colors
    val subtitle = listOfNotNull(album.artistName.takeIf { it.isNotBlank() }, album.year?.toString()).joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(album.coverUrl, modifier = Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(album.title, style = Rengaine.type.cardTitle, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = Rengaine.type.secondary, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (album.trackCount > 0) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    pluralStringResource(R.plurals.track_count, album.trackCount, album.trackCount),
                    style = Rengaine.type.small,
                    color = colors.muted,
                )
                if (album.durationMs > 0) {
                    Text(formatMinutes(album.durationMs), style = Rengaine.type.small, color = colors.muted)
                }
            }
        }
    }
}
