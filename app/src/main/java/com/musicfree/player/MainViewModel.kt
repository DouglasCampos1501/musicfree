package com.musicfree.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicfree.player.data.Song
import com.musicfree.player.playback.PlayerController
import android.net.Uri
import com.musicfree.player.data.SortOrder
import com.musicfree.player.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicFreeApp
    val repository = app.repository
    val prefs = app.prefs
    val player = PlayerController(application, viewModelScope, prefs)

    val visibleSongs: StateFlow<List<Song>> = repository.visibleSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenSongs: StateFlow<List<Song>> = repository.hiddenSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerState = player.state

    val sortOrder: StateFlow<SortOrder> = prefs.sortOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortOrder.TITLE)

    /** Músicas visíveis já na ordem escolhida pelo usuário. */
    val sortedSongs: StateFlow<List<Song>> = combine(visibleSongs, sortOrder) { songs, order ->
        when (order) {
            SortOrder.TITLE -> songs.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            SortOrder.RECENT -> songs.sortedByDescending { it.dateAdded }
            SortOrder.DURATION -> songs.sortedByDescending { it.durationMs }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<Long>> = repository.favoriteIds.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mostPlayedSongs: StateFlow<List<Song>> = repository.mostPlayedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentlyAddedSongs: StateFlow<List<Song>> = repository.recentlyAddedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages = player.messages

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val dynamicColor: StateFlow<Boolean> = prefs.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val eqEnabled: StateFlow<Boolean> = prefs.eqEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val eqLevels: StateFlow<List<Int>> = prefs.eqLevels
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val loudnessMb: StateFlow<Int> = prefs.loudnessMb
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _toast = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Mensagens de operações do ViewModel (import/export de playlist). Mescladas com as do player na UI. */
    val toast = _toast

    val lyricsRepository = app.lyrics
    private val _lyricsVersion = kotlinx.coroutines.flow.MutableStateFlow(0)
    /** Muda toda vez que uma letra é importada/removida, para a UI recarregar. */
    val lyricsVersion: StateFlow<Int> = _lyricsVersion
    val lyricsFolder: StateFlow<String?> = prefs.lyricsTree
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun importLyrics(song: Song, uri: Uri) {
        viewModelScope.launch {
            val ok = lyricsRepository.import(song, uri)
            _toast.tryEmit(if (ok) "Letra importada" else "Não foi possível ler o arquivo de letra")
            _lyricsVersion.value++
        }
    }

    fun removeLyrics(song: Song) {
        viewModelScope.launch {
            lyricsRepository.remove(song)
            _lyricsVersion.value++
        }
    }

    fun setLyricsFolder(uri: Uri?) {
        viewModelScope.launch {
            if (uri != null) {
                runCatching {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            prefs.setLyricsTree(uri?.toString())
            lyricsRepository.refreshFolderIndex()
            _lyricsVersion.value++
        }
    }

    /** Pedido de permissão de escrita do sistema (Android 11+) para editar os metadados da [song]. */
    fun createWriteRequest(song: Song): android.content.IntentSender =
        android.provider.MediaStore.createWriteRequest(
            getApplication<Application>().contentResolver, listOf(song.contentUri)
        ).intentSender

    /** Atualiza título/artista/álbum no MediaStore (vale para toda a biblioteca do aparelho). */
    fun applySongEdit(song: Song, title: String, artist: String, album: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.TITLE, title)
                put(android.provider.MediaStore.Audio.Media.ARTIST, artist)
                put(android.provider.MediaStore.Audio.Media.ALBUM, album)
            }
            val rows = runCatching {
                getApplication<Application>().contentResolver.update(song.contentUri, values, null, null)
            }.getOrDefault(0)
            if (rows > 0) repository.refresh()
            _toast.tryEmit(if (rows > 0) "Informações atualizadas" else "Não foi possível atualizar a música")
        }
    }

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { prefs.setThemeMode(mode) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { prefs.setDynamicColor(enabled) } }

    /** Grava a playlist em formato .m3u no [uri] escolhido pelo usuário (Storage Access Framework). */
    fun exportPlaylistM3u(uri: Uri, songs: List<Song>) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { w ->
                    w.appendLine("#EXTM3U")
                    songs.forEach { s ->
                        w.appendLine("#EXTINF:${s.durationMs / 1000},${s.artist} - ${s.title}")
                        w.appendLine(if (s.folder.isNotEmpty()) "${s.folder}/${s.fileName}" else s.fileName)
                    }
                } != null
            }.getOrDefault(false)
            _toast.tryEmit(if (ok) "Playlist exportada (${songs.size} músicas)" else "Falha ao exportar a playlist")
        }
    }

    /** Importa um .m3u: casa as linhas com as músicas da biblioteca pelo nome do arquivo. */
    fun importPlaylistM3u(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val result = runCatching {
                val lines = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }.orEmpty()
                val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    ?.substringBeforeLast('.')
                    ?: "Playlist importada"
                val byName = (visibleSongs.value + hiddenSongs.value).groupBy { it.fileName.lowercase() }
                val matched = lines
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .mapNotNull { line ->
                        byName[line.substringAfterLast('/').substringAfterLast('\\').lowercase()]?.firstOrNull()
                    }
                    .distinctBy { it.id }
                val total = lines.count { it.isNotBlank() && !it.trim().startsWith("#") }
                val id = repository.createPlaylist(name)
                matched.forEach { repository.addSongToPlaylist(id, it.id) }
                "Playlist \"$name\" importada: ${matched.size} de $total músicas encontradas"
            }.getOrElse { "Falha ao importar a playlist" }
            _toast.tryEmit(result)
        }
    }

    init {
        player.connect()
        viewModelScope.launch { repository.refresh() }
        // Conta uma reprodução sempre que a faixa atual muda (alimenta "Mais tocadas").
        viewModelScope.launch {
            player.state.map { it.currentSongId }.distinctUntilChanged().filterNotNull().collect {
                repository.registerPlay(it)
            }
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { prefs.setSortOrder(order) }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { repository.setFavorite(songId, songId !in favoriteIds.value) }
    }

    fun refreshLibrary() {
        viewModelScope.launch { repository.refresh() }
    }

    // ---- Importação do Google Drive ----
    private val drive = com.musicfree.player.data.DriveImporter(application)
    val driveProgress = drive.progress
    val driveApiKey: StateFlow<String> = prefs.driveApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    private var driveJob: kotlinx.coroutines.Job? = null

    fun setDriveApiKey(key: String) {
        viewModelScope.launch { prefs.setDriveApiKey(key) }
    }

    fun importFromDrive(link: String) {
        val folderId = drive.parseFolderId(link)
        val key = driveApiKey.value
        if (folderId == null) { _toast.tryEmit("Link da pasta inválido"); return }
        if (key.isBlank()) { _toast.tryEmit("Informe a chave de API do Google Drive"); return }
        if (driveJob?.isActive == true) return
        driveJob = viewModelScope.launch {
            drive.import(folderId, key)
            kotlinx.coroutines.delay(1500) // dá tempo ao MediaStore de indexar os arquivos novos
            repository.refresh()
        }
    }

    fun cancelDriveImport() {
        driveJob?.cancel()
        viewModelScope.launch { kotlinx.coroutines.delay(500); repository.refresh() }
    }

    // ---- Correção automática de nomes, capas e letras ----
    private val fixer = com.musicfree.player.data.MetadataFixer(application, repository, app.lyrics)
    val fixProgress = fixer.progress
    private var fixJob: kotlinx.coroutines.Job? = null

    fun fixMetadata(onlyIncomplete: Boolean) {
        if (fixJob?.isActive == true) return
        fixJob = viewModelScope.launch {
            fixer.run(onlyIncomplete)
            _lyricsVersion.value++
        }
    }

    fun cancelFix() { fixJob?.cancel() }

    fun undoFixes() {
        viewModelScope.launch {
            repository.clearAllMeta()
            _toast.tryEmit("Correções automáticas desfeitas")
        }
    }

    fun playFromQueue(queue: List<Song>, startIndex: Int, shuffle: Boolean? = null) {
        player.playQueue(queue, startIndex, shuffle)
    }

    fun playSingleRepeated(song: Song) {
        player.playSingle(song)
    }

    fun hideSong(songId: Long) {
        viewModelScope.launch { repository.hideSong(songId) }
    }

    /** Oculta de uma vez todas as músicas visíveis com duração menor que [thresholdMs]. */
    fun hideShortSongs(thresholdMs: Long = 60_000L) {
        val shortSongIds = visibleSongs.value.filter { it.durationMs < thresholdMs }.map { it.id }
        if (shortSongIds.isEmpty()) return
        viewModelScope.launch { repository.hideSongs(shortSongIds) }
    }

    fun countShortSongs(thresholdMs: Long = 60_000L): Int =
        visibleSongs.value.count { it.durationMs < thresholdMs }

    fun unhideSong(songId: Long) {
        viewModelScope.launch { repository.unhideSong(songId) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.addSongToPlaylist(playlistId, songId) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.removeSongFromPlaylist(playlistId, songId) }
    }

    fun reorderPlaylist(playlistId: Long, orderedSongIds: List<Long>) {
        viewModelScope.launch { repository.reorderPlaylist(playlistId, orderedSongIds) }
    }

    fun songsForPlaylist(playlistId: Long) = repository.songsForPlaylist(playlistId)

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
