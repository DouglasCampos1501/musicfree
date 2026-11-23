package com.musicfree.player.playback

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.glance.appwidget.updateAll
import com.musicfree.player.widget.MusicWidget
import com.musicfree.player.widget.WidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

const val CMD_EQ_SET = "musicfree.EQ_SET"
const val CMD_EQ_INFO = "musicfree.EQ_INFO"
const val KEY_EQ_ENABLED = "enabled"
const val KEY_EQ_LEVELS = "levels"          // IntArray em milibéis, um por banda
const val KEY_LOUDNESS = "loudness"         // ganho em milibéis (0 = desligado)
const val KEY_EQ_BANDS = "bands"            // Int
const val KEY_EQ_MIN = "minLevel"           // Int (mB)
const val KEY_EQ_MAX = "maxLevel"           // Int (mB)
const val KEY_EQ_FREQS = "freqs"            // IntArray (Hz)

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var eqEnabled = false
    private var eqLevels = IntArray(0)
    private var loudnessMb = 0
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun refreshWidget(player: Player) {
        val item = player.currentMediaItem
        WidgetState.save(
            this,
            item?.mediaMetadata?.title?.toString().orEmpty().ifEmpty { "MusicFree" },
            item?.mediaMetadata?.artist?.toString().orEmpty(),
            player.isPlaying
        )
        serviceScope.launch { runCatching { MusicWidget().updateAll(this@PlaybackService) } }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // EXTENSION_RENDERER_MODE_ON: usa os decoders nativos primeiro e cai para o FFmpeg
        // quando o formato não é suportado pelo Android/ExoPlayer.
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Arquivo corrompido/não suportado: pula para a próxima faixa em vez de travar.
        // O contador evita laço infinito quando todas as faixas da fila falham (ex.: repeat ALL).
        var consecutiveErrors = 0
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) consecutiveErrors = 0
                refreshWidget(player)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                refreshWidget(player)
            }

            override fun onPlayerError(error: PlaybackException) {
                consecutiveErrors++
                if (consecutiveErrors <= player.mediaItemCount && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        // Os efeitos de áudio precisam ser recriados quando o ExoPlayer troca de audio session.
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                rebuildEffects(audioSessionId)
            }
        })
        rebuildEffects(player.audioSessionId)

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(CMD_EQ_SET, Bundle.EMPTY))
                        .add(SessionCommand(CMD_EQ_INFO, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.accept(
                        commands,
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                    )
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CMD_EQ_SET -> {
                            eqEnabled = args.getBoolean(KEY_EQ_ENABLED, false)
                            eqLevels = args.getIntArray(KEY_EQ_LEVELS) ?: IntArray(0)
                            loudnessMb = args.getInt(KEY_LOUDNESS, 0)
                            applyEffects()
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        CMD_EQ_INFO -> {
                            val extras = Bundle()
                            equalizer?.let { eq ->
                                val range = eq.bandLevelRange
                                extras.putInt(KEY_EQ_BANDS, eq.numberOfBands.toInt())
                                extras.putInt(KEY_EQ_MIN, range[0].toInt())
                                extras.putInt(KEY_EQ_MAX, range[1].toInt())
                                extras.putIntArray(
                                    KEY_EQ_FREQS,
                                    IntArray(eq.numberOfBands.toInt()) { eq.getCenterFreq(it.toShort()) / 1000 }
                                )
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()
    }

    private fun rebuildEffects(sessionId: Int) {
        releaseEffects()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        try {
            equalizer = Equalizer(0, sessionId)
            loudness = LoudnessEnhancer(sessionId)
            applyEffects()
        } catch (_: Exception) {
            // Dispositivo sem suporte a efeitos: o app segue tocando normalmente.
            releaseEffects()
        }
    }

    private fun applyEffects() {
        try {
            equalizer?.let { eq ->
                eq.enabled = eqEnabled
                if (eqEnabled) {
                    val range = eq.bandLevelRange
                    for (band in 0 until eq.numberOfBands.toInt()) {
                        val level = eqLevels.getOrElse(band) { 0 }
                            .coerceIn(range[0].toInt(), range[1].toInt())
                        eq.setBandLevel(band.toShort(), level.toShort())
                    }
                }
            }
            loudness?.let { le ->
                le.setTargetGain(loudnessMb.coerceAtLeast(0))
                le.enabled = loudnessMb > 0
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        loudness = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseEffects()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
