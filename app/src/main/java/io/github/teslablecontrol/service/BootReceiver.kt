package io.github.teslablecontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.teslablecontrol.container
import kotlinx.coroutines.launch

/** Restarts the background connection after a reboot or app update, if the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.container
        app.scope.launch {
            try {
                val settings = app.settings.awaitLoaded()
                if (settings.onboardingDone && settings.stayConnected && settings.startOnBoot && !settings.demoMode) {
                    VehicleLinkService.start(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
