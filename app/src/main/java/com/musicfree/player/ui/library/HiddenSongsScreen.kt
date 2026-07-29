package com.musicfree.player.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.musicfree.player.MainViewModel
import com.musicfree.player.ui.common.SongListItem
import com.musicfree.player.ui.theme.TextSecondary

@Composable
fun HiddenSongsScreen(viewModel: MainViewModel) {
    val hidden by viewModel.hiddenSongs.collectAsState()

    if (hidden.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhuma música oculta", color = TextSecondary)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(hidden, key = { it.id }) { song ->
            SongListItem(
                song = song,
                isCurrentlyPlaying = false,
                isHidden = true,
                onClick = { viewModel.unhideSong(song.id) },
                onToggleHide = { viewModel.unhideSong(song.id) },
                onAddToPlaylist = {},
                onPlaySingleLoop = {}
            )
        }
    }
}
