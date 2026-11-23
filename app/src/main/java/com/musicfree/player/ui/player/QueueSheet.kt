package com.musicfree.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.musicfree.player.MainViewModel
import com.musicfree.player.ui.theme.SpotifyGreen
import com.musicfree.player.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** Item da fila com identidade própria (a mesma música pode aparecer várias vezes). */
private data class QueueEntry(val uid: Int, val songId: Long)

private val RowHeight = 64.dp

/** Fila de reprodução atual: tocar um item, reordenar (toque longo na alça e arraste) ou remover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val state by viewModel.playerState.collectAsState()
    val visible by viewModel.visibleSongs.collectAsState()
    val hidden by viewModel.hiddenSongs.collectAsState()
    val byId = remember(visible, hidden) { (visible + hidden).associateBy { it.id } }
    val listState = rememberLazyListState()

    val entries = remember { mutableStateListOf<QueueEntry>() }
    var draggedUid by remember { mutableStateOf<Int?>(null) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }

    // Sincroniza com a fila real, exceto durante um arraste.
    LaunchedEffect(state.queueSongIds) {
        if (draggedUid == null) {
            entries.clear()
            entries.addAll(state.queueSongIds.mapIndexed { i, id -> QueueEntry(i, id) })
        }
    }
    LaunchedEffect(Unit) {
        if (state.currentIndex > 0) listState.scrollToItem(state.currentIndex)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Fila de reprodução (${entries.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 480.dp)) {
            items(entries, key = { it.uid }) { entry ->
                val song = byId[entry.songId]
                val index = entries.indexOf(entry)
                val isCurrent = index == state.currentIndex
                val isDragged = entry.uid == draggedUid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                        .clickable { viewModel.player.skipToQueueIndex(index) }
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {},
                        modifier = Modifier.pointerInput(entry.uid) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedUid = entry.uid
                                    dragStartIndex = entries.indexOfFirst { it.uid == entry.uid }
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val current = entries.indexOfFirst { it.uid == entry.uid }
                                    if (current == -1) return@detectDragGesturesAfterLongPress
                                    val steps = (dragOffset / rowHeightPx).roundToInt()
                                    val target = (current + steps).coerceIn(0, entries.lastIndex)
                                    if (target != current) {
                                        entries.add(target, entries.removeAt(current))
                                        dragOffset -= (target - current) * rowHeightPx
                                    }
                                },
                                onDragEnd = {
                                    val end = entries.indexOfFirst { it.uid == entry.uid }
                                    draggedUid = null
                                    dragOffset = 0f
                                    if (end != -1 && end != dragStartIndex) {
                                        viewModel.player.moveInQueue(dragStartIndex, end)
                                    }
                                },
                                onDragCancel = {
                                    draggedUid = null
                                    dragOffset = 0f
                                    entries.clear()
                                    entries.addAll(state.queueSongIds.mapIndexed { i, id -> QueueEntry(i, id) })
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Filled.DragHandle, contentDescription = "Arrastar para reordenar", tint = TextSecondary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song?.title ?: "Música #${entry.songId}",
                            color = if (isCurrent) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song?.artist.orEmpty(),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.player.removeFromQueue(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remover da fila")
                    }
                }
            }
        }
    }
}
