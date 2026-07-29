package com.musicfree.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.musicfree.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerUiState(
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queueSongIds: List<Long> = emptyList()
)

class PlayerController(private val context: Context, private val scope: CoroutineScope) {

    private var controller: MediaController? = null
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun connect(onReady: () -> Unit = {}) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get()
            attachListener()
            onReady()
        }, MoreExecutors.directExecutor())
    }

    private fun attachListener() {
        val c = controller ?: return
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updateState(player)
            }
        })
        updateState(c)
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                controller?.let { updateState(it) }
                delay(500)
            }
        }
    }

    private fun updateState(player: Player) {
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val queueIds = (0 until player.mediaItemCount).mapNotNull {
            player.getMediaItemAt(it).mediaId.toLongOrNull()
        }
        _state.value = _state.value.copy(
            currentSongId = currentId,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            queueSongIds = queueIds
        )
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .build()
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun playQueue(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        val items = songs.map { it.toMediaItem() }
        c.setMediaItems(items, startIndex, 0L)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
    }

    fun playSingle(song: Song) {
        val c = controller ?: return
        c.setMediaItem(song.toMediaItem())
        c.shuffleModeEnabled = false
        c.repeatMode = Player.REPEAT_MODE_ONE
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipNext() = controller?.seekToNextMediaItem()
    fun skipPrevious() = controller?.seekToPreviousMediaItem()
    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun release() {
        tickerJob?.cancel()
        controller?.release()
        controller = null
    }
}
