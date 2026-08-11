package com.musicfree.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicfree.player.data.Song
import com.musicfree.player.playback.PlayerController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicFreeApp
    val repository = app.repository
    val player = PlayerController(application, viewModelScope)

    val visibleSongs: StateFlow<List<Song>> = repository.visibleSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenSongs: StateFlow<List<Song>> = repository.hiddenSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerState = player.state

    init {
        player.connect()
        viewModelScope.launch { repository.refresh() }
    }

    fun refreshLibrary() {
        viewModelScope.launch { repository.refresh() }
    }

    fun playFromQueue(queue: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        player.playQueue(queue, startIndex, shuffle)
    }

    fun playSingleRepeated(song: Song) {
        player.playSingle(song)
    }

    fun hideSong(songId: Long) {
        viewModelScope.launch { repository.hideSong(songId) }
    }

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
