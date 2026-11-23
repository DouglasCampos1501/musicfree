package com.musicfree.player.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.musicfree.player.MainViewModel
import com.musicfree.player.data.Song
import com.musicfree.player.playback.RepeatMode
import com.musicfree.player.ui.theme.SpotifyGreen
import com.musicfree.player.ui.theme.TextSecondary
import java.util.concurrent.TimeUnit

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val sleepOptions = listOf(15, 30, 45, 60, 90)

@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    currentSong: Song?,
    onClose: () -> Unit
) {
    val playerState by viewModel.playerState.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    // Posição enquanto o usuário arrasta o slider (evita brigar com a atualização automática).
    var dragPosition by remember { mutableStateOf<Float?>(null) }

    val accent = rememberArtworkColor(currentSong?.albumArtUri)
    val topColor by animateColorAsState(accent, label = "bg")
    val bg = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topColor, bg, bg)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Fechar player")
                }
                Text("Tocando agora", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = "Fila de reprodução")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 8.dp)
                    .aspectRatio(1f)
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.fillMaxSize(0.4f)
                )
                if (currentSong?.albumArtUri != null) {
                    AsyncImage(
                        model = currentSong.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        currentSong?.title ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        currentSong?.artist ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (currentSong != null) {
                    val fav = currentSong.id in favorites
                    IconButton(onClick = { viewModel.toggleFavorite(currentSong.id) }) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (fav) "Remover dos favoritos" else "Favoritar",
                            tint = if (fav) SpotifyGreen else TextSecondary
                        )
                    }
                }
            }

            val duration = playerState.durationMs.toFloat().coerceAtLeast(1f)
            val shownPosition = dragPosition ?: playerState.positionMs.toFloat().coerceAtMost(duration)
            Slider(
                value = shownPosition,
                onValueChange = { dragPosition = it },
                onValueChangeFinished = {
                    dragPosition?.let { viewModel.player.seekTo(it.toLong()) }
                    dragPosition = null
                },
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(shownPosition.toLong()), color = TextSecondary)
                Text(formatDuration(playerState.durationMs), color = TextSecondary)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleIcon(
                    active = playerState.shuffleEnabled,
                    onClick = { viewModel.player.toggleShuffle() }
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Aleatório",
                        tint = if (playerState.shuffleEnabled) SpotifyGreen else TextSecondary
                    )
                }
                IconButton(onClick = { viewModel.player.skipPrevious() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(36.dp))
                }
                IconButton(
                    onClick = { viewModel.player.togglePlayPause() },
                    modifier = Modifier.size(72.dp).background(SpotifyGreen, CircleShape)
                ) {
                    Icon(
                        if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { viewModel.player.skipNext() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Próxima", modifier = Modifier.size(36.dp))
                }
                ToggleIcon(
                    active = playerState.repeatMode != RepeatMode.OFF,
                    onClick = { viewModel.player.cycleRepeatMode() }
                ) {
                    Icon(
                        if (playerState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repetir",
                        tint = if (playerState.repeatMode != RepeatMode.OFF) SpotifyGreen else TextSecondary
                    )
                }
            }

            Text(
                when (playerState.repeatMode) {
                    RepeatMode.OFF -> "Repetição desligada"
                    RepeatMode.ALL -> "Repetindo a playlist inteira"
                    RepeatMode.ONE -> "Repetindo esta música"
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showLyrics = true }, enabled = currentSong != null) {
                    Icon(Icons.Filled.Lyrics, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Letra")
                }
                Box {
                    TextButton(onClick = { speedMenu = true }) {
                        Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" ${formatSpeed(playerState.speed)}x")
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        speedOptions.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${formatSpeed(s)}x") },
                                onClick = {
                                    speedMenu = false
                                    viewModel.player.setSpeed(s)
                                }
                            )
                        }
                    }
                }
                Box {
                    val endsAt = playerState.sleepTimerEndsAt
                    TextButton(onClick = { sleepMenu = true }) {
                        Icon(Icons.Filled.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            if (endsAt != null) {
                                val left = ((endsAt - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
                                " $left min"
                            } else {
                                " Timer"
                            }
                        )
                    }
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        sleepOptions.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("Pausar em $m min") },
                                onClick = {
                                    sleepMenu = false
                                    viewModel.player.setSleepTimer(m)
                                }
                            )
                        }
                        if (endsAt != null) {
                            DropdownMenuItem(
                                text = { Text("Cancelar timer") },
                                onClick = {
                                    sleepMenu = false
                                    viewModel.player.setSleepTimer(0)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLyrics && currentSong != null) {
        LyricsSheet(viewModel = viewModel, song = currentSong, onDismiss = { showLyrics = false })
    }
    if (showQueue) {
        QueueSheet(viewModel = viewModel, onDismiss = { showQueue = false })
    }
}

@Composable
private fun ToggleIcon(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.background(
            if (active) SpotifyGreen.copy(alpha = 0.18f) else Color.Transparent,
            CircleShape
        )
    ) { content() }
}

/** Cor dominante da capa (via Palette), escurecida, para o fundo em gradiente do player. */
@Composable
private fun rememberArtworkColor(artUri: android.net.Uri?): Color {
    val context = LocalContext.current
    var color by remember(artUri) { mutableStateOf(Color(0xFF2A2A2A)) }
    LaunchedEffect(artUri) {
        if (artUri == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(artUri)
            .allowHardware(false)
            .size(128)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@LaunchedEffect
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val rgb = Palette.from(bitmap.copy(Bitmap.Config.ARGB_8888, false))
            .generate()
            .let { it.getDarkVibrantColor(it.getDominantColor(0xFF2A2A2A.toInt())) }
        color = Color(rgb).copy(alpha = 1f)
    }
    return color
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')

internal fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%d:%02d", minutes, seconds)
}
