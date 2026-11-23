package com.musicfree.player.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "hidden_songs")
data class HiddenSongEntity(
    @PrimaryKey val songId: Long,
    val hiddenAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L
)
