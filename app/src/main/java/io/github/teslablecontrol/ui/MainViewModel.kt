package io.github.teslablecontrol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.teslablecontrol.AppContainer
import io.github.teslablecontrol.R
import io.github.teslablecontrol.container
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.core.ControllerState
import io.github.teslablecontrol.core.FailureKind
import io.github.teslablecontrol.core.LinkStatus
import io.github.teslablecontrol.core.VehicleController
import io.github.teslablecontrol.data.AppSettings
import io.github.teslablecontrol.data.VehicleProfile
import io.github.teslablecontrol.keys.KeyManager
import io.github.teslablecontrol.protocol.commands.CommandCatalog
import io.github.teslablecontrol.protocol.commands.SessionRequirement
import io.github.teslablecontrol.protocol.vehicle.CommandResult
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.service.VehicleLinkService
import io.github.teslablecontrol.ui.commands.CommandInfos
import io.github.teslablecontrol.ui.format.FailureAction
import io.github.teslablecontrol.ui.format.failureAction
import io.github.teslablecontrol.ui.format.failureMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A message for the snackbar (and TalkBack), optionally with a fix. */
data class UiMessage(
    val text: String,
    val action: FailureAction? = null,
    val isError: Boolean = false,
    val kind: FailureKind? = null,
    /** The command to resend for [FailureAction.RETRY]. */
    val commandId: String? = null,
    val args: Map<String, String> = emptyMap(),
)

/** A command waiting for the user to confirm it. */
data class PendingCommand(
    val commandId: String,
    val args: Map<String, String>,
    val title: String,
    val description: String,
    val onResult: (CommandOutcome) -> Unit = {},
)

private const val IDLE_DISCONNECT_MILLIS = 30_000L

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app: AppContainer = application.container

    val settings: StateFlow<AppSettings?> = app.settings.settings
    val profiles: StateFlow<List<VehicleProfile>?> = app.profiles.profiles
    val profile: StateFlow<VehicleProfile?> = app.activeProfile
    val keyStatus: StateFlow<KeyManager.Status> = app.keys.status

    val controllerState: StateFlow<ControllerState> = profile
        .flatMapLatest { p -> if (p == null) flowOf(ControllerState()) else app.controllerFor(p).state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ControllerState())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _pending = MutableStateFlow<PendingCommand?>(null)
    val pending: StateFlow<PendingCommand?> = _pending.asStateFlow()

    private val _running = MutableStateFlow<String?>(null)

    /**
     * The command being sent from this screen, or waiting for the user to confirm it, for per-control
     * progress; a slider keeps showing the new choice meanwhile.
     */
    val running: StateFlow<String?> = combine(_running, _pending) { sending, waiting -> sending ?: waiting?.commandId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val controller: VehicleController? get() = profile.value?.let(app::controllerFor)

    /** Settings the car doesn't report, as last set from this phone (kept in memory for the demo car too). */
    private val lastSetHere = MutableStateFlow<Map<String, Map<String, Boolean>>>(emptyMap())
    val lastSet: StateFlow<Map<String, Boolean>> = combine(profile, lastSetHere) { p, here ->
        p?.let { it.lastSet + here[it.vin].orEmpty() }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Sends a command, asking first if it's risky and confirmations are on. */
    fun send(
        commandId: String,
        args: Map<String, String> = emptyMap(),
        confirmed: Boolean = false,
        onResult: (CommandOutcome) -> Unit = {},
    ) {
        val info = CommandInfos.get(commandId)
        if (info.risky && !confirmed && app.settings.current.confirmRisky) {
            _pending.value = PendingCommand(commandId, args, info.title, info.description, onResult)
            return
        }
        run(commandId, args, onResult)
    }

    fun confirmPending() {
        val pending = _pending.value ?: return
        _pending.value = null
        run(pending.commandId, pending.args, pending.onResult)
    }

    fun dismissPending() {
        _pending.value = null
    }

    /** Runs a command now (no confirmation), reports the outcome and passes it to [onResult]. */
    fun run(commandId: String, args: Map<String, String> = emptyMap(), onResult: (CommandOutcome) -> Unit = {}) {
        val target = controller ?: return
        viewModelScope.launch {
            _running.value = commandId
            val outcome = target.execute(commandId, args)
            _running.update { if (it == commandId) null else it }
            if (outcome is CommandOutcome.Success) rememberSetting(commandId, args)
            report(commandId, outcome, args)
            onResult(outcome)
        }
    }

    /** Remembers settings the car can't report, so their tiles can show what was last set here. */
    private suspend fun rememberSetting(commandId: String, args: Map<String, String>) {
        val vin = profile.value?.vin ?: return
        val (key, value) = when (commandId) {
            "low-power-mode", "keep-accessory-power" -> commandId to (args["STATE"]?.lowercase() == "on")
            "guest-mode-on" -> "guest-mode" to true
            "guest-mode-off" -> "guest-mode" to false
            else -> return
        }
        lastSetHere.update { all -> all + (vin to (all[vin].orEmpty() + (key to value))) }
        app.profiles.update(vin) { it.copy(lastSet = it.lastSet + (key to value)) }
    }

    /** Remembers which seats the car has heaters and fans in, so the Climate screen knows while it sleeps. */
    fun rememberSeats(heated: Set<String>, cooled: Set<String>) {
        val p = profile.value ?: return
        if (p.heatedSeats == heated && p.cooledSeats == cooled) return
        viewModelScope.launch { app.profiles.update(p.vin) { it.copy(heatedSeats = heated, cooledSeats = cooled) } }
    }

    private fun report(commandId: String, outcome: CommandOutcome, args: Map<String, String> = emptyMap()) {
        val context = getApplication<Application>()
        when (outcome) {
            is CommandOutcome.Success -> {
                // Reads update the screen directly; only actions get a confirmation message.
                val quiet = outcome.result is CommandResult.State || outcome.result is CommandResult.BodyStatus ||
                    outcome.result is CommandResult.KeyList
                // The buttons show the result, so confirmations are optional.
                if (!quiet && app.settings.current.commandMessages) _messages.tryEmit(UiMessage(CommandInfos.get(commandId).successText))
            }
            is CommandOutcome.Failure -> _messages.tryEmit(
                UiMessage(
                    failureMessage(context, outcome), failureAction(outcome), isError = !outcome.mayHaveSucceeded,
                    kind = outcome.kind, commandId = commandId, args = args,
                ),
            )
        }
    }

    /** Re-reads state. When [wake] is false a sleeping car is left asleep (its cached data stays). */
    fun refresh(categories: Collection<StateCategory>, wake: Boolean = false): Job? {
        val target = controller ?: return null
        return viewModelScope.launch {
            target.refreshBodyState()
            val outcome = target.refresh(categories, wake)
            if (wake && outcome is CommandOutcome.Failure) report("state", outcome)
        }
    }

    private val _checkingQuietly = MutableStateFlow(false)

    /** True while a periodic state check runs, which the screen doesn't show as activity. */
    val checkingQuietly: StateFlow<Boolean> = _checkingQuietly.asStateFlow()

    /**
     * A periodic state check: the doors and locks, then [categories] if the car is awake. It never
     * wakes the car, and runs only over an open connection, so it never starts a Bluetooth search.
     */
    fun checkState(categories: Collection<StateCategory>) {
        val target = controller ?: return
        val now = target.state.value
        if (now.link != LinkStatus.CONNECTED || now.activity != null || _checkingQuietly.value) return
        viewModelScope.launch {
            _checkingQuietly.value = true
            try {
                target.refreshBodyState()
                // The body controller says whether the car is awake; asking a sleeping car only fails.
                if (target.state.value.snapshot.isAsleep == false) target.refresh(categories, wake = false)
            } finally {
                _checkingQuietly.value = false
            }
        }
    }

    fun refreshBody() {
        val target = controller ?: return
        viewModelScope.launch { target.refreshBodyState() }
    }

    /** True if the command needs the car awake (so the UI can say "wakes the car"). */
    fun wakesCar(commandId: String): Boolean = CommandCatalog.find(commandId)?.requirement == SessionRequirement.INFOTAINMENT

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val saved = app.settings.update(transform)
            syncBackgroundConnection(settings = saved)
        }
    }

    fun selectVehicle(vin: String) = updateSettings { it.copy(selectedVin = vin, demoMode = false) }

    fun saveProfile(profile: VehicleProfile) {
        viewModelScope.launch { syncBackgroundConnection(profiles = app.profiles.upsert(profile)) }
    }

    /** Records that the car accepted this phone's key (after pairing or an import). */
    fun markPaired(vin: String, role: String? = null) {
        viewModelScope.launch {
            val saved = app.profiles.update(vin) {
                it.copy(paired = true, pairedAtMillis = it.pairedAtMillis ?: System.currentTimeMillis(), role = role ?: it.role)
            }
            syncBackgroundConnection(profiles = saved)
        }
    }

    fun removeVehicle(vin: String) {
        viewModelScope.launch {
            app.controllerFor(vin).disconnect()
            app.sessionStore.clear(vin)
            val profiles = app.profiles.remove(vin)
            val settings = app.settings.update { s -> if (s.selectedVin == vin) s.copy(selectedVin = null) else s }
            syncBackgroundConnection(settings, profiles)
        }
    }

    /** Asks the car whether it knows this phone's key and records the answer. */
    fun recheckPairing(onResult: (Boolean?) -> Unit = {}) {
        val p = profile.value ?: return
        val target = controller ?: return
        viewModelScope.launch {
            val paired = target.checkPaired()
            if (paired == true) markPaired(p.vin)
            if (paired == false) {
                syncBackgroundConnection(profiles = app.profiles.update(p.vin) { it.copy(paired = false, pairedAtMillis = null) })
            }
            onResult(paired)
        }
    }

    /** Replaces this phone's key with an imported one, then checks whether the car knows it. */
    fun importKey(pem: String) {
        viewModelScope.launch {
            val imported = runCatching { withContext(Dispatchers.IO) { app.keys.importPem(pem) } }
            if (imported.isFailure) {
                _messages.tryEmit(UiMessage("That isn't a usable private key: ${imported.exceptionOrNull()?.message}", isError = true))
                return@launch
            }
            forgetPairing()
            _messages.tryEmit(UiMessage("Key imported. Checking it with the car…"))
            recheckPairing { paired ->
                _messages.tryEmit(
                    when (paired) {
                        true -> UiMessage("The car accepts the imported key")
                        false -> UiMessage(getApplication<Application>().getString(R.string.failure_not_paired), FailureAction.PAIR, isError = true)
                        null -> UiMessage("Couldn't reach the car to check the key. It's checked again on the next command.")
                    },
                )
            }
        }
    }

    /** Makes a fresh key; every car must be paired again. */
    fun createNewKey() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.keys.replaceWithNewKey() }
            forgetPairing()
            _messages.tryEmit(UiMessage("New key created. Pair this phone with your car again.", FailureAction.PAIR))
        }
    }

    private suspend fun forgetPairing() {
        var saved = app.profiles.profiles.value.orEmpty()
        saved.forEach { p ->
            app.controllerFor(p.vin).resetKey()
            saved = app.profiles.update(p.vin) { it.copy(paired = false, pairedAtMillis = null) }
        }
        syncBackgroundConnection(profiles = saved)
    }

    private var idleDisconnect: Job? = null

    fun onAppVisible() {
        idleDisconnect?.cancel()
        viewModelScope.launch {
            app.settings.awaitLoaded()
            syncBackgroundConnection(profiles = app.profiles.profiles.filterNotNull().first())
        }
    }

    /**
     * Without the background connection, the link is closed a little after the app leaves the screen,
     * so it doesn't hold one of the car's three Bluetooth connections. A quick switch to another app
     * keeps it.
     */
    fun onAppHidden() {
        idleDisconnect?.cancel()
        idleDisconnect = app.scope.launch {
            delay(IDLE_DISCONNECT_MILLIS)
            val s = app.settings.current
            val backgroundKeepsLink = s.stayConnected && !s.demoMode && profile.value?.paired == true
            val controller = app.activeController() ?: return@launch
            if (!backgroundKeepsLink && controller.state.value.activity == null) controller.disconnect()
        }
    }

    fun emit(message: UiMessage) {
        _messages.tryEmit(message)
    }

    /**
     * Starts or stops the background connection to match the settings. Callers that just saved
     * settings or profiles pass what they saved, since the store's flows update a moment later.
     */
    fun syncBackgroundConnection(
        settings: AppSettings = app.settings.current,
        profiles: List<VehicleProfile>? = app.profiles.profiles.value,
    ) {
        val context = getApplication<Application>()
        val active = app.activeProfileFor(settings, profiles)
        if (settings.onboardingDone && settings.stayConnected && !settings.demoMode && active != null && active.paired) {
            VehicleLinkService.start(context)
        } else {
            VehicleLinkService.stop(context)
        }
    }
}
