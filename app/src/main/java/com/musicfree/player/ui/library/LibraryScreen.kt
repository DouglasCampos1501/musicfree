package com.musicfree.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicfree.player.MainViewModel
import com.musicfree.player.data.Song
import com.musicfree.player.ui.common.SongListItem

private data class AlbumGroup(val name: String, val artist: String, val songs: List<Song>)

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val allSongs by viewModel.visibleSongs.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showHideShortDialog by remember { mutableStateOf(false) }
    val tabs = listOf("Músicas", "Álbuns", "Artistas")

    val songs = remember(allSongs, query) {
        if (query.isBlank()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Buscar por título ou artista") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpar busca")
                        }
                    }
                }
            )
            IconButton(onClick = { showHideShortDialog = true }) {
                Icon(Icons.Filled.Timer, contentDescription = "Ocultar músicas com menos de 1 minuto")
            }
        }

        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (tabIndex) {
            0 -> SongsTab(
                songs = songs,
                currentSongId = playerState.currentSongId,
                onSongClick = { index -> viewModel.playFromQueue(songs, index) },
                viewModel = viewModel,
                onAddToPlaylist = onAddToPlaylist
            )
            1 -> AlbumsTab(songs = songs, onOpenAlbum = onOpenAlbum)
            2 -> ArtistsTab(songs = songs, onOpenArtist = onOpenArtist)
        }
    }

    if (showHideShortDialog) {
        val count = viewModel.countShortSongs()
        AlertDialog(
            onDismissRequest = { showHideShortDialog = false },
            title = { Text("Ocultar músicas curtas") },
            text = {
                Text(
                    if (count == 0) {
                        "Nenhuma música com menos de 1 minuto encontrada."
                    } else {
                        "$count música(s) com menos de 1 minuto serão ocultadas. Você pode reexibi-las depois na aba Ocultas."
                    }
                )
            },
            confirmButton = {
                if (count > 0) {
                    TextButton(onClick = {
                        viewModel.hideShortSongs()
                        showHideShortDialog = false
                    }) { Text("Ocultar") }
                } else {
                    TextButton(onClick = { showHideShortDialog = false }) { Text("OK") }
                }
            },
            dismissButton = {
                if (count > 0) {
                    TextButton(onClick = { showHideShortDialog = false }) { Text("Cancelar") }
                }
            }
        )
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    currentSongId: Long?,
    onSongClick: (Int) -> Unit,
    viewModel: MainViewModel,
    onAddToPlaylist: (Song) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(songs, key = { it.id }) { song ->
            val index = songs.indexOf(song)
            SongListItem(
                song = song,
                isCurrentlyPlaying = song.id == currentSongId,
                isHidden = false,
                onClick = { onSongClick(index) },
                onToggleHide = { viewModel.hideSong(song.id) },
                onAddToPlaylist = { onAddToPlaylist(song) },
                onPlaySingleLoop = { viewModel.playSingleRepeated(song) }
            )
        }
    }
}

@Composable
private fun AlbumsTab(songs: List<Song>, onOpenAlbum: (String) -> Unit) {
    val groups = remember(songs) {
        songs.groupBy { it.album }
            .map { (album, list) -> AlbumGroup(album, list.first().artist, list) }
            .sortedBy { it.name.lowercase() }
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(groups, key = { it.name }) { group ->
            SimpleGroupRow(
                title = group.name,
                subtitle = "${group.artist} · ${group.songs.size} música(s)",
                onClick = { onOpenAlbum(group.name) }
            )
        }
    }
}

@Composable
private fun ArtistsTab(songs: List<Song>, onOpenArtist: (String) -> Unit) {
    val groups = remember(songs) {
        songs.groupBy { it.artist }
            .toList()
            .sortedBy { it.first.lowercase() }
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(groups, key = { it.first }) { (artist, list) ->
            SimpleGroupRow(
                title = artist,
                subtitle = "${list.size} música(s)",
                onClick = { onOpenArtist(artist) }
            )
        }
    }
}

@Composable
private fun SimpleGroupRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}
