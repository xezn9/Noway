package com.mfp.filemanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val ICON_SIZE = floatPreferencesKey("icon_size")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val VIEW_MODE = intPreferencesKey("view_mode")
        val SEARCH_BLUR_ENABLED = booleanPreferencesKey("search_blur_enabled")
        val SWIPE_NAVIGATION_ENABLED = booleanPreferencesKey("swipe_navigation_enabled")
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
        val CACHE_SIZE_LIMIT = intPreferencesKey("cache_size_limit")
        val LAST_KNOWN_USED_BYTES = longPreferencesKey("last_known_used_bytes")
        val LAST_KNOWN_USED_TIME = longPreferencesKey("last_known_used_time")
        val THUMBNAIL_SEED = longPreferencesKey("thumbnail_seed")
        // Liquid glass tuning, each 0f..1f (0.5f = the original look of the app)
        val GLASS_BLUR = floatPreferencesKey("glass_blur")
        val GLASS_TRANSPARENCY = floatPreferencesKey("glass_transparency")
        val GLASS_REFRACTION = floatPreferencesKey("glass_refraction")
    }

    val themeMode: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[THEME_MODE] ?: 0 }

    val accentColor: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[ACCENT_COLOR] ?: com.mfp.filemanager.ui.theme.AccentPalette.default.argb }

    val iconSize: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[ICON_SIZE] ?: 1.0f }

    val showHiddenFiles: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SHOW_HIDDEN_FILES] ?: false }

    val viewMode: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[VIEW_MODE] ?: 0 }

    val searchBlurEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SEARCH_BLUR_ENABLED] ?: true }

    val swipeNavigationEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SWIPE_NAVIGATION_ENABLED] ?: false }

    val trashRetentionDays: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[TRASH_RETENTION_DAYS] ?: 30 }

    val autoUpdateEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[AUTO_UPDATE_ENABLED] ?: false }

    val lastUpdateCheckTime: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[LAST_UPDATE_CHECK_TIME] ?: 0L }

    val cacheSizeLimit: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[CACHE_SIZE_LIMIT] ?: 200 }

    val lastKnownUsedBytes: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[LAST_KNOWN_USED_BYTES] ?: -1L }

    val lastKnownUsedTime: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[LAST_KNOWN_USED_TIME] ?: -1L }

    val glassBlur: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[GLASS_BLUR] ?: 0.5f }

    val glassTransparency: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[GLASS_TRANSPARENCY] ?: 0.5f }

    val glassRefraction: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[GLASS_REFRACTION] ?: 0.5f }

    val thumbnailSeed: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[THUMBNAIL_SEED] ?: 0L }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setAccentColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[ACCENT_COLOR] = color
        }
    }

    suspend fun setIconSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[ICON_SIZE] = size
        }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_HIDDEN_FILES] = show
        }
    }

    suspend fun setViewMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[VIEW_MODE] = mode
        }
    }

    suspend fun setSearchBlurEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SEARCH_BLUR_ENABLED] = enabled
        }
    }

    suspend fun setSwipeNavigationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SWIPE_NAVIGATION_ENABLED] = enabled
        }
    }

    suspend fun setTrashRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[TRASH_RETENTION_DAYS] = days
        }
    }

    suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_UPDATE_ENABLED] = enabled
        }
    }

    suspend fun setLastUpdateCheckTime(time: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_UPDATE_CHECK_TIME] = time
        }
    }

    suspend fun setCacheSizeLimit(megabytes: Int) {
        context.dataStore.edit { preferences ->
            preferences[CACHE_SIZE_LIMIT] = megabytes
        }
    }

    suspend fun setLastKnownUsed(bytes: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_KNOWN_USED_BYTES] = bytes
            preferences[LAST_KNOWN_USED_TIME] = System.currentTimeMillis()
        }
    }

    suspend fun setThumbnailSeed(seed: Long) {
        context.dataStore.edit { preferences ->
            preferences[THUMBNAIL_SEED] = seed
        }
    }

    suspend fun setGlassBlur(value: Float) {
        context.dataStore.edit { it[GLASS_BLUR] = value.coerceIn(0f, 1f) }
    }

    suspend fun setGlassTransparency(value: Float) {
        context.dataStore.edit { it[GLASS_TRANSPARENCY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setGlassRefraction(value: Float) {
        context.dataStore.edit { it[GLASS_REFRACTION] = value.coerceIn(0f, 1f) }
    }
}
