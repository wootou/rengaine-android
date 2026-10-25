package app.rengaine.ui.history

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.R
import app.rengaine.data.remote.dto.PlayHistoryItemDto
import app.rengaine.ui.components.ButtonKind
import app.rengaine.ui.components.ErrorState
import app.rengaine.ui.components.LinkButton
import app.rengaine.ui.components.LoadingState
import app.rengaine.ui.components.MessageState
import app.rengaine.ui.components.RengaineButton
import app.rengaine.ui.components.formatDuration
import app.rengaine.ui.player.Cover
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes
import java.time.LocalDate
import java.time.ZoneId

/**
 * Historique d'écoute (contrat 2.26.0), comme la page du site : titre,
 * nombre d'écoutes, « Tout effacer », aide ; écoutes groupées par jour
 * (« Aujourd'hui », « Hier », « Vendredi 23 octobre ») ; chaque ligne :
 * pochette, titre, « artiste · album », heure, « passée à m:ss » si la
 * piste n'a pas été finie, appareil (sur un écran assez large), croix.
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    val deleteFailed = stringResource(R.string.history_delete_failed)
    val cleared = stringResource(R.string.history_cleared)
    val clearFailed = stringResource(R.string.history_clear_failed)
    LaunchedEffect(Unit) {
        viewModel.messages.collect { m ->
            val text = when (m) {
                HistoryMessage.DELETE_FAILED -> deleteFailed
                HistoryMessage.CLEARED -> cleared
                HistoryMessage.CLEAR_FAILED -> clearFailed
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 15
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) viewModel.loadMore() }

    // Comme le site sous 480 px : l'appareil n'est affiché que sur un écran assez large.
    val showDevice = LocalConfiguration.current.screenWidthDp >= 480
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val locale = LocalConfiguration.current.locales[0]
    // Écoutes groupées par jour, dans l'ordre reçu (les plus récentes d'abord).
    val days = remember(state.items) { state.items.groupBy { HistoryDays.dayOf(it.startedAt, zone) } }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.text)
                    }
                    Text(
                        stringResource(R.string.history_title),
                        style = Rengaine.type.pageTitle,
                        color = colors.text,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.total?.let { n ->
                        Text(pluralStringResource(R.plurals.history_count, n, n), style = Rengaine.type.numeric, color = colors.muted)
                    }
                    Text(stringResource(R.string.history_help), style = Rengaine.type.secondary, color = colors.muted)
                    if ((state.total ?: 0) > 0) {
                        RengaineButton(
                            text = stringResource(R.string.history_clear),
                            onClick = { confirmClear = true },
                            kind = ButtonKind.Secondary,
                            icon = Icons.Outlined.Delete,
                            loading = state.clearing,
                        )
                    }
                }
            }
        }
        when {
            state.failed -> item { ErrorState(onRetry = viewModel::retry) }
            state.items.isEmpty() && state.loading -> item { LoadingState() }
            state.items.isEmpty() && state.total == 0 -> item { MessageState(stringResource(R.string.history_empty)) }
            else -> {
                days.forEach { (day, plays) ->
                    item(key = "d-$day") {
                        val header = day?.let { HistoryDays.header(it, today, locale) }
                        Text(
                            when (header) {
                                DayHeader.Today -> stringResource(R.string.history_today)
                                DayHeader.Yesterday -> stringResource(R.string.history_yesterday)
                                is DayHeader.Date -> header.text
                                null -> ""
                            },
                            style = Rengaine.type.sectionTitle,
                            color = colors.text,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp).semantics { heading() },
                        )
                    }
                    items(plays, key = { "p-${it.id}" }) { play ->
                        PlayRow(
                            play = play,
                            time = HistoryDays.timeOf(play.startedAt, zone),
                            showDevice = showDevice,
                            isCurrent = player.trackId == play.track.id,
                            onPlay = { viewModel.play(play) },
                            onDelete = { viewModel.delete(play) },
                        )
                    }
                }
                if (state.moreFailed) {
                    item { LinkButton(text = stringResource(R.string.action_retry), onClick = viewModel::retry) }
                } else if (state.loading) {
                    item { LoadingState() }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            shape = RengaineShapes.dialog,
            containerColor = colors.surface,
            text = { Text(stringResource(R.string.history_clear_confirm), style = Rengaine.type.body, color = colors.text) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearAll()
                }) { Text(stringResource(R.string.history_clear), color = colors.danger, style = Rengaine.type.button) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel), color = colors.muted, style = Rengaine.type.button)
                }
            },
        )
    }
}

@Composable
internal fun PlayRow(
    play: PlayHistoryItemDto,
    time: String,
    showDevice: Boolean,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Rengaine.colors
    val track = play.track
    val enabled = track.available
    val meta = buildList {
        add(time)
        if (!play.completed) add(stringResource(R.string.history_skipped_at, formatDuration(play.playedMs)))
        if (showDevice) play.device?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")
    // Texte très agrandi (Q1) : l'heure passe sous l'artiste, sur toute la
    // largeur, au lieu de s'entasser à droite du titre.
    val metaBelow = LocalDensity.current.fontScale >= 1.3f
    val playLabel = stringResource(R.string.history_play)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = playLabel, onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.coverUrl, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                track.title,
                style = Rengaine.type.body,
                color = when {
                    !enabled -> colors.faint
                    isCurrent -> colors.accent
                    else -> colors.text
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(track.artistCredit.takeIf { it.isNotBlank() }, track.album?.title).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = Rengaine.type.secondary, color = if (enabled) colors.muted else colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (metaBelow) Text(meta, style = Rengaine.type.small, color = colors.muted)
        }
        // Le titre garde la place : l'heure (et l'appareil) passent à la
        // ligne plutôt que de l'écraser.
        if (!metaBelow) {
            Text(meta, style = Rengaine.type.small, color = colors.muted, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 160.dp).padding(end = 4.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, stringResource(R.string.history_delete_play, track.title), tint = colors.muted)
        }
    }
}
