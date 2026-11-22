package com.island.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.islandDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "island_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val AUTO_START_BOOT = booleanPreferencesKey("auto_start_boot")
        val HIDE_FULLSCREEN = booleanPreferencesKey("hide_fullscreen")
        val BLUETOOTH_AUDIO_ONLY = booleanPreferencesKey("bluetooth_audio_only")
        val APP_EXIT_ANIMATION = booleanPreferencesKey("app_exit_animation")
        val ANIMATION_SPEED = floatPreferencesKey("animation_speed")
        val DISPLAY_DURATION_SEC = intPreferencesKey("display_duration_sec")
        val OFFSET_X = floatPreferencesKey("offset_x")
        val OFFSET_Y = floatPreferencesKey("offset_y")
        val IDLE_WIDTH = floatPreferencesKey("idle_width")
        val IDLE_HEIGHT = floatPreferencesKey("idle_height")
        val CORNER_RADIUS = floatPreferencesKey("corner_radius")

        fun eventKey(type: IslandEventType): Preferences.Key<Boolean> =
            booleanPreferencesKey("event_enabled_${type.key}")
    }

    val settingsFlow: Flow<IslandSettings> = context.islandDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val defaults = IslandSettings.Default
            val eventMap = IslandEventType.entries.associateWith { type ->
                prefs[Keys.eventKey(type)] ?: true
            }
            IslandSettings(
                masterEnabled = prefs[Keys.MASTER_ENABLED] ?: defaults.masterEnabled,
                autoStartOnBoot = prefs[Keys.AUTO_START_BOOT] ?: defaults.autoStartOnBoot,
                hideInFullscreen = prefs[Keys.HIDE_FULLSCREEN] ?: defaults.hideInFullscreen,
                bluetoothAudioOnly = prefs[Keys.BLUETOOTH_AUDIO_ONLY] ?: defaults.bluetoothAudioOnly,
                appExitAnimationEnabled = prefs[Keys.APP_EXIT_ANIMATION] ?: defaults.appExitAnimationEnabled,
                animationSpeed = (prefs[Keys.ANIMATION_SPEED] ?: defaults.animationSpeed).coerceIn(0.5f, 2.0f),
                displayDuration = DisplayDurationOption.fromSeconds(
                    prefs[Keys.DISPLAY_DURATION_SEC] ?: defaults.displayDuration.seconds
                ),
                offsetX = prefs[Keys.OFFSET_X] ?: defaults.offsetX,
                offsetY = prefs[Keys.OFFSET_Y] ?: defaults.offsetY,
                idleWidthDp = prefs[Keys.IDLE_WIDTH] ?: defaults.idleWidthDp,
                idleHeightDp = prefs[Keys.IDLE_HEIGHT] ?: defaults.idleHeightDp,
                cornerRadiusDp = prefs[Keys.CORNER_RADIUS] ?: defaults.cornerRadiusDp,
                enabledEvents = eventMap
            )
        }

    suspend fun setMasterEnabled(enabled: Boolean) {
        context.islandDataStore.edit { it[Keys.MASTER_ENABLED] = enabled }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.islandDataStore.edit { it[Keys.AUTO_START_BOOT] = enabled }
    }

    suspend fun setHideInFullscreen(enabled: Boolean) {
        context.islandDataStore.edit { it[Keys.HIDE_FULLSCREEN] = enabled }
    }

    suspend fun setBluetoothAudioOnly(enabled: Boolean) {
        context.islandDataStore.edit { it[Keys.BLUETOOTH_AUDIO_ONLY] = enabled }
    }

    suspend fun setAppExitAnimationEnabled(enabled: Boolean) {
        context.islandDataStore.edit { it[Keys.APP_EXIT_ANIMATION] = enabled }
    }

    suspend fun setAnimationSpeed(speed: Float) {
        context.islandDataStore.edit { it[Keys.ANIMATION_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setDisplayDuration(option: DisplayDurationOption) {
        context.islandDataStore.edit { it[Keys.DISPLAY_DURATION_SEC] = option.seconds }
    }

    suspend fun setPositionAndSize(
        offsetX: Float? = null,
        offsetY: Float? = null,
        idleWidthDp: Float? = null,
        idleHeightDp: Float? = null,
        cornerRadiusDp: Float? = null
    ) {
        context.islandDataStore.edit { prefs ->
            offsetX?.let { prefs[Keys.OFFSET_X] = it.coerceIn(-120f, 120f) }
            offsetY?.let { prefs[Keys.OFFSET_Y] = it.coerceIn(0f, 96f) }
            idleWidthDp?.let { prefs[Keys.IDLE_WIDTH] = it.coerceIn(84f, 220f) }
            idleHeightDp?.let { prefs[Keys.IDLE_HEIGHT] = it.coerceIn(24f, 54f) }
            cornerRadiusDp?.let { prefs[Keys.CORNER_RADIUS] = it.coerceIn(12f, 36f) }
        }
    }

    suspend fun setEventEnabled(type: IslandEventType, enabled: Boolean) {
        context.islandDataStore.edit { it[Keys.eventKey(type)] = enabled }
    }

    suspend fun resetToDefault() {
        val d = IslandSettings.Default
        context.islandDataStore.edit { prefs ->
            prefs[Keys.OFFSET_X] = d.offsetX
            prefs[Keys.OFFSET_Y] = d.offsetY
            prefs[Keys.IDLE_WIDTH] = d.idleWidthDp
            prefs[Keys.IDLE_HEIGHT] = d.idleHeightDp
            prefs[Keys.CORNER_RADIUS] = d.cornerRadiusDp
            prefs[Keys.DISPLAY_DURATION_SEC] = d.displayDuration.seconds
            prefs[Keys.HIDE_FULLSCREEN] = d.hideInFullscreen
            prefs[Keys.BLUETOOTH_AUDIO_ONLY] = d.bluetoothAudioOnly
            prefs[Keys.AUTO_START_BOOT] = d.autoStartOnBoot
            prefs[Keys.APP_EXIT_ANIMATION] = d.appExitAnimationEnabled
            prefs[Keys.ANIMATION_SPEED] = d.animationSpeed
            IslandEventType.entries.forEach { prefs[Keys.eventKey(it)] = true }
        }
    }
}
