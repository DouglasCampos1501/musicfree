package com.musicfree.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.musicfree.player.data.Prefs
import com.musicfree.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerUiState(
    val currentSongId: Long? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queueSongIds: List<Long> = emptyList(),
    val speed: Float = 1f,
    /** Instante (System.currentTimeMillis) em que o timer de sono encerra a reprodução, ou null. */
    val sleepTimerEndsAt: Long? = null
)

data class EqInfo(val bands: Int, val minLevel: Int, val maxLevel: Int, val freqsHz: List<Int>)

class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: Prefs
) {

    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Mensagens para a UI exibir (ex.: arquivo que não pôde ser reproduzido). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun connect(onReady: () -> Unit = {}) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            // Restaura as preferências salvas (repetição, aleatório e velocidade).
            scope.launch {
                c.repeatMode = prefs.repeatMode.first()
                c.shuffleModeEnabled = prefs.shuffle.first()
                c.playbackParameters = PlaybackParameters(prefs.speed.first())
                applyEq(prefs.eqEnabled.first(), prefs.eqLevels.first(), prefs.loudnessMb.first(), save = false)
                attachListener()
                onReady()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun attachListener() {
        val c = controller ?: return
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updateState(player)
            }

            override fun onPlayerError(error: PlaybackException) {
                val title = c.currentMediaItem?.mediaMetadata?.title ?: "arquivo"
                _messages.tryEmit("Não foi possível tocar \"$title\" (formato não suportado ou arquivo corrompido)")
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
            currentIndex = player.currentMediaItemIndex,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            queueSongIds = queueIds,
            speed = player.playbackParameters.speed
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

    /**
     * Toca [songs] a partir de [startIndex]. O modo de repetição usado é sempre o salvo pelo
     * usuário (assim "repetir uma música" de um play avulso não vaza para a playlist).
     * [shuffle] = null usa a preferência salva.
     */
    fun playQueue(songs: List<Song>, startIndex: Int, shuffle: Boolean? = null) {
        val c = controller ?: return
        scope.launch {
            val items = songs.map { it.toMediaItem() }
            c.repeatMode = prefs.repeatMode.first()
            c.shuffleModeEnabled = shuffle ?: prefs.shuffle.first()
            c.setMediaItems(items, startIndex, 0L)
            c.prepare()
            c.play()
        }
    }

    /** Toca só esta música em loop. Não altera o modo de repetição salvo. */
    fun playSingle(song: Song) {
        val c = controller ?: return
        c.setMediaItem(song.toMediaItem())
        c.shuffleModeEnabled = false
        c.repeatMode = Player.REPEAT_MODE_ONE
        c.prepare()
        c.play()
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return
        }
        c.addMediaItem(song.toMediaItem())
    }

    fun skipToQueueIndex(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
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
        val enabled = !c.shuffleModeEnabled
        c.shuffleModeEnabled = enabled
        scope.launch { prefs.setShuffle(enabled) }
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        setRepeat(
            when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        )
    }

    /** Liga/desliga o loop da fila/playlist inteira. */
    fun toggleRepeatAll() {
        val c = controller ?: return
        setRepeat(if (c.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL)
    }

    private fun setRepeat(mode: Int) {
        controller?.repeatMode = mode
        scope.launch { prefs.setRepeatMode(mode) }
    }

    fun setSpeed(speed: Float) {
        controller?.playbackParameters = PlaybackParameters(speed)
        scope.launch { prefs.setSpeed(speed) }
    }

    /** Pausa a reprodução depois de [minutes] minutos. 0 cancela o timer. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _state.value = _state.value.copy(sleepTimerEndsAt = null)
            return
        }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.value = _state.value.copy(sleepTimerEndsAt = endsAt)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _state.value = _state.value.copy(sleepTimerEndsAt = null)
            _messages.tryEmit("Timer de sono: reprodução pausada")
        }
    }

    /** Pergunta ao serviço quantas bandas o equalizador do aparelho tem (null se não houver suporte). */
    suspend fun getEqInfo(): EqInfo? {
        val c = controller ?: return null
        val result = suspendCancellableCoroutine<SessionResult?> { cont ->
            val future = c.sendCustomCommand(SessionCommand(CMD_EQ_INFO, Bundle.EMPTY), Bundle.EMPTY)
            future.addListener({
                cont.resume(runCatching { future.get() }.getOrNull())
            }, MoreExecutors.directExecutor())
        }
        val extras = result?.extras ?: return null
        val bands = extras.getInt(KEY_EQ_BANDS, 0)
        if (bands <= 0) return null
        return EqInfo(
            bands = bands,
            minLevel = extras.getInt(KEY_EQ_MIN),
            maxLevel = extras.getInt(KEY_EQ_MAX),
            freqsHz = extras.getIntArray(KEY_EQ_FREQS)?.toList().orEmpty()
        )
    }

    fun applyEq(enabled: Boolean, levels: List<Int>, loudnessMb: Int, save: Boolean = true) {
        val c = controller ?: return
        val args = Bundle().apply {
            putBoolean(KEY_EQ_ENABLED, enabled)
            putIntArray(KEY_EQ_LEVELS, levels.toIntArray())
            putInt(KEY_LOUDNESS, loudnessMb)
        }
        c.sendCustomCommand(SessionCommand(CMD_EQ_SET, Bundle.EMPTY), args)
        if (save) scope.launch { prefs.setEq(enabled, levels, loudnessMb) }
    }

    fun release() {
        tickerJob?.cancel()
        sleepJob?.cancel()
        controller?.release()
        controller = null
    }
}
