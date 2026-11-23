package com.musicfree.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

class MusicScanner(private val context: Context) {

    /**
     * Lê todos os áudios do MediaStore. Não filtra mais por IS_MUSIC (que escondia .opus, .ogg,
     * .m4a sem tag, gravações, etc.): aceita qualquer MIME do tipo audio e só descarta toques,
     * notificações e alarmes.
     */
    fun scanAll(): List<Song> {
        val songs = mutableListOf<Song>()

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection = "(${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' " +
            "OR ${MediaStore.Audio.Media.MIME_TYPE} = 'application/ogg') " +
            "AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0 " +
            "AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 " +
            "AND ${MediaStore.Audio.Media.IS_ALARM} = 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val contentUri = ContentUris.withAppendedId(collection, id)
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )

                val rawTitle = cursor.getString(titleCol)
                val displayName = cursor.getString(nameCol)
                val title = when {
                    !rawTitle.isNullOrBlank() -> rawTitle
                    !displayName.isNullOrBlank() -> displayName.substringBeforeLast('.')
                    else -> "Desconhecido"
                }
                val artist = cursor.getString(artistCol)
                    ?.takeUnless { it == MediaStore.UNKNOWN_STRING || it.isBlank() }
                    ?: "Artista desconhecido"
                val album = cursor.getString(albumCol)
                    ?.takeUnless { it.isBlank() }
                    ?: "Álbum desconhecido"

                songs += Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = albumId,
                    durationMs = cursor.getLong(durationCol),
                    contentUri = contentUri,
                    albumArtUri = albumArtUri,
                    dateAdded = cursor.getLong(addedCol),
                    mimeType = cursor.getString(mimeCol).orEmpty(),
                    folder = cursor.getString(pathCol).orEmpty().trimEnd('/'),
                    fileName = displayName.orEmpty()
                )
            }
        }

        return songs
    }
}
