package com.island.app.service

import com.island.app.data.IslandSettings
import com.island.app.model.IslandEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface MediaCommand {
    data object PlayPause : MediaCommand
    data object Next : MediaCommand
    data object Previous : MediaCommand
    data class SeekTo(val positionMs: Long) : MediaCommand
}

sealed interface CallCommand {
    data object Accept : CallCommand
    data object Decline : CallCommand
}

object IslandEventBus {
    private val _events = MutableSharedFlow<IslandEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<IslandEvent> = _events.asSharedFlow()

    private val _mediaCommands = MutableSharedFlow<MediaCommand>(extraBufferCapacity = 8)
    val mediaCommands: SharedFlow<MediaCommand> = _mediaCommands.asSharedFlow()

    private val _callCommands = MutableSharedFlow<CallCommand>(extraBufferCapacity = 4)
    val callCommands: SharedFlow<CallCommand> = _callCommands.asSharedFlow()

    private val _livePreviewSettings = MutableStateFlow<IslandSettings?>(null)
    val livePreviewSettings: StateFlow<IslandSettings?> = _livePreviewSettings.asStateFlow()

    fun emitEvent(event: IslandEvent) {
        _events.tryEmit(event)
    }

    fun sendMediaCommand(command: MediaCommand) {
        _mediaCommands.tryEmit(command)
    }

    fun sendCallCommand(command: CallCommand) {
        _callCommands.tryEmit(command)
    }

    fun updateLivePreviewSettings(settings: IslandSettings?) {
        _livePreviewSettings.value = settings
    }
}
