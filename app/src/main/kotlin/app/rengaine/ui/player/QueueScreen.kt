package app.rengaine.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rengaine.R
import app.rengaine.playback.QueueEntry
import app.rengaine.ui.components.formatDuration
import app.rengaine.ui.theme.Rengaine
import app.rengaine.ui.theme.RengaineShapes

/**
 * File d'attente (capture `13`) : « File d'attente », « 100 pistes ·
 * 6:11:58 restantes », fermer ; section « À SUIVRE » avec « Vider la
 * suite » ; piste en cours mise en évidence ; toucher une piste la lance,
 * × la retire. Appui long puis glisser : la piste change de place
 * (TalkBack : actions « Monter » et « Descendre », Q1).
 *
 * Pas encore : carte « Mode infini » (F5), enregistrer en playlist (F10).
 */
@Composable
fun QueueScreen(onClose: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val colors = Rengaine.colors
    val type = Rengaine.type
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    // Pendant un glisser, l'ordre vit ici ; le lecteur ne le reçoit qu'au
    // lâcher (un seul déplacement, une seule mise à jour de la file).
    var rows by remember { mutableStateOf(queue) }
    var dragKey by remember { mutableStateOf<Int?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(queue) { if (dragKey == null) rows = queue }

    fun swapIfNeeded() {
        val key = dragKey ?: return
        val at = rows.indexOfFirst { it.index == key }
        if (at < 0) return
        val sizes = listState.layoutInfo.visibleItemsInfo.associate { it.key to it.size }
        val below = rows.getOrNull(at + 1)?.let { sizes[it.index] }
        val above = rows.getOrNull(at - 1)?.let { sizes[it.index] }
        if (below != null && dragOffset > below / 2f) {
            rows = rows.toMutableList().apply { add(at + 1, removeAt(at)) }
            dragOffset -= below
        } else if (above != null && dragOffset < -above / 2f) {
            rows = rows.toMutableList().apply { add(at - 1, removeAt(at)) }
            dragOffset += above
        }
    }

    fun endDrag() {
        val key = dragKey ?: return
        val to = rows.indexOfFirst { it.index == key }
        if (to >= 0 && dragFrom >= 0 && to != dragFrom) viewModel.move(dragFrom, to)
        dragKey = null
        dragOffset = 0f
        dragFrom = -1
    }

    // À l'ouverture, la piste en cours en haut de l'écran.
    LaunchedEffect(Unit) {
        if (state.index > 0) listState.scrollToItem((state.index).coerceAtMost(queue.lastIndex.coerceAtLeast(0)))
    }

    Column(Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.queue_title),
                    style = type.pageTitle,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                if (queue.isNotEmpty()) {
                    Text(
                        pluralStringResource(R.plurals.queue_summary, queue.size, queue.size, formatDuration(state.remainingMs)),
                        style = type.secondary,
                        color = colors.muted,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = colors.text)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.queue_up_next).uppercase(),
                style = type.overline,
                color = colors.muted,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = viewModel::clearUpcoming, enabled = state.hasNext) {
                Icon(
                    Icons.Filled.ClearAll,
                    stringResource(R.string.queue_clear_upcoming),
                    tint = if (state.hasNext) colors.text else colors.faint,
                )
            }
        }

        if (queue.isEmpty()) {
            Text(stringResource(R.string.queue_empty), style = type.body, color = colors.muted, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(rows, key = { _, it -> it.index }) { position, entry ->
                val dragging = entry.index == dragKey
                val moveUp = stringResource(R.string.queue_move_up)
                val moveDown = stringResource(R.string.queue_move_down)
                QueueRow(
                    entry = entry,
                    isCurrent = entry.index == state.index,
                    isPlaying = state.isPlaying,
                    dragging = dragging,
                    onPlay = { viewModel.playAt(entry.index) },
                    onRemove = { viewModel.removeAt(entry.index) },
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .pointerInput(entry.index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragKey = entry.index
                                    dragFrom = rows.indexOfFirst { it.index == entry.index }
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    swapIfNeeded()
                                },
                                onDragEnd = ::endDrag,
                                onDragCancel = ::endDrag,
                            )
                        }
                        .semantics {
                            customActions = buildList {
                                if (position > 0) add(CustomAccessibilityAction(moveUp) { viewModel.move(position, position - 1); true })
                                if (position < rows.lastIndex) add(CustomAccessibilityAction(moveDown) { viewModel.move(position, position + 1); true })
                            }
                        },
                )
            }
        }
    }
}

@Composable
internal fun QueueRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    isPlaying: Boolean,
    dragging: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Rengaine.colors
    val type = Rengaine.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .shadow(if (dragging) 8.dp else 0.dp, RengaineShapes.card)
            .clip(RengaineShapes.card)
            .background(
                when {
                    isCurrent -> colors.accentSoft
                    dragging -> colors.surface
                    else -> colors.bg
                },
            )
            // Liseré braise de la piste en cours.
            .drawBehind { if (isCurrent) drawRect(colors.accent, size = Size(3.dp.toPx(), size.height)) }
            .clickable(onClick = onPlay),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(entry.coverUrl, modifier = Modifier.padding(start = 11.dp).size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                entry.title,
                style = if (isCurrent) type.bodyStrong else type.body,
                color = if (isCurrent) colors.accent else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(entry.artist, style = type.secondary, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isCurrent && isPlaying) {
            Icon(Icons.Filled.Equalizer, stringResource(R.string.track_now_playing), tint = colors.accent, modifier = Modifier.size(22.dp))
        } else if (entry.durationMs > 0) {
            Text(formatDuration(entry.durationMs), style = type.numeric, color = colors.muted)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, stringResource(R.string.queue_remove, entry.title), tint = colors.muted)
        }
    }
}
