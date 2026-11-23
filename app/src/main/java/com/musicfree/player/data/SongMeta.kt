package com.musicfree.player.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Correções de metadados guardadas só no banco do app (os arquivos não são alterados).
 * Campos nulos = sem correção. Uma linha toda nula significa "já tentei e não achei nada".
 */
@Entity(tableName = "song_meta")
data class SongMetaEntity(
    @PrimaryKey val songId: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val coverPath: String? = null
)

@Dao
interface SongMetaDao {
    @Query("SELECT * FROM song_meta")
    fun observeAll(): Flow<List<SongMetaEntity>>

    @Query("SELECT songId FROM song_meta")
    suspend fun processedIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SongMetaEntity)

    @Query("SELECT coverPath FROM song_meta WHERE coverPath IS NOT NULL")
    suspend fun coverPaths(): List<String>

    @Query("DELETE FROM song_meta")
    suspend fun clear()
}
