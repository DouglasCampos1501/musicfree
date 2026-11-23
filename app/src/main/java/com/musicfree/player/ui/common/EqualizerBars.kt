package com.musicfree.player.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicfree.player.ui.theme.SpotifyGreen

/** Três barrinhas animadas estilo equalizador; ficam paradas quando [animate] é falso. */
@Composable
fun EqualizerBars(animate: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val durations = listOf(450, 650, 550)
    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        durations.forEachIndexed { i, duration ->
            val fraction by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Reverse),
                label = "bar$i"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(if (animate) fraction else 0.4f)
                    .background(SpotifyGreen, RoundedCornerShape(2.dp))
            )
        }
    }
}
