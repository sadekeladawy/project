package com.island.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.island.app.data.DisplayDurationOption
import com.island.app.data.IslandEventType
import com.island.app.data.IslandSettings
import com.island.app.model.IslandDisplayMode
import com.island.app.model.IslandEvent
import com.island.app.state.IslandUiState
import com.island.app.ui.overlay.DynamicIslandOverlay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: IslandSettings,
    hasOverlayPermission: Boolean,
    hasNotificationAccess: Boolean,
    isMediaListenerConnected: Boolean,
    hasAccessibilityAccess: Boolean,
    hasUsageStatsAccess: Boolean,
    activeMediaTitle: String? = null,
    lastCrashLog: String? = null,
    onClearCrashLog: () -> Unit = {},
    onShareCrashLog: () -> Unit = {},
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onReconnectMediaListener: () -> Unit,
    onRequestAccessibilityAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onMasterToggle: (Boolean) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit,
    onHideFullscreenToggle: (Boolean) -> Unit,
    onBluetoothAudioOnlyToggle: (Boolean) -> Unit = {},
    onAppExitAnimationToggle: (Boolean) -> Unit,
    onAnimationSpeedChange: (Float) -> Unit,
    onDurationChange: (DisplayDurationOption) -> Unit,
    onPositionSizeChange: (Float?, Float?, Float?, Float?, Float?) -> Unit,
    onLivePreviewSettingsChange: (IslandSettings?) -> Unit = {},
    onEventToggle: (IslandEventType, Boolean) -> Unit,
    onResetToDefault: () -> Unit,
    onTriggerTestEvent: (IslandEvent) -> Unit,
    onTestAppExitAnimation: () -> Unit
) {
    var previewEvent by remember { mutableStateOf<IslandEvent?>(null) }
    var previewMode by remember { mutableStateOf(IslandDisplayMode.IDLE) }

    // Hold slider values locally and stream them in-memory to IslandOverlayService
    // so the real floating capsule moves/resizes smoothly at 60/120fps under your finger
    // while DataStore only commits once when you lift your finger.
    var draftOffsetX by remember { mutableFloatStateOf(settings.offsetX) }
    var draftOffsetY by remember { mutableFloatStateOf(settings.offsetY) }
    var draftIdleWidth by remember { mutableFloatStateOf(settings.idleWidthDp) }
    var draftIdleHeight by remember { mutableFloatStateOf(settings.idleHeightDp) }
    var draftCornerRadius by remember { mutableFloatStateOf(settings.cornerRadiusDp) }
    var draftAnimationSpeed by remember { mutableFloatStateOf(settings.animationSpeed) }

    LaunchedEffect(
        settings.offsetX,
        settings.offsetY,
        settings.idleWidthDp,
        settings.idleHeightDp,
        settings.cornerRadiusDp,
        settings.animationSpeed
    ) {
        draftOffsetX = settings.offsetX
        draftOffsetY = settings.offsetY
        draftIdleWidth = settings.idleWidthDp
        draftIdleHeight = settings.idleHeightDp
        draftCornerRadius = settings.cornerRadiusDp
        draftAnimationSpeed = settings.animationSpeed
    }

    DisposableEffect(Unit) {
        onDispose {
            onLivePreviewSettingsChange(null)
        }
    }

    val previewSettings = settings.copy(
        offsetX = draftOffsetX,
        offsetY = draftOffsetY,
        idleWidthDp = draftIdleWidth,
        idleHeightDp = draftIdleHeight,
        cornerRadiusDp = draftCornerRadius,
        animationSpeed = draftAnimationSpeed
    )

    fun pushLivePreview(
        offsetX: Float = draftOffsetX,
        offsetY: Float = draftOffsetY,
        idleWidthDp: Float = draftIdleWidth,
        idleHeightDp: Float = draftIdleHeight,
        cornerRadiusDp: Float = draftCornerRadius,
        animationSpeed: Float = draftAnimationSpeed
    ) {
        onLivePreviewSettingsChange(
            settings.copy(
                offsetX = offsetX,
                offsetY = offsetY,
                idleWidthDp = idleWidthDp,
                idleHeightDp = idleHeightDp,
                cornerRadiusDp = cornerRadiusDp,
                animationSpeed = animationSpeed
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Island",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (settings.masterEnabled) "Active" else "Off",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = settings.masterEnabled,
                            onCheckedChange = onMasterToggle
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Preview anchored at the top so slider adjustments reflect immediately
            LiveIslandPreviewCard(
                settings = previewSettings,
                previewEvent = previewEvent,
                previewMode = previewMode,
                onTapPreview = {
                    if (previewEvent == null) {
                        previewEvent = IslandEvent.ChargingStarted(84)
                        previewMode = IslandDisplayMode.COMPACT
                    } else if (previewMode == IslandDisplayMode.COMPACT) {
                        previewMode = IslandDisplayMode.EXPANDED
                    } else {
                        previewEvent = null
                        previewMode = IslandDisplayMode.IDLE
                    }
                },
                onLongPressPreview = {
                    if (previewEvent == null) {
                        previewEvent = IslandEvent.MusicPlayback(
                            title = "Midnight Refraction",
                            artist = "Kiasmos",
                            isPlaying = true,
                            positionMs = 74_000L,
                            durationMs = 212_000L
                        )
                    }
                    previewMode = IslandDisplayMode.FULL_EXPANDED
                },
                onCollapsePreview = {
                    previewEvent = null
                    previewMode = IslandDisplayMode.IDLE
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!lastCrashLog.isNullOrBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Last Diagnostics Log",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = onShareCrashLog) {
                                            Text("Share")
                                        }
                                        OutlinedButton(onClick = onClearCrashLog) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                                Text(
                                    text = lastCrashLog.take(600),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                item {
                    PermissionsSection(
                        hasOverlayPermission = hasOverlayPermission,
                        hasNotificationAccess = hasNotificationAccess,
                        isMediaListenerConnected = isMediaListenerConnected,
                        hasAccessibilityAccess = hasAccessibilityAccess,
                        hasUsageStatsAccess = hasUsageStatsAccess,
                        activeMediaTitle = activeMediaTitle,
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onRequestNotificationAccess = onRequestNotificationAccess,
                        onReconnectMediaListener = onReconnectMediaListener,
                        onRequestAccessibilityAccess = onRequestAccessibilityAccess,
                        onRequestUsageAccess = onRequestUsageAccess
                    )
                }

                item {
                    AppExitAnimationCard(
                        settings = previewSettings,
                        hasAccessibilityAccess = hasAccessibilityAccess,
                        hasUsageStatsAccess = hasUsageStatsAccess,
                        onAppExitAnimationToggle = onAppExitAnimationToggle,
                        onAnimationSpeedDraftChange = {
                            draftAnimationSpeed = it
                            pushLivePreview(animationSpeed = it)
                        },
                        onAnimationSpeedCommit = { onAnimationSpeedChange(draftAnimationSpeed) },
                        onRequestAccessibilityAccess = onRequestAccessibilityAccess,
                        onRequestUsageAccess = onRequestUsageAccess,
                        onTestAppExitAnimation = onTestAppExitAnimation
                    )
                }

                item {
                    PositionAndSizeCard(
                        settings = previewSettings,
                        onOffsetXDraftChange = {
                            draftOffsetX = it
                            pushLivePreview(offsetX = it)
                        },
                        onOffsetYDraftChange = {
                            draftOffsetY = it
                            pushLivePreview(offsetY = it)
                        },
                        onWidthDraftChange = {
                            draftIdleWidth = it
                            pushLivePreview(idleWidthDp = it)
                        },
                        onHeightDraftChange = {
                            draftIdleHeight = it
                            pushLivePreview(idleHeightDp = it)
                        },
                        onCornerDraftChange = {
                            draftCornerRadius = it
                            pushLivePreview(cornerRadiusDp = it)
                        },
                        onPositionSizeCommit = { x, y, w, h, r ->
                            onPositionSizeChange(x, y, w, h, r)
                        },
                        onResetToDefault = onResetToDefault
                    )
                }

                item {
                    GeneralBehaviorCard(
                        settings = settings,
                        onAutoStartToggle = onAutoStartToggle,
                        onHideFullscreenToggle = onHideFullscreenToggle,
                        onBluetoothAudioOnlyToggle = onBluetoothAudioOnlyToggle,
                        onDurationChange = onDurationChange
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Event Cards",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(
                            onClick = {
                                onTriggerTestEvent(IslandEvent.ChargingStarted(86))
                            }
                        ) {
                            Text("Test Overlay")
                        }
                    }
                }

                items(IslandEventType.entries, key = { it.key }) { eventType ->
                    EventToggleRow(
                        eventType = eventType,
                        enabled = settings.isEventEnabled(eventType),
                        onToggle = { onEventToggle(eventType, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveIslandPreviewCard(
    settings: IslandSettings,
    previewEvent: IslandEvent?,
    previewMode: IslandDisplayMode,
    onTapPreview: () -> Unit,
    onLongPressPreview: () -> Unit,
    onCollapsePreview: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0E1117),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
        ) {
            Text(
                text = "Live Preview (tap or long-press)",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(
                        x = (settings.offsetX * 0.5f).dp,
                        y = (settings.offsetY * 0.6f).dp
                    )
            ) {
                DynamicIslandOverlay(
                    uiState = IslandUiState(
                        activeEvent = previewEvent,
                        displayMode = previewMode
                    ),
                    settings = settings,
                    onTap = onTapPreview,
                    onLongPress = onLongPressPreview,
                    onSwipeUp = onCollapsePreview,
                    onMediaCommand = {},
                    onCallCommand = {}
                )
            }
        }
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFF30D158) else Color(0xFFFF9F0A))
    )
}

@Composable
private fun PermissionsSection(
    hasOverlayPermission: Boolean,
    hasNotificationAccess: Boolean,
    isMediaListenerConnected: Boolean,
    hasAccessibilityAccess: Boolean,
    hasUsageStatsAccess: Boolean,
    activeMediaTitle: String?,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onReconnectMediaListener: () -> Unit,
    onRequestAccessibilityAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Permissions & Media Access", style = MaterialTheme.typography.titleMedium)

            // 1. Display over other apps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(hasOverlayPermission)
                        Spacer(Modifier.width(8.dp))
                        Text("Display over other apps", fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = if (hasOverlayPermission) "Granted · Floating overlay ready" else "Required to float the island above apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!hasOverlayPermission) {
                    Button(onClick = onRequestOverlayPermission) {
                        Text("Grant")
                    }
                }
            }

            HorizontalDivider()

            // 2. Notification & Media Listener Access
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(hasNotificationAccess && isMediaListenerConnected)
                        Spacer(Modifier.width(8.dp))
                        Text("Notification & Media access", fontWeight = FontWeight.Medium)
                    }
                    val statusSubtitle = when {
                        !hasNotificationAccess -> "Required for Spotify, YouTube Music & live seekbar"
                        !isMediaListenerConnected -> "Enabled in Settings, waiting for Android to bind"
                        !activeMediaTitle.isNullOrBlank() -> "Connected · $activeMediaTitle"
                        else -> "Connected · Ready for active media sessions"
                    }
                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                when {
                    !hasNotificationAccess -> {
                        Button(onClick = onRequestNotificationAccess) {
                            Text("Enable")
                        }
                    }
                    !isMediaListenerConnected -> {
                        OutlinedButton(onClick = onReconnectMediaListener) {
                            Text("Reconnect")
                        }
                    }
                    else -> {
                        OutlinedButton(onClick = onRequestNotificationAccess) {
                            Text("Manage")
                        }
                    }
                }
            }

            HorizontalDivider()

            // 3. App Exit Detection (Accessibility or Usage Access)
            val exitReady = hasAccessibilityAccess || hasUsageStatsAccess
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(exitReady)
                        Spacer(Modifier.width(8.dp))
                        Text("App-exit detection", fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = when {
                            hasAccessibilityAccess -> "Accessibility active · Instant window change detection"
                            hasUsageStatsAccess -> "Usage Access fallback active (enable Accessibility for instant morph)"
                            else -> "Enable Island Accessibility (or Usage Access) to detect when you leave Spotify or YouTube Music"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (!hasAccessibilityAccess) {
                    Button(onClick = onRequestAccessibilityAccess) {
                        Text("Enable")
                    }
                } else {
                    OutlinedButton(onClick = onRequestAccessibilityAccess) {
                        Text("Manage")
                    }
                }
            }

            if (!hasAccessibilityAccess && !hasUsageStatsAccess) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onRequestUsageAccess) {
                        Text("Use Usage Access fallback instead")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppExitAnimationCard(
    settings: IslandSettings,
    hasAccessibilityAccess: Boolean,
    hasUsageStatsAccess: Boolean,
    onAppExitAnimationToggle: (Boolean) -> Unit,
    onAnimationSpeedDraftChange: (Float) -> Unit,
    onAnimationSpeedCommit: () -> Unit,
    onRequestAccessibilityAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onTestAppExitAnimation: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("App exit animation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Morphs Spotify / YouTube Music into the island when returning home, and reverses when reopening",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = settings.appExitAnimationEnabled,
                    onCheckedChange = onAppExitAnimationToggle
                )
            }

            SettingSliderRow(
                label = "Animation speed",
                valueText = String.format(Locale.US, "%.2fx", settings.animationSpeed),
                value = settings.animationSpeed,
                range = 0.5f..2.0f,
                onValueChange = onAnimationSpeedDraftChange,
                onValueChangeFinished = onAnimationSpeedCommit
            )

            if (settings.appExitAnimationEnabled && !hasAccessibilityAccess && !hasUsageStatsAccess) {
                Text(
                    text = "Requires Island Accessibility Service (watches window package switches only; never reads screen text) or Usage Access fallback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestAccessibilityAccess) {
                        Text("Accessibility")
                    }
                    OutlinedButton(onClick = onRequestUsageAccess) {
                        Text("Usage Access")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onTestAppExitAnimation) {
                    Text("Test App Exit")
                }
            }
        }
    }
}

@Composable
private fun PositionAndSizeCard(
    settings: IslandSettings,
    onOffsetXDraftChange: (Float) -> Unit,
    onOffsetYDraftChange: (Float) -> Unit,
    onWidthDraftChange: (Float) -> Unit,
    onHeightDraftChange: (Float) -> Unit,
    onCornerDraftChange: (Float) -> Unit,
    onPositionSizeCommit: (Float?, Float?, Float?, Float?, Float?) -> Unit,
    onResetToDefault: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Position & Size", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onResetToDefault,
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Reset to default")
                }
            }

            SettingSliderRow(
                label = "Vertical position (Y)",
                valueText = "${settings.offsetY.roundToInt()} dp",
                value = settings.offsetY,
                range = 0f..96f,
                onValueChange = onOffsetYDraftChange,
                onValueChangeFinished = { onPositionSizeCommit(null, settings.offsetY, null, null, null) }
            )

            SettingSliderRow(
                label = "Horizontal position (X)",
                valueText = "${settings.offsetX.roundToInt()} dp",
                value = settings.offsetX,
                range = -120f..120f,
                onValueChange = onOffsetXDraftChange,
                onValueChangeFinished = { onPositionSizeCommit(settings.offsetX, null, null, null, null) }
            )

            SettingSliderRow(
                label = "Idle width",
                valueText = "${settings.idleWidthDp.roundToInt()} dp",
                value = settings.idleWidthDp,
                range = 84f..220f,
                onValueChange = onWidthDraftChange,
                onValueChangeFinished = { onPositionSizeCommit(null, null, settings.idleWidthDp, null, null) }
            )

            SettingSliderRow(
                label = "Idle height",
                valueText = "${settings.idleHeightDp.roundToInt()} dp",
                value = settings.idleHeightDp,
                range = 24f..54f,
                onValueChange = onHeightDraftChange,
                onValueChangeFinished = { onPositionSizeCommit(null, null, null, settings.idleHeightDp, null) }
            )

            SettingSliderRow(
                label = "Corner radius",
                valueText = "${settings.cornerRadiusDp.roundToInt()} dp",
                value = settings.cornerRadiusDp,
                range = 12f..36f,
                onValueChange = onCornerDraftChange,
                onValueChangeFinished = { onPositionSizeCommit(null, null, null, null, settings.cornerRadiusDp) }
            )
        }
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range
        )
    }
}

@Composable
private fun GeneralBehaviorCard(
    settings: IslandSettings,
    onAutoStartToggle: (Boolean) -> Unit,
    onHideFullscreenToggle: (Boolean) -> Unit,
    onBluetoothAudioOnlyToggle: (Boolean) -> Unit,
    onDurationChange: (DisplayDurationOption) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Behavior", style = MaterialTheme.typography.titleMedium)

            Text("Card visible duration", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DisplayDurationOption.entries.forEachIndexed { idx, option ->
                    SegmentedButton(
                        selected = settings.displayDuration == option,
                        onClick = { onDurationChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = idx,
                            count = DisplayDurationOption.entries.size
                        )
                    ) {
                        Text(option.label)
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-start on boot", fontWeight = FontWeight.Medium)
                    Text(
                        "Restore floating island after restart",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.autoStartOnBoot,
                    onCheckedChange = onAutoStartToggle
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hide in fullscreen", fontWeight = FontWeight.Medium)
                    Text(
                        "Fade out during videos and games",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.hideInFullscreen,
                    onCheckedChange = onHideFullscreenToggle
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Audio devices only", fontWeight = FontWeight.Medium)
                    Text(
                        "Ignore non-audio Bluetooth trackers, watches, and cars",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.bluetoothAudioOnly,
                    onCheckedChange = onBluetoothAudioOnlyToggle
                )
            }
        }
    }
}

@Composable
private fun EventToggleRow(
    eventType: IslandEventType,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(eventType.displayName, fontWeight = FontWeight.Medium)
                Text(
                    text = eventType.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}
