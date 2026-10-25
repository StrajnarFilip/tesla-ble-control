package io.github.teslablecontrol.ui.climate

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tesla.generated.carserver.common.Common
import com.tesla.generated.carserver.vehicle.Vehicle
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.data.CarModel
import io.github.teslablecontrol.data.UnitSystem
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.ui.CommandRoute
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.commands.onOff
import io.github.teslablecontrol.ui.components.ButtonWithNote
import io.github.teslablecontrol.ui.components.ChoiceRow
import io.github.teslablecontrol.ui.components.InfoRow
import io.github.teslablecontrol.ui.components.ItemRow
import io.github.teslablecontrol.ui.components.MainScreenFrame
import io.github.teslablecontrol.ui.components.SectionGroup
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.SliderControl
import io.github.teslablecontrol.ui.components.TeslaButton
import io.github.teslablecontrol.ui.components.ToggleRow
import io.github.teslablecontrol.ui.format.Units
import io.github.teslablecontrol.ui.format.celsiusFrom
import io.github.teslablecontrol.ui.format.daysText
import io.github.teslablecontrol.ui.format.minutesToClock
import io.github.teslablecontrol.ui.format.temperature
import io.github.teslablecontrol.ui.format.temperatureValue
import java.util.Locale
import kotlin.math.roundToInt

private val CLIMATE_CATEGORIES = listOf(StateCategory.CLIMATE, StateCategory.PRECONDITIONING_SCHEDULE)

/**
 * A seat heater: the catalog's seat id, its label, how to read its level from the car, and which
 * side of the car it's on (-1 left, 0 centre, 1 right).
 */
private class Seat(
    val id: String,
    val label: String,
    val read: (Vehicle.ClimateState) -> Int?,
    val side: Int,
    val rearOnly: Boolean = false,
    val thirdRow: Boolean = false,
    val back: Boolean = false,
)

private val SEATS = listOf(
    Seat("front-left", "Front left", { c -> c.takeIf { it.hasSeatHeaterLeft() }?.seatHeaterLeft }, side = -1),
    Seat("front-right", "Front right", { c -> c.takeIf { it.hasSeatHeaterRight() }?.seatHeaterRight }, side = 1),
    Seat("2nd-row-left", "Rear left", { c -> c.takeIf { it.hasSeatHeaterRearLeft() }?.seatHeaterRearLeft }, side = -1, rearOnly = true),
    Seat("2nd-row-center", "Rear centre", { c -> c.takeIf { it.hasSeatHeaterRearCenter() }?.seatHeaterRearCenter }, side = 0, rearOnly = true),
    Seat("2nd-row-right", "Rear right", { c -> c.takeIf { it.hasSeatHeaterRearRight() }?.seatHeaterRearRight }, side = 1, rearOnly = true),
    Seat("2nd-row-left-back", "Rear left backrest", { c -> c.takeIf { it.hasSeatHeaterRearLeftBack() }?.seatHeaterRearLeftBack }, side = -1, back = true),
    Seat("2nd-row-right-back", "Rear right backrest", { c -> c.takeIf { it.hasSeatHeaterRearRightBack() }?.seatHeaterRearRightBack }, side = 1, back = true),
    Seat("3rd-row-left", "Third row left", { c -> c.takeIf { it.hasSeatHeaterThirdRowLeft() }?.seatHeaterThirdRowLeft }, side = -1, thirdRow = true),
    Seat("3rd-row-right", "Third row right", { c -> c.takeIf { it.hasSeatHeaterThirdRowRight() }?.seatHeaterThirdRowRight }, side = 1, thirdRow = true),
)

/** The front seats' cooling fans: the seat, and how to read its fan level from the car. */
private val SEAT_FANS: List<Pair<Seat, (Vehicle.ClimateState) -> Int?>> = listOf(
    SEATS[0] to { c -> c.takeIf { it.hasSeatFanFrontLeft() }?.seatFanFrontLeft },
    SEATS[1] to { c -> c.takeIf { it.hasSeatFanFrontRight() }?.seatFanFrontRight },
)

/** The seats the car reports a heater in, by seat id; it leaves out the others. */
private fun Vehicle.ClimateState.heatedSeats() = SEATS.filter { it.read(this) != null }.mapTo(mutableSetOf()) { it.id }

/** The seats the car reports a cooling fan in, by seat id. */
private fun Vehicle.ClimateState.cooledSeats() =
    SEAT_FANS.filter { (_, read) -> read(this) != null }.mapTo(mutableSetOf()) { (seat, _) -> seat.id }

/**
 * Regions where Tesla sells cars with the steering wheel on the right. The car doesn't report it,
 * so the phone's region decides, as it does for units.
 */
private val RIGHT_HAND_DRIVE = setOf("GB", "IE", "AU", "NZ", "JP", "HK", "MO", "SG", "MY", "TH", "IN", "CY", "MT")

/** Whose seat this is, so "front left" reads as "Driver". */
private fun Seat.note(rightHandDrive: Boolean): String {
    val driverSide = side == (if (rightHandDrive) 1 else -1)
    return when {
        side == 0 -> "Middle of the back seat"
        thirdRow -> if (driverSide) "Third row, driver's side" else "Third row, passenger's side"
        back -> if (driverSide) "Backrest behind the driver" else "Backrest behind the front passenger"
        rearOnly -> if (driverSide) "Behind the driver" else "Behind the front passenger"
        else -> if (driverSide) "Driver" else "Front passenger"
    }
}

@Composable
fun ClimateScreen(viewModel: MainViewModel, navigator: Navigator) {
    val state by viewModel.controllerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val running by viewModel.running.collectAsState()
    val units = Units.resolve(settings?.units ?: UnitSystem.AUTO)
    val showAll = settings?.showAllControls == true
    val climate = state.snapshot.climate
    val levelIds = listOf("off", "low", "medium", "high")
    // The car's three heat levels, as on its own seat buttons.
    val levelOptions = listOf(0 to stringResource(R.string.level_off), 1 to "1", 2 to "2", 3 to "3")
    // The seat whose level is being sent, so only its slider shows progress.
    var sendingSeat by remember { mutableStateOf<String?>(null) }
    // Likewise the side whose automatic seat climate is being set ("L" or "R").
    var sendingAutoSeat by remember { mutableStateOf<String?>(null) }
    val rightHandDrive = Locale.getDefault().country in RIGHT_HAND_DRIVE

    @Composable
    fun SeatLevelRow(seat: Seat, level: Int?, command: String) = ChoiceRow(
        title = seat.label,
        description = seat.note(rightHandDrive),
        options = levelOptions,
        selected = level,
        onSelect = { value ->
            sendingSeat = seat.id
            viewModel.send(command, mapOf("SEAT" to seat.id, "LEVEL" to levelIds[value]))
        },
        busy = running == command && sendingSeat == seat.id,
    )

    MainScreenFrame(viewModel, navigator, stringResource(R.string.climate_title), CLIMATE_CATEGORIES) {
        SectionGroup {
            InfoRow(stringResource(R.string.home_inside), units.temperature(state.snapshot.insideTempCelsius))
            InfoRow(stringResource(R.string.home_outside), units.temperature(state.snapshot.outsideTempCelsius))
            ToggleRow(
                title = stringResource(R.string.climate_power),
                description = stringResource(if (state.snapshot.isClimateOn == true) R.string.climate_power_on_desc else R.string.climate_power_off_desc),
                descriptions = listOf(stringResource(R.string.climate_power_on_desc), stringResource(R.string.climate_power_off_desc)),
                checked = state.snapshot.isClimateOn,
                busy = running == "climate-on" || running == "climate-off",
                onCheckedChange = { on -> viewModel.send(if (on) "climate-on" else "climate-off") },
            )
        }

        TemperatureCard(viewModel, units, climate, running == "climate-set-temp")

        SectionHeader(stringResource(R.string.climate_seats))
        // The car leaves out the seats it has no heater or fan in. What it last said is kept, so a sleeping
        // car shows the same seats; before the first read, the heated seats most cars of its model have,
        // and no fans.
        LaunchedEffect(climate) {
            climate?.let { c -> viewModel.rememberSeats(heated = c.heatedSeats(), cooled = c.cooledSeats()) }
        }
        val heatedSeats = climate?.heatedSeats() ?: profile?.heatedSeats
        val cooledSeats = climate?.cooledSeats() ?: profile?.cooledSeats
        val visible = SEATS.filter { seat ->
            when {
                showAll -> true
                heatedSeats != null -> seat.id in heatedSeats
                seat.thirdRow -> profile?.model == CarModel.MODEL_X || profile?.model == CarModel.MODEL_Y
                seat.back -> false
                else -> true
            }
        }
        // One seat per row, front to back, each saying whose seat it is.
        SectionGroup {
            visible.forEach { seat -> SeatLevelRow(seat, climate?.let(seat.read), "seat-heater") }
        }

        val fans = SEAT_FANS.filter { (seat, _) -> showAll || cooledSeats?.contains(seat.id) == true }
        if (fans.isNotEmpty()) {
            SectionHeader(stringResource(R.string.climate_seat_cooling))
            SectionGroup {
                fans.forEach { (seat, read) -> SeatLevelRow(seat, climate?.let(read), "seat-cooler") }
            }
        }

        SectionHeader(stringResource(R.string.climate_title))
        SectionGroup {
            val wheelLevel = climate?.takeIf { it.hasSteeringWheelHeatLevel() }?.steeringWheelHeatLevel
            val wheelAuto = climate?.takeIf { it.hasAutoSteeringWheelHeat() }?.autoSteeringWheelHeat == true
            val wheelState = stringResource(
                R.string.climate_steering_setting,
                listOfNotNull(
                    when (wheelLevel) {
                        Common.StwHeatLevel.StwHeatLevel_Low -> stringResource(R.string.climate_steering_level_1)
                        Common.StwHeatLevel.StwHeatLevel_High -> stringResource(R.string.climate_steering_level_2)
                        Common.StwHeatLevel.StwHeatLevel_Off -> stringResource(R.string.word_off)
                        else -> null
                    },
                    stringResource(R.string.climate_steering_auto).takeIf { wheelAuto },
                ).joinToString(" · ").ifEmpty { stringResource(R.string.value_unknown) },
            )
            ToggleRow(
                title = stringResource(R.string.climate_steering_wheel),
                // Always the setting line, then the note, so the row keeps its height when the car reports.
                description = "$wheelState\n${stringResource(R.string.climate_steering_note)}",
                checked = climate?.takeIf { it.hasSteeringWheelHeater() }?.steeringWheelHeater
                    ?: wheelLevel?.let { it == Common.StwHeatLevel.StwHeatLevel_Low || it == Common.StwHeatLevel.StwHeatLevel_High },
                busy = running == "steering-wheel-heater",
                onCheckedChange = { on -> viewModel.send("steering-wheel-heater", mapOf("STATE" to onOff(on))) },
            )
            ToggleRow(
                title = stringResource(R.string.climate_defrost),
                description = stringResource(R.string.climate_defrost_desc),
                checked = climate?.takeIf { it.hasDefrostMode() }?.defrostMode?.typeCase?.let { it == Vehicle.ClimateState.DefrostMode.TypeCase.MAX },
                busy = running == "preconditioning-max",
                onCheckedChange = { on -> viewModel.send("preconditioning-max", mapOf("STATE" to onOff(on))) },
            )
            ChoiceRow(
                title = stringResource(R.string.climate_keeper),
                // Short, so four fit side by side; the title says what they keep.
                options = listOf("off" to "Off", "on" to "On", "dog" to "Dog", "camp" to "Camp"),
                // The car leaves the mode out when climate keeping is off.
                selected = climate?.let {
                    when (it.climateKeeperMode.typeCase) {
                        Vehicle.ClimateState.ClimateKeeperMode.TypeCase.ON -> "on"
                        Vehicle.ClimateState.ClimateKeeperMode.TypeCase.DOG -> "dog"
                        Vehicle.ClimateState.ClimateKeeperMode.TypeCase.PARTY -> "camp"
                        else -> "off"
                    }
                },
                busy = running == "climate-keeper",
                onSelect = { mode -> viewModel.send("climate-keeper", mapOf("MODE" to mode)) },
            )
            ChoiceRow(
                title = stringResource(R.string.climate_cop),
                options = listOf("off" to "Off", "on" to "On", "fan" to "Fan only"),
                selected = climate?.takeIf { it.hasCabinOverheatProtection() }?.cabinOverheatProtection?.let {
                    when (it) {
                        Vehicle.ClimateState.CabinOverheatProtection_E.CabinOverheatProtectionOn -> "on"
                        Vehicle.ClimateState.CabinOverheatProtection_E.CabinOverheatProtectionFanOnly -> "fan"
                        Vehicle.ClimateState.CabinOverheatProtection_E.CabinOverheatProtectionOff -> "off"
                        else -> null
                    }
                },
                busy = running == "cabin-overheat-protection",
                onSelect = { mode ->
                    viewModel.send(
                        "cabin-overheat-protection",
                        mapOf("STATE" to onOff(mode != "off"), "FAN_ONLY" to onOff(mode == "fan")),
                    )
                },
            )
            ChoiceRow(
                title = stringResource(R.string.climate_cop_temp),
                options = listOf("low" to stringResource(R.string.level_low), "medium" to stringResource(R.string.level_medium), "high" to stringResource(R.string.level_high)),
                selected = climate?.takeIf { it.hasCopActivationTemperature() }?.copActivationTemperature?.let {
                    when (it) {
                        Vehicle.ClimateState.CopActivationTemp.CopActivationTempLow -> "low"
                        Vehicle.ClimateState.CopActivationTemp.CopActivationTempMedium -> "medium"
                        Vehicle.ClimateState.CopActivationTemp.CopActivationTempHigh -> "high"
                        else -> null
                    }
                },
                busy = running == "cabin-overheat-protection-temp",
                onSelect = { level -> viewModel.send("cabin-overheat-protection-temp", mapOf("LEVEL" to level)) },
            )
            if (climate?.hasBioweaponModeOn() == true || showAll || profile?.model in setOf(CarModel.MODEL_S, CarModel.MODEL_X, CarModel.MODEL_Y)) {
                ToggleRow(
                    title = stringResource(R.string.climate_bioweapon),
                    description = stringResource(R.string.climate_bioweapon_desc),
                    checked = climate?.takeIf { it.hasBioweaponModeOn() }?.bioweaponModeOn,
                    busy = running == "bioweapon-mode",
                    onCheckedChange = { on -> viewModel.send("bioweapon-mode", mapOf("STATE" to onOff(on))) },
                )
            }
            ToggleRow(
                title = stringResource(R.string.climate_auto_seat) + " (driver)",
                checked = climate?.takeIf { it.hasAutoSeatClimateLeft() }?.autoSeatClimateLeft,
                busy = running == "auto-seat-and-climate" && sendingAutoSeat == "L",
                onCheckedChange = { on ->
                    sendingAutoSeat = "L"
                    viewModel.send("auto-seat-and-climate", mapOf("POSITIONS" to "L", "STATE" to onOff(on)))
                },
            )
            ToggleRow(
                title = stringResource(R.string.climate_auto_seat) + " (passenger)",
                checked = climate?.takeIf { it.hasAutoSeatClimateRight() }?.autoSeatClimateRight,
                busy = running == "auto-seat-and-climate" && sendingAutoSeat == "R",
                onCheckedChange = { on ->
                    sendingAutoSeat = "R"
                    viewModel.send("auto-seat-and-climate", mapOf("POSITIONS" to "R", "STATE" to onOff(on)))
                },
            )
        }

        SectionHeader(stringResource(R.string.climate_schedules))
        // Actions first: adding or removing schedules changes only the list below them.
        TeslaButton("Add precondition schedule", { navigator.to(CommandRoute("precondition-schedule-add")) }, Modifier.fillMaxWidth())
        ButtonWithNote(
            label = "Scheduled departure",
            onClick = { navigator.to(CommandRoute("scheduled-departure")) },
            note = "Precondition and charge to be ready at a time",
        )
        SectionGroup {
            val schedules = state.snapshot.preconditionSchedules?.preconditionSchedulesList.orEmpty()
            if (schedules.isEmpty()) {
                Text(
                    stringResource(R.string.charging_no_schedules),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            schedules.forEach { schedule ->
                ItemRow(
                    title = "${minutesToClock(schedule.preconditionTime)} · ${daysText(schedule.daysOfWeek)}",
                    description = (if (schedule.enabled) "Enabled" else "Disabled") + if (schedule.oneTime) " · once" else "",
                    actionLabel = stringResource(R.string.action_remove),
                    onAction = { viewModel.send("precondition-schedule-remove", mapOf("TYPE" to "id", "ID" to schedule.id.toString())) },
                )
            }
        }
    }
}

/**
 * Driver and passenger temperature as sliders in the user's units. A change is sent when the finger
 * lifts (or shortly after the last − / + tap); if the car refuses it, the slider returns to the car's
 * setting.
 */
@Composable
private fun TemperatureCard(viewModel: MainViewModel, units: Units, climate: Vehicle.ClimateState?, busy: Boolean) {
    val minC = climate?.takeIf { it.hasMinAvailTempCelsius() }?.minAvailTempCelsius ?: 15f
    val maxC = climate?.takeIf { it.hasMaxAvailTempCelsius() }?.maxAvailTempCelsius ?: 28f
    val carDriver = climate?.takeIf { it.hasDriverTempSetting() }?.driverTempSetting
    val carPassenger = climate?.takeIf { it.hasPassengerTempSetting() }?.passengerTempSetting
    // Fahrenheit moves in whole degrees, Celsius in halves, as on the car.
    val step = if (units.fahrenheit) 1f else 0.5f
    fun toDisplay(celsius: Float): Float {
        val v = units.temperatureValue(celsius)
        return (v / step).roundToInt() * step
    }
    val range = toDisplay(minC)..toDisplay(maxC)
    var failures by remember { mutableIntStateOf(0) }
    var driver by remember(carDriver, failures) { mutableFloatStateOf(toDisplay(carDriver ?: 21f).coerceIn(range)) }
    var passenger by remember(carPassenger, failures) { mutableFloatStateOf(toDisplay(carPassenger ?: carDriver ?: 21f).coerceIn(range)) }
    var together by remember(carDriver, carPassenger) { mutableStateOf(carDriver == null || carPassenger == null || carDriver == carPassenger) }

    fun text(display: Float) = units.temperature(units.celsiusFrom(display))
    fun arg(display: Float) = if (units.fahrenheit) "${display.roundToInt()}f" else String.format(Locale.ROOT, "%.1fc", display)
    // A lambda, not a local fun passed as ::commit: Compose keeps reusing a function reference's
    // first instance, which would keep reading the slider values from the first time it was shown.
    val commit: () -> Unit = commit@{
        val passengerValue = if (together) driver else passenger
        val unchanged = carDriver != null && carPassenger != null &&
            driver == toDisplay(carDriver) && passengerValue == toDisplay(carPassenger)
        if (unchanged) return@commit
        viewModel.send("climate-set-temp", mapOf("TEMP" to arg(driver), "PASSENGER_TEMP" to arg(passengerValue))) { outcome ->
            if (outcome is CommandOutcome.Failure) failures++
        }
    }

    SectionHeader(stringResource(R.string.climate_temperature))
    SectionGroup {
        val driverLabel = stringResource(R.string.climate_driver)
        val passengerLabel = stringResource(R.string.climate_passenger)
        SliderControl(
            title = if (together) stringResource(R.string.climate_both_seats) else driverLabel,
            valueText = text(driver),
            value = driver,
            onValueChange = {
                driver = it
                if (together) passenger = it
            },
            onCommit = commit,
            valueRange = range,
            step = step,
            decreaseLabel = stringResource(R.string.climate_decrease, driverLabel),
            increaseLabel = stringResource(R.string.climate_increase, driverLabel),
            busy = busy,
        )
        ToggleRow(
            title = stringResource(R.string.climate_sync_passenger),
            checked = together,
            onCheckedChange = { on ->
                together = on
                if (on && passenger != driver) {
                    passenger = driver
                    commit()
                }
            },
        )
        if (!together) {
            SliderControl(
                title = passengerLabel,
                valueText = text(passenger),
                value = passenger,
                onValueChange = { passenger = it },
                onCommit = commit,
                valueRange = range,
                step = step,
                decreaseLabel = stringResource(R.string.climate_decrease, passengerLabel),
                increaseLabel = stringResource(R.string.climate_increase, passengerLabel),
                busy = busy,
            )
        }
    }
}
