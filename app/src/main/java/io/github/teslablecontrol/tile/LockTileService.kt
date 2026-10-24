package io.github.teslablecontrol.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.teslablecontrol.MainActivity
import io.github.teslablecontrol.R
import io.github.teslablecontrol.container
import io.github.teslablecontrol.core.LinkStatus
import io.github.teslablecontrol.core.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Lock/unlock from Quick Settings. Unlocking from a locked phone asks for the phone's own unlock
 * first. With the background connection running the command is sent at once; otherwise a small
 * activity connects and shows progress.
 */
class LockTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val app = container
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch {
                app.activeProfile.collectLatest { profile ->
                    if (profile == null) {
                        render(null, null, null, false)
                    } else {
                        app.controllerFor(profile).state.collect { state ->
                            render(profile.name, state.snapshot.isLocked, state.activity?.stage, state.link == LinkStatus.CONNECTED)
                        }
                    }
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    private fun render(name: String?, locked: Boolean?, stage: Stage?, connected: Boolean) {
        val tile = qsTile ?: return
        tile.label = name ?: getString(R.string.tile_label)
        tile.state = when {
            name == null -> Tile.STATE_UNAVAILABLE
            locked == true -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, if (locked == false) R.drawable.ic_tile_unlock else R.drawable.ic_tile_lock)
        val subtitle = when {
            stage != null -> getString(R.string.status_sending)
            locked == true -> getString(R.string.status_locked)
            locked == false -> getString(R.string.status_unlocked)
            connected -> getString(R.string.status_connected)
            else -> getString(R.string.status_not_connected)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tile.stateDescription = subtitle
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val app = container
        val profile = app.activeProfile.value
        if (profile == null) {
            launch(Intent(this, MainActivity::class.java))
            return
        }
        val controller = app.controllerFor(profile)
        // When the lock state is unknown, locking is the safe choice.
        val command = if (controller.state.value.snapshot.isLocked == true) "unlock" else "lock"
        val perform = {
            if (controller.state.value.link == LinkStatus.CONNECTED) {
                app.scope.launch { controller.execute(command) }
            } else {
                launch(QuickCommandActivity.intent(this, command))
            }
            Unit
        }
        if (command == "unlock" && isLocked) unlockAndRun(perform) else perform()
    }

    // Before Android 14 the deprecated Intent overload is the only way to open an activity from a tile.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, intent.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Asks Quick Settings to re-read the tile, e.g. after the lock state changes. */
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, LockTileService::class.java)) }
        }

        /** Android 13+ shows a one-tap "Add tile" prompt; older versions need the manual steps. */
        val canRequestAdd: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        /** What happened when the app asked to add the tile. */
        enum class AddResult { ADDED, ALREADY_ADDED, DECLINED, UNAVAILABLE }

        /**
         * Asks Android to add the tile. Android shows nothing when the tile is already there, so the
         * result is always reported, on the main thread.
         */
        fun requestAdd(context: Context, onResult: (AddResult) -> Unit) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return onResult(AddResult.UNAVAILABLE)
            val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return onResult(AddResult.UNAVAILABLE)
            statusBar.requestAddTileService(
                ComponentName(context, LockTileService::class.java),
                context.getString(R.string.tile_label),
                Icon.createWithResource(context, R.drawable.ic_tile_lock),
                context.mainExecutor,
            ) { code ->
                onResult(
                    when (code) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> AddResult.ADDED
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> AddResult.ALREADY_ADDED
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> AddResult.DECLINED
                        else -> AddResult.UNAVAILABLE
                    },
                )
            }
        }
    }
}
