package com.musicfree.player.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicfree.player.MainViewModel
import com.musicfree.player.ui.common.SongListItem
import com.musicfree.player.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistName: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val songs by remember(playlistId) { viewModel.songsForPlaylist(playlistId) }.collectAsState(initial = emptyList())
    val playerState by viewModel.playerState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (songs.isEmpty()) {
                Text(
                    "Nenhuma música nesta playlist ainda.",
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { viewModel.playFromQueue(songs, 0, shuffle = false) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(" Tocar tudo")
                    }
                    Button(onClick = { viewModel.playFromQueue(songs, 0, shuffle = true) }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null)
                        Text(" Aleatório")
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(songs, key = { it.id }) { song ->
                        val index = songs.indexOf(song)
                        SongListItem(
                            song = song,
                            isCurrentlyPlaying = song.id == playerState.currentSongId,
                            isHidden = false,
                            onClick = { viewModel.playFromQueue(songs, index) },
                            onToggleHide = { viewModel.hideSong(song.id) },
                            onAddToPlaylist = {},
                            onPlaySingleLoop = { viewModel.playSingleRepeated(song) },
                            onRemoveFromPlaylist = { viewModel.removeSongFromPlaylist(playlistId, song.id) }
                        )
                    }
                }
            }
        }
    }
}
