package com.musicfree.player

import android.app.Application
import com.musicfree.player.data.MusicRepository
import com.musicfree.player.data.LyricsRepository
import com.musicfree.player.data.Prefs

class MusicFreeApp : Application() {
    lateinit var repository: MusicRepository
        private set

    lateinit var prefs: Prefs
        private set
    lateinit var lyrics: LyricsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(this)
        prefs = Prefs(this)
        lyrics = LyricsRepository(this, prefs)
    }
}
