package com.island.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.KeyEvent
import androidx.palette.graphics.Palette
import com.island.app.model.IslandEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MediaListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var mediaCommandJob: Job? = null
    private var cachedArtSourceKey: String? = null
    private var cachedArtBitmap: Bitmap? = null
    private var cachedDominantColor: Int = IslandEvent.MusicPlayback.DEFAULT_MEDIA_COLOR
    private var lastPublishedFingerprint: String? = null

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            val list = controllers.orEmpty()
            Log.d(
                TAG,
                "onActiveSessionsChanged: count=${list.size} -> " +
                    list.joinToString { "${it.packageName}(state=${playbackStateName(it.playbackState?.state)})" }
            )
            scope.launch {
                bindController(selectPrimaryController(list))
            }
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d(
                TAG,
                "onPlaybackStateChanged: pkg=${activeController?.packageName}, " +
                    "state=${playbackStateName(state?.state)}, pos=${state?.position}"
            )
            scope.launch {
                // Re-evaluate all sessions in case another app started playing
                refreshActiveSessions()
                publishCurrentMedia()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            Log.d(TAG, "onMetadataChanged: pkg=${activeController?.packageName}, title=$title")
            scope.launch { publishCurrentMedia() }
        }

        override fun onSessionDestroyed() {
            Log.d(TAG, "onSessionDestroyed: pkg=${activeController?.packageName}")
            scope.launch {
                bindController(null)
                refreshActiveSessions()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "onListenerConnected: MediaListenerService bound successfully")
        _isListenerConnected.value = true
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

        val component = ComponentName(this, MediaListenerService::class.java)
        runCatching {
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            val initial = mediaSessionManager?.getActiveSessions(component).orEmpty()
            Log.d(
                TAG,
                "Initial session discovery: count=${initial.size} -> " +
                    initial.joinToString { "${it.packageName}(state=${playbackStateName(it.playbackState?.state)})" }
            )
            bindController(selectPrimaryController(initial))
        }.onFailure { err ->
            Log.e(TAG, "Failed to query active media sessions on connect", err)
        }

        mediaCommandJob?.cancel()
        mediaCommandJob = scope.launch {
            IslandEventBus.mediaCommands.collect { command ->
                handleMediaCommand(command)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "onListenerDisconnected: clearing controller and requesting rebind")
        _isListenerConnected.value = false
        mediaCommandJob?.cancel()
        runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        bindController(null)
        requestRebindIfNeeded(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        // If the notification belongs to our active media package or carries a media session, refresh art & sessions
        val extras = sbn.notification?.extras
        val hasMediaToken = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
        if (hasMediaToken || pkg == activeController?.packageName) {
            Log.d(TAG, "onNotificationPosted (media): pkg=$pkg, hasToken=$hasMediaToken")
            scope.launch {
                refreshActiveSessions()
                publishCurrentMedia()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg == activeController?.packageName) {
            Log.d(TAG, "onNotificationRemoved (media): pkg=$pkg")
            scope.launch { refreshActiveSessions() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: MediaListenerService tearing down")
        _isListenerConnected.value = false
        mediaCommandJob?.cancel()
        runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
        scope.cancel()
    }

    private fun refreshActiveSessions() {
        val component = ComponentName(this, MediaListenerService::class.java)
        runCatching {
            val sessions = mediaSessionManager?.getActiveSessions(component).orEmpty()
            Log.d(TAG, "refreshActiveSessions: found ${sessions.size} session(s)")
            bindController(selectPrimaryController(sessions))
        }.onFailure { err ->
            Log.w(TAG, "refreshActiveSessions error: ${err.message}")
        }
    }

    private fun selectPrimaryController(controllers: List<MediaController>): MediaController? {
        if (controllers.isEmpty()) {
            Log.d(TAG, "selectPrimaryController: no active sessions")
            return null
        }

        val playing = controllers.firstOrNull {
            runCatching { it.playbackState?.state == PlaybackState.STATE_PLAYING }.getOrDefault(false)
        }
        if (playing != null) {
            Log.d(TAG, "selectPrimaryController: picked PLAYING session -> ${playing.packageName}")
            return playing
        }

        val buffering = controllers.firstOrNull {
            runCatching { it.playbackState?.state == PlaybackState.STATE_BUFFERING }.getOrDefault(false)
        }
        if (buffering != null) {
            Log.d(TAG, "selectPrimaryController: picked BUFFERING session -> ${buffering.packageName}")
            return buffering
        }

        val pausedWithMeta = controllers.firstOrNull { controller ->
            runCatching {
                controller.playbackState?.state == PlaybackState.STATE_PAUSED &&
                    !controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
            }.getOrDefault(false)
        }
        val chosen = pausedWithMeta ?: controllers.firstOrNull()
        Log.d(
            TAG,
            "selectPrimaryController: picked fallback session -> ${chosen?.packageName} " +
                "(state=${playbackStateName(chosen?.playbackState?.state)})"
        )
        return chosen
    }

    private fun bindController(controller: MediaController?) {
        if (activeController?.sessionToken == controller?.sessionToken) {
            if (controller != null) publishCurrentMedia()
            return
        }
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = controller
        cachedArtSourceKey = null
        if (controller != null) {
            Log.d(TAG, "bindController: registering callback for ${controller.packageName}")
            runCatching { controller.registerCallback(controllerCallback) }
        } else {
            Log.d(TAG, "bindController: detached active controller")
        }
        publishCurrentMedia()
    }

    private fun handleMediaCommand(command: MediaCommand) {
        if (activeController == null) {
            refreshActiveSessions()
        }
        val controller = activeController
        if (controller == null) {
            Log.d(TAG, "handleMediaCommand($command): no active controller, falling back to AudioManager media key")
            dispatchFallbackMediaKey(this, command)
            return
        }
        Log.d(TAG, "handleMediaCommand: $command -> ${controller.packageName}")
        val dispatched = runCatching {
            val controls = controller.transportControls
            when (command) {
                MediaCommand.PlayPause -> {
                    val stateCode = controller.playbackState?.state
                    val isCurrentlyPlaying = stateCode == PlaybackState.STATE_PLAYING ||
                        stateCode == PlaybackState.STATE_BUFFERING
                    if (isCurrentlyPlaying) controls.pause() else controls.play()
                }
                MediaCommand.Next -> controls.skipToNext()
                MediaCommand.Previous -> controls.skipToPrevious()
                is MediaCommand.SeekTo -> {
                    controls.seekTo(command.positionMs)
                    val current = _mediaState.value
                    if (current != null) {
                        val clampedPos = command.positionMs.coerceIn(0L, current.durationMs.coerceAtLeast(1L))
                        val nowElapsed = SystemClock.elapsedRealtime()
                        val updated = current.copy(
                            positionMs = clampedPos,
                            lastPositionUpdateTimeMs = nowElapsed,
                            timestampMs = System.currentTimeMillis()
                        )
                        lastPublishedFingerprint = buildFingerprint(updated)
                        _mediaState.value = updated
                    }
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "TransportControls command $command failed: ${err.message}")
        }.isSuccess

        if (!dispatched) {
            dispatchFallbackMediaKey(this, command)
        }
    }

    private fun publishCurrentMedia() {
        val controller = activeController
        if (controller == null) {
            if (_mediaState.value != null || lastPublishedFingerprint != null) {
                lastPublishedFingerprint = null
                _mediaState.value = null
            }
            return
        }

        val metadata = runCatching { controller.metadata }.getOrNull()
        val state = runCatching { controller.playbackState }.getOrNull()
        val pkg = runCatching { controller.packageName }.getOrDefault("")
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING ||
            state?.state == PlaybackState.STATE_BUFFERING

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?.takeIf { it.isNotBlank() }
            ?: resolveNotificationTitle(pkg)
            ?: "Unknown Track"

        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?.takeIf { it.isNotBlank() }
            ?: "Unknown Artist"

        val artKey = "$pkg|$title|$artist"
        val resolvedArt: Bitmap?
        val dominantColor: Int

        val metadataRawArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        if (metadataRawArt != null && !metadataRawArt.isRecycled) {
            resolvedArt = safeDownscaleBitmap(metadataRawArt)
            dominantColor = extractDominantColor(resolvedArt)
            cachedArtSourceKey = artKey
            cachedArtBitmap = resolvedArt
            cachedDominantColor = dominantColor
            Log.d(TAG, "Album art loaded from MediaMetadata for $pkg ($title)")
        } else if (cachedArtSourceKey == artKey && cachedArtBitmap != null && cachedArtBitmap?.isRecycled == false) {
            resolvedArt = cachedArtBitmap
            dominantColor = cachedDominantColor
        } else {
            val notifBitmap = resolveNotificationLargeIcon(pkg)
            resolvedArt = safeDownscaleBitmap(notifBitmap)
            dominantColor = extractDominantColor(resolvedArt)
            cachedArtSourceKey = artKey
            cachedArtBitmap = resolvedArt
            cachedDominantColor = dominantColor
            Log.d(
                TAG,
                if (resolvedArt != null) {
                    "Album art loaded from notification largeIcon fallback for $pkg"
                } else {
                    "No album art available for $pkg"
                }
            )
        }

        val duration = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(1L)
        val rawPosition = (state?.position ?: 0L).coerceIn(0L, duration)
        val lastUpdateMs = state?.lastPositionUpdateTime
            ?.takeIf { it > 0L }
            ?: SystemClock.elapsedRealtime()
        val playbackSpeed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f

        val playback = IslandEvent.MusicPlayback(
            title = title,
            artist = artist,
            albumArt = resolvedArt,
            dominantColorArgb = dominantColor,
            isPlaying = isPlaying,
            positionMs = rawPosition,
            durationMs = duration,
            lastPositionUpdateTimeMs = lastUpdateMs,
            playbackSpeed = playbackSpeed,
            packageName = pkg
        )

        val fingerprint = buildFingerprint(playback)
        if (fingerprint == lastPublishedFingerprint && _mediaState.value != null) {
            return
        }
        lastPublishedFingerprint = fingerprint
        _mediaState.value = playback
    }

    private fun buildFingerprint(playback: IslandEvent.MusicPlayback): String {
        return "${playback.packageName}|${playback.title}|${playback.artist}|" +
            "${playback.isPlaying}|${playback.positionMs}|${playback.durationMs}|" +
            "${playback.lastPositionUpdateTimeMs}|${playback.playbackSpeed}|" +
            "${playback.dominantColorArgb}|${playback.albumArt != null}"
    }

    private fun resolveNotificationTitle(packageName: String): String? {
        return runCatching {
            activeNotifications
                ?.firstOrNull { it.packageName == packageName }
                ?.notification
                ?.extras
                ?.getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun resolveNotificationLargeIcon(packageName: String): Bitmap? {
        return runCatching {
            val sbn = activeNotifications
                ?.firstOrNull {
                    it.packageName == packageName &&
                        it.notification?.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
                }
                ?: activeNotifications?.firstOrNull { it.packageName == packageName }
                ?: return null

            val notification = sbn.notification ?: return null
            val largeIcon = notification.getLargeIcon()
            if (largeIcon != null) {
                val drawable = largeIcon.loadDrawable(this)
                if (drawable != null) {
                    return drawableToBitmap(drawable)
                }
            }

            @Suppress("DEPRECATION")
            val legacyBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notification.extras?.getParcelable(Notification.EXTRA_LARGE_ICON, Bitmap::class.java)
            } else {
                notification.extras?.getParcelable(Notification.EXTRA_LARGE_ICON)
            }
            legacyBitmap
        }.getOrNull()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.coerceIn(1, 256)
        val h = drawable.intrinsicHeight.coerceIn(1, 256)
        return runCatching {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }.getOrNull()
    }

    private fun safeDownscaleBitmap(source: Bitmap?): Bitmap? {
        if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) {
            return null
        }
        return runCatching {
            val maxDim = 220
            val scale = minOf(1f, maxDim.toFloat() / maxOf(source.width, source.height))
            val targetW = (source.width * scale).toInt().coerceAtLeast(1)
            val targetH = (source.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }.getOrNull()
    }

    private fun extractDominantColor(bitmap: Bitmap?): Int {
        if (bitmap == null || bitmap.isRecycled) {
            return IslandEvent.MusicPlayback.DEFAULT_MEDIA_COLOR
        }
        return runCatching {
            val palette = Palette.from(bitmap).generate()
            palette.darkVibrantSwatch?.rgb
                ?: palette.vibrantSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: IslandEvent.MusicPlayback.DEFAULT_MEDIA_COLOR
        }.getOrDefault(IslandEvent.MusicPlayback.DEFAULT_MEDIA_COLOR)
    }

    private fun playbackStateName(state: Int?): String = when (state) {
        PlaybackState.STATE_PLAYING -> "PLAYING"
        PlaybackState.STATE_PAUSED -> "PAUSED"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"
        PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_NONE -> "NONE"
        null -> "NULL"
        else -> "STATE_$state"
    }

    companion object {
        private const val TAG = "IslandMedia"

        private val _mediaState = MutableStateFlow<IslandEvent.MusicPlayback?>(null)
        val mediaState: StateFlow<IslandEvent.MusicPlayback?> = _mediaState.asStateFlow()

        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

        fun requestRebindIfNeeded(context: Context) {
            if (_isListenerConnected.value) return
            val component = ComponentName(context, MediaListenerService::class.java)
            Log.d(TAG, "requestRebindIfNeeded: calling NotificationListenerService.requestRebind($component)")
            runCatching {
                requestRebind(component)
            }.onFailure { err ->
                Log.w(TAG, "requestRebind failed: ${err.message}")
            }
        }

        fun dispatchFallbackMediaKey(context: Context, command: MediaCommand) {
            val keyCode = when (command) {
                MediaCommand.PlayPause -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                MediaCommand.Next -> KeyEvent.KEYCODE_MEDIA_NEXT
                MediaCommand.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                is MediaCommand.SeekTo -> return
            }
            runCatching {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val eventTime = SystemClock.uptimeMillis()
                audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
                audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
            }.onFailure { err ->
                Log.w(TAG, "Fallback AudioManager media key failed: ${err.message}")
            }
        }
    }
}
