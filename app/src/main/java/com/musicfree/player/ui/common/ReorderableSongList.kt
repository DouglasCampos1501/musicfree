package com.musicfree.player.ui.common

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.musicfree.player.data.Song
import com.musicfree.player.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Lista de músicas de uma playlist com suporte a reordenação por arrastar
 * (toque longo na alça à esquerda de cada item, depois arraste).
 */
@Composable
fun ReorderableSongList(
    songs: List<Song>,
    currentSongId: Long?,
    onSongClick: (Int) -> Unit,
    onToggleHide: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onPlaySingleLoop: (Song) -> Unit,
    onRemove: (Song) -> Unit,
    onOrderChanged: (List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember { mutableStateListOf<Song>() }
    var draggedSongId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 64.dp.toPx() }

    LaunchedEffect(songs) {
        if (draggedSongId == null) {
            items.clear()
            items.addAll(songs)
        }
    }

    LazyColumn(modifier = modifier) {
        itemsIndexed(items, key = { _, song -> song.id }) { _, song ->
            val isDragged = song.id == draggedSongId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
            ) {
                IconButton(
                    onClick = {},
                    modifier = Modifier.pointerInput(song.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedSongId = song.id
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                                val currentIndex = items.indexOfFirst { it.id == song.id }
                                if (currentIndex == -1) return@detectDragGesturesAfterLongPress
                                val steps = (dragOffset / itemHeightPx).roundToInt()
                                val targetIndex = (currentIndex + steps).coerceIn(0, items.size - 1)
                                if (targetIndex != currentIndex) {
                                    val moved = items.removeAt(currentIndex)
                                    items.add(targetIndex, moved)
                                    dragOffset -= (targetIndex - currentIndex) * itemHeightPx
                                }
                            },
                            onDragEnd = {
                                draggedSongId = null
                                dragOffset = 0f
                                onOrderChanged(items.toList())
                            },
                            onDragCancel = {
                                draggedSongId = null
                                dragOffset = 0f
                            }
                        )
                    }
                ) {
                    Icon(Icons.Filled.DragHandle, contentDescription = "Arrastar para reordenar", tint = TextSecondary)
                }

                SongListItem(
                    song = song,
                    isCurrentlyPlaying = song.id == currentSongId,
                    isHidden = false,
                    onClick = { onSongClick(items.indexOfFirst { it.id == song.id }) },
                    onToggleHide = { onToggleHide(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onPlaySingleLoop = { onPlaySingleLoop(song) },
                    onRemoveFromPlaylist = { onRemove(song) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
