package com.island.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.app.data.CrashLogger
import com.island.app.data.IslandEventType
import com.island.app.service.AppForegroundTracker
import com.island.app.service.IslandOverlayService
import com.island.app.service.MediaListenerService
import com.island.app.state.IslandViewModel
import com.island.app.ui.screens.SettingsScreen
import com.island.app.ui.theme.IslandTheme

private data class PermissionRationaleRequest(
    val title: String,
    val message: String,
    val permissions: List<String>
)

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasNotificationAccess by mutableStateOf(false)
    private var hasAccessibilityAccess by mutableStateOf(false)
    private var hasUsageStatsAccess by mutableStateOf(false)
    private var lastCrashLog by mutableStateOf<String?>(null)
    private var pendingPermissionRationale by mutableStateOf<PermissionRationaleRequest?>(null)
    private var permissionsRequestedOnce = false

    private val runtimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissionsAndService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }

        refreshPermissionsAndService()
        lastCrashLog = CrashLogger.readLastCrash(this)

        if (savedInstanceState == null && !permissionsRequestedOnce) {
            permissionsRequestedOnce = true
            requestStartupNotificationPermission()
        }

        val viewModel = ViewModelProvider(this)[IslandViewModel::class.java]

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isMediaListenerConnected by viewModel.isMediaListenerConnected.collectAsStateWithLifecycle()
            val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsStateWithLifecycle()
            val activeMedia by viewModel.mediaState.collectAsStateWithLifecycle()

            LaunchedEffect(settings.masterEnabled, hasOverlayPermission) {
                if (settings.masterEnabled && hasOverlayPermission) {
                    IslandOverlayService.start(this@MainActivity)
                } else {
                    IslandOverlayService.stop(this@MainActivity)
                }
            }

            // If Notification Access is granted in Settings but the listener disconnected, request rebind
            LaunchedEffect(hasNotificationAccess, isMediaListenerConnected) {
                if (hasNotificationAccess && !isMediaListenerConnected) {
                    MediaListenerService.requestRebindIfNeeded(this@MainActivity)
                }
            }

            IslandTheme {
                SettingsScreen(
                    settings = settings,
                    hasOverlayPermission = hasOverlayPermission,
                    hasNotificationAccess = hasNotificationAccess,
                    isMediaListenerConnected = isMediaListenerConnected,
                    hasAccessibilityAccess = hasAccessibilityAccess || isAccessibilityConnected,
                    hasUsageStatsAccess = hasUsageStatsAccess,
                    activeMediaTitle = activeMedia?.let { "${it.title} — ${it.artist}" },
                    lastCrashLog = lastCrashLog,
                    onClearCrashLog = {
                        CrashLogger.clearLastCrash(this@MainActivity)
                        lastCrashLog = null
                    },
                    onShareCrashLog = { shareCrashLog() },
                    onRequestOverlayPermission = { openOverlaySettings() },
                    onRequestNotificationAccess = { openNotificationListenerSettings() },
                    onReconnectMediaListener = {
                        MediaListenerService.requestRebindIfNeeded(this@MainActivity)
                    },
                    onRequestAccessibilityAccess = { openAccessibilitySettings() },
                    onRequestUsageAccess = { openUsageAccessSettings() },
                    onMasterToggle = { viewModel.setMasterEnabled(it) },
                    onAutoStartToggle = { viewModel.setAutoStartOnBoot(it) },
                    onHideFullscreenToggle = { viewModel.setHideInFullscreen(it) },
                    onBluetoothAudioOnlyToggle = { viewModel.setBluetoothAudioOnly(it) },
                    onAppExitAnimationToggle = { viewModel.setAppExitAnimationEnabled(it) },
                    onAnimationSpeedChange = { viewModel.setAnimationSpeed(it) },
                    onDurationChange = { viewModel.setDisplayDuration(it) },
                    onPositionSizeChange = { x, y, w, h, r ->
                        viewModel.setPositionAndSize(x, y, w, h, r)
                    },
                    onLivePreviewSettingsChange = { viewModel.updateLivePreview(it) },
                    onEventToggle = { type, enabled ->
                        viewModel.setEventEnabled(type, enabled)
                        if (enabled) {
                            maybePromptForEventPermissions(type)
                        }
                    },
                    onResetToDefault = { viewModel.resetToDefault() },
                    onTriggerTestEvent = { viewModel.triggerTestEvent(it) },
                    onTestAppExitAnimation = { viewModel.triggerTestAppExitAnimation() }
                )

                pendingPermissionRationale?.let { request ->
                    AlertDialog(
                        onDismissRequest = { pendingPermissionRationale = null },
                        title = { Text(request.title) },
                        text = { Text(request.message) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val perms = request.permissions
                                    pendingPermissionRationale = null
                                    if (perms.isNotEmpty()) {
                                        runCatching {
                                            runtimePermissionsLauncher.launch(perms.toTypedArray())
                                        }
                                    }
                                }
                            ) {
                                Text("Continue")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingPermissionRationale = null }) {
                                Text("Not now")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionsAndService()
        lastCrashLog = CrashLogger.readLastCrash(this)
    }

    private fun refreshPermissionsAndService() {
        hasOverlayPermission = runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false)
        hasNotificationAccess = runCatching { isNotificationListenerEnabled() }.getOrDefault(false)
        hasAccessibilityAccess = AppForegroundTracker.isAccessibilityEnabledInSettings(this)
        hasUsageStatsAccess = AppForegroundTracker.hasUsageStatsPermission(this)

        if (hasNotificationAccess && !MediaListenerService.isListenerConnected.value) {
            MediaListenerService.requestRebindIfNeeded(this)
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, MediaListenerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it) == cn }
    }

    private fun shareCrashLog() {
        val log = lastCrashLog ?: CrashLogger.readLastCrash(this) ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Island Diagnostics Log")
            putExtra(Intent.EXTRA_TEXT, log)
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, "Share Diagnostics Log"))
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
        }
    }

    private fun openNotificationListenerSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun openUsageAccessSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    /**
     * Request only POST_NOTIFICATIONS on first launch so the foreground service
     * notification is allowed on Android 13+. Other event permissions are requested
     * on demand with an explanation when the user enables those specific cards.
     */
    private fun requestStartupNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching {
                runtimePermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    private fun maybePromptForEventPermissions(type: IslandEventType) {
        when (type) {
            IslandEventType.WIFI_CONNECTED -> {
                val hasFine = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasFine && !hasCoarse) {
                    pendingPermissionRationale = PermissionRationaleRequest(
                        title = "Show Wi-Fi network names",
                        message = "Android requires Location permission to read the connected Wi-Fi network name (SSID). If you skip this, the card still works and shows \"Wi-Fi Network\".",
                        permissions = listOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
            IslandEventType.BLUETOOTH -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val granted = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        pendingPermissionRationale = PermissionRationaleRequest(
                            title = "Show Bluetooth device names",
                            message = "Nearby devices permission lets Island display the name of connected headphones or speakers. If you skip this, the card still works and shows \"Bluetooth Device\".",
                            permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT)
                        )
                    }
                }
            }
            IslandEventType.INCOMING_CALL -> {
                val needed = buildList {
                    add(Manifest.permission.READ_PHONE_STATE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        add(Manifest.permission.ANSWER_PHONE_CALLS)
                    }
                }.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
                if (needed.isNotEmpty()) {
                    pendingPermissionRationale = PermissionRationaleRequest(
                        title = "Incoming call controls",
                        message = "Phone permissions let Island detect incoming calls and answer or decline them from the capsule. If you skip this, the card still works with \"Incoming Call\" and opens your dialer.",
                        permissions = needed
                    )
                }
            }
            else -> Unit
        }
    }
}
