package io.github.teslablecontrol.protocol.vehicle

import com.tesla.generated.carserver.common.Common
import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.carserver.vehicle.Vehicle
import com.tesla.generated.keys.Keys
import com.tesla.generated.vcsec.Vcsec
import io.github.teslablecontrol.protocol.VehicleErrors
import io.github.teslablecontrol.protocol.crypto.P256
import io.github.teslablecontrol.protocol.toByteString

/** Seat positions (`vehicle.SeatPosition`). "Back" values are backrest heaters on some Model S. */
enum class SeatPosition {
    UNKNOWN,
    FRONT_LEFT,
    FRONT_RIGHT,
    SECOND_ROW_LEFT,
    SECOND_ROW_LEFT_BACK,
    SECOND_ROW_CENTER,
    SECOND_ROW_RIGHT,
    SECOND_ROW_RIGHT_BACK,
    THIRD_ROW_LEFT,
    THIRD_ROW_RIGHT,
}

/** Heater/cooler levels (`vehicle.Level`). */
enum class Level { OFF, LOW, MEDIUM, HIGH }

/** Policies for scheduled departure (`vehicle.ChargingPolicy`). */
enum class ChargingPolicy { OFF, ALL_DAYS, WEEKDAYS }

/**
 * Builders for every command, one per public `pkg/vehicle` method, producing the same protobuf
 * messages as the official library.
 */
object VehicleRequests {
    private val VOID: Common.Void = Common.Void.getDefaultInstance()

    private fun action(build: CarServer.VehicleAction.Builder.() -> Unit) =
        VehicleRequest.CarServerAction(CarServer.VehicleAction.newBuilder().apply(build).build())

    private fun closure(build: Vcsec.ClosureMoveRequest.Builder.() -> Unit) =
        VehicleRequest.Closure(Vcsec.ClosureMoveRequest.newBuilder().apply(build).build())

    private fun requireValidPublicKey(publicKey: ByteArray) {
        if (!P256.isValidPublicKey(publicKey)) throw VehicleErrors.invalidArgument("invalid public key")
    }

    /** Four decimal digits (`vehicle.IsValidPIN`). */
    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it in '0'..'9' }

    private fun requireValidPin(pin: String) {
        if (!isValidPin(pin)) throw VehicleErrors.invalidArgument("PIN codes must be four digits")
    }

    // --- VCSEC: locks, closures, keys ---

    fun lock() = VehicleRequest.Rke(Vcsec.RKEAction_E.RKE_ACTION_LOCK)
    fun unlock() = VehicleRequest.Rke(Vcsec.RKEAction_E.RKE_ACTION_UNLOCK)
    fun wakeUp() = VehicleRequest.Rke(Vcsec.RKEAction_E.RKE_ACTION_WAKE_VEHICLE)
    fun remoteDrive() = VehicleRequest.Rke(Vcsec.RKEAction_E.RKE_ACTION_REMOTE_DRIVE)
    fun autoSecureVehicle() = VehicleRequest.Rke(Vcsec.RKEAction_E.RKE_ACTION_AUTO_SECURE_VEHICLE)

    fun actuateTrunk() = closure { setRearTrunk(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_MOVE) }
    fun openTrunk() = closure { setRearTrunk(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_MOVE) }
    fun closeTrunk() = closure { setRearTrunk(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_CLOSE) }
    fun openFrunk() = closure { setFrontTrunk(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_MOVE) }
    fun openTonneau() = closure { setTonneau(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_OPEN) }
    fun closeTonneau() = closure { setTonneau(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_CLOSE) }
    fun stopTonneau() = closure { setTonneau(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_STOP) }

    fun bodyControllerState() = VehicleRequest.BodyControllerState
    fun listKeys() = VehicleRequest.ListKeys

    fun addKeyWithRole(publicKey: ByteArray, role: Keys.Role, formFactor: Vcsec.KeyFormFactor): VehicleRequest.Whitelist {
        requireValidPublicKey(publicKey)
        return VehicleRequest.Whitelist(VehicleRequest.addKeyOperation(publicKey, role, formFactor))
    }

    fun removeKey(publicKey: ByteArray): VehicleRequest.Whitelist {
        requireValidPublicKey(publicKey)
        return VehicleRequest.Whitelist(
            Vcsec.WhitelistOperation.newBuilder()
                .setRemovePublicKeyFromWhitelist(Vcsec.PublicKey.newBuilder().setPublicKeyRaw(publicKey.toByteString()))
                .build(),
        )
    }

    fun sendAddKeyRequestWithRole(publicKey: ByteArray, role: Keys.Role, formFactor: Vcsec.KeyFormFactor): VehicleRequest.AddKeyRequest {
        requireValidPublicKey(publicKey)
        return VehicleRequest.AddKeyRequest(publicKey.copyOf(), role, formFactor)
    }

    fun sessionInfo(publicKey: ByteArray, domain: io.github.teslablecontrol.protocol.Domain): VehicleRequest.SessionInfoQuery {
        requireValidPublicKey(publicKey)
        return VehicleRequest.SessionInfoQuery(publicKey.copyOf(), domain)
    }

    // --- Alerts, closures and misc. (actions.go, infotainment.go) ---

    fun honkHorn() = action { setVehicleControlHonkHornAction(CarServer.VehicleControlHonkHornAction.getDefaultInstance()) }
    fun flashLights() = action { setVehicleControlFlashLightsAction(CarServer.VehicleControlFlashLightsAction.getDefaultInstance()) }

    fun changeSunroofState(level: Int) = action {
        setVehicleControlSunroofOpenCloseAction(CarServer.VehicleControlSunroofOpenCloseAction.newBuilder().setAbsoluteLevel(level).build())
    }

    fun closeWindows() = action {
        setVehicleControlWindowAction(CarServer.VehicleControlWindowAction.newBuilder().setClose(VOID).build())
    }

    fun ventWindows() = action {
        setVehicleControlWindowAction(CarServer.VehicleControlWindowAction.newBuilder().setVent(VOID).build())
    }

    fun openChargePort() = action { setChargePortDoorOpen(CarServer.ChargePortDoorOpen.getDefaultInstance()) }
    fun closeChargePort() = action { setChargePortDoorClose(CarServer.ChargePortDoorClose.getDefaultInstance()) }

    fun ping() = action { setPing(CarServer.Ping.newBuilder().setPingId(1).build()) }

    fun setVehicleName(name: String) = action {
        setSetVehicleNameAction(CarServer.SetVehicleNameAction.newBuilder().setVehicleName(name).build())
    }

    fun triggerHomelink(latitude: Float, longitude: Float) = action {
        setVehicleControlTriggerHomelinkAction(CarServer.VehicleControlTriggerHomelinkAction.newBuilder()
            .setLocation(Common.LatLong.newBuilder().setLatitude(latitude).setLongitude(longitude))
            .build())
    }

    fun scheduleSoftwareUpdate(delaySeconds: Int) = action {
        setVehicleControlScheduleSoftwareUpdateAction(
            CarServer.VehicleControlScheduleSoftwareUpdateAction.newBuilder().setOffsetSec(delaySeconds).build(),
        )
    }

    fun cancelSoftwareUpdate() = action {
        setVehicleControlCancelSoftwareUpdateAction(CarServer.VehicleControlCancelSoftwareUpdateAction.getDefaultInstance())
    }

    // --- Media ---

    private fun adjustVolume(delta: Int) = action {
        setMediaUpdateVolume(CarServer.MediaUpdateVolume.newBuilder().setVolumeDelta(delta).build())
    }

    fun volumeUp() = adjustVolume(1)
    fun volumeDown() = adjustVolume(-1)

    fun setVolume(volume: Float): VehicleRequest.CarServerAction {
        if (volume < 0f || volume > 10f) throw VehicleErrors.invalidArgument("invalid volume (should be in [0, 10])")
        return action { setMediaUpdateVolume(CarServer.MediaUpdateVolume.newBuilder().setVolumeAbsoluteFloat(volume).build()) }
    }

    fun mediaNextTrack() = action { setMediaNextTrack(CarServer.MediaNextTrack.getDefaultInstance()) }
    fun mediaPreviousTrack() = action { setMediaPreviousTrack(CarServer.MediaPreviousTrack.getDefaultInstance()) }
    fun mediaNextFavorite() = action { setMediaNextFavorite(CarServer.MediaNextFavorite.getDefaultInstance()) }
    fun mediaPreviousFavorite() = action { setMediaPreviousFavorite(CarServer.MediaPreviousFavorite.getDefaultInstance()) }
    fun toggleMediaPlayback() = action { setMediaPlayAction(CarServer.MediaPlayAction.getDefaultInstance()) }

    // --- Climate (climate.go) ---

    fun climateOn() = action { setHvacAutoAction(CarServer.HvacAutoAction.newBuilder().setPowerOn(true).build()) }
    fun climateOff() = action { setHvacAutoAction(CarServer.HvacAutoAction.newBuilder().setPowerOn(false).build()) }

    fun changeClimateTemp(driverCelsius: Float, passengerCelsius: Float) = action {
        setHvacTemperatureAdjustmentAction(
            CarServer.HvacTemperatureAdjustmentAction.newBuilder()
                .setDriverTempCelsius(driverCelsius)
                .setPassengerTempCelsius(passengerCelsius)
                // The official library always sends TEMP_MAX alongside the absolute temperatures.
                .setLevel(CarServer.HvacTemperatureAdjustmentAction.Temperature.newBuilder().setTEMPMAX(VOID))
                .build(),
        )
    }

    fun setSeatHeater(levels: Map<SeatPosition, Level>) = action {
        val heaters = CarServer.HvacSeatHeaterActions.newBuilder()
        for ((position, level) in levels) {
            val heater = CarServer.HvacSeatHeaterActions.HvacSeatHeaterAction.newBuilder()
            when (level) {
                Level.OFF -> heater.setSEATHEATEROFF(VOID)
                Level.LOW -> heater.setSEATHEATERLOW(VOID)
                Level.MEDIUM -> heater.setSEATHEATERMED(VOID)
                Level.HIGH -> heater.setSEATHEATERHIGH(VOID)
            }
            when (position) {
                SeatPosition.FRONT_LEFT -> heater.setCARSEATFRONTLEFT(VOID)
                SeatPosition.FRONT_RIGHT -> heater.setCARSEATFRONTRIGHT(VOID)
                SeatPosition.SECOND_ROW_LEFT -> heater.setCARSEATREARLEFT(VOID)
                SeatPosition.SECOND_ROW_LEFT_BACK -> heater.setCARSEATREARLEFTBACK(VOID)
                SeatPosition.SECOND_ROW_CENTER -> heater.setCARSEATREARCENTER(VOID)
                SeatPosition.SECOND_ROW_RIGHT -> heater.setCARSEATREARRIGHT(VOID)
                SeatPosition.SECOND_ROW_RIGHT_BACK -> heater.setCARSEATREARRIGHTBACK(VOID)
                SeatPosition.THIRD_ROW_LEFT -> heater.setCARSEATTHIRDROWLEFT(VOID)
                SeatPosition.THIRD_ROW_RIGHT -> heater.setCARSEATTHIRDROWRIGHT(VOID)
                SeatPosition.UNKNOWN -> heater.setCARSEATUNKNOWN(VOID)
            }
            heaters.addHvacSeatHeaterAction(heater)
        }
        setHvacSeatHeaterActions(heaters.build())
    }

    fun setSeatCooler(level: Level, seat: SeatPosition): VehicleRequest.CarServerAction {
        val position = when (seat) {
            SeatPosition.FRONT_LEFT -> CarServer.HvacSeatCoolerActions.HvacSeatCoolerPosition_E.HvacSeatCoolerPosition_FrontLeft
            SeatPosition.FRONT_RIGHT -> CarServer.HvacSeatCoolerActions.HvacSeatCoolerPosition_E.HvacSeatCoolerPosition_FrontRight
            else -> throw VehicleErrors.invalidArgument("invalid seat position")
        }
        // The protobuf enum starts at 0 for "unknown", so levels are shifted by one.
        val coolerLevel = CarServer.HvacSeatCoolerActions.HvacSeatCoolerLevel_E.forNumber(level.ordinal + 1)
        return action {
            setHvacSeatCoolerActions(CarServer.HvacSeatCoolerActions.newBuilder()
                .addHvacSeatCoolerAction(
                    CarServer.HvacSeatCoolerActions.HvacSeatCoolerAction.newBuilder()
                        .setSeatCoolerLevel(coolerLevel)
                        .setSeatPosition(position),
                )
                .build())
        }
    }

    fun autoSeatAndClimate(positions: List<SeatPosition>, enabled: Boolean) = action {
        val seats = CarServer.AutoSeatClimateAction.newBuilder()
        for (position in positions) {
            val protoPosition = when (position) {
                SeatPosition.UNKNOWN -> CarServer.AutoSeatClimateAction.AutoSeatPosition_E.AutoSeatPosition_Unknown
                SeatPosition.FRONT_LEFT -> CarServer.AutoSeatClimateAction.AutoSeatPosition_E.AutoSeatPosition_FrontLeft
                SeatPosition.FRONT_RIGHT -> CarServer.AutoSeatClimateAction.AutoSeatPosition_E.AutoSeatPosition_FrontRight
                else -> null
            } ?: continue
            seats.addCarseat(CarServer.AutoSeatClimateAction.CarSeat.newBuilder().setOn(enabled).setSeatPosition(protoPosition))
        }
        setAutoSeatClimateAction(seats.build())
    }

    fun setSteeringWheelHeater(enabled: Boolean) = action {
        setHvacSteeringWheelHeaterAction(CarServer.HvacSteeringWheelHeaterAction.newBuilder().setPowerOn(enabled).build())
    }

    fun setPreconditioningMax(enabled: Boolean, manualOverride: Boolean) = action {
        setHvacSetPreconditioningMaxAction(CarServer.HvacSetPreconditioningMaxAction.newBuilder()
            .setOn(enabled).setManualOverride(manualOverride).build())
    }

    fun setBioweaponDefenseMode(enabled: Boolean, manualOverride: Boolean) = action {
        setHvacBioweaponModeAction(CarServer.HvacBioweaponModeAction.newBuilder()
            .setOn(enabled).setManualOverride(manualOverride).build())
    }

    fun setCabinOverheatProtection(enabled: Boolean, fanOnly: Boolean) = action {
        setSetCabinOverheatProtectionAction(CarServer.SetCabinOverheatProtectionAction.newBuilder()
            .setOn(enabled).setFanOnly(fanOnly).build())
    }

    fun setCabinOverheatProtectionTemperature(level: Level) = action {
        setSetCopTempAction(CarServer.SetCopTempAction.newBuilder()
            .setCopActivationTempValue(level.ordinal).build())
    }

    fun setClimateKeeperMode(mode: CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E, override: Boolean) = action {
        setHvacClimateKeeperAction(CarServer.HvacClimateKeeperAction.newBuilder()
            .setClimateKeeperAction(mode).setManualOverride(override).build())
    }

    // --- Charging (charge.go) ---

    fun addChargeSchedule(schedule: Common.ChargeSchedule) = action { setAddChargeScheduleAction(schedule) }

    fun removeChargeSchedule(id: Long) = action {
        setRemoveChargeScheduleAction(CarServer.RemoveChargeScheduleAction.newBuilder().setId(id).build())
    }

    fun batchRemoveChargeSchedules(home: Boolean, work: Boolean, other: Boolean) = action {
        setBatchRemoveChargeSchedulesAction(CarServer.BatchRemoveChargeSchedulesAction.newBuilder()
            .setHome(home).setWork(work).setOther(other).build())
    }

    fun addPreconditionSchedule(schedule: Common.PreconditionSchedule) = action { setAddPreconditionScheduleAction(schedule) }

    fun removePreconditionSchedule(id: Long) = action {
        setRemovePreconditionScheduleAction(CarServer.RemovePreconditionScheduleAction.newBuilder().setId(id).build())
    }

    fun batchRemovePreconditionSchedules(home: Boolean, work: Boolean, other: Boolean) = action {
        setBatchRemovePreconditionSchedulesAction(CarServer.BatchRemovePreconditionSchedulesAction.newBuilder()
            .setHome(home).setWork(work).setOther(other).build())
    }

    fun changeChargeLimit(percent: Int) = action {
        setChargingSetLimitAction(CarServer.ChargingSetLimitAction.newBuilder().setPercent(percent).build())
    }

    private fun startStop(build: CarServer.ChargingStartStopAction.Builder.() -> Unit) = action {
        setChargingStartStopAction(CarServer.ChargingStartStopAction.newBuilder().apply(build).build())
    }

    fun chargeStart() = startStop { setStart(VOID) }
    fun chargeStop() = startStop { setStop(VOID) }
    fun chargeMaxRange() = startStop { setStartMaxRange(VOID) }
    fun chargeStandardRange() = startStop { setStartStandard(VOID) }

    fun setChargingAmps(amps: Int) = action {
        setSetChargingAmpsAction(CarServer.SetChargingAmpsAction.newBuilder().setChargingAmps(amps).build())
    }

    /** Charge for departure at [departAtMinutes] (minutes after midnight). */
    fun scheduleDeparture(
        departAtMinutes: Int,
        offPeakEndMinutes: Int,
        preconditioning: ChargingPolicy,
        offPeak: ChargingPolicy,
    ): VehicleRequest.CarServerAction {
        if (departAtMinutes < 0 || departAtMinutes > 24 * 60) throw VehicleErrors.invalidArgument("invalid departure time")
        val departure = CarServer.ScheduledDepartureAction.newBuilder()
            .setEnabled(true)
            .setDepartureTime(departAtMinutes)
            .setOffPeakHoursEndTime(offPeakEndMinutes)
        when (preconditioning) {
            ChargingPolicy.OFF -> Unit
            ChargingPolicy.ALL_DAYS -> departure.setPreconditioningTimes(Common.PreconditioningTimes.newBuilder().setAllWeek(VOID))
            ChargingPolicy.WEEKDAYS -> departure.setPreconditioningTimes(Common.PreconditioningTimes.newBuilder().setWeekdays(VOID))
        }
        when (offPeak) {
            ChargingPolicy.OFF -> Unit
            ChargingPolicy.ALL_DAYS -> departure.setOffPeakChargingTimes(Common.OffPeakChargingTimes.newBuilder().setAllWeek(VOID))
            ChargingPolicy.WEEKDAYS -> departure.setOffPeakChargingTimes(Common.OffPeakChargingTimes.newBuilder().setWeekdays(VOID))
        }
        return action { setScheduledDepartureAction(departure.build()) }
    }

    fun clearScheduledDeparture() = action {
        setScheduledDepartureAction(CarServer.ScheduledDepartureAction.newBuilder().setEnabled(false).build())
    }

    /** Start charging [minutesAfterMidnight] each day, or disable scheduled charging. */
    fun scheduleCharging(enabled: Boolean, minutesAfterMidnight: Int) = action {
        setScheduledChargingAction(CarServer.ScheduledChargingAction.newBuilder()
            .setEnabled(enabled).setChargingTime(minutesAfterMidnight).build())
    }

    fun setLowPowerMode(enabled: Boolean) = action {
        setSetLowPowerModeAction(CarServer.SetLowPowerModeAction.newBuilder().setLowPowerMode(enabled).build())
    }

    fun setKeepAccessoryPowerMode(enabled: Boolean) = action {
        setSetKeepAccessoryPowerModeAction(CarServer.SetKeepAccessoryPowerModeAction.newBuilder()
            .setKeepAccessoryPowerMode(enabled).build())
    }

    // --- Security (security.go) ---

    fun enableValetMode(pin: String): VehicleRequest.CarServerAction {
        requireValidPin(pin)
        return action {
            setVehicleControlSetValetModeAction(CarServer.VehicleControlSetValetModeAction.newBuilder()
                .setOn(true).setPassword(pin).build())
        }
    }

    fun disableValetMode() = VehicleRequest.CarServerAction(
        CarServer.VehicleAction.newBuilder()
            .setVehicleControlSetValetModeAction(
                CarServer.VehicleControlSetValetModeAction.newBuilder().setOn(false).setPassword(""),
            )
            .build(),
        successIfErrorEndsWith = "already off",
    )

    fun resetValetPin() = action {
        setVehicleControlResetValetPinAction(CarServer.VehicleControlResetValetPinAction.getDefaultInstance())
    }

    fun activateSpeedLimit(pin: String) = action {
        setDrivingSpeedLimitAction(CarServer.DrivingSpeedLimitAction.newBuilder().setActivate(true).setPin(pin).build())
    }

    fun deactivateSpeedLimit(pin: String) = action {
        setDrivingSpeedLimitAction(CarServer.DrivingSpeedLimitAction.newBuilder().setActivate(false).setPin(pin).build())
    }

    fun clearSpeedLimitPinAdmin() = action {
        setDrivingClearSpeedLimitPinAdminAction(CarServer.DrivingClearSpeedLimitPinAdminAction.getDefaultInstance())
    }

    fun speedLimitSetLimitMph(mph: Double) = action {
        setDrivingSetSpeedLimitAction(CarServer.DrivingSetSpeedLimitAction.newBuilder().setLimitMph(mph).build())
    }

    fun clearSpeedLimitPin(pin: String) = action {
        setDrivingClearSpeedLimitPinAction(CarServer.DrivingClearSpeedLimitPinAction.newBuilder().setPin(pin).build())
    }

    fun setSentryMode(enabled: Boolean) = action {
        setVehicleControlSetSentryModeAction(CarServer.VehicleControlSetSentryModeAction.newBuilder().setOn(enabled).build())
    }

    fun setGuestMode(enabled: Boolean) = action {
        setGuestModeAction(Vehicle.VehicleState.GuestMode.newBuilder().setGuestModeActive(enabled).build())
    }

    fun clearPinToDrive() = action {
        setVehicleControlResetPinToDriveAdminAction(CarServer.VehicleControlResetPinToDriveAdminAction.getDefaultInstance())
    }

    fun eraseGuestData() = action { setEraseUserDataAction(CarServer.EraseUserDataAction.getDefaultInstance()) }

    fun parentalControlsActivate(pin: String): VehicleRequest.CarServerAction {
        requireValidPin(pin)
        return action {
            setParentalControlsAction(CarServer.ParentalControlsAction.newBuilder().setActivate(true).setPin(pin).build())
        }
    }

    fun parentalControlsDeactivate(pin: String): VehicleRequest.CarServerAction {
        requireValidPin(pin)
        return action {
            setParentalControlsAction(CarServer.ParentalControlsAction.newBuilder().setActivate(false).setPin(pin).build())
        }
    }

    fun parentalControlsEnableSetting(
        setting: CarServer.ParentalControlsEnableSettingsAction.ParentalControlsSetting_E,
        enable: Boolean,
    ) = action {
        setParentalControlsEnableSettingsAction(CarServer.ParentalControlsEnableSettingsAction.newBuilder()
            .setSetting(setting).setEnable(enable).build())
    }

    fun parentalControlsSetSpeedLimit(mph: Double) = action {
        setParentalControlsSetSpeedLimitAction(CarServer.ParentalControlsSetSpeedLimitAction.newBuilder().setLimitMph(mph).build())
    }

    fun parentalControlsClearPin() = action {
        setParentalControlsClearPinAdminAction(CarServer.ParentalControlsClearPinAdminAction.getDefaultInstance())
    }

    // --- State (state.go) ---

    fun getState(category: StateCategory) = VehicleRequest.State(category)
}
