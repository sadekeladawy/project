package com.island.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.island.app.IslandApplication
import com.island.app.data.DisplayDurationOption
import com.island.app.data.IslandEventType
import com.island.app.data.IslandSettings
import com.island.app.model.AppTransitionDirection
import com.island.app.model.AppTransitionEvent
import com.island.app.model.IslandEvent
import com.island.app.service.AppForegroundTracker
import com.island.app.service.IslandEventBus
import com.island.app.service.MediaListenerService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IslandViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as IslandApplication).settingsRepository

    val settings: StateFlow<IslandSettings> = repo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IslandSettings.Default
    )

    val mediaState: StateFlow<IslandEvent.MusicPlayback?> = MediaListenerService.mediaState
    val isMediaListenerConnected: StateFlow<Boolean> = MediaListenerService.isListenerConnected
    val isAccessibilityConnected: StateFlow<Boolean> = AppForegroundTracker.isAccessibilityConnected

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setMasterEnabled(enabled) }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { repo.setAutoStartOnBoot(enabled) }
    }

    fun setHideInFullscreen(enabled: Boolean) {
        viewModelScope.launch { repo.setHideInFullscreen(enabled) }
    }

    fun setBluetoothAudioOnly(enabled: Boolean) {
        viewModelScope.launch { repo.setBluetoothAudioOnly(enabled) }
    }

    fun setAppExitAnimationEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setAppExitAnimationEnabled(enabled) }
    }

    fun updateLivePreview(settings: IslandSettings?) {
        IslandEventBus.updateLivePreviewSettings(settings)
    }

    fun setAnimationSpeed(speed: Float) {
        viewModelScope.launch {
            repo.setAnimationSpeed(speed)
            IslandEventBus.updateLivePreviewSettings(null)
        }
    }

    fun setDisplayDuration(option: DisplayDurationOption) {
        viewModelScope.launch { repo.setDisplayDuration(option) }
    }

    fun setPositionAndSize(
        offsetX: Float? = null,
        offsetY: Float? = null,
        idleWidthDp: Float? = null,
        idleHeightDp: Float? = null,
        cornerRadiusDp: Float? = null
    ) {
        viewModelScope.launch {
            repo.setPositionAndSize(offsetX, offsetY, idleWidthDp, idleHeightDp, cornerRadiusDp)
            IslandEventBus.updateLivePreviewSettings(null)
        }
    }

    fun setEventEnabled(type: IslandEventType, enabled: Boolean) {
        viewModelScope.launch { repo.setEventEnabled(type, enabled) }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            repo.resetToDefault()
            IslandEventBus.updateLivePreviewSettings(null)
        }
    }

    fun triggerTestEvent(event: IslandEvent) {
        IslandEventBus.emitEvent(event)
    }

    fun triggerTestAppExitAnimation() {
        val currentMedia = mediaState.value ?: IslandEvent.MusicPlayback(
            title = "Midnight Refraction",
            artist = "Kiasmos",
            isPlaying = true,
            positionMs = 68_000L,
            durationMs = 214_000L,
            packageName = "com.spotify.music"
        )
        IslandEventBus.emitEvent(currentMedia)
        AppForegroundTracker.emitManualTransition(
            AppTransitionEvent(
                direction = AppTransitionDirection.EXIT_TO_ISLAND,
                packageName = currentMedia.packageName.ifBlank { "com.spotify.music" },
                dominantColorArgb = currentMedia.dominantColorArgb,
                albumArt = currentMedia.albumArt,
                title = currentMedia.title,
                artist = currentMedia.artist
            )
        )
    }
}
