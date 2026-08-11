package com.musicfree.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    private val scanner = MusicScanner(context)
    private val db = AppDatabase.get(context)
    private val playlistDao = db.playlistDao()
    private val hiddenSongDao = db.hiddenSongDao()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    val hiddenIds = hiddenSongDao.observeHiddenIds()

    val visibleSongs = combine(_allSongs, hiddenIds) { songs, hidden ->
        val hiddenSet = hidden.toSet()
        songs.filterNot { it.id in hiddenSet }
    }

    val hiddenSongs = combine(_allSongs, hiddenIds) { songs, hidden ->
        val hiddenSet = hidden.toSet()
        songs.filter { it.id in hiddenSet }
    }

    suspend fun refresh() {
        val songs = withContext(Dispatchers.IO) { scanner.scanAll() }
        _allSongs.value = songs
    }

    suspend fun hideSong(songId: Long) {
        hiddenSongDao.hide(HiddenSongEntity(songId))
    }

    suspend fun unhideSong(songId: Long) {
        hiddenSongDao.unhide(songId)
    }

    fun observePlaylists() = playlistDao.observePlaylists()

    suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        playlistDao.renamePlaylist(playlistId, name)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val nextPosition = playlistDao.getMaxPosition(playlistId) + 1
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId, nextPosition))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun reorderPlaylist(playlistId: Long, orderedSongIds: List<Long>) {
        playlistDao.reorderPlaylist(playlistId, orderedSongIds)
    }

    fun observePlaylistSongIds(playlistId: Long) = playlistDao.observePlaylistSongs(playlistId)

    fun songsForPlaylist(playlistId: Long) = combine(
        observePlaylistSongIds(playlistId),
        visibleSongs
    ) { crossRefs, visible ->
        val songById = visible.associateBy { it.id }
        crossRefs.sortedBy { it.position }.mapNotNull { songById[it.songId] }
    }
}
