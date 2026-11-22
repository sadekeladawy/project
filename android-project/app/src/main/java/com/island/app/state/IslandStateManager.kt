package com.island.app.state

import androidx.compose.runtime.Immutable
import com.island.app.data.IslandEventType
import com.island.app.data.IslandSettings
import com.island.app.model.AppTransitionDirection
import com.island.app.model.AppTransitionEvent
import com.island.app.model.IslandDisplayMode
import com.island.app.model.IslandEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class IslandUiState(
    val activeEvent: IslandEvent? = null,
    val queuedCount: Int = 0,
    val displayMode: IslandDisplayMode = IslandDisplayMode.IDLE,
    val persistentMusic: IslandEvent.MusicPlayback? = null,
    val isFullscreenHidden: Boolean = false,
    val activeTransition: AppTransitionEvent? = null
)

class IslandStateManager(
    private val scope: CoroutineScope,
    private val settingsProvider: () -> IslandSettings
) {
    private val _uiState = MutableStateFlow(IslandUiState())
    val uiState: StateFlow<IslandUiState> = _uiState.asStateFlow()

    // Accessed strictly on scope's Main.immediate dispatcher to prevent ConcurrentModificationException
    private val eventQueue = ArrayDeque<IslandEvent>()
    private var autoCollapseJob: Job? = null
    private var postExitShrinkJob: Job? = null

    fun postEvent(event: IslandEvent) {
        scope.launch {
            handlePostEvent(event)
        }
    }

    private fun handlePostEvent(event: IslandEvent) {
        val settings = settingsProvider()
        if (!settings.masterEnabled) {
            return
        }

        if (event is IslandEvent.MusicSessionEnded) {
            handleMusicSessionEnded()
            return
        }

        if (!settings.isEventEnabled(event.type)) {
            return
        }

        if (event is IslandEvent.BluetoothChanged &&
            settings.bluetoothAudioOnly &&
            !event.isAudioDevice
        ) {
            return
        }

        if (event is IslandEvent.MusicPlayback) {
            handleMusicUpdate(event)
            return
        }

        if (event is IslandEvent.IncomingCall) {
            eventQueue.removeAll { it is IslandEvent.IncomingCall }
            showImmediate(event, IslandDisplayMode.EXPANDED)
            return
        }

        val current = _uiState.value.activeEvent
        if (current == null || current is IslandEvent.MusicPlayback) {
            showImmediate(event, IslandDisplayMode.COMPACT)
        } else {
            val existingIndex = eventQueue.indexOfFirst { it.type == event.type }
            if (existingIndex >= 0) {
                eventQueue[existingIndex] = event
            } else {
                if (eventQueue.size >= 4) {
                    eventQueue.removeFirst()
                }
                eventQueue.addLast(event)
            }
            _uiState.update { it.copy(queuedCount = eventQueue.size) }
        }
    }

    fun startAppTransition(transition: AppTransitionEvent) {
        scope.launch {
            val settings = settingsProvider()
            if (!settings.masterEnabled || !settings.appExitAnimationEnabled) return@launch
            if (!settings.isEventEnabled(IslandEventType.MUSIC_PLAYER)) return@launch

            postExitShrinkJob?.cancel()
            _uiState.update { state ->
                if (transition.direction == AppTransitionDirection.ENTER_FROM_ISLAND) {
                    // Collapse island into compact pill while reverse card grows out of it
                    state.copy(
                        activeTransition = transition,
                        displayMode = if (state.persistentMusic != null) IslandDisplayMode.COMPACT else IslandDisplayMode.IDLE
                    )
                } else {
                    state.copy(activeTransition = transition)
                }
            }
        }
    }

    fun onExitAnimationReachedIsland() {
        scope.launch {
            val music = _uiState.value.persistentMusic ?: return@launch
            autoCollapseJob?.cancel()
            postExitShrinkJob?.cancel()

            // Expand into music card with spring bounce, hold for 2 seconds, then shrink to compact capsule
            _uiState.update {
                it.copy(
                    activeEvent = music,
                    displayMode = IslandDisplayMode.EXPANDED
                )
            }

            postExitShrinkJob = scope.launch {
                delay(2000L)
                _uiState.update { state ->
                    if (state.activeEvent is IslandEvent.MusicPlayback &&
                        state.displayMode == IslandDisplayMode.EXPANDED
                    ) {
                        state.copy(displayMode = IslandDisplayMode.COMPACT)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun onAppTransitionFinished(transitionId: String) {
        scope.launch {
            _uiState.update { state ->
                if (state.activeTransition?.id == transitionId) {
                    state.copy(activeTransition = null)
                } else {
                    state
                }
            }
        }
    }

    fun clearIncomingCall() {
        scope.launch {
            val current = _uiState.value.activeEvent
            if (current is IslandEvent.IncomingCall) {
                advanceOrCollapse()
            } else {
                eventQueue.removeAll { it is IslandEvent.IncomingCall }
                _uiState.update { it.copy(queuedCount = eventQueue.size) }
            }
        }
    }

    private fun handleMusicSessionEnded() {
        postExitShrinkJob?.cancel()
        val state = _uiState.value
        val wasMusicActive = state.activeEvent is IslandEvent.MusicPlayback
        if (wasMusicActive) {
            _uiState.update { it.copy(persistentMusic = null) }
            advanceOrCollapse()
        } else {
            _uiState.update { it.copy(persistentMusic = null) }
        }
    }

    private fun handleMusicUpdate(music: IslandEvent.MusicPlayback) {
        if (music.title.isBlank()) {
            handleMusicSessionEnded()
            return
        }

        _uiState.update { state ->
            val isMusicActive = state.activeEvent is IslandEvent.MusicPlayback
            val shouldPromote = state.activeEvent == null || isMusicActive
            if (shouldPromote) {
                val isExpanded = state.displayMode == IslandDisplayMode.EXPANDED ||
                    state.displayMode == IslandDisplayMode.FULL_EXPANDED
                val targetMode = when {
                    isExpanded -> state.displayMode
                    music.isPlaying -> IslandDisplayMode.COMPACT
                    else -> IslandDisplayMode.IDLE
                }
                state.copy(
                    activeEvent = if (targetMode == IslandDisplayMode.IDLE) null else music,
                    persistentMusic = music,
                    displayMode = targetMode
                )
            } else {
                state.copy(persistentMusic = music)
            }
        }
    }

    private fun showImmediate(event: IslandEvent, initialMode: IslandDisplayMode) {
        autoCollapseJob?.cancel()
        _uiState.update {
            it.copy(
                activeEvent = event,
                displayMode = initialMode,
                queuedCount = eventQueue.size
            )
        }
        if (!event.isSticky) {
            scheduleAutoCollapse(settingsProvider().displayDuration.millis)
        }
    }

    private fun scheduleAutoCollapse(durationMs: Long) {
        autoCollapseJob?.cancel()
        autoCollapseJob = scope.launch {
            delay(durationMs)
            advanceOrCollapse()
        }
    }

    private fun advanceOrCollapse() {
        autoCollapseJob?.cancel()
        if (eventQueue.isNotEmpty()) {
            val next = eventQueue.removeFirst()
            autoCollapseJob = scope.launch {
                _uiState.update { it.copy(queuedCount = eventQueue.size) }
                delay(120L)
                showImmediate(
                    next,
                    if (next is IslandEvent.IncomingCall) IslandDisplayMode.EXPANDED else IslandDisplayMode.COMPACT
                )
            }
        } else {
            val music = _uiState.value.persistentMusic
            if (music != null && music.isPlaying) {
                _uiState.update {
                    it.copy(
                        activeEvent = music,
                        displayMode = IslandDisplayMode.COMPACT,
                        queuedCount = 0
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        activeEvent = null,
                        displayMode = IslandDisplayMode.IDLE,
                        queuedCount = 0
                    )
                }
            }
        }
    }

    private fun resolveExpandableCandidate(state: IslandUiState): IslandEvent? {
        val candidate = state.activeEvent ?: state.persistentMusic
        if (candidate is IslandEvent.MusicPlayback && candidate.title.isBlank()) {
            return null
        }
        if (candidate is IslandEvent.MusicSessionEnded) {
            return null
        }
        return candidate
    }

    fun onIslandTap() {
        scope.launch {
            postExitShrinkJob?.cancel()
            val state = _uiState.value
            val active = resolveExpandableCandidate(state)
            when (state.displayMode) {
                IslandDisplayMode.IDLE -> {
                    if (active != null) {
                        autoCollapseJob?.cancel()
                        _uiState.update {
                            it.copy(activeEvent = active, displayMode = IslandDisplayMode.EXPANDED)
                        }
                        if (!active.isSticky) {
                            scheduleAutoCollapse(settingsProvider().displayDuration.millis + 1500L)
                        }
                    }
                }
                IslandDisplayMode.COMPACT -> {
                    if (active == null) {
                        collapseInternal()
                        return@launch
                    }
                    autoCollapseJob?.cancel()
                    _uiState.update { it.copy(activeEvent = active, displayMode = IslandDisplayMode.EXPANDED) }
                    if (!active.isSticky) {
                        scheduleAutoCollapse(settingsProvider().displayDuration.millis + 2000L)
                    }
                }
                IslandDisplayMode.EXPANDED,
                IslandDisplayMode.FULL_EXPANDED -> collapseInternal()
            }
        }
    }

    fun onIslandLongPress() {
        scope.launch {
            postExitShrinkJob?.cancel()
            val state = _uiState.value
            val active = resolveExpandableCandidate(state) ?: return@launch
            autoCollapseJob?.cancel()
            _uiState.update {
                it.copy(
                    activeEvent = active,
                    displayMode = IslandDisplayMode.FULL_EXPANDED
                )
            }
        }
    }

    fun collapse() {
        scope.launch {
            postExitShrinkJob?.cancel()
            collapseInternal()
        }
    }

    private fun collapseInternal() {
        autoCollapseJob?.cancel()
        val state = _uiState.value
        val music = state.persistentMusic
        if (state.activeEvent is IslandEvent.IncomingCall) {
            _uiState.update { it.copy(displayMode = IslandDisplayMode.COMPACT) }
            return
        }
        if (music != null && music.isPlaying) {
            _uiState.update {
                it.copy(activeEvent = music, displayMode = IslandDisplayMode.COMPACT)
            }
        } else {
            _uiState.update {
                it.copy(activeEvent = null, displayMode = IslandDisplayMode.IDLE)
            }
        }
    }

    fun setFullscreenState(isFullscreen: Boolean) {
        scope.launch {
            _uiState.update { it.copy(isFullscreenHidden = isFullscreen) }
        }
    }

    fun onSettingsChanged(settings: IslandSettings) {
        scope.launch {
            if (!settings.masterEnabled) {
                autoCollapseJob?.cancel()
                postExitShrinkJob?.cancel()
                eventQueue.clear()
                _uiState.update {
                    it.copy(
                        activeEvent = null,
                        displayMode = IslandDisplayMode.IDLE,
                        queuedCount = 0,
                        activeTransition = null
                    )
                }
            } else if (!settings.isEventEnabled(IslandEventType.MUSIC_PLAYER)) {
                _uiState.update { state ->
                    val active = if (state.activeEvent is IslandEvent.MusicPlayback) null else state.activeEvent
                    state.copy(
                        activeEvent = active,
                        persistentMusic = null,
                        activeTransition = null,
                        displayMode = if (active == null) IslandDisplayMode.IDLE else state.displayMode
                    )
                }
            }
        }
    }
}
