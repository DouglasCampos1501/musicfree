package com.musicfree.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicfree.player.MainViewModel
import com.musicfree.player.data.Song
import com.musicfree.player.playback.RepeatMode
import com.musicfree.player.ui.theme.SpotifyGreen
import com.musicfree.player.ui.theme.TextSecondary
import java.util.concurrent.TimeUnit

@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    currentSong: Song?,
    onClose: () -> Unit
) {
    val playerState by viewModel.playerState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Fechar player")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(vertical = 24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (currentSong?.albumArtUri != null) {
                AsyncImage(
                    model = currentSong.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.fillMaxSize(0.4f)
                )
            }
        }

        Text(
            currentSong?.title ?: "",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Start
        )
        Text(
            currentSong?.artist ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Slider(
            value = playerState.positionMs.toFloat().coerceAtMost(playerState.durationMs.toFloat().coerceAtLeast(1f)),
            onValueChange = { viewModel.player.seekTo(it.toLong()) },
            valueRange = 0f..(playerState.durationMs.toFloat().coerceAtLeast(1f)),
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(playerState.positionMs), color = TextSecondary)
            Text(formatDuration(playerState.durationMs), color = TextSecondary)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.player.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Aleatório",
                    tint = if (playerState.shuffleEnabled) SpotifyGreen else TextSecondary
                )
            }
            IconButton(onClick = { viewModel.player.skipPrevious() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior")
            }
            IconButton(
                onClick = { viewModel.player.togglePlayPause() },
                modifier = Modifier
                    .background(SpotifyGreen, RoundedCornerShape(50))
                    .padding(4.dp)
            ) {
                Icon(
                    if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton(onClick = { viewModel.player.skipNext() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Próxima")
            }
            IconButton(onClick = { viewModel.player.cycleRepeatMode() }) {
                Icon(
                    if (playerState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repetir",
                    tint = if (playerState.repeatMode != RepeatMode.OFF) SpotifyGreen else TextSecondary
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%d:%02d", minutes, seconds)
}
