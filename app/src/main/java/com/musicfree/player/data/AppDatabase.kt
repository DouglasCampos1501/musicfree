package com.musicfree.player.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaylistEntity::class, PlaylistSongCrossRef::class, HiddenSongEntity::class,
        FavoriteSongEntity::class, PlayStatEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun hiddenSongDao(): HiddenSongDao
    abstract fun favoriteSongDao(): FavoriteSongDao
    abstract fun playStatDao(): PlayStatDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_songs` (`songId` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `play_stats` (`songId` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "musicfree.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}
