package com.musicfree.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "musicfree_prefs")

enum class SortOrder(val label: String) {
    TITLE("Título"),
    ARTIST("Artista"),
    RECENT("Mais recentes"),
    DURATION("Duração")
}

enum class ThemeMode(val label: String) {
    SYSTEM("Seguir o sistema"),
    DARK("Escuro"),
    LIGHT("Claro")
}

/** Preferências persistidas do app (repetição, aleatório, ordenação, etc.). */
class Prefs(private val context: Context) {

    private object Keys {
        val REPEAT = intPreferencesKey("repeat_mode")      // valores de Player.REPEAT_MODE_* (0 off, 1 one, 2 all)
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val SORT = stringPreferencesKey("sort_order")
        val SPEED = floatPreferencesKey("playback_speed")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_LEVELS = stringPreferencesKey("eq_levels")   // níveis em milibéis separados por vírgula
        val LOUDNESS = intPreferencesKey("loudness_mb")
        val DRIVE_KEY = stringPreferencesKey("drive_api_key")
        val LYRICS_TREE = stringPreferencesKey("lyrics_tree_uri")
    }

    val repeatMode: Flow<Int> = context.dataStore.data.map { it[Keys.REPEAT] ?: 0 }
    val shuffle: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHUFFLE] ?: false }
    val sortOrder: Flow<SortOrder> = context.dataStore.data.map {
        runCatching { SortOrder.valueOf(it[Keys.SORT] ?: "") }.getOrDefault(SortOrder.TITLE)
    }
    val speed: Flow<Float> = context.dataStore.data.map { it[Keys.SPEED] ?: 1f }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME] ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC] ?: false }
    val eqEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.EQ_ENABLED] ?: false }
    val eqLevels: Flow<List<Int>> = context.dataStore.data.map { p ->
        p[Keys.EQ_LEVELS].orEmpty().split(',').mapNotNull { it.toIntOrNull() }
    }
    val loudnessMb: Flow<Int> = context.dataStore.data.map { it[Keys.LOUDNESS] ?: 0 }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC] = enabled }
    suspend fun setEq(enabled: Boolean, levels: List<Int>, loudnessMb: Int) = context.dataStore.edit {
        it[Keys.EQ_ENABLED] = enabled
        it[Keys.EQ_LEVELS] = levels.joinToString(",")
        it[Keys.LOUDNESS] = loudnessMb
    }

    val driveApiKey: Flow<String> = context.dataStore.data.map { it[Keys.DRIVE_KEY].orEmpty() }
    suspend fun setDriveApiKey(key: String) = context.dataStore.edit { it[Keys.DRIVE_KEY] = key.trim() }

    val lyricsTree: Flow<String?> = context.dataStore.data.map { it[Keys.LYRICS_TREE] }
    suspend fun setLyricsTree(uri: String?) = context.dataStore.edit {
        if (uri == null) it.remove(Keys.LYRICS_TREE) else it[Keys.LYRICS_TREE] = uri
    }

    suspend fun setRepeatMode(mode: Int) = context.dataStore.edit { it[Keys.REPEAT] = mode }
    suspend fun setShuffle(enabled: Boolean) = context.dataStore.edit { it[Keys.SHUFFLE] = enabled }
    suspend fun setSortOrder(order: SortOrder) = context.dataStore.edit { it[Keys.SORT] = order.name }
    suspend fun setSpeed(speed: Float) = context.dataStore.edit { it[Keys.SPEED] = speed }
}
