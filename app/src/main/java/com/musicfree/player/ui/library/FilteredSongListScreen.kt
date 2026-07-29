package com.musicfree.player.ui.library

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicfree.player.MainViewModel
import com.musicfree.player.data.Song
import com.musicfree.player.ui.common.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredSongListScreen(
    title: String,
    songs: List<Song>,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val playerState by viewModel.playerState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onPlaySingleLoop = { viewModel.playSingleRepeated(song) }
                    )
                }
            }
        }
    }
}
