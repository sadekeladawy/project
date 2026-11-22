package com.island.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.island.app.IslandApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as IslandApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = app.settingsRepository.settingsFlow.first()
                if (settings.autoStartOnBoot && settings.masterEnabled && Settings.canDrawOverlays(context)) {
                    IslandOverlayService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
