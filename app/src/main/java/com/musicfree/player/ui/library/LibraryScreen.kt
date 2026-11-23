package com.musicfree.player.ui.library

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicfree.player.MainViewModel
import com.musicfree.player.data.Song
import com.musicfree.player.data.SortOrder
import com.musicfree.player.ui.common.SongListItem
import com.musicfree.player.ui.theme.TextSecondary

private data class AlbumGroup(val name: String, val artist: String, val songs: List<Song>)

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val allSongs by viewModel.sortedSongs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSongs.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showHideShortDialog by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val tabs = listOf("Músicas", "Álbuns", "Artistas", "Favoritas", "Mais tocadas", "Recentes")

    fun List<Song>.filtered(): List<Song> =
        if (query.isBlank()) this else filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }

    val songs = remember(allSongs, query) { allSongs.filtered() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Buscar título, artista ou álbum") },
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
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = "Ordenar")
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            leadingIcon = { RadioButton(selected = order == sortOrder, onClick = null) },
                            onClick = {
                                sortMenuOpen = false
                                viewModel.setSortOrder(order)
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { showHideShortDialog = true }) {
                Icon(Icons.Filled.Timer, contentDescription = "Ocultar músicas com menos de 1 minuto")
            }
        }

        ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 8.dp) {
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
                emptyMessage = if (query.isBlank()) "Nenhuma música encontrada" else "Nada encontrado para \"$query\"",
                viewModel = viewModel,
                onAddToPlaylist = onAddToPlaylist
            )
            1 -> AlbumsTab(songs = songs, onOpenAlbum = onOpenAlbum)
            2 -> ArtistsTab(songs = songs, onOpenArtist = onOpenArtist)
            3 -> SongsTab(
                songs = favoriteSongs.filtered(),
                emptyMessage = "Nenhuma favorita ainda.\nUse o menu ⋮ de uma música para favoritar.",
                viewModel = viewModel,
                onAddToPlaylist = onAddToPlaylist
            )
            4 -> SongsTab(
                songs = mostPlayed.filtered(),
                emptyMessage = "Toque algumas músicas e elas aparecem aqui.",
                viewModel = viewModel,
                onAddToPlaylist = onAddToPlaylist
            )
            5 -> SongsTab(
                songs = recentlyAdded.filtered(),
                emptyMessage = "Nenhuma música adicionada recentemente.",
                viewModel = viewModel,
                onAddToPlaylist = onAddToPlaylist
            )
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
    emptyMessage: String,
    viewModel: MainViewModel,
    onAddToPlaylist: (Song) -> Unit
) {
    val playerState by viewModel.playerState.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    var editing by remember { mutableStateOf<Song?>(null) }
    var pendingEdit by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val song = editing
        val edit = pendingEdit
        if (result.resultCode == Activity.RESULT_OK && song != null && edit != null) {
            viewModel.applySongEdit(song, edit.first, edit.second, edit.third)
        }
        editing = null
        pendingEdit = null
    }
    editing?.let { song ->
        if (pendingEdit == null) {
            EditSongDialog(
                song = song,
                onDismiss = { editing = null },
                onSave = { title, artist, album ->
                    pendingEdit = Triple(title, artist, album)
                    writeLauncher.launch(IntentSenderRequest.Builder(viewModel.createWriteRequest(song)).build())
                }
            )
        }
    }

    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    emptyMessage,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
                TextButton(onClick = { viewModel.refreshLibrary() }) { Text("Atualizar biblioteca") }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { viewModel.playFromQueue(songs, 0, shuffle = false) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(" Tocar tudo")
            }
            Button(onClick = { viewModel.playFromQueue(songs, 0, shuffle = true) }) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
                Text(" Aleatório")
            }
            Text("${songs.size} músicas", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isCurrentlyPlaying = song.id == playerState.currentSongId,
                    isPlaying = playerState.isPlaying,
                    isHidden = false,
                    onClick = { viewModel.playFromQueue(songs, songs.indexOf(song)) },
                    onToggleHide = { viewModel.hideSong(song.id) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onPlaySingleLoop = { viewModel.playSingleRepeated(song) },
                    isFavorite = song.id in favorites,
                    onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                    onPlayNext = { viewModel.player.playNext(song) },
                    onAddToQueue = { viewModel.player.addToQueue(song) },
                    onEdit = { editing = song }
                )
            }
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
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(groups, key = { it.name }) { group ->
            Column(modifier = Modifier.clickable { onOpenAlbum(group.name) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextSecondary)
                    AsyncImage(
                        model = group.songs.first().albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    group.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "${group.artist} · ${group.songs.size}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenArtist(artist) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = TextSecondary)
                    AsyncImage(
                        model = list.first().albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${list.size} música(s)", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun EditSongDialog(song: Song, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar informações") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text("Álbum") }, singleLine = true)
                Text(
                    "Atualiza as informações no Android (biblioteca do aparelho). O arquivo de áudio em si não é alterado.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onSave(title.trim(), artist.trim(), album.trim()) }) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
