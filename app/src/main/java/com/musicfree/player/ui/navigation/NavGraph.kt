package com.musicfree.player.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.musicfree.player.MainViewModel
import com.musicfree.player.data.Song
import com.musicfree.player.ui.library.FilteredSongListScreen
import com.musicfree.player.ui.library.HiddenSongsScreen
import com.musicfree.player.ui.library.LibraryScreen
import com.musicfree.player.ui.player.MiniPlayer
import com.musicfree.player.ui.player.PlayerScreen
import com.musicfree.player.ui.playlists.AddToPlaylistSheet
import com.musicfree.player.ui.playlists.PlaylistDetailScreen
import com.musicfree.player.ui.playlists.PlaylistsScreen
import java.net.URLDecoder
import java.net.URLEncoder

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_PLAYLISTS = "playlists"
private const val ROUTE_HIDDEN = "hidden"

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(ROUTE_LIBRARY, "Biblioteca", Icons.Filled.LibraryMusic),
    BottomTab(ROUTE_PLAYLISTS, "Playlists", Icons.Filled.QueueMusic),
    BottomTab(ROUTE_HIDDEN, "Ocultas", Icons.Filled.VisibilityOff)
)

@Composable
fun AppNavGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val playerState by viewModel.playerState.collectAsState()
    val allSongs by viewModel.visibleSongs.collectAsState()
    val hiddenSongs by viewModel.hiddenSongs.collectAsState()
    var showPlayer by remember { mutableStateOf(false) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    val currentSong = (allSongs + hiddenSongs).find { it.id == playerState.currentSongId }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            Column {
                AnimatedVisibility(visible = currentSong != null && !showPlayer) {
                    MiniPlayer(
                        playerState = playerState,
                        currentSong = currentSong,
                        onClick = { showPlayer = true },
                        viewModel = viewModel
                    )
                }
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_LIBRARY,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(ROUTE_LIBRARY) {
                    LibraryScreen(
                        viewModel = viewModel,
                        onOpenAlbum = { album ->
                            navController.navigate("album/${encode(album)}")
                        },
                        onOpenArtist = { artist ->
                            navController.navigate("artist/${encode(artist)}")
                        },
                        onAddToPlaylist = { song -> songToAddToPlaylist = song }
                    )
                }
                composable("album/{name}") { backStackEntry ->
                    val name = decode(backStackEntry.arguments?.getString("name").orEmpty())
                    val songs = allSongs.filter { it.album == name }
                    FilteredSongListScreen(
                        title = name,
                        songs = songs,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onAddToPlaylist = { song -> songToAddToPlaylist = song }
                    )
                }
                composable("artist/{name}") { backStackEntry ->
                    val name = decode(backStackEntry.arguments?.getString("name").orEmpty())
                    val songs = allSongs.filter { it.artist == name }
                    FilteredSongListScreen(
                        title = name,
                        songs = songs,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onAddToPlaylist = { song -> songToAddToPlaylist = song }
                    )
                }
                composable(ROUTE_PLAYLISTS) {
                    PlaylistsScreen(
                        viewModel = viewModel,
                        onOpenPlaylist = { id, name ->
                            navController.navigate("playlist/$id/${encode(name)}")
                        }
                    )
                }
                composable("playlist/{id}/{name}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                    val name = decode(backStackEntry.arguments?.getString("name").orEmpty())
                    PlaylistDetailScreen(
                        playlistId = id,
                        playlistName = name,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(ROUTE_HIDDEN) {
                    HiddenSongsScreen(viewModel = viewModel)
                }
            }
        }
    }

    if (showPlayer) {
        PlayerScreen(
            viewModel = viewModel,
            currentSong = currentSong,
            onClose = { showPlayer = false }
        )
    }

    songToAddToPlaylist?.let { song ->
        AddToPlaylistSheet(
            song = song,
            viewModel = viewModel,
            onDismiss = { songToAddToPlaylist = null }
        )
    }
    }
}

private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")
