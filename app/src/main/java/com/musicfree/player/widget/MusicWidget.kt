package com.musicfree.player.widget

import android.content.ComponentName
import android.content.Context

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.action.ActionParameters
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.musicfree.player.MainActivity
import com.musicfree.player.R
import com.musicfree.player.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val PREFS = "widget_state"

/** Estado mostrado pelo widget; gravado pelo [com.musicfree.player.playback.PlaybackService]. */
object WidgetState {
    fun save(context: Context, title: String, artist: String, playing: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", title).putString("artist", artist).putBoolean("playing", playing).apply()
    }
}

class MusicWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString("title", null)
        val artist = prefs.getString("artist", "").orEmpty()
        val playing = prefs.getBoolean("playing", false)

        provideContent {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF181818)))
                    .cornerRadius(20.dp)
                    .padding(12.dp)
                    .clickable(actionStartActivity(android.content.ComponentName(context, MainActivity::class.java))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        title ?: "MusicFree",
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        if (title == null) "Toque para abrir" else artist,
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Color(0xFFB3B3B3)), fontSize = 12.sp)
                    )
                }
                WidgetButton(R.drawable.ic_widget_prev, "Anterior", actionRunCallback<PreviousAction>())
                WidgetButton(
                    if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    "Play/Pause",
                    actionRunCallback<PlayPauseAction>()
                )
                WidgetButton(R.drawable.ic_widget_next, "Próxima", actionRunCallback<NextAction>())
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetButton(icon: Int, description: String, action: androidx.glance.action.Action) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        modifier = GlanceModifier
            .size(40.dp)
            .padding(8.dp)
            .clickable(action)
    )
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}

private suspend fun withController(context: Context, block: (MediaController) -> Unit) {
    withContext(Dispatchers.Main) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val controller = suspendCancellableCoroutine<MediaController?> { cont ->
            future.addListener({ cont.resume(runCatching { future.get() }.getOrNull()) }, MoreExecutors.directExecutor())
        } ?: return@withContext
        block(controller)
        delay(300) // dá tempo de o comando chegar ao serviço antes de soltar o controller
        controller.release()
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withController(context) { if (it.isPlaying) it.pause() else it.play() }
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withController(context) { it.seekToNextMediaItem() }
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withController(context) { it.seekToPreviousMediaItem() }
    }
}
