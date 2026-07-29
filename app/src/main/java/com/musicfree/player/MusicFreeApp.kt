package com.musicfree.player

import android.app.Application
import com.musicfree.player.data.MusicRepository

class MusicFreeApp : Application() {
    lateinit var repository: MusicRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(this)
    }
}
