package io.github.teslablecontrol.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.teslablecontrol.AppContainer
import io.github.teslablecontrol.R
import io.github.teslablecontrol.ble.BlePermissions
import io.github.teslablecontrol.container
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.core.LinkException
import io.github.teslablecontrol.core.LinkStatus
import io.github.teslablecontrol.core.VehicleController
import io.github.teslablecontrol.data.VehicleProfile
import io.github.teslablecontrol.protocol.transport.Transport
import io.github.teslablecontrol.ui.format.statusLine
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * Keeps a Bluetooth connection to the selected car. Instead of scanning in the background (which
 * Android restricts), it lets Android reconnect to the remembered car whenever it comes into range.
 * It reads only the body controller, which never wakes the car, as often as the state-check
 * interval in Settings says.
 */
class VehicleLinkService : LifecycleService() {
    private val app: AppContainer get() = container
    private var started = false
    @Volatile private var transientText: String? = null

    override fun onCreate() {
        super.onCreate()
        if (!enterForeground()) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                app.scope.launch { app.settings.update { it.copy(stayConnected = false) } }
                app.activeController()?.disconnect()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LOCK -> runCommand("lock")
            ACTION_UNLOCK -> runCommand("unlock")
        }
        if (!started) {
            started = true
            lifecycleScope.launch { app.activeProfile.collectLatest { profile -> if (profile != null) keepConnected(profile) } }
            lifecycleScope.launch { updateNotifications() }
        }
        return START_STICKY
    }

    private fun enterForeground(): Boolean = try {
        val notification = Notifications.linkNotification(this, getString(R.string.app_name), getString(R.string.status_connecting), false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifications.LINK_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(Notifications.LINK_NOTIFICATION_ID, notification)
        }
        true
    } catch (e: Exception) {
        // Android refuses connected-device services without Bluetooth permission, or from the background.
        app.diagnostics.warn("Background connection could not start: ${e.message}")
        false
    }

    private suspend fun keepConnected(profile: VehicleProfile) {
        if (profile.vin == AppContainer.DEMO_PROFILE.vin) return
        val controller = app.controllerFor(profile)
        var backoff = 5.seconds
        while (true) {
            if (controller.state.value.link != LinkStatus.CONNECTED) {
                if (app.profiles.find(profile.vin)?.bleAddress == null || !BlePermissions.canConnect(this)) {
                    // Nothing to reconnect to until the app has found the car once in the foreground.
                    delay(30.seconds)
                    continue
                }
                val transport = try {
                    waitForCar(controller, profile.vin)
                } catch (e: LinkException) {
                    app.diagnostics.warn("Background connection: ${e.message}")
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(120.seconds)
                    continue
                }
                if (transport != null && controller.attach(transport)) {
                    app.diagnostics.info("Background connection established")
                }
                backoff = 5.seconds
                controller.refreshBodyState()
            }
            coroutineScope {
                val poller = launch {
                    app.settings.settings.filterNotNull().map { it.stateCheckSeconds }.distinctUntilChanged().collectLatest { seconds ->
                        while (seconds > 0) {
                            delay(seconds.seconds)
                            controller.refreshBodyState()
                        }
                    }
                }
                controller.awaitDisconnected()
                poller.cancel()
            }
            delay(2.seconds)
        }
    }

    /** Waits for Android to reach the car, unless the app connects some other way first. */
    private suspend fun waitForCar(controller: VehicleController, vin: String): Transport? = coroutineScope {
        val link = async { app.linkFactory.waitForVehicle(vin) }
        val connectedElsewhere = async { controller.state.first { it.link == LinkStatus.CONNECTED } }
        select {
            link.onAwait {
                connectedElsewhere.cancel()
                it
            }
            connectedElsewhere.onAwait {
                link.cancel()
                null
            }
        }
    }

    private fun runCommand(id: String) {
        val controller = app.activeController() ?: return
        app.scope.launch {
            transientText = getString(if (id == "lock") R.string.status_locking else R.string.status_unlocking)
            val outcome = controller.execute(id)
            transientText = if (outcome is CommandOutcome.Failure) {
                getString(R.string.notification_command_failed, outcome.message)
            } else {
                null
            }
            refreshNotification()
            delay(5.seconds)
            transientText = null
            refreshNotification()
        }
    }

    private suspend fun updateNotifications() {
        app.activeProfile.collectLatest { profile ->
            if (profile == null) return@collectLatest
            val controller = app.controllerFor(profile)
            combine(controller.state, app.settings.settings) { state, _ -> state }
                .map { state -> notificationContent(profile, state.link, statusLine(this, state)) }
                .distinctUntilChanged()
                .collect { refreshNotification() }
        }
    }

    private fun notificationContent(profile: VehicleProfile, link: LinkStatus, status: String) = Triple(profile.name, link, status)

    private fun refreshNotification() {
        val profile = app.activeProfile.value ?: return
        val state = app.controllerFor(profile).state.value
        val text = transientText ?: statusLine(this, state)
        val notification = Notifications.linkNotification(this, profile.name, text, showLockActions = state.link == LinkStatus.CONNECTED)
        getSystemService(NotificationManager::class.java)?.notify(Notifications.LINK_NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_LOCK = "io.github.teslablecontrol.action.LOCK"
        const val ACTION_UNLOCK = "io.github.teslablecontrol.action.UNLOCK"
        const val ACTION_STOP = "io.github.teslablecontrol.action.STOP"

        /** Starts the background connection; harmless if already running or not allowed right now. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, VehicleLinkService::class.java))
            } catch (e: Exception) {
                context.container.diagnostics.warn("Could not start background connection: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VehicleLinkService::class.java))
        }
    }
}
