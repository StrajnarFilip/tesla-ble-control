package io.github.teslablecontrol.core

import com.tesla.generated.carserver.common.Common
import com.tesla.generated.carserver.vehicle.Vehicle
import com.tesla.generated.keys.Keys
import com.tesla.generated.vcsec.Vcsec
import io.github.teslablecontrol.protocol.commands.Args
import io.github.teslablecontrol.protocol.commands.CommandCatalog
import io.github.teslablecontrol.protocol.transport.Transport
import io.github.teslablecontrol.protocol.vehicle.CommandResult
import io.github.teslablecontrol.protocol.vehicle.KeyEntry
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A pretend car for trying the app without a vehicle (and for UI tests). It behaves like the real
 * controller — connecting, securing, waking — with short delays, and keeps a plausible state.
 */
class DemoVehicleController(
    override val vin: String = "5YJ3DEMO000000001",
    private val stepDelay: Duration = 350.milliseconds,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : VehicleController {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(ControllerState(keyPaired = true))
    override val state: StateFlow<ControllerState> = _state.asStateFlow()

    private var connected = false
    private var secured = false
    private var asleep = true

    private var locked = true
    private var frunkOpen = false
    private var trunkOpen = false
    private var portOpen = false
    private var windowsOpen = false
    private var climateOn = false
    private var driverTemp = 21f
    private var passengerTemp = 21f
    private var limit = 80
    private var amps = 16
    private var charging = false
    private var battery = 72
    private var sentry = false
    private var valet = false
    private val seatHeaters = mutableMapOf<String, Int>()
    private var steeringHeater = false
    private var volume = 4f
    private var playing = true

    override suspend fun execute(commandId: String, args: Map<String, String>): CommandOutcome = mutex.withLock {
        val spec = CommandCatalog.find(commandId)
            ?: return@withLock CommandOutcome.Failure(FailureKind.INVALID_INPUT, "unknown command $commandId")
        try {
            spec.build(args)
        } catch (e: Exception) {
            return@withLock e.toFailure()
        }
        step(commandId)
        if (spec.requirement != io.github.teslablecontrol.protocol.commands.SessionRequirement.NONE && !secured) {
            setStage(commandId, Stage.SECURING)
            delay(stepDelay)
            secured = true
        }
        if (spec.requirement == io.github.teslablecontrol.protocol.commands.SessionRequirement.INFOTAINMENT && asleep) {
            setStage(commandId, Stage.WAKING)
            delay(stepDelay * 3)
            asleep = false
        }
        setStage(commandId, Stage.SENDING)
        delay(stepDelay)
        val outcome = apply(commandId, Args(args))
        publish()
        _state.update { it.copy(activity = null, lastFailure = outcome as? CommandOutcome.Failure) }
        outcome
    }

    private suspend fun step(label: String) {
        if (!connected) {
            setStage(label, Stage.CONNECTING)
            _state.update { it.copy(link = LinkStatus.CONNECTING) }
            delay(stepDelay)
            connected = true
            _state.update { it.copy(link = LinkStatus.CONNECTED) }
        }
    }

    private fun setStage(label: String, stage: Stage) = _state.update { it.copy(activity = Activity(label, stage)) }

    private fun apply(id: String, args: Args): CommandOutcome {
        when (id) {
            "lock", "autosecure-modelx" -> locked = true
            "unlock" -> locked = false
            "wake" -> asleep = false
            "frunk-open" -> frunkOpen = true
            "trunk-open" -> trunkOpen = true
            "trunk-close" -> trunkOpen = false
            "trunk-move" -> trunkOpen = !trunkOpen
            "charge-port-open" -> portOpen = true
            "charge-port-close" -> if (charging) {
                return CommandOutcome.Failure(FailureKind.REFUSED, "charge port is in use")
            } else {
                portOpen = false
            }
            "windows-vent" -> windowsOpen = true
            "windows-close" -> windowsOpen = false
            "climate-on" -> climateOn = true
            "climate-off" -> climateOn = false
            "climate-set-temp" -> {
                driverTemp = args.temperatureCelsius("TEMP")
                passengerTemp = if (args.has("PASSENGER_TEMP")) args.temperatureCelsius("PASSENGER_TEMP") else driverTemp
            }
            "charging-set-limit" -> limit = args.int("PERCENT")
            "charging-set-amps" -> amps = args.int("AMPS")
            "charging-start", "charge-max-range", "charge-standard-range" -> if (!portOpen) {
                return CommandOutcome.Failure(FailureKind.REFUSED, "cable not connected")
            } else {
                charging = true
            }
            "charging-stop" -> charging = false
            "sentry-mode" -> sentry = args.onOff("STATE")
            "valet-mode-on" -> valet = true
            "valet-mode-off" -> valet = false
            "seat-heater" -> seatHeaters[args.string("SEAT")] = CommandCatalog.LEVELS.keys.indexOf(args.string("LEVEL").lowercase())
            "steering-wheel-heater" -> steeringHeater = args.onOff("STATE")
            "media-set-volume" -> volume = args.float("VOLUME")
            "media-volume-up" -> volume = (volume + 0.5f).coerceAtMost(10f)
            "media-volume-down" -> volume = (volume - 0.5f).coerceAtLeast(0f)
            "media-toggle-playback" -> playing = !playing
            "list-keys" -> return CommandOutcome.Success(CommandResult.KeyList(demoKeys()))
            "body-controller-state" -> return CommandOutcome.Success(CommandResult.BodyStatus(bodyStatus()))
            "add-key-request" -> return CommandOutcome.Success(CommandResult.Sent)
            "state" -> {
                val category = args.choice("CATEGORY", CommandCatalog.STATE_CATEGORIES)
                return CommandOutcome.Success(CommandResult.State(vehicleData(category)))
            }
        }
        return CommandOutcome.Success(CommandResult.Done)
    }

    override suspend fun refresh(categories: Collection<StateCategory>, wake: Boolean): CommandOutcome = mutex.withLock {
        step("refresh")
        if (asleep && !wake) {
            publishBody()
            return@withLock CommandOutcome.Failure(FailureKind.ASLEEP, "the car is asleep")
        }
        if (asleep) {
            setStage("refresh", Stage.WAKING)
            delay(stepDelay * 3)
            asleep = false
        }
        setStage("refresh", Stage.REFRESHING)
        delay(stepDelay)
        publish(categories)
        _state.update { it.copy(activity = null) }
        CommandOutcome.Success(CommandResult.Done)
    }

    override suspend fun refreshBodyState(): CommandOutcome = mutex.withLock {
        step("body-controller-state")
        publishBody()
        _state.update { it.copy(activity = null) }
        CommandOutcome.Success(CommandResult.BodyStatus(bodyStatus()))
    }

    override suspend fun requestPairing(role: String): CommandOutcome = CommandOutcome.Success(CommandResult.Sent)

    override suspend fun checkPaired(): Boolean = true

    override suspend fun connect(): CommandOutcome = refreshBodyState()

    override suspend fun attach(transport: Transport): Boolean {
        transport.close()
        return false
    }

    override fun disconnect() {
        connected = false
        secured = false
        _state.update { it.copy(link = LinkStatus.DISCONNECTED) }
    }

    override fun resetKey() = Unit

    override suspend fun awaitDisconnected() = Unit

    private fun publishBody() {
        _state.update { it.copy(snapshot = it.snapshot.withBody(bodyStatus(), nowMillis())) }
    }

    private fun publish(categories: Collection<StateCategory> = StateCategory.entries) {
        publishBody()
        if (asleep) return
        val now = nowMillis()
        _state.update { current ->
            categories.fold(current.snapshot) { snapshot, category -> snapshot.withCategory(category, vehicleData(category), now) }
                .let { current.copy(snapshot = it) }
        }
    }

    private fun closure(open: Boolean) = if (open) Vcsec.ClosureState_E.CLOSURESTATE_OPEN else Vcsec.ClosureState_E.CLOSURESTATE_CLOSED

    private fun bodyStatus(): Vcsec.VehicleStatus = Vcsec.VehicleStatus.newBuilder()
        .setVehicleLockState(if (locked) Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED else Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_UNLOCKED)
        .setVehicleSleepStatus(
            if (asleep) Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_ASLEEP else Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_AWAKE,
        )
        .setUserPresence(Vcsec.UserPresence_E.VEHICLE_USER_PRESENCE_NOT_PRESENT)
        .setClosureStatuses(
            Vcsec.ClosureStatuses.newBuilder()
                .setFrontTrunk(closure(frunkOpen))
                .setRearTrunk(closure(trunkOpen))
                .setChargePort(closure(portOpen)),
        )
        .build()

    private val void: Common.Void = Common.Void.getDefaultInstance()

    private fun vehicleData(category: StateCategory): Vehicle.VehicleData {
        val data = Vehicle.VehicleData.newBuilder()
        // Built outside apply{}: the builders' oneof members ("charging") would shadow our fields.
        val chargingState = Vehicle.ChargeState.ChargingState.newBuilder()
        when {
            charging -> chargingState.setCharging(void)
            portOpen -> chargingState.setStopped(void)
            else -> chargingState.setDisconnected(void)
        }
        val sentryState = Vehicle.ClosuresState.SentryModeState.newBuilder()
        if (sentry) sentryState.setArmed(void) else sentryState.setOff(void)
        when (category) {
            StateCategory.CHARGE -> data.setChargeState(
                Vehicle.ChargeState.newBuilder()
                    .setChargingState(chargingState)
                    .setBatteryLevel(battery)
                    .setBatteryRange(battery * 3.3f)
                    .setEstBatteryRange(battery * 3.0f)
                    .setChargeLimitSoc(limit)
                    .setChargeLimitSocMin(50)
                    .setChargeLimitSocMax(100)
                    .setChargingAmps(amps)
                    .setChargeCurrentRequestMax(32)
                    .setChargePortDoorOpen(portOpen)
                    .setChargerPower(if (charging) 11 else 0)
                    .setChargerVoltage(if (charging) 230 else 0)
                    .setChargerActualCurrent(if (charging) amps else 0)
                    .setMinutesToFullCharge(if (charging) 85 else 0),
            )
            StateCategory.CLIMATE -> data.setClimateState(
                Vehicle.ClimateState.newBuilder()
                    .setInsideTempCelsius(if (climateOn) driverTemp - 0.5f else 14.5f)
                    .setOutsideTempCelsius(9f)
                    .setDriverTempSetting(driverTemp)
                    .setPassengerTempSetting(passengerTemp)
                    .setIsClimateOn(climateOn)
                    .setMinAvailTempCelsius(15f)
                    .setMaxAvailTempCelsius(28f)
                    .setSeatHeaterLeft(seatHeaters["front-left"] ?: 0)
                    .setSeatHeaterRight(seatHeaters["front-right"] ?: 0)
                    .setSeatHeaterRearLeft(seatHeaters["2nd-row-left"] ?: 0)
                    .setSeatHeaterRearCenter(seatHeaters["2nd-row-center"] ?: 0)
                    .setSeatHeaterRearRight(seatHeaters["2nd-row-right"] ?: 0)
                    .setSteeringWheelHeater(steeringHeater),
            )
            StateCategory.CLOSURES -> data.setClosuresState(
                Vehicle.ClosuresState.newBuilder()
                    .setLocked(locked)
                    .setDoorOpenDriverFront(false)
                    .setDoorOpenPassengerFront(false)
                    .setDoorOpenDriverRear(false)
                    .setDoorOpenPassengerRear(false)
                    .setDoorOpenTrunkFront(frunkOpen)
                    .setDoorOpenTrunkRear(trunkOpen)
                    .setWindowOpenDriverFront(windowsOpen)
                    .setWindowOpenPassengerFront(windowsOpen)
                    .setWindowOpenDriverRear(windowsOpen)
                    .setWindowOpenPassengerRear(windowsOpen)
                    .setValetMode(valet)
                    .setSentryModeAvailable(true)
                    .setSentryModeState(sentryState),
            )
            StateCategory.DRIVE -> data.setDriveState(
                Vehicle.DriveState.newBuilder()
                    .setShiftState(Vehicle.ShiftState.newBuilder().setP(void))
                    .setOdometerInHundredthsOfAMile(1_523_400),
            )
            StateCategory.LOCATION -> data.setLocationState(
                Vehicle.LocationState.newBuilder().setLatitude(46.0569f).setLongitude(14.5058f).setHeading(270),
            )
            StateCategory.TIRE_PRESSURE -> data.setTirePressureState(
                Vehicle.TirePressureState.newBuilder()
                    .setTpmsPressureFl(2.9f).setTpmsPressureFr(2.9f).setTpmsPressureRl(2.85f).setTpmsPressureRr(2.8f),
            )
            StateCategory.MEDIA -> data.setMediaState(
                Vehicle.MediaState.newBuilder()
                    .setNowPlayingTitle("Demo Track")
                    .setNowPlayingArtist("Demo Artist")
                    .setAudioVolume(volume)
                    .setAudioVolumeMax(10.333f)
                    .setMediaPlaybackStatus(
                        if (playing) Common.MediaPlaybackStatus.Playing else Common.MediaPlaybackStatus.Paused,
                    ),
            )
            StateCategory.MEDIA_DETAIL -> data.setMediaDetailState(Vehicle.MediaDetailState.getDefaultInstance())
            StateCategory.SOFTWARE_UPDATE -> data.setSoftwareUpdateState(
                Vehicle.SoftwareUpdateState.newBuilder()
                    .setStatus(Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.newBuilder().setAvailable(void))
                    .setVersion("2026.38.4"),
            )
            StateCategory.PARENTAL_CONTROLS -> data.setParentalControlsState(
                Vehicle.ParentalControlsState.newBuilder().setParentalControlsActive(false),
            )
            StateCategory.CHARGE_SCHEDULE -> data.setChargeScheduleState(Vehicle.ChargeScheduleState.getDefaultInstance())
            StateCategory.PRECONDITIONING_SCHEDULE ->
                data.setPreconditioningScheduleState(Vehicle.PreconditioningScheduleState.getDefaultInstance())
        }
        return data.build()
    }

    private fun demoKeys(): List<KeyEntry> = listOf(
        KeyEntry(0, ByteArray(65) { if (it == 0) 4 else 1 }, Keys.Role.ROLE_OWNER, Vcsec.KeyFormFactor.KEY_FORM_FACTOR_NFC_CARD),
        KeyEntry(1, ByteArray(65) { if (it == 0) 4 else 2 }, Keys.Role.ROLE_OWNER, Vcsec.KeyFormFactor.KEY_FORM_FACTOR_ANDROID_DEVICE),
    )
}
