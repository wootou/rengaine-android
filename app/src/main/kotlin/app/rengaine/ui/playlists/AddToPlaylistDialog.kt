package app.rengaine.ui.playlists

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.rengaine.R
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.dto.PlaylistSummaryDto
import app.rengaine.data.repository.PlaylistRepository
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.MosaicCover
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Résultat d'un ajout, pour le message affiché. */
sealed interface AddResult {
    data class Added(val playlistName: String, val count: Int) : AddResult
    data object Failed : AddResult
}

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {
    private val _playlists = MutableStateFlow<List<PlaylistSummaryDto>?>(null)
    /** `null` pendant le chargement. Seules les miennes : on ne modifie pas celles des autres. */
    val playlists = _playlists.asStateFlow()
    private val _done = MutableSharedFlow<AddResult>(extraBufferCapacity = 1)
    val done = _done.asSharedFlow()

    init {
        viewModelScope.launch {
            _playlists.value = (repository.playlists() as? ApiResult.Ok)?.value.orEmpty().filter { it.mine }
        }
    }

    fun addTo(playlist: PlaylistSummaryDto, trackIds: List<Long>) {
        viewModelScope.launch {
            _done.emit(
                when (repository.addTracks(playlist.id, trackIds)) {
                    is ApiResult.Ok -> AddResult.Added(playlist.name, trackIds.size)
                    is ApiResult.Err -> AddResult.Failed
                },
            )
        }
    }

    fun createWith(name: String, trackIds: List<Long>) {
        viewModelScope.launch {
            _done.emit(
                when (val r = repository.create(name, trackIds)) {
                    is ApiResult.Ok -> AddResult.Added(r.value.name, trackIds.size)
                    is ApiResult.Err -> AddResult.Failed
                },
            )
        }
    }
}

/**
 * « Ajouter à une playlist » (F10) : mes playlists, ou une nouvelle créée
 * directement avec ces pistes. Un message confirme l'ajout.
 */
@Composable
fun AddToPlaylistDialog(
    trackIds: List<Long>,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistViewModel = hiltViewModel(key = "add-${trackIds.hashCode()}"),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val type = Rengaine.type
    val context = LocalContext.current
    val resources = LocalResources.current
    var naming by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.done.collect { result ->
            val message = when (result) {
                is AddResult.Added -> resources.getQuantityString(R.plurals.playlist_added, result.count, result.count, result.playlistName)
                AddResult.Failed -> resources.getString(R.string.playlist_add_failed)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    if (naming) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            initial = "",
            onConfirm = { viewModel.createWith(it, trackIds) },
            onDismiss = { naming = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RengaineShapes.dialog,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.playlist_add_title), style = type.sectionTitle, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { naming = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Add, null, tint = colors.accent, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.playlist_new), style = type.bodyStrong, color = colors.accent)
                }
                when (val list = playlists) {
                    null -> LoadingState()
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list, key = { it.id }) { p ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { viewModel.addTo(p, trackIds) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                MosaicCover(p.coverUrls, Modifier.size(44.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = type.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        pluralStringResource(R.plurals.track_count, p.trackCount, p.trackCount),
                                        style = type.secondary,
                                        color = colors.muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.muted, style = type.button) }
        },
    )
}
