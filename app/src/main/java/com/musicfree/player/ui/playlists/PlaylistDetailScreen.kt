package com.musicfree.player.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconToggleButton
import com.musicfree.player.playback.RepeatMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicfree.player.MainViewModel
import com.musicfree.player.ui.common.ReorderableSongList
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
    val favorites by viewModel.favoriteIds.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri -> if (uri != null) viewModel.exportPlaylistM3u(uri, songs) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { exportLauncher.launch("$playlistName.m3u") },
                        enabled = songs.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Exportar playlist (.m3u)")
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
                    val loopOn = playerState.repeatMode == RepeatMode.ALL
                    FilledTonalIconToggleButton(
                        checked = loopOn,
                        onCheckedChange = { viewModel.player.toggleRepeatAll() }
                    ) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = if (loopOn) "Desativar loop da playlist" else "Repetir playlist inteira"
                        )
                    }
                }
                Text(
                    "Toque e segure a alça ☰ para reordenar",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                ReorderableSongList(
                    songs = songs,
                    currentSongId = playerState.currentSongId,
                    onSongClick = { index -> viewModel.playFromQueue(songs, index) },
                    onToggleHide = { song -> viewModel.hideSong(song.id) },
                    onAddToPlaylist = {},
                    onPlaySingleLoop = { song -> viewModel.playSingleRepeated(song) },
                    onRemove = { song -> viewModel.removeSongFromPlaylist(playlistId, song.id) },
                    onOrderChanged = { reordered ->
                        viewModel.reorderPlaylist(playlistId, reordered.map { it.id })
                    },
                    isPlaying = playerState.isPlaying,
                    favoriteIds = favorites,
                    onToggleFavorite = { song -> viewModel.toggleFavorite(song.id) },
                    onPlayNext = { song -> viewModel.player.playNext(song) },
                    onAddToQueue = { song -> viewModel.player.addToQueue(song) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
