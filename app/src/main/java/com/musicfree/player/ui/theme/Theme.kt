package com.musicfree.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SpotifyGreen = Color(0xFF1DB954)
val AppBackground = Color(0xFF121212)
val SurfaceDark = Color(0xFF181818)
val SurfaceElevated = Color(0xFF282828)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB3B3B3)

private val MusicFreeColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary
)

@Composable
fun MusicFreeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MusicFreeColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
