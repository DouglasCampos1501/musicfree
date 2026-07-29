package com.musicfree.player.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicfree.player.MainViewModel
import com.musicfree.player.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: Song,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Adicionar \"${song.title}\" a:",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (playlists.isEmpty()) {
                Text(
                    "Crie uma playlist primeiro na aba Playlists.",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addSongToPlaylist(playlist.id, song.id)
                                    onDismiss()
                                }
                        )
                    }
                }
            }
        }
    }
}
