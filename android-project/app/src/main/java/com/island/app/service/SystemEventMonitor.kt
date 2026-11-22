package com.island.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.island.app.data.CrashLogger
import com.island.app.data.IslandSettings
import com.island.app.model.IslandEvent
import com.island.app.model.RingerMode
import com.island.app.state.IslandStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SystemEventMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val stateManager: IslandStateManager,
    private val settingsProvider: () -> IslandSettings = { IslandSettings.Default }
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var lastChargingState: Boolean? = null
    private var lastBatteryLevel: Int = -1
    private var warned20 = false
    private var warned10 = false
    private var notifiedFull = false
    private var wifiBaselineRecorded = false
    private var lastWifiConnected: Boolean? = null
    private var lastWifiSsid: String? = null
    private var lastHotspotEnabled: Boolean? = null
    private var lastRingerMode: Int = runCatching {
        audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
    }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)
    private var lastAirplaneMode: Boolean? = null
    private val lastBluetoothEvents = mutableMapOf<String, Pair<Boolean, Long>>()
    private var isRegistered = false
    private var callCommandJob: Job? = null

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            runCatching {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        val pct = readCurrentBatteryPercent()
                        lastChargingState = true
                        warned20 = false
                        warned10 = false
                        stateManager.postEvent(IslandEvent.ChargingStarted(pct))
                    }

                    Intent.ACTION_POWER_DISCONNECTED -> {
                        val pct = readCurrentBatteryPercent()
                        lastChargingState = false
                        notifiedFull = false
                        stateManager.postEvent(IslandEvent.ChargingStopped(pct))
                    }

                    Intent.ACTION_BATTERY_CHANGED -> handleBatteryChanged(intent)

                    "android.net.wifi.WIFI_AP_STATE_CHANGED" -> handleHotspotChanged(intent)

                    BluetoothDevice.ACTION_ACL_CONNECTED -> handleBluetoothEvent(intent, connected = true)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleBluetoothEvent(intent, connected = false)

                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        val enabled = intent.getBooleanExtra("state", isAirplaneModeOn())
                        if (lastAirplaneMode != enabled) {
                            lastAirplaneMode = enabled
                            stateManager.postEvent(IslandEvent.AirplaneModeChanged(enabled))
                        }
                    }

                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        val fallbackMode = runCatching {
                            audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
                        }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)
                        val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, fallbackMode)
                        if (lastRingerMode != mode) {
                            lastRingerMode = mode
                            val mapped = when (mode) {
                                AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
                                AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
                                else -> RingerMode.RING
                            }
                            stateManager.postEvent(IslandEvent.RingerModeChanged(mapped))
                        }
                    }

                    TelephonyManager.ACTION_PHONE_STATE_CHANGED -> handlePhoneState(intent)
                }
            }.onFailure {
                CrashLogger.recordNonFatal(context, "BroadcastReceiver error for ${intent.action}", it)
            }
        }
    }

    private val wifiCallback: ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
            ) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val transportWifi = caps.transportInfo as? WifiInfo
                    val ssid = sanitizeSsid(transportWifi?.ssid) ?: resolveCurrentWifiSsid()
                    dispatchWifiConnected(ssid)
                }

                override fun onLost(network: Network) {
                    dispatchWifiLost()
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    dispatchWifiConnected(resolveCurrentWifiSsid())
                }

                override fun onLost(network: Network) {
                    dispatchWifiLost()
                }
            }
        }

    private fun dispatchWifiConnected(ssid: String) {
        scope.launch {
            if (!wifiBaselineRecorded) {
                wifiBaselineRecorded = true
                lastWifiConnected = true
                lastWifiSsid = ssid
                return@launch
            }
            if (lastWifiConnected != true || lastWifiSsid != ssid) {
                lastWifiConnected = true
                lastWifiSsid = ssid
                stateManager.postEvent(IslandEvent.WifiConnected(ssid = ssid))
            }
        }
    }

    private fun dispatchWifiLost() {
        scope.launch {
            if (!wifiBaselineRecorded) {
                wifiBaselineRecorded = true
                lastWifiConnected = false
                lastWifiSsid = null
                return@launch
            }
            if (lastWifiConnected == true) {
                lastWifiConnected = false
                val prev = lastWifiSsid
                lastWifiSsid = null
                stateManager.postEvent(IslandEvent.WifiDisconnected(previousSsid = prev))
            }
        }
    }

    fun start() {
        if (isRegistered) return
        isRegistered = true
        wifiBaselineRecorded = false

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                systemReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.onFailure {
            CrashLogger.recordNonFatal(context, "registerReceiver failed", it)
        }

        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching { connectivityManager?.registerNetworkCallback(wifiRequest, wifiCallback) }

        callCommandJob?.cancel()
        callCommandJob = scope.launch {
            IslandEventBus.callCommands.collectLatest { cmd ->
                handleCallCommand(cmd)
            }
        }
    }

    fun stop() {
        if (!isRegistered) return
        isRegistered = false
        wifiBaselineRecorded = false
        callCommandJob?.cancel()
        callCommandJob = null
        runCatching { context.unregisterReceiver(systemReceiver) }
        runCatching { connectivityManager?.unregisterNetworkCallback(wifiCallback) }
    }

    private fun handleBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return
        val percent = (level * 100) / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        if (lastBatteryLevel == -1) {
            lastBatteryLevel = percent
            lastChargingState = isCharging
            return
        }

        if (isCharging && (status == BatteryManager.BATTERY_STATUS_FULL || percent >= 100) && !notifiedFull) {
            notifiedFull = true
            stateManager.postEvent(IslandEvent.BatteryFull(percent))
        } else if (!isCharging) {
            notifiedFull = false
            if (percent <= 10 && !warned10) {
                warned10 = true
                warned20 = true
                stateManager.postEvent(IslandEvent.LowBattery(percent, isCritical = true))
            } else if (percent <= 20 && !warned20) {
                warned20 = true
                stateManager.postEvent(IslandEvent.LowBattery(percent, isCritical = false))
            } else if (percent > 22) {
                warned20 = false
                warned10 = false
            }
        }
        lastBatteryLevel = percent
        lastChargingState = isCharging
    }

    private fun handleHotspotChanged(intent: Intent) {
        val state = intent.getIntExtra("wifi_state", -1)
        val enabled = when (state) {
            13 -> true
            11 -> false
            else -> return
        }
        if (lastHotspotEnabled != enabled) {
            lastHotspotEnabled = enabled
            stateManager.postEvent(IslandEvent.HotspotChanged(enabled = enabled))
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleBluetoothEvent(intent: Intent, connected: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (shouldDebounceBluetooth("generic_bt", connected, now)) return
            stateManager.postEvent(
                IslandEvent.BluetoothChanged(
                    deviceName = "Bluetooth Device",
                    connected = connected,
                    isAudioDevice = true
                )
            )
            return
        }

        val device: BluetoothDevice? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        }.getOrNull() ?: return

        val isAudio = runCatching {
            val majorClass = device.bluetoothClass?.majorDeviceClass
            val deviceClass = device.bluetoothClass?.deviceClass
            majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER
        }.getOrDefault(false)

        if (settingsProvider().bluetoothAudioOnly && !isAudio) {
            return
        }

        val name = runCatching {
            device.name?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "Bluetooth Device"

        val deviceKey = runCatching { device.address?.takeIf { it.isNotBlank() } }.getOrNull() ?: name
        if (shouldDebounceBluetooth(deviceKey, connected, now)) {
            return
        }

        stateManager.postEvent(
            IslandEvent.BluetoothChanged(
                deviceName = name,
                connected = connected,
                isAudioDevice = isAudio
            )
        )
    }

    private fun shouldDebounceBluetooth(deviceKey: String, connected: Boolean, nowMs: Long): Boolean {
        val previous = lastBluetoothEvents[deviceKey]
        if (previous != null && previous.first == connected && (nowMs - previous.second) < BLUETOOTH_DEBOUNCE_MS) {
            return true
        }
        lastBluetoothEvents[deviceKey] = connected to nowMs
        return false
    }

    @SuppressLint("MissingPermission")
    private fun handlePhoneState(intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    ?.takeIf { it.isNotBlank() }
                    ?: "Incoming Call"
                stateManager.postEvent(
                    IslandEvent.IncomingCall(
                        callerName = number,
                        phoneNumber = number
                    )
                )
            }
            TelephonyManager.EXTRA_STATE_IDLE,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                stateManager.clearIncomingCall()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleCallCommand(command: CallCommand) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            CrashLogger.recordNonFatal(
                context,
                "Call control $command requires Android 9 (API 28) or higher"
            )
            openDefaultDialer()
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            CrashLogger.recordNonFatal(
                context,
                "Missing ANSWER_PHONE_CALLS permission for call action $command"
            )
            openDefaultDialer()
            return
        }
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (telecom == null) {
            CrashLogger.recordNonFatal(context, "TelecomManager unavailable for call action $command")
            openDefaultDialer()
            return
        }

        val succeeded = runCatching {
            when (command) {
                CallCommand.Accept -> {
                    telecom.acceptRingingCall()
                    true
                }
                CallCommand.Decline -> telecom.endCall()
            }
        }.onFailure { err ->
            CrashLogger.recordNonFatal(context, "TelecomManager call action $command threw", err)
        }.getOrDefault(false)

        if (succeeded) {
            stateManager.clearIncomingCall()
        } else {
            CrashLogger.recordNonFatal(context, "TelecomManager call action $command did not complete")
            openDefaultDialer()
        }
    }

    private fun openDefaultDialer() {
        runCatching {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
        }.onFailure { err ->
            CrashLogger.recordNonFatal(context, "Unable to open default dialer", err)
        }
    }

    private fun readCurrentBatteryPercent(): Int {
        return runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            (bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0).coerceIn(0, 100)
        }.getOrDefault(0)
    }

    @Suppress("DEPRECATION")
    private fun resolveCurrentWifiSsid(): String {
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) return "Wi-Fi Network"
        return runCatching {
            sanitizeSsid(wifiManager?.connectionInfo?.ssid)
        }.getOrNull() ?: "Wi-Fi Network"
    }

    private fun sanitizeSsid(raw: String?): String? {
        val cleaned = raw?.removePrefix("\"")?.removeSuffix("\"")
        return if (cleaned.isNullOrBlank() || cleaned == "<unknown ssid>") null else cleaned
    }

    private fun isAirplaneModeOn(): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }.getOrDefault(false)

    companion object {
        private const val BLUETOOTH_DEBOUNCE_MS = 5_000L
    }
}
