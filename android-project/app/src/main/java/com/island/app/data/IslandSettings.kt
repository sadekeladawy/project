package com.island.app.data

import androidx.compose.runtime.Immutable

enum class IslandEventType(
    val displayName: String,
    val description: String,
    val key: String
) {
    CHARGING_STARTED("Charging Started", "Bolt icon and filling battery ring", "charging_started"),
    CHARGING_STOPPED("Charging Stopped", "Unplugged battery summary", "charging_stopped"),
    BATTERY_FULL("Battery Full", "100% full charge badge", "battery_full"),
    LOW_BATTERY("Low Battery", "20% and 10% warning alerts", "low_battery"),
    WIFI_CONNECTED("Wi-Fi Connected", "Connected network name and icon", "wifi_connected"),
    WIFI_DISCONNECTED("Wi-Fi Disconnected", "Crossed-out Wi-Fi alert", "wifi_disconnected"),
    HOTSPOT("Hotspot On / Off", "Personal hotspot state changes", "hotspot"),
    BLUETOOTH("Bluetooth Devices", "Device name & headphone icon", "bluetooth"),
    AIRPLANE_MODE("Airplane Mode", "Radio status toggle alert", "airplane_mode"),
    RINGER_MODE("Silent / Vibrate / Ring", "Audio ringer profile changes", "ringer_mode"),
    INCOMING_CALL("Incoming Call", "Caller card with Accept & Decline", "incoming_call"),
    MUSIC_PLAYER("Music Player", "Live compact equalizer & media controls", "music_player")
}

enum class DisplayDurationOption(val seconds: Int, val label: String) {
    SHORT(2, "2s"),
    MEDIUM(4, "4s"),
    LONG(6, "6s");

    val millis: Long get() = seconds * 1000L

    companion object {
        fun fromSeconds(seconds: Int): DisplayDurationOption =
            entries.firstOrNull { it.seconds == seconds } ?: MEDIUM
    }
}

@Immutable
data class IslandSettings(
    val masterEnabled: Boolean = true,
    val autoStartOnBoot: Boolean = true,
    val hideInFullscreen: Boolean = true,
    val bluetoothAudioOnly: Boolean = true,
    val appExitAnimationEnabled: Boolean = true,
    val animationSpeed: Float = 1.0f,  // 0.5x (slower) .. 2.0x (faster)
    val displayDuration: DisplayDurationOption = DisplayDurationOption.MEDIUM,
    val offsetX: Float = 0f,           // dp: -120..120
    val offsetY: Float = 12f,          // dp: 0..96
    val idleWidthDp: Float = 126f,     // dp: 84..220
    val idleHeightDp: Float = 36f,     // dp: 24..54
    val cornerRadiusDp: Float = 24f,   // dp: 12..36
    val enabledEvents: Map<IslandEventType, Boolean> = IslandEventType.entries.associateWith { true }
) {
    fun isEventEnabled(type: IslandEventType): Boolean = enabledEvents[type] ?: true

    companion object {
        val Default = IslandSettings()
    }
}
