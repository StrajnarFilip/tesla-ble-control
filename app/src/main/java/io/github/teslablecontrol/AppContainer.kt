package io.github.teslablecontrol

import android.content.Context
import io.github.teslablecontrol.ble.BleLinkFactory
import io.github.teslablecontrol.ble.VehicleScanner
import io.github.teslablecontrol.core.ControllerConfig
import io.github.teslablecontrol.core.DemoVehicleController
import io.github.teslablecontrol.core.LinkFactory
import io.github.teslablecontrol.core.RealVehicleController
import io.github.teslablecontrol.core.VehicleController
import io.github.teslablecontrol.data.AppSettings
import io.github.teslablecontrol.data.DiagnosticsLog
import io.github.teslablecontrol.data.FileSessionStore
import io.github.teslablecontrol.data.ProfileStore
import io.github.teslablecontrol.data.SettingsStore
import io.github.teslablecontrol.data.VehicleProfile
import io.github.teslablecontrol.keys.KeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.io.File

/** App-wide objects. Controllers live as long as the process, so connections survive screen changes. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val diagnostics = DiagnosticsLog()
    val settings = SettingsStore(appContext, scope)
    val profiles = ProfileStore(appContext, scope)
    val keys = KeyManager(appContext)
    val sessionStore = FileSessionStore(File(appContext.noBackupFilesDir, "sessions"))
    val scanner = VehicleScanner(appContext, diagnostics)
    val linkFactory = BleLinkFactory(appContext, profiles, scanner, diagnostics) { vin, address ->
        profiles.update(vin) { it.copy(bleAddress = address) }
    }

    /** Debug builds can reach a simulated car instead of Bluetooth; see DebugLinks. */
    private val controllerLinks: LinkFactory = DebugLinks.simulator(appContext)
        ?.also { diagnostics.info("Using the vehicle simulator instead of Bluetooth") }
        ?: linkFactory

    val demoController = DemoVehicleController()
    private val controllers = mutableMapOf<String, RealVehicleController>()

    fun controllerFor(vin: String): VehicleController = synchronized(controllers) {
        controllers.getOrPut(vin) {
            RealVehicleController(
                vin = vin,
                linkFactory = controllerLinks,
                keyProvider = { keys.currentKey() },
                sessionStore = sessionStore,
                scope = scope,
                config = ControllerConfig(autoWake = { settings.current.autoWake }),
                log = diagnostics,
            )
        }
    }

    /** The car the app is showing: the selected profile, or the demo car in demo mode. */
    val activeProfile: StateFlow<VehicleProfile?> = combine(settings.settings, profiles.profiles, ::activeProfileFor)
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun activeProfileFor(s: AppSettings?, list: List<VehicleProfile>?): VehicleProfile? = when {
        s == null || list == null -> null
        s.demoMode -> DEMO_PROFILE
        else -> list.firstOrNull { it.vin == s.selectedVin } ?: list.firstOrNull()
    }

    fun controllerFor(profile: VehicleProfile): VehicleController =
        if (profile.vin == DEMO_PROFILE.vin) demoController else controllerFor(profile.vin)

    fun activeController(): VehicleController? = activeProfile.value?.let(::controllerFor)

    init {
        settings.settings.onEach { it?.let { s -> diagnostics.detailed = s.detailedLog } }.launchIn(scope)
    }

    companion object {
        val DEMO_PROFILE = VehicleProfile(vin = "5YJ3DEMO000000001", name = "Demo car", paired = true)
    }
}
