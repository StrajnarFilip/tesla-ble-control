package io.github.teslablecontrol.ui.climate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Masks
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.painterResource
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
import io.github.teslablecontrol.ui.components.ActionRow
import io.github.teslablecontrol.ui.components.ChoiceRow
import io.github.teslablecontrol.ui.components.InfoRow
import io.github.teslablecontrol.ui.components.MainScreenFrame
import io.github.teslablecontrol.ui.components.SectionCard
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.SliderControl
import io.github.teslablecontrol.ui.components.Stepper
import io.github.teslablecontrol.ui.components.ToggleRow
import io.github.teslablecontrol.ui.commands.onOff
import io.github.teslablecontrol.ui.format.Units
import io.github.teslablecontrol.ui.format.celsiusFrom
import io.github.teslablecontrol.ui.format.daysText
import io.github.teslablecontrol.ui.format.minutesToClock
import io.github.teslablecontrol.ui.format.temperature
import io.github.teslablecontrol.ui.format.temperatureValue
import java.util.Locale
import kotlin.math.roundToInt

private val CLIMATE_CATEGORIES = listOf(StateCategory.CLIMATE, StateCategory.PRECONDITIONING_SCHEDULE)

/** A seat heater: the catalog's seat id, its label, and how to read its level from the car. */
private class Seat(val id: String, val label: String, val read: (Vehicle.ClimateState) -> Int?, val rearOnly: Boolean = false, val thirdRow: Boolean = false, val back: Boolean = false)

private val SEATS = listOf(
    Seat("front-left", "Front left seat", { c -> c.takeIf { it.hasSeatHeaterLeft() }?.seatHeaterLeft }),
    Seat("front-right", "Front right seat", { c -> c.takeIf { it.hasSeatHeaterRight() }?.seatHeaterRight }),
    Seat("2nd-row-left", "Rear left seat", { c -> c.takeIf { it.hasSeatHeaterRearLeft() }?.seatHeaterRearLeft }, rearOnly = true),
    Seat("2nd-row-center", "Rear centre seat", { c -> c.takeIf { it.hasSeatHeaterRearCenter() }?.seatHeaterRearCenter }, rearOnly = true),
    Seat("2nd-row-right", "Rear right seat", { c -> c.takeIf { it.hasSeatHeaterRearRight() }?.seatHeaterRearRight }, rearOnly = true),
    Seat("2nd-row-left-back", "Rear left backrest", { c -> c.takeIf { it.hasSeatHeaterRearLeftBack() }?.seatHeaterRearLeftBack }, back = true),
    Seat("2nd-row-right-back", "Rear right backrest", { c -> c.takeIf { it.hasSeatHeaterRearRightBack() }?.seatHeaterRearRightBack }, back = true),
    Seat("3rd-row-left", "Third row left seat", { c -> c.takeIf { it.hasSeatHeaterThirdRowLeft() }?.seatHeaterThirdRowLeft }, thirdRow = true),
    Seat("3rd-row-right", "Third row right seat", { c -> c.takeIf { it.hasSeatHeaterThirdRowRight() }?.seatHeaterThirdRowRight }, thirdRow = true),
)

private val COP_ICONS = mapOf("off" to R.drawable.ic_action_off, "on" to R.drawable.ic_action_cop_on, "fan" to R.drawable.ic_action_cop_fan)
private val COP_TEMP_ICONS = mapOf(
    "low" to R.drawable.ic_action_temp_low, "medium" to R.drawable.ic_action_temp_medium, "high" to R.drawable.ic_action_temp_high,
)
private val KEEPER_ICONS = mapOf(
    "off" to R.drawable.ic_action_off, "on" to R.drawable.ic_action_keeper_on,
    "dog" to R.drawable.ic_action_keeper_dog, "camp" to R.drawable.ic_action_keeper_camp,
)

@Composable
fun ClimateScreen(viewModel: MainViewModel, navigator: Navigator) {
    val state by viewModel.controllerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val running by viewModel.running.collectAsState()
    val units = Units.resolve(settings?.units ?: UnitSystem.AUTO)
    val showAll = settings?.showAllControls == true
    val climate = state.snapshot.climate
    val levels = listOf(0 to stringResource(R.string.level_off), 1 to stringResource(R.string.level_low), 2 to stringResource(R.string.level_medium), 3 to stringResource(R.string.level_high))
    val levelIds = listOf("off", "low", "medium", "high")

    LaunchedEffect(profile?.vin) { viewModel.refresh(CLIMATE_CATEGORIES) }

    MainScreenFrame(viewModel, navigator, stringResource(R.string.climate_title), onRefresh = { viewModel.refresh(CLIMATE_CATEGORIES) }) {
        SectionCard {
            InfoRow(stringResource(R.string.home_inside), units.temperature(state.snapshot.insideTempCelsius))
            InfoRow(stringResource(R.string.home_outside), units.temperature(state.snapshot.outsideTempCelsius))
            ToggleRow(
                title = stringResource(R.string.climate_power),
                description = stringResource(if (state.snapshot.isClimateOn == true) R.string.climate_power_on_desc else R.string.climate_power_off_desc),
                descriptions = listOf(stringResource(R.string.climate_power_on_desc), stringResource(R.string.climate_power_off_desc)),
                checked = state.snapshot.isClimateOn,
                icon = painterResource(R.drawable.ic_action_climate),
                busy = running == "climate-on" || running == "climate-off",
                onCheckedChange = { on -> viewModel.send(if (on) "climate-on" else "climate-off") },
            )
        }

        TemperatureCard(viewModel, units, climate, running == "climate-set-temp")

        SectionHeader(stringResource(R.string.climate_seats))
        val levelNames = listOf(
            stringResource(R.string.level_off), stringResource(R.string.level_low),
            stringResource(R.string.level_medium), stringResource(R.string.level_high),
        )
        val frontLabel = stringResource(R.string.climate_front)
        SectionCard {
            val visible = SEATS.filter { seat ->
                val known = climate?.let(seat.read) != null
                when {
                    showAll || known -> true
                    seat.thirdRow -> profile?.model == CarModel.MODEL_X || profile?.model == CarModel.MODEL_Y
                    seat.back -> false
                    seat.rearOnly -> climate == null
                    else -> true
                }
            }.associateBy { it.id }
            fun row(vararg ids: String) = ids.mapNotNull { visible[it] }.map { PlanSeat(it.id, it.label, climate?.let(it.read)) }
            val rows = listOf(
                row("front-left", "front-right"),
                row("2nd-row-left", "2nd-row-center", "2nd-row-right"),
                row("2nd-row-left-back", "2nd-row-right-back"),
                row("3rd-row-left", "3rd-row-right"),
            ).filter { it.isNotEmpty() }
            SeatPlan(
                rows = rows,
                levelNames = levelNames,
                heating = true,
                onSet = { id, level -> viewModel.send("seat-heater", mapOf("SEAT" to id, "LEVEL" to levelIds[level])) },
                frontLabel = frontLabel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        val hasCooling = climate?.hasSeatFanFrontLeft() == true || showAll
        if (hasCooling) {
            SectionHeader(stringResource(R.string.climate_seat_cooling))
            SectionCard {
                SeatPlan(
                    rows = listOf(
                        listOf(
                            PlanSeat("front-left", SEATS[0].label, climate?.takeIf { it.hasSeatFanFrontLeft() }?.seatFanFrontLeft),
                            PlanSeat("front-right", SEATS[1].label, climate?.takeIf { it.hasSeatFanFrontRight() }?.seatFanFrontRight),
                        ),
                    ),
                    levelNames = levelNames,
                    heating = false,
                    onSet = { id, level -> viewModel.send("seat-cooler", mapOf("SEAT" to id, "LEVEL" to levelIds[level])) },
                    frontLabel = frontLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        SectionHeader(stringResource(R.string.climate_title))
        SectionCard {
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
                icon = painterResource(R.drawable.ic_action_steering_heat),
                busy = running == "steering-wheel-heater",
                onCheckedChange = { on -> viewModel.send("steering-wheel-heater", mapOf("STATE" to onOff(on))) },
            )
            ToggleRow(
                title = stringResource(R.string.climate_defrost),
                description = stringResource(R.string.climate_defrost_desc),
                checked = climate?.takeIf { it.hasDefrostMode() }?.defrostMode?.typeCase?.let { it == Vehicle.ClimateState.DefrostMode.TypeCase.MAX },
                icon = painterResource(R.drawable.ic_action_defrost),
                busy = running == "preconditioning-max",
                onCheckedChange = { on -> viewModel.send("preconditioning-max", mapOf("STATE" to onOff(on))) },
            )
            ChoiceRow(
                title = stringResource(R.string.climate_keeper),
                options = listOf("off" to "Off", "on" to "Keep on", "dog" to "Dog mode", "camp" to "Camp mode"),
                // The car leaves the mode out when climate keeping is off.
                selected = climate?.let {
                    when (it.climateKeeperMode.typeCase) {
                        Vehicle.ClimateState.ClimateKeeperMode.TypeCase.ON -> "on"
                        Vehicle.ClimateState.ClimateKeeperMode.TypeCase.DOG -> "dog"
                        Vehicle.ClimateState.ClimateKeeperMode.TypeCase.PARTY -> "camp"
                        else -> "off"
                    }
                },
                enabled = running != "climate-keeper",
                icons = { mode -> KEEPER_ICONS[mode] },
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
                enabled = running != "cabin-overheat-protection",
                icons = { mode -> COP_ICONS[mode] },
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
                enabled = running != "cabin-overheat-protection-temp",
                icons = { level -> COP_TEMP_ICONS[level] },
                onSelect = { level -> viewModel.send("cabin-overheat-protection-temp", mapOf("LEVEL" to level)) },
            )
            if (climate?.hasBioweaponModeOn() == true || showAll || profile?.model in setOf(CarModel.MODEL_S, CarModel.MODEL_X, CarModel.MODEL_Y)) {
                ToggleRow(
                    title = stringResource(R.string.climate_bioweapon),
                    description = stringResource(R.string.climate_bioweapon_desc),
                    checked = climate?.takeIf { it.hasBioweaponModeOn() }?.bioweaponModeOn,
                    icon = Icons.Outlined.Masks,
                    busy = running == "bioweapon-mode",
                    onCheckedChange = { on -> viewModel.send("bioweapon-mode", mapOf("STATE" to onOff(on))) },
                )
            }
            ToggleRow(
                title = stringResource(R.string.climate_auto_seat) + " (driver)",
                checked = climate?.takeIf { it.hasAutoSeatClimateLeft() }?.autoSeatClimateLeft,
                busy = running == "auto-seat-and-climate",
                onCheckedChange = { on -> viewModel.send("auto-seat-and-climate", mapOf("POSITIONS" to "L", "STATE" to onOff(on))) },
            )
            ToggleRow(
                title = stringResource(R.string.climate_auto_seat) + " (passenger)",
                checked = climate?.takeIf { it.hasAutoSeatClimateRight() }?.autoSeatClimateRight,
                busy = running == "auto-seat-and-climate",
                onCheckedChange = { on -> viewModel.send("auto-seat-and-climate", mapOf("POSITIONS" to "R", "STATE" to onOff(on))) },
            )
        }

        SectionHeader(stringResource(R.string.climate_schedules))
        SectionCard {
            // Actions first: adding or removing schedules changes only the list below them.
            ActionRow(
                title = "Add precondition schedule",
                icon = Icons.Outlined.Add,
                onClick = { navigator.to(CommandRoute("precondition-schedule-add")) },
            )
            ActionRow(
                title = "Scheduled departure",
                description = "Precondition and charge to be ready at a time",
                icon = Icons.Outlined.Schedule,
                onClick = { navigator.to(CommandRoute("scheduled-departure")) },
            )
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
                ActionRow(
                    title = "${minutesToClock(schedule.preconditionTime)} · ${daysText(schedule.daysOfWeek)}",
                    description = (if (schedule.enabled) "Enabled" else "Disabled") + if (schedule.oneTime) " · once" else "",
                    icon = Icons.Outlined.Delete,
                    trailing = stringResource(R.string.action_remove),
                    onClick = { viewModel.send("precondition-schedule-remove", mapOf("TYPE" to "id", "ID" to schedule.id.toString())) },
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
    SectionCard {
        val driverLabel = stringResource(R.string.climate_driver)
        val passengerLabel = stringResource(R.string.climate_passenger)
        SliderControl(
            title = if (together) stringResource(R.string.climate_temperature) else driverLabel,
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
