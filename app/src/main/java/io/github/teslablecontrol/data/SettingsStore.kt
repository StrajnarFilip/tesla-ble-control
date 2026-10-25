package io.github.teslablecontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class UnitSystem { AUTO, METRIC, IMPERIAL }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val units: UnitSystem = UnitSystem.AUTO,
    /** Ask before unlock, opening the trunk/frunk, remote start and other risky commands. */
    val confirmRisky: Boolean = true,
    /** Wake a sleeping car automatically for commands that need it. */
    val autoWake: Boolean = true,
    /** Keep a background Bluetooth connection (foreground service). */
    val stayConnected: Boolean = true,
    val startOnBoot: Boolean = true,
    /** Show controls for every model instead of only the selected car's. */
    val showAllControls: Boolean = false,
    val demoMode: Boolean = false,
    val onboardingDone: Boolean = false,
    /** Record every protocol message in the diagnostics log. */
    val detailedLog: Boolean = false,
    /** Show a message when a command succeeds (problems are always shown). */
    val commandMessages: Boolean = false,
    /**
     * How often the car's state is read again, in seconds (0 = never): by the open screen, and by
     * the background connection. These reads never wake the car.
     */
    val stateCheckSeconds: Int = 30,
    val selectedVin: String? = null,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context, scope: CoroutineScope) {
    private val store = context.settingsDataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val UNITS = stringPreferencesKey("units")
        val CONFIRM = booleanPreferencesKey("confirm_risky")
        val AUTO_WAKE = booleanPreferencesKey("auto_wake")
        val STAY_CONNECTED = booleanPreferencesKey("stay_connected")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val SHOW_ALL = booleanPreferencesKey("show_all_controls")
        val DEMO = booleanPreferencesKey("demo_mode")
        val ONBOARDED = booleanPreferencesKey("onboarding_done")
        val DETAILED_LOG = booleanPreferencesKey("detailed_log")
        val COMMAND_MESSAGES = booleanPreferencesKey("command_messages")
        val STATE_CHECK_SECONDS = intPreferencesKey("state_check_seconds")
        val SELECTED_VIN = stringPreferencesKey("selected_vin")
    }

    /** Null until the stored settings have been read once. */
    val settings: StateFlow<AppSettings?> = store.data
        .map(::read)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun read(p: Preferences) = AppSettings(
        theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        units = p[Keys.UNITS]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.AUTO,
        confirmRisky = p[Keys.CONFIRM] ?: true,
        autoWake = p[Keys.AUTO_WAKE] ?: true,
        stayConnected = p[Keys.STAY_CONNECTED] ?: true,
        startOnBoot = p[Keys.START_ON_BOOT] ?: true,
        showAllControls = p[Keys.SHOW_ALL] ?: false,
        demoMode = p[Keys.DEMO] ?: false,
        onboardingDone = p[Keys.ONBOARDED] ?: false,
        detailedLog = p[Keys.DETAILED_LOG] ?: false,
        commandMessages = p[Keys.COMMAND_MESSAGES] ?: false,
        // 15 s was an option before 5 s replaced it.
        stateCheckSeconds = p[Keys.STATE_CHECK_SECONDS]?.let { if (it == 15) 5 else it } ?: 30,
        selectedVin = p[Keys.SELECTED_VIN],
    )

    /** The current settings, or defaults if they haven't loaded yet. */
    val current: AppSettings get() = settings.value ?: AppSettings()

    suspend fun awaitLoaded(): AppSettings = settings.filterNotNull().first()

    /** Applies [transform] and returns the settings as saved. */
    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        var saved = AppSettings()
        store.edit { p ->
            val after = transform(read(p))
            saved = after
            p[Keys.THEME] = after.theme.name
            p[Keys.UNITS] = after.units.name
            p[Keys.CONFIRM] = after.confirmRisky
            p[Keys.AUTO_WAKE] = after.autoWake
            p[Keys.STAY_CONNECTED] = after.stayConnected
            p[Keys.START_ON_BOOT] = after.startOnBoot
            p[Keys.SHOW_ALL] = after.showAllControls
            p[Keys.DEMO] = after.demoMode
            p[Keys.ONBOARDED] = after.onboardingDone
            p[Keys.DETAILED_LOG] = after.detailedLog
            p[Keys.COMMAND_MESSAGES] = after.commandMessages
            p[Keys.STATE_CHECK_SECONDS] = after.stateCheckSeconds
            if (after.selectedVin != null) p[Keys.SELECTED_VIN] = after.selectedVin else p.remove(Keys.SELECTED_VIN)
        }
        return saved
    }
}
