package io.github.teslablecontrol

import android.app.Application
import android.content.Context
import io.github.teslablecontrol.service.Notifications
import io.github.teslablecontrol.tile.LockTileService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TeslaBleApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        // Keep the Quick Settings tile in step with the car's lock state.
        container.scope.launch {
            container.activeProfile.collectLatest { profile ->
                LockTileService.requestUpdate(this@TeslaBleApp)
                if (profile == null) return@collectLatest
                container.controllerFor(profile).state
                    .map { Triple(it.snapshot.isLocked, it.activity != null, it.link) }
                    .distinctUntilChanged()
                    .collect { LockTileService.requestUpdate(this@TeslaBleApp) }
            }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as TeslaBleApp).container
