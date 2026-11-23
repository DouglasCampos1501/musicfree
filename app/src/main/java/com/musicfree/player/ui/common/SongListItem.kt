package com.musicfree.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicfree.player.data.Song
import com.musicfree.player.ui.theme.SpotifyGreen
import com.musicfree.player.ui.theme.TextSecondary

@Composable
fun SongListItem(
    song: Song,
    isCurrentlyPlaying: Boolean,
    isHidden: Boolean,
    onClick: () -> Unit,
    onToggleHide: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlaySingleLoop: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp)
            )
            if (song.albumArtUri == null) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextSecondary)
            }
            if (isCurrentlyPlaying) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerBars(animate = isPlaying)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = song.title,
                color = if (isCurrentlyPlaying) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = song.artist,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (isFavorite) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Favorita",
                tint = SpotifyGreen,
                modifier = Modifier.size(18.dp)
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = TextSecondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onToggleFavorite != null) {
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos") },
                        leadingIcon = {
                            Icon(
                                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null
                            )
                        },
                        onClick = { menuOpen = false; onToggleFavorite() }
                    )
                }
                if (onPlayNext != null) {
                    DropdownMenuItem(
                        text = { Text("Tocar a seguir") },
                        leadingIcon = { Icon(Icons.Filled.SkipNext, contentDescription = null) },
                        onClick = { menuOpen = false; onPlayNext() }
                    )
                }
                if (onAddToQueue != null) {
                    DropdownMenuItem(
                        text = { Text("Adicionar à fila") },
                        leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                        onClick = { menuOpen = false; onAddToQueue() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Tocar apenas esta (loop)") },
                    leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null) },
                    onClick = { menuOpen = false; onPlaySingleLoop() }
                )
                DropdownMenuItem(
                    text = { Text("Adicionar à playlist") },
                    leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                    onClick = { menuOpen = false; onAddToPlaylist() }
                )
                DropdownMenuItem(
                    text = { Text(if (isHidden) "Reexibir música" else "Ocultar música") },
                    leadingIcon = {
                        Icon(
                            if (isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = null
                        )
                    },
                    onClick = { menuOpen = false; onToggleHide() }
                )
                if (onEdit != null) {
                    DropdownMenuItem(
                        text = { Text("Editar informações") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() }
                    )
                }
                if (onRemoveFromPlaylist != null) {
                    DropdownMenuItem(
                        text = { Text("Remover da playlist") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onRemoveFromPlaylist() }
                    )
                }
            }
        }
    }
}
