package io.github.teslablecontrol.ui.charging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tesla.generated.carserver.vehicle.Vehicle
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.data.UnitSystem
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.ui.CommandRoute
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.commands.onOff
import io.github.teslablecontrol.ui.components.ActionRow
import io.github.teslablecontrol.ui.components.ActionTile
import io.github.teslablecontrol.ui.components.ChargePortTile
import io.github.teslablecontrol.ui.components.InfoRow
import io.github.teslablecontrol.ui.components.MainScreenFrame
import io.github.teslablecontrol.ui.components.ReadingText
import io.github.teslablecontrol.ui.components.SectionCard
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.SettingTile
import io.github.teslablecontrol.ui.components.SliderControl
import io.github.teslablecontrol.ui.components.Stepper
import io.github.teslablecontrol.ui.components.TileGrid
import io.github.teslablecontrol.ui.format.Units
import io.github.teslablecontrol.ui.format.daysText
import io.github.teslablecontrol.ui.format.distanceFromMiles
import io.github.teslablecontrol.ui.format.distanceReading
import io.github.teslablecontrol.ui.format.durationText
import io.github.teslablecontrol.ui.format.minutesToClock
import io.github.teslablecontrol.ui.format.oneDecimal
import io.github.teslablecontrol.ui.format.percentReading
import io.github.teslablecontrol.ui.theme.NumberStyle
import kotlin.math.roundToInt

private val CHARGING_CATEGORIES = listOf(StateCategory.CHARGE, StateCategory.CHARGE_SCHEDULE)

@Composable
fun ChargingScreen(viewModel: MainViewModel, navigator: Navigator) {
    val state by viewModel.controllerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val running by viewModel.running.collectAsState()
    val units = Units.resolve(settings?.units ?: UnitSystem.AUTO)
    val snapshot = state.snapshot
    val charge = snapshot.charge

    LaunchedEffect(profile?.vin) { viewModel.refresh(CHARGING_CATEGORIES) }

    MainScreenFrame(viewModel, navigator, stringResource(R.string.charging_title), onRefresh = { viewModel.refresh(CHARGING_CATEGORIES) }) {
        BatteryCard(charge, units)

        val charging = snapshot.isCharging == true
        Button(
            onClick = { viewModel.send(if (charging) "charging-stop" else "charging-start") },
            enabled = running != "charging-start" && running != "charging-stop",
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(
                painterResource(if (charging) R.drawable.ic_action_charge_stop else R.drawable.ic_action_charge_start),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(stringResource(if (charging) R.string.charging_stop else R.string.charging_start))
        }

        SectionHeader(stringResource(R.string.charging_limit))
        SectionCard {
            LimitEditor(viewModel, charge, running == "charging-set-limit")
        }
        TileGrid { cell ->
            ActionTile(
                icon = R.drawable.ic_action_charge_standard,
                title = stringResource(R.string.charging_to_standard),
                state = null,
                onClick = { viewModel.send("charge-standard-range") },
                modifier = cell,
                busy = running == "charge-standard-range",
            )
            ActionTile(
                icon = R.drawable.ic_action_charge_max,
                title = stringResource(R.string.charging_to_max),
                state = null,
                onClick = { viewModel.send("charge-max-range") },
                modifier = cell,
                busy = running == "charge-max-range",
            )
        }

        SectionHeader(stringResource(R.string.charging_amps))
        SectionCard { AmpsEditor(viewModel, charge, running == "charging-set-amps") }

        SectionHeader(stringResource(R.string.charging_port))
        TileGrid { cell ->
            ChargePortTile(viewModel, snapshot, running, cell)
        }

        SectionHeader(stringResource(R.string.charging_scheduled))
        SectionCard {
            val mode = charge?.takeIf { it.hasScheduledChargingMode() }?.scheduledChargingMode
            val modeText = when (mode) {
                Vehicle.ChargeState.ScheduledChargingMode.ScheduledChargingModeStartAt ->
                    stringResource(R.string.charging_mode_start_at, minutesToClock(charge.scheduledChargingStartTimeMinutes))
                Vehicle.ChargeState.ScheduledChargingMode.ScheduledChargingModeDepartBy ->
                    stringResource(R.string.charging_mode_depart_by, minutesToClock(charge.scheduledDepartureTimeMinutes))
                Vehicle.ChargeState.ScheduledChargingMode.ScheduledChargingModeOff -> stringResource(R.string.charging_mode_off)
                else -> stringResource(R.string.value_unknown)
            }
            InfoRow(stringResource(R.string.charging_mode), modeText)
            ActionRow(
                title = stringResource(R.string.charging_schedule_start),
                icon = Icons.Outlined.Schedule,
                onClick = { navigator.to(CommandRoute("charging-schedule")) },
            )
            ActionRow(
                title = stringResource(R.string.charging_schedule_off),
                icon = Icons.Outlined.TimerOff,
                busy = running == "charging-schedule-cancel",
                onClick = { viewModel.send("charging-schedule-cancel") },
            )
            ActionRow(
                title = stringResource(R.string.charging_departure),
                description = "Charge and warm up the cabin to be ready at a time",
                icon = Icons.Outlined.Event,
                onClick = { navigator.to(CommandRoute("scheduled-departure")) },
            )
            ActionRow(
                title = stringResource(R.string.charging_departure_off),
                icon = Icons.Outlined.EventBusy,
                busy = running == "scheduled-departure-clear",
                onClick = { viewModel.send("scheduled-departure-clear") },
            )
        }

        SectionHeader(stringResource(R.string.charging_schedules))
        SectionCard {
            // Actions first: adding or removing schedules changes only the list below them.
            ActionRow(
                title = stringResource(R.string.charging_add_schedule),
                icon = Icons.Outlined.Add,
                onClick = { navigator.to(CommandRoute("charging-schedule-add")) },
            )
            val schedules = snapshot.chargeSchedules?.chargeSchedulesList.orEmpty()
            if (schedules.isEmpty()) {
                Text(
                    stringResource(R.string.charging_no_schedules),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            schedules.forEach { schedule ->
                val start = if (schedule.startEnabled) minutesToClock(schedule.startTime) else "…"
                val end = if (schedule.endEnabled) minutesToClock(schedule.endTime) else "…"
                ActionRow(
                    title = "$start–$end · ${daysText(schedule.daysOfWeek)}",
                    description = listOfNotNull(
                        schedule.name.takeIf { it.isNotBlank() },
                        if (schedule.enabled) "Enabled" else "Disabled",
                        "once".takeIf { schedule.oneTime },
                    ).joinToString(" · "),
                    icon = Icons.Outlined.Delete,
                    trailing = stringResource(R.string.action_remove),
                    onClick = {
                        viewModel.send("charging-schedule-remove", mapOf("TYPE" to "id", "ID" to java.lang.Long.toUnsignedString(schedule.id)))
                    },
                )
            }
        }

        SectionHeader(stringResource(R.string.charging_power_settings))
        val lastSet by viewModel.lastSet.collectAsState()
        TileGrid { cell ->
            SettingTile(
                name = stringResource(R.string.charging_low_power),
                on = lastSet["low-power-mode"],
                onIcon = R.drawable.ic_action_leaf,
                offIcon = R.drawable.ic_action_leaf_off,
                onSet = { on -> viewModel.send("low-power-mode", mapOf("STATE" to onOff(on))) },
                modifier = cell,
                busy = running == "low-power-mode",
            )
            SettingTile(
                name = stringResource(R.string.charging_accessory_power),
                on = lastSet["keep-accessory-power"],
                onIcon = R.drawable.ic_action_outlet,
                offIcon = R.drawable.ic_action_outlet_off,
                onSet = { on -> viewModel.send("keep-accessory-power", mapOf("STATE" to onOff(on))) },
                modifier = cell,
                busy = running == "keep-accessory-power",
            )
        }
        Text(
            stringResource(R.string.setting_last_set_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Battery level and range as large numbers, then what the charger is doing in words. */
@Composable
private fun BatteryCard(charge: Vehicle.ChargeState?, units: Units) {
    val unknown = stringResource(R.string.value_unknown)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The rows below bring their own side padding.
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    ReadingText(percentReading(charge?.takeIf { it.hasBatteryLevel() }?.batteryLevel))
                    Text(stringResource(R.string.home_battery), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    ReadingText(units.distanceReading(charge?.takeIf { it.hasBatteryRange() }?.batteryRange))
                    Text(stringResource(R.string.home_range), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // The same rows whether or not the car is charging, so the controls below never move.
            val charging = charge != null && charge.chargingState.typeCase == Vehicle.ChargeState.ChargingState.TypeCase.CHARGING
            val minutes = charge?.takeIf { charging }?.let {
                it.takeIf { c -> c.hasMinutesToChargeLimit() }?.minutesToChargeLimit ?: it.takeIf { c -> c.hasMinutesToFullCharge() }?.minutesToFullCharge
            }?.takeIf { it > 0 }
            InfoRow(stringResource(R.string.charging_status), chargingStateText(charge) ?: unknown)
            InfoRow(
                stringResource(R.string.charging_power),
                if (charging) {
                    stringResource(R.string.charging_power_now, charge.chargerPower.toString(), charge.chargerVoltage.toString(), charge.chargerActualCurrent.toString())
                } else {
                    unknown
                },
            )
            InfoRow(stringResource(R.string.charging_until_limit), minutes?.let { durationText(it) } ?: unknown)
            InfoRow(
                stringResource(R.string.charging_energy_added),
                charge?.takeIf { it.hasChargeEnergyAdded() && it.chargeEnergyAdded > 0f }?.let { "${oneDecimal(it.chargeEnergyAdded)} kWh" } ?: unknown,
            )
        }
    }
}

@Composable
private fun chargingStateText(charge: Vehicle.ChargeState?): String? {
    val case = charge?.takeIf { it.hasChargingState() }?.chargingState?.typeCase ?: return null
    return when (case) {
        Vehicle.ChargeState.ChargingState.TypeCase.DISCONNECTED -> stringResource(R.string.charging_state_disconnected)
        Vehicle.ChargeState.ChargingState.TypeCase.NOPOWER -> stringResource(R.string.charging_state_no_power)
        Vehicle.ChargeState.ChargingState.TypeCase.STARTING -> stringResource(R.string.charging_state_starting)
        Vehicle.ChargeState.ChargingState.TypeCase.CHARGING -> stringResource(R.string.charging_state_charging)
        Vehicle.ChargeState.ChargingState.TypeCase.COMPLETE -> stringResource(R.string.charging_state_complete)
        Vehicle.ChargeState.ChargingState.TypeCase.STOPPED -> stringResource(R.string.charging_state_stopped)
        Vehicle.ChargeState.ChargingState.TypeCase.CALIBRATING -> stringResource(R.string.charging_state_calibrating)
        else -> null
    }
}

/** The charge limit, sent when the slider is released; returns to the car's value if refused. */
@Composable
private fun LimitEditor(viewModel: MainViewModel, charge: Vehicle.ChargeState?, busy: Boolean) {
    val min = charge?.takeIf { it.hasChargeLimitSocMin() }?.chargeLimitSocMin?.takeIf { it > 0 } ?: 50
    val max = charge?.takeIf { it.hasChargeLimitSocMax() }?.chargeLimitSocMax?.takeIf { it > min } ?: 100
    val carLimit = charge?.takeIf { it.hasChargeLimitSoc() }?.chargeLimitSoc
    var failures by remember { mutableIntStateOf(0) }
    var limit by remember(carLimit, failures) { mutableIntStateOf((carLimit ?: 80).coerceIn(min, max)) }
    SliderControl(
        title = stringResource(R.string.charging_limit),
        valueText = "$limit %",
        value = limit.toFloat(),
        onValueChange = { limit = it.roundToInt() },
        onCommit = {
            if (limit != carLimit) {
                viewModel.send("charging-set-limit", mapOf("PERCENT" to limit.toString())) { if (it is CommandOutcome.Failure) failures++ }
            }
        },
        valueRange = min.toFloat()..max.toFloat(),
        step = 1f,
        decreaseLabel = stringResource(R.string.charging_decrease_limit),
        increaseLabel = stringResource(R.string.charging_increase_limit),
        busy = busy,
    )
}

/** Charging current in amps, up to what the charger allows; sent like the limit. */
@Composable
private fun AmpsEditor(viewModel: MainViewModel, charge: Vehicle.ChargeState?, busy: Boolean) {
    val max = charge?.takeIf { it.hasChargeCurrentRequestMax() }?.chargeCurrentRequestMax?.takeIf { it > 1 } ?: 48
    val carAmps = charge?.takeIf { it.hasChargingAmps() }?.chargingAmps?.takeIf { it > 0 }
        ?: charge?.takeIf { it.hasChargeCurrentRequest() }?.chargeCurrentRequest?.takeIf { it > 0 }
    var failures by remember { mutableIntStateOf(0) }
    var amps by remember(carAmps, failures) { mutableIntStateOf((carAmps ?: 16).coerceIn(1, max)) }
    SliderControl(
        title = stringResource(R.string.charging_amps),
        valueText = "$amps A",
        value = amps.toFloat(),
        onValueChange = { amps = it.roundToInt() },
        onCommit = {
            if (amps != carAmps) {
                viewModel.send("charging-set-amps", mapOf("AMPS" to amps.toString())) { if (it is CommandOutcome.Failure) failures++ }
            }
        },
        valueRange = 1f..max.toFloat(),
        step = 1f,
        decreaseLabel = stringResource(R.string.charging_decrease_amps),
        increaseLabel = stringResource(R.string.charging_increase_amps),
        busy = busy,
    )
}
