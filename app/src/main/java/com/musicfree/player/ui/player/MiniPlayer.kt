package com.musicfree.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import com.musicfree.player.playback.RepeatMode
import com.musicfree.player.ui.theme.SpotifyGreen
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicfree.player.MainViewModel
import com.musicfree.player.data.Song
import com.musicfree.player.playback.PlayerUiState

import com.musicfree.player.ui.theme.TextSecondary

@Composable
fun MiniPlayer(
    playerState: PlayerUiState,
    currentSong: Song?,
    onClick: () -> Unit,
    viewModel: MainViewModel
) {
    if (currentSong == null) return

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
    LinearProgressIndicator(
        progress = { if (playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs else 0f },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = SpotifyGreen,
        trackColor = Color.Transparent
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(model = currentSong.albumArtUri, contentDescription = null, modifier = Modifier.size(40.dp))
            if (currentSong.albumArtUri == null) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextSecondary)
            }
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(currentSong.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(currentSong.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextSecondary)
        }

        IconButton(onClick = { viewModel.player.toggleRepeatAll() }) {
            Icon(
                if (playerState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Repetir playlist",
                tint = if (playerState.repeatMode != RepeatMode.OFF) SpotifyGreen else TextSecondary
            )
        }
        IconButton(onClick = { viewModel.player.togglePlayPause() }) {
            Icon(
                if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play/Pause"
            )
        }
        IconButton(onClick = { viewModel.player.skipNext() }) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Próxima")
        }
    }
    }
}
