package io.github.teslablecontrol.core

import com.tesla.generated.vcsec.Vcsec
import io.github.teslablecontrol.protocol.Clock
import io.github.teslablecontrol.protocol.Deadline
import io.github.teslablecontrol.protocol.Domain
import io.github.teslablecontrol.protocol.ProtocolConfig
import io.github.teslablecontrol.protocol.ProtocolLog
import io.github.teslablecontrol.protocol.commands.CommandCatalog
import io.github.teslablecontrol.protocol.commands.SessionRequirement
import io.github.teslablecontrol.protocol.crypto.EcdhKey
import io.github.teslablecontrol.protocol.toHex
import io.github.teslablecontrol.protocol.transport.Transport
import io.github.teslablecontrol.protocol.vehicle.CommandResult
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.protocol.vehicle.VehicleClient
import io.github.teslablecontrol.protocol.vehicle.VehicleRequest
import io.github.teslablecontrol.protocol.vehicle.VehicleRequests
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class ControllerConfig(
    val handshakeTimeout: Duration = 15.seconds,
    val commandTimeout: Duration = 10.seconds,
    val wakeTimeout: Duration = 30.seconds,
    val wakePollInterval: Duration = 1.seconds,
    /** A fresh "awake" reading is trusted this long before checking again. */
    val awakeTrust: Duration = 30.seconds,
    /** Wake a sleeping car for infotainment commands. */
    val autoWake: () -> Boolean = { true },
    /** Re-read related state this long after a command succeeds (cars apply changes asynchronously). */
    val followUpDelay: Duration = 700.milliseconds,
    /**
     * Further reads after commands that move something (trunks, windows, locks…), which take seconds
     * to finish; without them the screen would keep showing the state from just after the command.
     */
    val movingFollowUps: List<Duration> = listOf(2.seconds, 4.seconds, 7.seconds, 12.seconds),
    val protocol: ProtocolConfig = ProtocolConfig(),
)

/**
 * Controls one vehicle over a real link. Every operation takes the same lock, so commands, state
 * reads and connection changes never interleave; VCSEC in particular can't handle concurrent requests.
 */
class RealVehicleController(
    override val vin: String,
    private val linkFactory: LinkFactory,
    private val keyProvider: () -> EcdhKey?,
    private val sessionStore: SessionStore,
    private val scope: CoroutineScope,
    private val config: ControllerConfig = ControllerConfig(),
    private val clock: Clock = Clock.SYSTEM,
    private val log: ProtocolLog = ProtocolLog.NONE,
) : VehicleController {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(ControllerState())
    override val state: StateFlow<ControllerState> = _state.asStateFlow()

    private val linkLock = Any()
    private var client: VehicleClient? = null
    private var clientJob: Job? = null

    override suspend fun execute(commandId: String, args: Map<String, String>): CommandOutcome {
        val spec = CommandCatalog.find(commandId)
            ?: return CommandOutcome.Failure(FailureKind.INVALID_INPUT, "unknown command $commandId")
        val request = try {
            spec.build(args)
        } catch (e: Exception) {
            return e.toFailure()
        }
        val outcome = withVehicle(commandId) { c ->
            prepare(c, spec.requirement, commandId)
            setActivity(commandId, Stage.SENDING)
            c.execute(request, config.commandTimeout).also { record(request, it) }
        }
        _state.update { it.copy(lastFailure = outcome as? CommandOutcome.Failure) }
        if (outcome is CommandOutcome.Success) scheduleFollowUp(commandId)
        return outcome
    }

    override suspend fun refresh(categories: Collection<StateCategory>, wake: Boolean): CommandOutcome =
        withVehicle("refresh") { c ->
            ensureSession(c, Domain.VCSEC, "refresh")
            ensureAwake(c, "refresh", wake)
            ensureSession(c, Domain.INFOTAINMENT, "refresh")
            setActivity("refresh", Stage.REFRESHING)
            for (category in categories) {
                val request = VehicleRequests.getState(category)
                record(request, c.execute(request, config.commandTimeout))
            }
            CommandResult.Done
        }

    override suspend fun refreshBodyState(): CommandOutcome = withVehicle("body-controller-state") { c ->
        CommandResult.BodyStatus(readBody(c))
    }

    override suspend fun requestPairing(role: String): CommandOutcome {
        val key = keyProvider() ?: return CommandOutcome.Failure(FailureKind.NOT_PAIRED, "no key on this phone")
        val request = try {
            CommandCatalog["add-key-request"].build(
                mapOf("PUBLIC_KEY" to key.publicKey.toHex(), "ROLE" to role, "FORM_FACTOR" to "android_device"),
            )
        } catch (e: Exception) {
            return e.toFailure()
        }
        return withVehicle("add-key-request") { c -> c.execute(request, config.commandTimeout) }
    }

    override suspend fun checkPaired(): Boolean? {
        val key = keyProvider() ?: return false
        val outcome = withVehicle("session-info") { c ->
            c.execute(VehicleRequests.sessionInfo(key.publicKey, Domain.VCSEC), config.commandTimeout)
        }
        return when {
            outcome is CommandOutcome.Success -> true.also { markPaired(true) }
            outcome is CommandOutcome.Failure && outcome.kind == FailureKind.NOT_PAIRED -> false
            else -> null
        }
    }

    override suspend fun connect(): CommandOutcome = withVehicle("connect") { c ->
        CommandResult.BodyStatus(readBody(c))
    }

    override fun disconnect() {
        val (closing, job) = synchronized(linkLock) {
            (client to clientJob).also {
                client = null
                clientJob = null
            }
        }
        closing?.disconnect()
        job?.cancel()
        _state.update { it.copy(link = LinkStatus.DISCONNECTED) }
    }

    override fun resetKey() {
        disconnect()
        sessionStore.clear(vin)
        _state.update { it.copy(keyPaired = null) }
    }

    private fun EcdhKey?.sameAs(other: EcdhKey?): Boolean = when {
        this == null || other == null -> this == null && other == null
        else -> this === other || publicKey.contentEquals(other.publicKey)
    }

    override suspend fun awaitDisconnected() {
        synchronized(linkLock) { client }?.awaitDisconnected()
    }

    /** Runs [block] with a connected client under the controller lock and maps failures. */
    private suspend fun withVehicle(label: String, block: suspend (VehicleClient) -> CommandResult): CommandOutcome =
        mutex.withLock {
            try {
                val c = ensureConnected(label)
                val result = block(c)
                persist(c)
                CommandOutcome.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                handleFailure(e)
            } finally {
                _state.update { it.copy(activity = null) }
            }
        }

    private fun handleFailure(error: Throwable): CommandOutcome.Failure {
        val failure = error.toFailure()
        log.log(ProtocolLog.Level.WARN, "Command failed (${failure.kind}): ${failure.message}")
        when (failure.kind) {
            FailureKind.NOT_PAIRED -> {
                markPaired(false)
                sessionStore.clear(vin)
            }
            FailureKind.CONNECTION_LOST -> disconnect()
            // The car may have fallen asleep; check again before the next infotainment command.
            FailureKind.TIMEOUT, FailureKind.ASLEEP -> _state.update {
                it.copy(snapshot = it.snapshot.copy(bodyUpdatedAtMillis = null))
            }
            else -> Unit
        }
        return failure
    }

    override suspend fun attach(transport: Transport): Boolean = mutex.withLock {
        val current = synchronized(linkLock) { client }
        if (current != null && current.isConnected) {
            transport.close()
            return@withLock false
        }
        install(transport)
        true
    }

    private suspend fun ensureConnected(label: String): VehicleClient {
        // A link opened with a different key (or none) can't authenticate as the current one.
        synchronized(linkLock) { client }?.let { if (it.isConnected && it.privateKey.sameAs(keyProvider())) return it }
        disconnect()
        setActivity(label, Stage.CONNECTING)
        _state.update { it.copy(link = LinkStatus.CONNECTING) }
        val transport = try {
            linkFactory.open(vin)
        } catch (e: Throwable) {
            _state.update { it.copy(link = LinkStatus.DISCONNECTED) }
            throw e
        }
        return install(transport)
    }

    private fun install(transport: Transport): VehicleClient {
        disconnect()
        val key = keyProvider()
        val connected = VehicleClient(vin, transport, key, config.protocol, clock, log)
        val job = SupervisorJob(scope.coroutineContext[Job])
        val clientScope = CoroutineScope(scope.coroutineContext + job)
        connected.connect(clientScope)
        if (key != null) {
            val cached = sessionStore.load(vin)
            if (cached.isNotEmpty()) {
                runCatching { connected.loadSessionCache(cached) }.onFailure {
                    log.log(ProtocolLog.Level.WARN, "Ignoring unusable session cache: ${it.message}")
                    sessionStore.clear(vin)
                }
            }
        }
        synchronized(linkLock) {
            client = connected
            clientJob = job
        }
        _state.update { it.copy(link = LinkStatus.CONNECTED) }
        clientScope.launch {
            connected.awaitDisconnected()
            val wasCurrent = synchronized(linkLock) {
                (client === connected).also { if (it) client = null }
            }
            if (wasCurrent) _state.update { it.copy(link = LinkStatus.DISCONNECTED) }
            job.cancel()
        }
        return connected
    }

    private suspend fun prepare(c: VehicleClient, requirement: SessionRequirement, commandId: String) {
        when (requirement) {
            SessionRequirement.NONE -> Unit
            SessionRequirement.VCSEC -> ensureSession(c, Domain.VCSEC, commandId)
            SessionRequirement.INFOTAINMENT -> {
                // Waking goes through VCSEC, so that session comes first.
                ensureSession(c, Domain.VCSEC, commandId)
                ensureAwake(c, commandId, config.autoWake())
                ensureSession(c, Domain.INFOTAINMENT, commandId)
            }
        }
    }

    private suspend fun ensureSession(c: VehicleClient, domain: Domain, label: String) {
        if (c.hasSession(domain)) return
        setActivity(label, Stage.SECURING)
        c.startSession(domain, config.handshakeTimeout)
        markPaired(true)
        persist(c)
    }

    private suspend fun ensureAwake(c: VehicleClient, label: String, wake: Boolean) {
        val snapshot = _state.value.snapshot
        val checkedAt = snapshot.bodyUpdatedAtMillis
        if (snapshot.isAsleep == false && checkedAt != null && clock.wallMillis() - checkedAt < config.awakeTrust.inWholeMilliseconds) {
            return
        }
        if (readBody(c).vehicleSleepStatus != Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_ASLEEP) return
        if (!wake) throw AsleepException("the car is asleep")
        setActivity(label, Stage.WAKING)
        c.execute(VehicleRequests.wakeUp(), config.commandTimeout)
        val deadline = Deadline.after(config.wakeTimeout, clock)
        while (true) {
            delay(config.wakePollInterval)
            if (readBody(c).vehicleSleepStatus != Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_ASLEEP) return
            if (deadline.isExpired) throw AsleepException("the car didn't wake up in time")
        }
    }

    private suspend fun readBody(c: VehicleClient): Vcsec.VehicleStatus {
        val result = c.execute(VehicleRequest.BodyControllerState, config.commandTimeout) as CommandResult.BodyStatus
        val status = result.status
        val before = _state.value.snapshot.body
        if (before == null || before.vehicleLockState != status.vehicleLockState || before.vehicleSleepStatus != status.vehicleSleepStatus) {
            // What the car itself says, for checking the status line against reality.
            log.log(ProtocolLog.Level.INFO, "Car reports ${status.vehicleLockState.name.lowercase()}, ${status.vehicleSleepStatus.name.lowercase()}")
        }
        _state.update { it.copy(snapshot = it.snapshot.withBody(status, clock.wallMillis())) }
        return status
    }

    private fun record(request: VehicleRequest, result: CommandResult) {
        when {
            result is CommandResult.BodyStatus ->
                _state.update { it.copy(snapshot = it.snapshot.withBody(result.status, clock.wallMillis())) }
            result is CommandResult.State && request is VehicleRequest.State ->
                _state.update { it.copy(snapshot = it.snapshot.withCategory(request.category, result.data, clock.wallMillis())) }
            else -> Unit
        }
    }

    private fun persist(c: VehicleClient) {
        if (c.privateKey == null) return
        runCatching { sessionStore.save(vin, c.exportSessionCache()) }
            .onFailure { log.log(ProtocolLog.Level.WARN, "Could not save session cache: ${it.message}") }
    }

    private fun markPaired(paired: Boolean) {
        _state.update { it.copy(keyPaired = paired) }
    }

    private fun setActivity(commandId: String, stage: Stage) {
        _state.update { it.copy(activity = Activity(commandId, stage)) }
    }

    private fun scheduleFollowUp(commandId: String) {
        val followUp = FollowUp.forCommand(commandId) ?: return
        scope.launch {
            var waited = Duration.ZERO
            val times = listOf(config.followUpDelay) + if (followUp.moving) config.movingFollowUps else emptyList()
            for (at in times) {
                delay((at - waited).coerceAtLeast(Duration.ZERO))
                waited = at
                if (followUp.body) refreshBodyState()
                if (followUp.categories.isNotEmpty()) refresh(followUp.categories, wake = false)
            }
        }
    }
}

/** Which state to re-read after a command succeeds, so the UI shows the car's actual state. */
internal data class FollowUp(val body: Boolean, val categories: List<StateCategory>, val moving: Boolean = false) {
    companion object {
        fun forCommand(id: String): FollowUp? = when {
            id in setOf("lock", "unlock", "drive", "autosecure-modelx") || id.startsWith("trunk-") ||
                id.startsWith("frunk-") || id.startsWith("tonneau-") -> FollowUp(true, emptyList(), moving = true)
            id == "wake" -> FollowUp(true, emptyList())
            id.startsWith("climate-") || id.startsWith("seat-") || id == "steering-wheel-heater" ||
                id == "auto-seat-and-climate" || id == "preconditioning-max" || id == "bioweapon-mode" ||
                id.startsWith("cabin-overheat") -> FollowUp(false, listOf(StateCategory.CLIMATE))
            id.startsWith("charging-schedule") -> FollowUp(false, listOf(StateCategory.CHARGE, StateCategory.CHARGE_SCHEDULE))
            id.startsWith("precondition-schedule") -> FollowUp(false, listOf(StateCategory.PRECONDITIONING_SCHEDULE))
            id.startsWith("charge-port") -> FollowUp(true, listOf(StateCategory.CHARGE), moving = true)
            id.startsWith("charging-") || id.startsWith("charge-") || id.startsWith("scheduled-departure") ||
                id == "low-power-mode" || id == "keep-accessory-power" -> FollowUp(false, listOf(StateCategory.CHARGE))
            id.startsWith("windows-") || id == "sunroof" -> FollowUp(false, listOf(StateCategory.CLOSURES), moving = true)
            id == "sentry-mode" || id.startsWith("valet-") || id.startsWith("speed-limit") ->
                FollowUp(false, listOf(StateCategory.CLOSURES))
            id.startsWith("media-") -> FollowUp(false, listOf(StateCategory.MEDIA))
            id.startsWith("software-update") -> FollowUp(false, listOf(StateCategory.SOFTWARE_UPDATE))
            id.startsWith("parental-controls") -> FollowUp(false, listOf(StateCategory.PARENTAL_CONTROLS))
            else -> null
        }
    }
}
