package io.github.teslablecontrol.protocol.commands

import com.tesla.generated.carserver.common.Common
import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.keys.Keys
import com.tesla.generated.vcsec.Vcsec
import io.github.teslablecontrol.protocol.Domain
import io.github.teslablecontrol.protocol.VehicleErrors
import io.github.teslablecontrol.protocol.vehicle.ChargingPolicy
import io.github.teslablecontrol.protocol.vehicle.Level
import io.github.teslablecontrol.protocol.vehicle.SeatPosition
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.protocol.vehicle.VehicleRequest
import io.github.teslablecontrol.protocol.vehicle.VehicleRequests

/** What must be in place before a command can be sent. */
enum class SessionRequirement {
    /** Unauthenticated VCSEC request; works without a paired key and while the car sleeps. */
    NONE,

    /** Authenticated VCSEC command; works while infotainment sleeps. */
    VCSEC,

    /** Infotainment command; the car must be awake. */
    INFOTAINMENT,
}

/** One vehicle command: its stable ID, parameters and how to build it. */
class CommandSpec internal constructor(
    val id: String,
    val requirement: SessionRequirement,
    val params: List<ParamSpec>,
    private val builder: (Args) -> VehicleRequest,
) {
    fun build(values: Map<String, String>, nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 }): VehicleRequest =
        builder(Args(values, nowEpochSeconds))
}

/**
 * Every command the official library can send over BLE. IDs match `tesla-control` where it has the
 * command; the rest name library methods the CLI doesn't expose.
 */
object CommandCatalog {
    val ON_OFF = ParamSpec("STATE", ParamType.OnOff)

    val ROLES: Map<String, Keys.Role> = mapOf(
        "owner" to Keys.Role.ROLE_OWNER,
        "driver" to Keys.Role.ROLE_DRIVER,
        "fm" to Keys.Role.ROLE_FM,
        "vehicle_monitor" to Keys.Role.ROLE_VEHICLE_MONITOR,
        "charging_manager" to Keys.Role.ROLE_CHARGING_MANAGER,
    )

    val FORM_FACTORS: Map<String, Vcsec.KeyFormFactor> = mapOf(
        "nfc_card" to Vcsec.KeyFormFactor.KEY_FORM_FACTOR_NFC_CARD,
        "ios_device" to Vcsec.KeyFormFactor.KEY_FORM_FACTOR_IOS_DEVICE,
        "android_device" to Vcsec.KeyFormFactor.KEY_FORM_FACTOR_ANDROID_DEVICE,
        "cloud_key" to Vcsec.KeyFormFactor.KEY_FORM_FACTOR_CLOUD_KEY,
    )

    val HEATER_SEATS: Map<String, SeatPosition> = linkedMapOf(
        "front-left" to SeatPosition.FRONT_LEFT,
        "front-right" to SeatPosition.FRONT_RIGHT,
        "2nd-row-left" to SeatPosition.SECOND_ROW_LEFT,
        "2nd-row-left-back" to SeatPosition.SECOND_ROW_LEFT_BACK,
        "2nd-row-center" to SeatPosition.SECOND_ROW_CENTER,
        "2nd-row-right" to SeatPosition.SECOND_ROW_RIGHT,
        "2nd-row-right-back" to SeatPosition.SECOND_ROW_RIGHT_BACK,
        "3rd-row-left" to SeatPosition.THIRD_ROW_LEFT,
        "3rd-row-right" to SeatPosition.THIRD_ROW_RIGHT,
    )

    val COOLER_SEATS: Map<String, SeatPosition> = linkedMapOf(
        "front-left" to SeatPosition.FRONT_LEFT,
        "front-right" to SeatPosition.FRONT_RIGHT,
    )

    val LEVELS: Map<String, Level> = linkedMapOf(
        "off" to Level.OFF, "low" to Level.LOW, "medium" to Level.MEDIUM, "high" to Level.HIGH,
    )

    val COP_LEVELS: Map<String, Level> = linkedMapOf("low" to Level.LOW, "medium" to Level.MEDIUM, "high" to Level.HIGH)

    val CLIMATE_KEEPER_MODES: Map<String, CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E> = linkedMapOf(
        "off" to CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E.ClimateKeeperAction_Off,
        "on" to CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E.ClimateKeeperAction_On,
        "dog" to CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E.ClimateKeeperAction_Dog,
        "camp" to CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E.ClimateKeeperAction_Camp,
    )

    val POLICIES: Map<String, ChargingPolicy> = linkedMapOf(
        "off" to ChargingPolicy.OFF, "all" to ChargingPolicy.ALL_DAYS, "weekdays" to ChargingPolicy.WEEKDAYS,
    )

    val PARENTAL_SETTINGS: Map<String, CarServer.ParentalControlsEnableSettingsAction.ParentalControlsSetting_E> = linkedMapOf(
        "speed-limit" to CarServer.ParentalControlsEnableSettingsAction.ParentalControlsSetting_E.SpeedLimit,
        "acceleration" to CarServer.ParentalControlsEnableSettingsAction.ParentalControlsSetting_E.Acceleration,
        "safety-features" to CarServer.ParentalControlsEnableSettingsAction.ParentalControlsSetting_E.SafetyFeatures,
        "curfew" to CarServer.ParentalControlsEnableSettingsAction.ParentalControlsSetting_E.Curfew,
        "browser-blocked" to CarServer.ParentalControlsEnableSettingsAction.ParentalControlsSetting_E.BrowserBlocked,
        "theater-blocked" to CarServer.ParentalControlsEnableSettingsAction.ParentalControlsSetting_E.TheaterBlocked,
        "arcade-blocked" to CarServer.ParentalControlsEnableSettingsAction.ParentalControlsSetting_E.ArcadeBlocked,
    )

    val DOMAINS: Map<String, Domain> = linkedMapOf("vcsec" to Domain.VCSEC, "infotainment" to Domain.INFOTAINMENT)

    val STATE_CATEGORIES: Map<String, StateCategory> = StateCategory.entries.associateBy { it.id }

    val SCHEDULE_TYPES = listOf("home", "work", "other", "id")

    private fun choice(name: String, options: Map<String, *>, required: Boolean = true, default: String? = null) =
        ParamSpec(name, ParamType.Choice(options.keys.toList()), required, default)

    private val specs = mutableListOf<CommandSpec>()

    private fun command(
        id: String,
        requirement: SessionRequirement,
        vararg params: ParamSpec,
        builder: (Args) -> VehicleRequest,
    ) {
        specs += CommandSpec(id, requirement, params.toList(), builder)
    }

    private fun vcsec(id: String, vararg params: ParamSpec, builder: (Args) -> VehicleRequest) =
        command(id, SessionRequirement.VCSEC, *params, builder = builder)

    private fun infotainment(id: String, vararg params: ParamSpec, builder: (Args) -> VehicleRequest) =
        command(id, SessionRequirement.INFOTAINMENT, *params, builder = builder)

    init {
        // --- Access (VCSEC) ---
        vcsec("lock") { VehicleRequests.lock() }
        vcsec("unlock") { VehicleRequests.unlock() }
        vcsec("wake") { VehicleRequests.wakeUp() }
        vcsec("drive") { VehicleRequests.remoteDrive() }
        vcsec("autosecure-modelx") { VehicleRequests.autoSecureVehicle() }
        vcsec("trunk-open") { VehicleRequests.openTrunk() }
        vcsec("trunk-close") { VehicleRequests.closeTrunk() }
        vcsec("trunk-move") { VehicleRequests.actuateTrunk() }
        vcsec("frunk-open") { VehicleRequests.openFrunk() }
        vcsec("tonneau-open") { VehicleRequests.openTonneau() }
        vcsec("tonneau-close") { VehicleRequests.closeTonneau() }
        vcsec("tonneau-stop") { VehicleRequests.stopTonneau() }
        command("body-controller-state", SessionRequirement.NONE) { VehicleRequests.bodyControllerState() }
        command("list-keys", SessionRequirement.NONE) { VehicleRequests.listKeys() }
        vcsec(
            "add-key",
            ParamSpec("PUBLIC_KEY", ParamType.PublicKey),
            choice("ROLE", ROLES, default = "driver"),
            choice("FORM_FACTOR", FORM_FACTORS, default = "android_device"),
        ) { VehicleRequests.addKeyWithRole(it.publicKey("PUBLIC_KEY"), it.choice("ROLE", ROLES), it.choice("FORM_FACTOR", FORM_FACTORS)) }
        command(
            "add-key-request", SessionRequirement.NONE,
            ParamSpec("PUBLIC_KEY", ParamType.PublicKey),
            choice("ROLE", ROLES, default = "owner"),
            choice("FORM_FACTOR", FORM_FACTORS, default = "android_device"),
        ) {
            VehicleRequests.sendAddKeyRequestWithRole(
                it.publicKey("PUBLIC_KEY"), it.choice("ROLE", ROLES), it.choice("FORM_FACTOR", FORM_FACTORS),
            )
        }
        vcsec("remove-key", ParamSpec("PUBLIC_KEY", ParamType.PublicKey)) { VehicleRequests.removeKey(it.publicKey("PUBLIC_KEY")) }
        command(
            "session-info", SessionRequirement.NONE,
            ParamSpec("PUBLIC_KEY", ParamType.PublicKey),
            choice("DOMAIN", DOMAINS, default = "vcsec"),
        ) { VehicleRequests.sessionInfo(it.publicKey("PUBLIC_KEY"), it.choice("DOMAIN", DOMAINS)) }

        // --- Climate ---
        infotainment("climate-on") { VehicleRequests.climateOn() }
        infotainment("climate-off") { VehicleRequests.climateOff() }
        infotainment(
            "climate-set-temp",
            ParamSpec("TEMP", ParamType.Temperature),
            ParamSpec("PASSENGER_TEMP", ParamType.Temperature, required = false),
        ) {
            val driver = it.temperatureCelsius("TEMP")
            val passenger = if (it.has("PASSENGER_TEMP")) it.temperatureCelsius("PASSENGER_TEMP") else driver
            VehicleRequests.changeClimateTemp(driver, passenger)
        }
        infotainment("seat-heater", choice("SEAT", HEATER_SEATS), choice("LEVEL", LEVELS)) {
            VehicleRequests.setSeatHeater(mapOf(it.choice("SEAT", HEATER_SEATS) to it.choice("LEVEL", LEVELS)))
        }
        infotainment("seat-cooler", choice("SEAT", COOLER_SEATS), choice("LEVEL", LEVELS)) {
            VehicleRequests.setSeatCooler(it.choice("LEVEL", LEVELS), it.choice("SEAT", COOLER_SEATS))
        }
        infotainment("steering-wheel-heater", ON_OFF) { VehicleRequests.setSteeringWheelHeater(it.onOff("STATE")) }
        infotainment(
            "auto-seat-and-climate",
            ParamSpec("POSITIONS", ParamType.Choice(listOf("L", "R", "LR"))),
            ParamSpec("STATE", ParamType.OnOff, required = false, default = "on"),
        ) {
            val text = it.string("POSITIONS").uppercase()
            val positions = buildList {
                if ('L' in text) add(SeatPosition.FRONT_LEFT)
                if ('R' in text) add(SeatPosition.FRONT_RIGHT)
            }
            if (positions.size != text.length) throw VehicleErrors.invalidArgument("POSITIONS must be L, R or LR")
            VehicleRequests.autoSeatAndClimate(positions, it.onOff("STATE", default = true))
        }
        infotainment("preconditioning-max", ON_OFF, ParamSpec("OVERRIDE", ParamType.OnOff, required = false, default = "off")) {
            VehicleRequests.setPreconditioningMax(it.onOff("STATE"), it.onOff("OVERRIDE", default = false))
        }
        infotainment("bioweapon-mode", ON_OFF, ParamSpec("OVERRIDE", ParamType.OnOff, required = false, default = "off")) {
            VehicleRequests.setBioweaponDefenseMode(it.onOff("STATE"), it.onOff("OVERRIDE", default = false))
        }
        infotainment("cabin-overheat-protection", ON_OFF, ParamSpec("FAN_ONLY", ParamType.OnOff, required = false, default = "off")) {
            VehicleRequests.setCabinOverheatProtection(it.onOff("STATE"), it.onOff("FAN_ONLY", default = false))
        }
        infotainment("cabin-overheat-protection-temp", choice("LEVEL", COP_LEVELS)) {
            VehicleRequests.setCabinOverheatProtectionTemperature(it.choice("LEVEL", COP_LEVELS))
        }
        infotainment(
            "climate-keeper",
            choice("MODE", CLIMATE_KEEPER_MODES),
            ParamSpec("OVERRIDE", ParamType.OnOff, required = false, default = "off"),
        ) { VehicleRequests.setClimateKeeperMode(it.choice("MODE", CLIMATE_KEEPER_MODES), it.onOff("OVERRIDE", default = false)) }

        // --- Charging ---
        infotainment("charging-start") { VehicleRequests.chargeStart() }
        infotainment("charging-stop") { VehicleRequests.chargeStop() }
        infotainment("charge-max-range") { VehicleRequests.chargeMaxRange() }
        infotainment("charge-standard-range") { VehicleRequests.chargeStandardRange() }
        infotainment("charging-set-limit", ParamSpec("PERCENT", ParamType.Integer(50, 100))) {
            VehicleRequests.changeChargeLimit(it.int("PERCENT", 0..100))
        }
        infotainment("charging-set-amps", ParamSpec("AMPS", ParamType.Integer(1, 80))) {
            VehicleRequests.setChargingAmps(it.int("AMPS", 0..1000))
        }
        infotainment("charge-port-open") { VehicleRequests.openChargePort() }
        infotainment("charge-port-close") { VehicleRequests.closeChargePort() }
        infotainment("charging-schedule", ParamSpec("TIME", ParamType.TimeOfDay)) {
            VehicleRequests.scheduleCharging(true, it.minutesAfterMidnight("TIME"))
        }
        infotainment("charging-schedule-cancel") { VehicleRequests.scheduleCharging(false, 0) }
        infotainment(
            "scheduled-departure",
            ParamSpec("DEPART_AT", ParamType.TimeOfDay),
            ParamSpec("OFF_PEAK_END", ParamType.TimeOfDay, required = false, default = "06:00"),
            choice("PRECONDITIONING", POLICIES, required = false, default = "all"),
            choice("OFF_PEAK", POLICIES, required = false, default = "off"),
        ) {
            VehicleRequests.scheduleDeparture(
                departAtMinutes = it.minutesAfterMidnight("DEPART_AT"),
                offPeakEndMinutes = if (it.has("OFF_PEAK_END")) it.minutesAfterMidnight("OFF_PEAK_END") else 6 * 60,
                preconditioning = if (it.has("PRECONDITIONING")) it.choice("PRECONDITIONING", POLICIES) else ChargingPolicy.ALL_DAYS,
                offPeak = if (it.has("OFF_PEAK")) it.choice("OFF_PEAK", POLICIES) else ChargingPolicy.OFF,
            )
        }
        infotainment("scheduled-departure-clear") { VehicleRequests.clearScheduledDeparture() }
        infotainment(
            "charging-schedule-add",
            ParamSpec("DAYS", ParamType.Days),
            ParamSpec("TIME", ParamType.TimeRange),
            ParamSpec("LATITUDE", ParamType.Latitude),
            ParamSpec("LONGITUDE", ParamType.Longitude),
            ParamSpec("REPEAT", ParamType.Choice(listOf("weekly", "once")), required = false, default = "weekly"),
            ParamSpec("ID", ParamType.Identifier, required = false),
            ParamSpec("ENABLED", ParamType.OnOff, required = false, default = "on"),
        ) {
            val (start, end) = it.timeRange("TIME")
            val schedule = Common.ChargeSchedule.newBuilder()
                .setId(if (it.has("ID")) it.long("ID") else it.nowEpochSeconds())
                .setEnabled(it.onOff("ENABLED", default = true))
                .setDaysOfWeek(it.days("DAYS"))
                .setLatitude(it.degrees("LATITUDE"))
                .setLongitude(it.degrees("LONGITUDE"))
                .setOneTime(it.raw("REPEAT")?.lowercase() == "once")
            if (start != null) schedule.setStartTime(start).setStartEnabled(true)
            if (end != null) schedule.setEndTime(end).setEndEnabled(true)
            VehicleRequests.addChargeSchedule(schedule.build())
        }
        infotainment(
            "charging-schedule-remove",
            ParamSpec("TYPE", ParamType.Choice(SCHEDULE_TYPES)),
            ParamSpec("ID", ParamType.Identifier, required = false),
        ) { removeSchedule(it, VehicleRequests::removeChargeSchedule, VehicleRequests::batchRemoveChargeSchedules) }
        infotainment(
            "precondition-schedule-add",
            ParamSpec("DAYS", ParamType.Days),
            ParamSpec("TIME", ParamType.TimeOfDay),
            ParamSpec("LATITUDE", ParamType.Latitude),
            ParamSpec("LONGITUDE", ParamType.Longitude),
            ParamSpec("REPEAT", ParamType.Choice(listOf("weekly", "once")), required = false, default = "weekly"),
            ParamSpec("ID", ParamType.Identifier, required = false),
            ParamSpec("ENABLED", ParamType.OnOff, required = false, default = "on"),
        ) {
            val schedule = Common.PreconditionSchedule.newBuilder()
                .setId(if (it.has("ID")) it.long("ID") else it.nowEpochSeconds())
                .setEnabled(it.onOff("ENABLED", default = true))
                .setDaysOfWeek(it.days("DAYS"))
                .setPreconditionTime(it.minutesAfterMidnight("TIME"))
                .setLatitude(it.degrees("LATITUDE"))
                .setLongitude(it.degrees("LONGITUDE"))
                .setOneTime(it.raw("REPEAT")?.lowercase() == "once")
            VehicleRequests.addPreconditionSchedule(schedule.build())
        }
        infotainment(
            "precondition-schedule-remove",
            ParamSpec("TYPE", ParamType.Choice(SCHEDULE_TYPES)),
            ParamSpec("ID", ParamType.Identifier, required = false),
        ) { removeSchedule(it, VehicleRequests::removePreconditionSchedule, VehicleRequests::batchRemovePreconditionSchedules) }
        infotainment("low-power-mode", ON_OFF) { VehicleRequests.setLowPowerMode(it.onOff("STATE")) }
        infotainment("keep-accessory-power", ON_OFF) { VehicleRequests.setKeepAccessoryPowerMode(it.onOff("STATE")) }

        // --- Body and other ---
        infotainment("windows-vent") { VehicleRequests.ventWindows() }
        infotainment("windows-close") { VehicleRequests.closeWindows() }
        infotainment("sunroof", ParamSpec("LEVEL", ParamType.Integer(0, 100))) {
            VehicleRequests.changeSunroofState(it.int("LEVEL", 0..100))
        }
        infotainment("homelink", ParamSpec("LATITUDE", ParamType.Latitude), ParamSpec("LONGITUDE", ParamType.Longitude)) {
            VehicleRequests.triggerHomelink(it.degrees("LATITUDE"), it.degrees("LONGITUDE"))
        }
        infotainment("honk") { VehicleRequests.honkHorn() }
        infotainment("flash-lights") { VehicleRequests.flashLights() }
        infotainment("ping") { VehicleRequests.ping() }
        infotainment("set-vehicle-name", ParamSpec("NAME", ParamType.Text(maxLength = 64))) {
            VehicleRequests.setVehicleName(it.string("NAME"))
        }

        // --- Media ---
        infotainment("media-toggle-playback") { VehicleRequests.toggleMediaPlayback() }
        infotainment("media-next-track") { VehicleRequests.mediaNextTrack() }
        infotainment("media-previous-track") { VehicleRequests.mediaPreviousTrack() }
        infotainment("media-next-favorite") { VehicleRequests.mediaNextFavorite() }
        infotainment("media-previous-favorite") { VehicleRequests.mediaPreviousFavorite() }
        infotainment("media-volume-up") { VehicleRequests.volumeUp() }
        infotainment("media-volume-down") { VehicleRequests.volumeDown() }
        infotainment("media-set-volume", ParamSpec("VOLUME", ParamType.Decimal(0.0, 10.0))) {
            VehicleRequests.setVolume(it.float("VOLUME", 0.0, 10.0))
        }

        // --- Security ---
        infotainment("sentry-mode", ON_OFF) { VehicleRequests.setSentryMode(it.onOff("STATE")) }
        infotainment("valet-mode-on", ParamSpec("PIN", ParamType.Pin)) { VehicleRequests.enableValetMode(it.pin("PIN")) }
        infotainment("valet-mode-off") { VehicleRequests.disableValetMode() }
        infotainment("valet-pin-reset") { VehicleRequests.resetValetPin() }
        infotainment("speed-limit-activate", ParamSpec("PIN", ParamType.Pin)) { VehicleRequests.activateSpeedLimit(it.pin("PIN")) }
        infotainment("speed-limit-deactivate", ParamSpec("PIN", ParamType.Pin)) { VehicleRequests.deactivateSpeedLimit(it.pin("PIN")) }
        infotainment("speed-limit-set", ParamSpec("MPH", ParamType.Decimal(50.0, 120.0))) {
            VehicleRequests.speedLimitSetLimitMph(it.double("MPH", 0.0, 200.0))
        }
        infotainment("speed-limit-clear-pin", ParamSpec("PIN", ParamType.Pin)) { VehicleRequests.clearSpeedLimitPin(it.pin("PIN")) }
        infotainment("speed-limit-clear-pin-admin") { VehicleRequests.clearSpeedLimitPinAdmin() }
        infotainment("pin-to-drive-clear") { VehicleRequests.clearPinToDrive() }
        infotainment("guest-mode-on") { VehicleRequests.setGuestMode(true) }
        infotainment("guest-mode-off") { VehicleRequests.setGuestMode(false) }
        infotainment("erase-guest-data") { VehicleRequests.eraseGuestData() }
        infotainment("parental-controls-on", ParamSpec("PIN", ParamType.Pin)) { VehicleRequests.parentalControlsActivate(it.pin("PIN")) }
        infotainment("parental-controls-off", ParamSpec("PIN", ParamType.Pin)) { VehicleRequests.parentalControlsDeactivate(it.pin("PIN")) }
        infotainment("parental-controls-set-speed-limit", ParamSpec("MPH", ParamType.Decimal(50.0, 120.0))) {
            VehicleRequests.parentalControlsSetSpeedLimit(it.double("MPH", 0.0, 200.0))
        }
        infotainment("parental-controls-enable-setting", choice("SETTING", PARENTAL_SETTINGS), ON_OFF) {
            VehicleRequests.parentalControlsEnableSetting(it.choice("SETTING", PARENTAL_SETTINGS), it.onOff("STATE"))
        }
        infotainment("parental-controls-clear-pin-admin") { VehicleRequests.parentalControlsClearPin() }

        // --- Software ---
        infotainment("software-update-start", ParamSpec("DELAY", ParamType.Duration)) {
            VehicleRequests.scheduleSoftwareUpdate(it.durationSeconds("DELAY"))
        }
        infotainment("software-update-cancel") { VehicleRequests.cancelSoftwareUpdate() }

        // --- State ---
        infotainment("state", choice("CATEGORY", STATE_CATEGORIES)) { VehicleRequests.getState(it.choice("CATEGORY", STATE_CATEGORIES)) }
    }

    private fun removeSchedule(
        args: Args,
        byId: (Long) -> VehicleRequest,
        batch: (Boolean, Boolean, Boolean) -> VehicleRequest,
    ): VehicleRequest = when (args.string("TYPE").lowercase()) {
        "id" -> if (args.has("ID")) byId(args.long("ID")) else throw VehicleErrors.invalidArgument("missing schedule ID")
        "home" -> batch(true, false, false)
        "work" -> batch(false, true, false)
        "other" -> batch(false, false, true)
        else -> throw VehicleErrors.invalidArgument("TYPE must be home|work|other|id")
    }

    val all: List<CommandSpec> = specs.toList()

    private val byId: Map<String, CommandSpec> = all.associateBy { it.id }

    fun find(id: String): CommandSpec? = byId[id]

    operator fun get(id: String): CommandSpec = byId[id] ?: throw IllegalArgumentException("unknown command $id")
}
