package com.musicfree.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    private val scanner = MusicScanner(context)
    private val db = AppDatabase.get(context)
    private val playlistDao = db.playlistDao()
    private val hiddenSongDao = db.hiddenSongDao()
    private val favoriteDao = db.favoriteSongDao()
    private val statDao = db.playStatDao()

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

    val favoriteIds = favoriteDao.observeFavoriteIds()
    val playStats = statDao.observeStats()

    val favoriteSongs = combine(visibleSongs, favoriteIds) { songs, favs ->
        val byId = songs.associateBy { it.id }
        favs.mapNotNull { byId[it] }
    }

    val mostPlayedSongs = combine(visibleSongs, playStats) { songs, stats ->
        val byId = songs.associateBy { it.id }
        stats.filter { it.playCount > 0 }.mapNotNull { byId[it.songId] }.take(100)
    }

    val recentlyAddedSongs = visibleSongs.map { songs -> songs.sortedByDescending { it.dateAdded }.take(100) }

    suspend fun setFavorite(songId: Long, favorite: Boolean) {
        if (favorite) favoriteDao.add(FavoriteSongEntity(songId)) else favoriteDao.remove(songId)
    }

    suspend fun registerPlay(songId: Long) {
        val current = statDao.get(songId) ?: PlayStatEntity(songId)
        statDao.upsert(current.copy(playCount = current.playCount + 1, lastPlayedAt = System.currentTimeMillis()))
    }

    suspend fun refresh() {
        val songs = withContext(Dispatchers.IO) { scanner.scanAll() }
        _allSongs.value = songs
    }

    suspend fun hideSong(songId: Long) {
        hiddenSongDao.hide(HiddenSongEntity(songId))
    }

    suspend fun hideSongs(songIds: List<Long>) {
        songIds.forEach { hiddenSongDao.hide(HiddenSongEntity(it)) }
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
