package com.island.app.service

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.island.app.model.AppTransitionDirection
import com.island.app.model.AppTransitionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AppForegroundTracker {
    private const val TAG = "IslandAppExit"
    private const val DEBOUNCE_MS = 550L

    private val ignoredPackages = setOf(
        "com.android.systemui",
        "com.island.app",
        "android",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.android.inputmethod.latin"
    )

    private val _currentPackage = MutableStateFlow<String?>(null)
    val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private val _transitions = MutableSharedFlow<AppTransitionEvent>(extraBufferCapacity = 4)
    val transitions: SharedFlow<AppTransitionEvent> = _transitions.asSharedFlow()

    private var lastTransitionAtMs: Long = 0L

    internal fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
        Log.d(TAG, "AccessibilityService connected=$connected")
    }

    fun onForegroundPackageChanged(rawPackageName: String?) {
        val pkg = rawPackageName?.takeIf { it.isNotBlank() } ?: return
        if (pkg in ignoredPackages || pkg.contains("inputmethod") || pkg.contains("keyboard")) {
            return
        }

        val previous = _currentPackage.value
        if (previous == pkg) return
        _currentPackage.value = pkg

        val media = MediaListenerService.mediaState.value ?: return
        val mediaPkg = media.packageName
        if (mediaPkg.isBlank() || !media.isPlaying) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastTransitionAtMs < DEBOUNCE_MS) return

        when {
            previous == mediaPkg && pkg != mediaPkg -> {
                lastTransitionAtMs = now
                Log.d(TAG, "Detected media app EXIT: $mediaPkg -> $pkg")
                _transitions.tryEmit(
                    AppTransitionEvent(
                        direction = AppTransitionDirection.EXIT_TO_ISLAND,
                        packageName = mediaPkg,
                        dominantColorArgb = media.dominantColorArgb,
                        albumArt = media.albumArt,
                        title = media.title,
                        artist = media.artist
                    )
                )
            }
            previous != null && previous != mediaPkg && pkg == mediaPkg -> {
                lastTransitionAtMs = now
                Log.d(TAG, "Detected media app ENTER: $previous -> $mediaPkg")
                _transitions.tryEmit(
                    AppTransitionEvent(
                        direction = AppTransitionDirection.ENTER_FROM_ISLAND,
                        packageName = mediaPkg,
                        dominantColorArgb = media.dominantColorArgb,
                        albumArt = media.albumArt,
                        title = media.title,
                        artist = media.artist
                    )
                )
            }
        }
    }

    fun emitManualTransition(event: AppTransitionEvent) {
        _transitions.tryEmit(event)
    }

    fun isAccessibilityEnabledInSettings(context: Context): Boolean {
        return runCatching {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabled.contains("${context.packageName}/${IslandAccessibilityService::class.java.name}") ||
                enabled.contains("${context.packageName}/.service.IslandAccessibilityService")
        }.getOrDefault(false)
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        return runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }
}

class IslandAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppForegroundTracker.setAccessibilityConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val pkg = event.packageName?.toString() ?: return
        AppForegroundTracker.onForegroundPackageChanged(pkg)
    }

    override fun onInterrupt() {
        // No speech or audio feedback to interrupt
    }

    override fun onDestroy() {
        super.onDestroy()
        AppForegroundTracker.setAccessibilityConnected(false)
    }
}

class ForegroundAppUsageFallback(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var pollJob: Job? = null

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(Dispatchers.Default) {
            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@launch
            var lastQueryEnd = System.currentTimeMillis()

            while (isActive) {
                delay(350L)
                if (AppForegroundTracker.isAccessibilityConnected.value) continue
                if (!AppForegroundTracker.hasUsageStatsPermission(context)) continue
                if (MediaListenerService.mediaState.value?.isPlaying != true) continue

                val now = System.currentTimeMillis()
                val start = (lastQueryEnd - 1500L).coerceAtLeast(now - 5000L)
                runCatching {
                    val events = usageManager.queryEvents(start, now)
                    val record = UsageEvents.Event()
                    var latestPkg: String? = null
                    while (events.hasNextEvent()) {
                        events.getNextEvent(record)
                        val isForegroundEvent =
                            record.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    record.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                        if (isForegroundEvent && !record.packageName.isNullOrBlank()) {
                            latestPkg = record.packageName
                        }
                    }
                    if (latestPkg != null) {
                        AppForegroundTracker.onForegroundPackageChanged(latestPkg)
                    }
                }
                lastQueryEnd = now
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }
}
