package com.island.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.island.app.IslandApplication
import com.island.app.MainActivity
import com.island.app.R
import com.island.app.data.CrashLogger
import com.island.app.data.IslandSettings
import com.island.app.model.AppTransitionEvent
import com.island.app.model.IslandDisplayMode
import com.island.app.model.IslandEvent
import com.island.app.model.IslandEventType
import com.island.app.state.IslandStateManager
import com.island.app.state.IslandUiState
import com.island.app.ui.overlay.AppExitTransitionOverlay
import com.island.app.ui.overlay.DynamicIslandOverlay
import com.island.app.ui.overlay.computeExpandedTopShiftDp
import com.island.app.ui.overlay.computeTargetIslandSizeDp
import com.island.app.ui.theme.IslandTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private data class CutoutMetrics(
    val widthDp: Float = 36f,
    val bottomDp: Float = 34f
)

/**
 * Minimal geometry-only key used to deduplicate WindowManager.updateViewLayout() calls.
 * Media playback toggles, track updates, and seekbar scrubbing do not change any field here,
 * eliminating window relayout jitter and touch latency while interacting with controls.
 */
private data class WindowGeometryKey(
    val displayMode: IslandDisplayMode,
    val eventType: IslandEventType?,
    val offsetX: Float,
    val offsetY: Float,
    val idleWidthDp: Float,
    val idleHeightDp: Float,
    val cutoutWidthDp: Float,
    val cutoutBottomDp: Float,
    val isLiveAdjusting: Boolean
)

class IslandOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var serviceWindowManager: WindowManager? = null
    private var overlayWindowManager: WindowManager? = null
    private var overlayWindowType: Int = appOverlayWindowType()
    private var stateManager: IslandStateManager? = null
    private var systemMonitor: SystemEventMonitor? = null
    private var usageFallback: ForegroundAppUsageFallback? = null

    private var overlayContainer: FrameLayout? = null
    private var overlayComposeView: ComposeView? = null
    private var transitionContainer: FrameLayout? = null
    private var fullscreenProbeView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var shrinkWindowJob: Job? = null
    private val settingsState = MutableStateFlow(IslandSettings.Default)
    private val isLiveAdjustingState = MutableStateFlow(false)
    private val cutoutMetricsState = MutableStateFlow(CutoutMetrics())

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // Always satisfy Android 12+ foreground service startup contract before any early exit
        startForegroundNotification()

        if (!Settings.canDrawOverlays(this)) {
            stopForegroundCompat()
            stopSelf()
            return
        }

        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm == null) {
            stopForegroundCompat()
            stopSelf()
            return
        }
        serviceWindowManager = wm

        val settingsRepo = (application as IslandApplication).settingsRepository
        val manager = IslandStateManager(
            scope = serviceScope,
            settingsProvider = { settingsState.value }
        )
        stateManager = manager

        val monitor = SystemEventMonitor(
            context = this,
            scope = serviceScope,
            stateManager = manager,
            settingsProvider = { settingsState.value }
        )
        systemMonitor = monitor

        val fallback = ForegroundAppUsageFallback(
            context = this,
            scope = serviceScope
        )
        usageFallback = fallback

        // Reach RESUMED before attaching ComposeView so WindowRecomposer starts cleanly
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        attachFullscreenProbe(wm, manager)
        val attached = runCatching { attachIslandWindow(wm, manager) }
            .onFailure { CrashLogger.recordNonFatal(this, "Failed to attach Dynamic Island overlay", it) }
            .isSuccess

        if (!attached) {
            stopForegroundCompat()
            stopSelf()
            return
        }

        runCatching { monitor.start() }
            .onFailure { CrashLogger.recordNonFatal(this, "SystemEventMonitor start error", it) }

        // Automatically upgrade/downgrade z-order layer when AccessibilityService connects or disconnects
        serviceScope.launch {
            AppForegroundTracker.activeService
                .drop(1)
                .distinctUntilChanged()
                .collect {
                    runCatching { attachIslandWindow(wm, manager) }
                        .onFailure { err ->
                            CrashLogger.recordNonFatal(this@IslandOverlayService, "Overlay layer re-attach error", err)
                        }
                }
        }

        // Merge persisted DataStore settings with zero-I/O live slider preview overrides
        // so moving any position/size slider updates the real overlay at 60/120fps immediately.
        serviceScope.launch {
            combine(
                settingsRepo.settingsFlow,
                IslandEventBus.livePreviewSettings
            ) { saved, preview ->
                Pair(preview ?: saved, preview != null)
            }.collect { (effectiveSettings, isLiveAdjusting) ->
                settingsState.value = effectiveSettings
                isLiveAdjustingState.value = isLiveAdjusting
                if (!isLiveAdjusting) {
                    manager.onSettingsChanged(effectiveSettings)
                    if (effectiveSettings.masterEnabled && effectiveSettings.appExitAnimationEnabled) {
                        fallback.start()
                    } else {
                        fallback.stop()
                    }
                }
            }
        }

        // Deduplicated WindowManager geometry sync: only invokes updateViewLayout() when
        // window bounds or position actually change (zero relayouts on media button clicks).
        serviceScope.launch {
            combine(
                manager.uiState,
                settingsState,
                cutoutMetricsState,
                isLiveAdjustingState
            ) { uiState, settings, cutout, isLiveAdjusting ->
                WindowGeometryKey(
                    displayMode = if (uiState.activeEvent == null) IslandDisplayMode.IDLE else uiState.displayMode,
                    eventType = uiState.activeEvent?.type,
                    offsetX = settings.offsetX,
                    offsetY = settings.offsetY,
                    idleWidthDp = settings.idleWidthDp,
                    idleHeightDp = settings.idleHeightDp,
                    cutoutWidthDp = cutout.widthDp,
                    cutoutBottomDp = cutout.bottomDp,
                    isLiveAdjusting = isLiveAdjusting
                )
            }.distinctUntilChanged().collect { key ->
                updateOverlayWindowBounds(
                    uiState = manager.uiState.value,
                    settings = settingsState.value,
                    cutout = cutoutMetricsState.value,
                    immediateShrink = key.isLiveAdjusting
                )
            }
        }

        // Dynamically attach/detach the full-screen spring morph window only while an app transition runs
        serviceScope.launch {
            manager.uiState
                .map { it.activeTransition }
                .distinctUntilChanged()
                .collectLatest { transition ->
                    if (transition != null) {
                        showTransitionOverlay(wm, manager, transition)
                    } else {
                        removeTransitionOverlay()
                    }
                }
        }

        // Collect live media playback StateFlow directly from MediaListenerService
        serviceScope.launch {
            var hadMedia = false
            MediaListenerService.mediaState.collect { playback ->
                if (playback != null) {
                    hadMedia = true
                    manager.postEvent(playback)
                } else if (hadMedia) {
                    hadMedia = false
                    manager.postEvent(IslandEvent.MusicSessionEnded())
                }
            }
        }

        // Collect foreground->background / background->foreground media app transitions
        serviceScope.launch {
            AppForegroundTracker.transitions.collect { transition ->
                manager.startAppTransition(transition)
            }
        }

        serviceScope.launch {
            IslandEventBus.events.collect { event ->
                manager.postEvent(event)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        if (!Settings.canDrawOverlays(this)) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        shrinkWindowJob?.cancel()
        runCatching { systemMonitor?.stop() }
        runCatching { usageFallback?.stop() }
        removeTransitionOverlay()
        detachIslandWindow()
        fullscreenProbeView?.let { probe ->
            if (probe.isAttachedToWindow) {
                runCatching { serviceWindowManager?.removeViewImmediate(probe) }
            }
        }
        fullscreenProbeView = null

        transitionLifecycleToDestroyed()
        viewModelStore.clear()
        serviceScope.cancel()
    }

    private fun appOverlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    /**
     * Prefers TYPE_ACCESSIBILITY_OVERLAY when IslandAccessibilityService is active so the
     * Dynamic Island always renders in the topmost SurfaceFlinger layer above status bar UI,
     * heads-up notifications, and TYPE_APPLICATION_OVERLAY windows. Falls back cleanly to
     * TYPE_APPLICATION_OVERLAY when Accessibility is disabled.
     */
    private fun resolveOverlayTarget(fallbackWm: WindowManager): Pair<WindowManager, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val accService = AppForegroundTracker.activeService.value
            if (accService != null) {
                val accWm = runCatching {
                    accService.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                }.getOrNull()
                if (accWm != null) {
                    return Pair(accWm, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                }
            }
        }
        return Pair(fallbackWm, appOverlayWindowType())
    }

    private fun detachIslandWindow() {
        val prevWm = overlayWindowManager ?: serviceWindowManager
        overlayContainer?.let { container ->
            if (container.isAttachedToWindow) {
                runCatching { prevWm?.removeViewImmediate(container) }
            }
        }
        runCatching { overlayComposeView?.disposeComposition() }
        overlayComposeView = null
        overlayContainer = null
        overlayParams = null
        overlayWindowManager = null
    }

    /**
     * Guarantees the Dynamic Island capsule remains strictly on top when both the capsule
     * and the temporary app-exit overlay share TYPE_APPLICATION_OVERLAY (Accessibility off).
     * Because ComposeView uses DisposeOnLifecycleDestroyed, re-adding preserves active springs.
     */
    private fun bringIslandWindowToTop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 &&
            overlayWindowType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        ) {
            // Already in a strictly higher SurfaceFlinger window layer than TYPE_APPLICATION_OVERLAY
            return
        }
        val wm = overlayWindowManager ?: return
        val container = overlayContainer ?: return
        val params = overlayParams ?: return
        if (!container.isAttachedToWindow) return
        runCatching {
            wm.removeViewImmediate(container)
            wm.addView(container, params)
            container.bringToFront()
        }
    }

    private fun showTransitionOverlay(
        wm: WindowManager,
        manager: IslandStateManager,
        transition: AppTransitionEvent
    ) {
        removeTransitionOverlay()
        val type = appOverlayWindowType()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val themedContext = ContextThemeWrapper(this, R.style.Theme_Island)
        val container = FrameLayout(themedContext).apply {
            setViewTreeLifecycleOwner(this@IslandOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IslandOverlayService)
            setViewTreeViewModelStoreOwner(this@IslandOverlayService)
        }

        val composeView = ComposeView(themedContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides this@IslandOverlayService) {
                    val currentSettings by settingsState.collectAsState()
                    IslandTheme(darkTheme = true, dynamicColor = false) {
                        AppExitTransitionOverlay(
                            transition = transition,
                            settings = currentSettings,
                            onReachedIsland = {
                                runCatching {
                                    overlayContainer?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                manager.onExitAnimationReachedIsland()
                            },
                            onFinished = {
                                manager.onAppTransitionFinished(transition.id)
                            }
                        )
                    }
                }
            }
        }

        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        runCatching {
            wm.addView(container, params)
            transitionContainer = container
            // Keep the Dynamic Island strictly above the transition overlay at all times
            bringIslandWindowToTop()
        }.onFailure {
            CrashLogger.recordNonFatal(this, "Failed to attach AppExitTransitionOverlay", it)
            manager.onAppTransitionFinished(transition.id)
        }
    }

    private fun removeTransitionOverlay() {
        val wm = serviceWindowManager ?: return
        transitionContainer?.let { container ->
            if (container.isAttachedToWindow) {
                runCatching { wm.removeViewImmediate(container) }
            }
        }
        transitionContainer = null
    }

    private fun transitionLifecycleToDestroyed() {
        runCatching {
            when (lifecycleRegistry.currentState) {
                Lifecycle.State.RESUMED -> {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }
                Lifecycle.State.STARTED -> {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }
                Lifecycle.State.CREATED -> {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }
                else -> Unit
            }
        }
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    private fun startForegroundNotification() {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(
            this,
            IslandApplication.CHANNEL_OVERLAY_SERVICE
        )
            .setSmallIcon(R.drawable.ic_island_notification)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            CrashLogger.recordNonFatal(this, "startForeground failed", it)
        }
    }

    private fun attachFullscreenProbe(wm: WindowManager, manager: IslandStateManager) {
        val type = appOverlayWindowType()
        val probe = View(this).apply {
            setOnApplyWindowInsetsListener { _, insets ->
                runCatching {
                    val statusBarVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        insets.isVisible(WindowInsets.Type.statusBars())
                    } else {
                        WindowInsetsCompat.toWindowInsetsCompat(insets)
                            .isVisible(WindowInsetsCompat.Type.statusBars())
                    }
                    manager.setFullscreenState(!statusBarVisible)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val cutout = insets.displayCutout
                        if (cutout != null) {
                            val topRect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                cutout.boundingRectTop
                            } else {
                                cutout.boundingRects.firstOrNull { it.top <= 4 }
                            }
                            val widthDp = if (topRect != null && topRect.width() > 0) {
                                pxToDp(topRect.width()).coerceIn(28f, 120f)
                            } else if (cutout.safeInsetTop > 0) {
                                38f
                            } else {
                                0f
                            }
                            val bottomDp = if (topRect != null && topRect.bottom > 0) {
                                pxToDp(topRect.bottom).coerceIn(20f, 64f)
                            } else if (cutout.safeInsetTop > 0) {
                                pxToDp(cutout.safeInsetTop).coerceIn(20f, 64f)
                            } else {
                                0f
                            }
                            cutoutMetricsState.value = CutoutMetrics(
                                widthDp = widthDp,
                                bottomDp = bottomDp
                            )
                        } else {
                            val statusTopPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
                            } else {
                                0
                            }
                            cutoutMetricsState.value = CutoutMetrics(
                                widthDp = 0f,
                                bottomDp = if (statusTopPx > 0) pxToDp(statusTopPx).coerceIn(0f, 44f) else 0f
                            )
                        }
                    }
                }
                insets
            }
        }
        val probeParams = WindowManager.LayoutParams(
            1,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching {
            wm.addView(probe, probeParams)
            fullscreenProbeView = probe
            ViewCompat.requestApplyInsets(probe)
        }
    }

    private fun attachIslandWindow(fallbackWm: WindowManager, manager: IslandStateManager) {
        detachIslandWindow()

        val (preferredWm, preferredType) = resolveOverlayTarget(fallbackWm)
        val initialSettings = settingsState.value
        val initialCutout = cutoutMetricsState.value
        val (initW, initH) = computeWindowPixelSize(
            uiState = manager.uiState.value,
            settings = initialSettings,
            cutout = initialCutout
        )
        val isExpanded = manager.uiState.value.displayMode == IslandDisplayMode.EXPANDED ||
            manager.uiState.value.displayMode == IslandDisplayMode.FULL_EXPANDED
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            if (isExpanded) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else 0

        val params = WindowManager.LayoutParams(
            initW,
            initH,
            preferredType,
            baseFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dpToPx(initialSettings.offsetX)
            y = dpToPx(initialSettings.offsetY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Wrap ComposeView inside a themed FrameLayout container (ComposeView itself is final)
        val themedContext = ContextThemeWrapper(this, R.style.Theme_Island)
        val container = object : FrameLayout(themedContext) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    manager.collapse()
                    return true
                }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            elevation = dpToPx(24f).toFloat()
            ViewCompat.setTranslationZ(this, dpToPx(24f).toFloat())
            setViewTreeLifecycleOwner(this@IslandOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IslandOverlayService)
            setViewTreeViewModelStoreOwner(this@IslandOverlayService)
        }

        val composeView = ComposeView(themedContext).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@IslandOverlayService)
            )
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides this@IslandOverlayService) {
                    val settings by settingsState.collectAsState()
                    val uiState by manager.uiState.collectAsState()
                    val cutout by cutoutMetricsState.collectAsState()
                    val isLiveAdjusting by isLiveAdjustingState.collectAsState()

                    IslandTheme(darkTheme = true, dynamicColor = false) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            DynamicIslandOverlay(
                                uiState = uiState,
                                settings = settings,
                                cutoutWidthDp = cutout.widthDp,
                                cutoutBottomDp = cutout.bottomDp,
                                isLiveAdjusting = isLiveAdjusting,
                                onTap = {
                                    runCatching { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                    manager.onIslandTap()
                                },
                                onLongPress = {
                                    runCatching { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                                    manager.onIslandLongPress()
                                },
                                onSwipeUp = {
                                    runCatching { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                    manager.collapse()
                                },
                                onMediaCommand = { cmd ->
                                    runCatching { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                    if (MediaListenerService.isListenerConnected.value) {
                                        IslandEventBus.sendMediaCommand(cmd)
                                    } else {
                                        MediaListenerService.dispatchFallbackMediaKey(this@IslandOverlayService, cmd)
                                    }
                                },
                                onCallCommand = { cmd ->
                                    runCatching { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                    IslandEventBus.sendCallCommand(cmd)
                                }
                            )
                        }
                    }
                }
            }
        }

        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        var activeWm = preferredWm
        var activeType = preferredType
        val addedPreferred = runCatching {
            preferredWm.addView(container, params)
        }.isSuccess

        if (!addedPreferred && preferredType != appOverlayWindowType()) {
            activeType = appOverlayWindowType()
            activeWm = fallbackWm
            params.type = activeType
            fallbackWm.addView(container, params)
        } else if (!addedPreferred) {
            throw IllegalStateException("Unable to add Dynamic Island overlay window")
        }

        container.bringToFront()
        overlayWindowManager = activeWm
        overlayWindowType = activeType
        overlayParams = params
        overlayContainer = container
        overlayComposeView = composeView
    }

    private fun computeWindowPixelSize(
        uiState: IslandUiState,
        settings: IslandSettings,
        cutout: CutoutMetrics
    ): Pair<Int, Int> {
        val screenWidthDp = pxToDp(resources.displayMetrics.widthPixels)
        val (targetW, targetH) = computeTargetIslandSizeDp(
            uiState = uiState,
            settings = settings,
            screenWidthDp = screenWidthDp
        )
        val isExpanded = uiState.activeEvent != null &&
            (uiState.displayMode == IslandDisplayMode.EXPANDED ||
                uiState.displayMode == IslandDisplayMode.FULL_EXPANDED)
        // Reserve room for the GPU-interpolated downward slide below the camera & status bar
        // so params.y never needs to jump mid-animation.
        val expandedShiftDp = if (isExpanded) {
            computeExpandedTopShiftDp(settings.offsetY, cutout.bottomDp)
        } else {
            0f
        }
        val overshootMarginW = if (uiState.displayMode == IslandDisplayMode.IDLE) 10f else 20f
        val overshootMarginH = if (uiState.displayMode == IslandDisplayMode.IDLE) 10f else 24f
        val maxScreenW = (resources.displayMetrics.widthPixels - dpToPx(8f)).coerceAtLeast(dpToPx(280f))
        val pxW = dpToPx(targetW + overshootMarginW).coerceAtMost(maxScreenW)
        val pxH = dpToPx(targetH + expandedShiftDp + overshootMarginH)
        return Pair(pxW, pxH)
    }

    /**
     * Updates the overlay window position, outside-touch flag, and explicit pixel size.
     * When growing, the window expands immediately so the spring morph runs inside a fixed
     * GPU surface with zero per-frame relayoutWindow() calls. When shrinking, the window stays
     * large until the spring settles (~460ms), then shrinks once so background touches pass through.
     */
    private fun updateOverlayWindowBounds(
        uiState: IslandUiState,
        settings: IslandSettings,
        cutout: CutoutMetrics,
        immediateShrink: Boolean
    ) {
        val view = overlayContainer ?: return
        val params = overlayParams ?: return
        val wm = overlayWindowManager ?: return
        if (!view.isAttachedToWindow) return

        val isExpanded = uiState.activeEvent != null &&
            (uiState.displayMode == IslandDisplayMode.EXPANDED ||
                uiState.displayMode == IslandDisplayMode.FULL_EXPANDED)
        val targetFlags = if (isExpanded) {
            params.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        }

        val newX = dpToPx(settings.offsetX)
        val newY = dpToPx(settings.offsetY)
        val (targetW, targetH) = computeWindowPixelSize(uiState, settings, cutout)

        val needsGrow = targetW > params.width || targetH > params.height
        val needsShrink = targetW < params.width || targetH < params.height

        shrinkWindowJob?.cancel()

        val immediateW = if (immediateShrink) targetW else max(params.width, targetW)
        val immediateH = if (immediateShrink) targetH else max(params.height, targetH)

        var changed = false
        if (params.flags != targetFlags) {
            params.flags = targetFlags
            changed = true
        }
        if (params.x != newX || params.y != newY) {
            params.x = newX
            params.y = newY
            changed = true
        }
        if (params.width != immediateW || params.height != immediateH) {
            params.width = immediateW
            params.height = immediateH
            changed = true
        }

        if (changed) {
            runCatching { wm.updateViewLayout(view, params) }
        }

        if (!immediateShrink && needsShrink && !needsGrow) {
            val speed = settings.animationSpeed.coerceIn(0.5f, 2.0f)
            val settleDelayMs = (460f / speed).toLong().coerceIn(260L, 880L)
            shrinkWindowJob = serviceScope.launch {
                delay(settleDelayMs)
                val currentView = overlayContainer ?: return@launch
                val currentParams = overlayParams ?: return@launch
                val currentWm = overlayWindowManager ?: return@launch
                if (!currentView.isAttachedToWindow) return@launch
                val (finalW, finalH) = computeWindowPixelSize(
                    uiState = stateManager?.uiState?.value ?: uiState,
                    settings = settingsState.value,
                    cutout = cutoutMetricsState.value
                )
                if (currentParams.width != finalW || currentParams.height != finalH) {
                    currentParams.width = finalW
                    currentParams.height = finalH
                    runCatching { currentWm.updateViewLayout(currentView, currentParams) }
                }
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).roundToInt()
    }

    private fun pxToDp(px: Int): Float {
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        return px / density
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, IslandOverlayService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure {
                CrashLogger.recordNonFatal(context, "Unable to start IslandOverlayService", it)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, IslandOverlayService::class.java))
            }
        }
    }
}
