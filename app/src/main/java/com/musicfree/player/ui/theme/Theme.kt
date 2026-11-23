package com.musicfree.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.musicfree.player.data.ThemeMode

val SpotifyGreen = Color(0xFF1DB954)
val AppBackground = Color(0xFF121212)
val SurfaceDark = Color(0xFF181818)
val SurfaceElevated = Color(0xFF282828)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E8E)

private val MusicFreeDarkScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = Color(0xFFB3B3B3)
)

private val MusicFreeLightScheme = lightColorScheme(
    primary = Color(0xFF168F42),
    onPrimary = Color.White,
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF121212),
    surface = Color.White,
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF555555)
)

@Composable
fun MusicFreeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> MusicFreeDarkScheme
        else -> MusicFreeLightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
