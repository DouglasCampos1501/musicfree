package com.musicfree.player.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?,
    val dateAdded: Long = 0L,
    val mimeType: String = "",
    val folder: String = "",
    val fileName: String = ""
)
