package com.island.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.island.app.data.CrashLogger
import com.island.app.data.SettingsRepository

class IslandApplication : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        settingsRepository = SettingsRepository(applicationContext)
        createServiceChannel()
    }

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val channel = NotificationChannel(
                    CHANNEL_OVERLAY_SERVICE,
                    getString(R.string.service_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.service_channel_description)
                    setShowBadge(false)
                }
                val nm = getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_OVERLAY_SERVICE = "island_overlay_channel"
    }
}
