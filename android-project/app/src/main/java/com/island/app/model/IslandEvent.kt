package com.island.app.model

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.island.app.data.IslandEventType
import java.util.UUID

enum class RingerMode {
    SILENT,
    VIBRATE,
    RING
}

enum class IslandDisplayMode {
    IDLE,
    COMPACT,
    EXPANDED,
    FULL_EXPANDED
}

enum class AppTransitionDirection {
    EXIT_TO_ISLAND,
    ENTER_FROM_ISLAND
}

@Immutable
data class AppTransitionEvent(
    val id: String = UUID.randomUUID().toString(),
    val direction: AppTransitionDirection,
    val packageName: String,
    val dominantColorArgb: Int,
    val albumArt: Bitmap?,
    val title: String,
    val artist: String
)

@Immutable
sealed interface IslandEvent {
    val id: String
    val type: IslandEventType
    val timestampMs: Long
    val isSticky: Boolean get() = false

    @Immutable
    data class ChargingStarted(
        val batteryPercent: Int,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.CHARGING_STARTED
    }

    @Immutable
    data class ChargingStopped(
        val batteryPercent: Int,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.CHARGING_STOPPED
    }

    @Immutable
    data class BatteryFull(
        val batteryPercent: Int = 100,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.BATTERY_FULL
    }

    @Immutable
    data class LowBattery(
        val batteryPercent: Int,
        val isCritical: Boolean = batteryPercent <= 10,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.LOW_BATTERY
    }

    @Immutable
    data class WifiConnected(
        val ssid: String,
        val signalStrength: Int = 4,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.WIFI_CONNECTED
    }

    @Immutable
    data class WifiDisconnected(
        val previousSsid: String? = null,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.WIFI_DISCONNECTED
    }

    @Immutable
    data class HotspotChanged(
        val enabled: Boolean,
        val connectedClients: Int = 0,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.HOTSPOT
    }

    @Immutable
    data class BluetoothChanged(
        val deviceName: String,
        val connected: Boolean,
        val isAudioDevice: Boolean,
        val batteryLevel: Int? = null,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.BLUETOOTH
    }

    @Immutable
    data class AirplaneModeChanged(
        val enabled: Boolean,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.AIRPLANE_MODE
    }

    @Immutable
    data class RingerModeChanged(
        val mode: RingerMode,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.RINGER_MODE
    }

    @Immutable
    data class IncomingCall(
        val callerName: String,
        val phoneNumber: String,
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.INCOMING_CALL
        override val isSticky: Boolean = true
    }

    @Immutable
    data class MusicPlayback(
        val title: String,
        val artist: String,
        val albumArt: Bitmap? = null,
        val dominantColorArgb: Int = DEFAULT_MEDIA_COLOR,
        val isPlaying: Boolean,
        val positionMs: Long = 0L,
        val durationMs: Long = 1L,
        val lastPositionUpdateTimeMs: Long = 0L,
        val playbackSpeed: Float = 1f,
        val packageName: String = "",
        override val id: String = "music_session",
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.MUSIC_PLAYER
        override val isSticky: Boolean = isPlaying

        companion object {
            const val DEFAULT_MEDIA_COLOR: Int = 0xFF1A1D2B.toInt()
        }
    }

    @Immutable
    data class MusicSessionEnded(
        override val id: String = UUID.randomUUID().toString(),
        override val timestampMs: Long = System.currentTimeMillis()
    ) : IslandEvent {
        override val type = IslandEventType.MUSIC_PLAYER
    }
}
