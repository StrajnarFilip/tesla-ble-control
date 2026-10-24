package io.github.teslablecontrol.core

import com.tesla.generated.carserver.vehicle.Vehicle
import com.tesla.generated.vcsec.Vcsec
import io.github.teslablecontrol.protocol.vehicle.StateCategory

/** Everything known about the vehicle, with when each piece was read. */
data class VehicleSnapshot(
    val body: Vcsec.VehicleStatus? = null,
    val bodyUpdatedAtMillis: Long? = null,
    val categories: Map<StateCategory, Fetched> = emptyMap(),
) {
    data class Fetched(val data: Vehicle.VehicleData, val atMillis: Long)

    fun withBody(status: Vcsec.VehicleStatus, atMillis: Long) = copy(body = status, bodyUpdatedAtMillis = atMillis)

    fun withCategory(category: StateCategory, data: Vehicle.VehicleData, atMillis: Long) =
        copy(categories = categories + (category to Fetched(data, atMillis)))

    private fun data(category: StateCategory): Vehicle.VehicleData? = categories[category]?.data

    val charge: Vehicle.ChargeState? get() = data(StateCategory.CHARGE)?.takeIf { it.hasChargeState() }?.chargeState
    val climate: Vehicle.ClimateState? get() = data(StateCategory.CLIMATE)?.takeIf { it.hasClimateState() }?.climateState
    val closures: Vehicle.ClosuresState? get() = data(StateCategory.CLOSURES)?.takeIf { it.hasClosuresState() }?.closuresState
    val drive: Vehicle.DriveState? get() = data(StateCategory.DRIVE)?.takeIf { it.hasDriveState() }?.driveState
    val location: Vehicle.LocationState? get() = data(StateCategory.LOCATION)?.takeIf { it.hasLocationState() }?.locationState
    val tires: Vehicle.TirePressureState? get() = data(StateCategory.TIRE_PRESSURE)?.takeIf { it.hasTirePressureState() }?.tirePressureState
    val media: Vehicle.MediaState? get() = data(StateCategory.MEDIA)?.takeIf { it.hasMediaState() }?.mediaState
    val mediaDetail: Vehicle.MediaDetailState? get() = data(StateCategory.MEDIA_DETAIL)?.takeIf { it.hasMediaDetailState() }?.mediaDetailState
    val softwareUpdate: Vehicle.SoftwareUpdateState? get() =
        data(StateCategory.SOFTWARE_UPDATE)?.takeIf { it.hasSoftwareUpdateState() }?.softwareUpdateState
    val parentalControls: Vehicle.ParentalControlsState? get() =
        data(StateCategory.PARENTAL_CONTROLS)?.takeIf { it.hasParentalControlsState() }?.parentalControlsState
    val chargeSchedules: Vehicle.ChargeScheduleState? get() =
        data(StateCategory.CHARGE_SCHEDULE)?.takeIf { it.hasChargeScheduleState() }?.chargeScheduleState
    val preconditionSchedules: Vehicle.PreconditioningScheduleState? get() =
        data(StateCategory.PRECONDITIONING_SCHEDULE)?.takeIf { it.hasPreconditioningScheduleState() }?.preconditioningScheduleState

    /** Asleep/awake from the body controller, which answers even while infotainment sleeps. */
    val isAsleep: Boolean?
        get() = when (body?.vehicleSleepStatus) {
            Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_ASLEEP -> true
            Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_AWAKE -> false
            else -> null
        }

    /** Lock state, preferring the body controller (it's cheaper to refresh and always current). */
    val isLocked: Boolean?
        get() = when (body?.vehicleLockState) {
            Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED, Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_INTERNAL_LOCKED -> true
            Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_UNLOCKED, Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_SELECTIVE_UNLOCKED -> false
            else -> closures?.takeIf { it.hasLocked() }?.locked
        }

    val isUserPresent: Boolean?
        get() = when (body?.userPresence) {
            Vcsec.UserPresence_E.VEHICLE_USER_PRESENCE_PRESENT -> true
            Vcsec.UserPresence_E.VEHICLE_USER_PRESENCE_NOT_PRESENT -> false
            else -> null
        }

    val batteryLevel: Int? get() = charge?.takeIf { it.hasBatteryLevel() }?.batteryLevel
    val chargeLimit: Int? get() = charge?.takeIf { it.hasChargeLimitSoc() }?.chargeLimitSoc

    /** Rated range in miles, as reported by the car. */
    val rangeMiles: Float? get() = charge?.takeIf { it.hasBatteryRange() }?.batteryRange

    val isCharging: Boolean?
        get() = charge?.takeIf { it.hasChargingState() }?.chargingState?.typeCase?.let {
            it == Vehicle.ChargeState.ChargingState.TypeCase.CHARGING || it == Vehicle.ChargeState.ChargingState.TypeCase.STARTING
        }

    val insideTempCelsius: Float? get() = climate?.takeIf { it.hasInsideTempCelsius() }?.insideTempCelsius
    val outsideTempCelsius: Float? get() = climate?.takeIf { it.hasOutsideTempCelsius() }?.outsideTempCelsius
    val isClimateOn: Boolean? get() = climate?.takeIf { it.hasIsClimateOn() }?.isClimateOn
    val driverTempSetting: Float? get() = climate?.takeIf { it.hasDriverTempSetting() }?.driverTempSetting
    val passengerTempSetting: Float? get() = climate?.takeIf { it.hasPassengerTempSetting() }?.passengerTempSetting

    val isFrunkOpen: Boolean?
        get() = body?.closureStatuses?.frontTrunk?.let { it.isOpenish() }
            ?: closures?.takeIf { it.hasDoorOpenTrunkFront() }?.doorOpenTrunkFront

    val isTrunkOpen: Boolean?
        get() = body?.closureStatuses?.rearTrunk?.let { it.isOpenish() }
            ?: closures?.takeIf { it.hasDoorOpenTrunkRear() }?.doorOpenTrunkRear

    val isChargePortOpen: Boolean?
        get() = body?.closureStatuses?.chargePort?.let { it.isOpenish() }
            ?: charge?.takeIf { it.hasChargePortDoorOpen() }?.chargePortDoorOpen

    val isTonneauOpen: Boolean? get() = body?.closureStatuses?.tonneau?.let { it.isOpenish() }

    val areWindowsOpen: Boolean?
        get() = closures?.let {
            (it.hasWindowOpenDriverFront() && it.windowOpenDriverFront) ||
                (it.hasWindowOpenPassengerFront() && it.windowOpenPassengerFront) ||
                (it.hasWindowOpenDriverRear() && it.windowOpenDriverRear) ||
                (it.hasWindowOpenPassengerRear() && it.windowOpenPassengerRear)
        }

    val isSentryOn: Boolean?
        get() = closures?.takeIf { it.hasSentryModeState() }?.sentryModeState?.typeCase?.let {
            it != Vehicle.ClosuresState.SentryModeState.TypeCase.OFF && it != Vehicle.ClosuresState.SentryModeState.TypeCase.TYPE_NOT_SET
        }

    val isValetOn: Boolean? get() = closures?.takeIf { it.hasValetMode() }?.valetMode

    private fun Vcsec.ClosureState_E.isOpenish(): Boolean? = when (this) {
        Vcsec.ClosureState_E.CLOSURESTATE_CLOSED -> false
        Vcsec.ClosureState_E.CLOSURESTATE_OPEN, Vcsec.ClosureState_E.CLOSURESTATE_AJAR,
        Vcsec.ClosureState_E.CLOSURESTATE_OPENING, Vcsec.ClosureState_E.CLOSURESTATE_FAILED_UNLATCH -> true
        Vcsec.ClosureState_E.CLOSURESTATE_CLOSING -> false
        else -> null
    }
}
