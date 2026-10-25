package io.github.teslablecontrol.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.AppContainer
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.VehicleSnapshot
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.ui.DetailsRoute
import io.github.teslablecontrol.ui.KeysRoute
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.components.ButtonGrid
import io.github.teslablecontrol.ui.components.ButtonWithNote
import io.github.teslablecontrol.ui.components.ChargePortRow
import io.github.teslablecontrol.ui.components.ControlGrid
import io.github.teslablecontrol.ui.components.FlashButton
import io.github.teslablecontrol.ui.components.FrunkRow
import io.github.teslablecontrol.ui.components.HonkButton
import io.github.teslablecontrol.ui.components.LockRow
import io.github.teslablecontrol.ui.components.MainScreenFrame
import io.github.teslablecontrol.ui.components.Notice
import io.github.teslablecontrol.ui.components.ReadingText
import io.github.teslablecontrol.ui.components.SectionGroup
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.StableText
import io.github.teslablecontrol.ui.components.ToggleRow
import io.github.teslablecontrol.ui.components.TrunkRow
import io.github.teslablecontrol.ui.components.WindowsRow
import io.github.teslablecontrol.ui.format.Reading
import io.github.teslablecontrol.ui.format.Units
import io.github.teslablecontrol.ui.format.distanceReading
import io.github.teslablecontrol.ui.format.percentReading
import io.github.teslablecontrol.ui.format.temperature
import io.github.teslablecontrol.ui.format.temperatureReading

private val HOME_CATEGORIES = listOf(StateCategory.CHARGE, StateCategory.CLIMATE, StateCategory.CLOSURES)

@Composable
fun HomeScreen(viewModel: MainViewModel, navigator: Navigator) {
    val state by viewModel.controllerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val running by viewModel.running.collectAsState()
    val units = Units.resolve(settings?.units ?: io.github.teslablecontrol.data.UnitSystem.AUTO)
    val snapshot = state.snapshot
    val isDemo = profile?.vin == AppContainer.DEMO_PROFILE.vin

    MainScreenFrame(viewModel, navigator, stringResource(R.string.nav_home), HOME_CATEGORIES) {
        if (isDemo) {
            Notice(title = stringResource(R.string.settings_demo), body = stringResource(R.string.status_demo), icon = Icons.Outlined.Info)
        }

        Summary(
            battery = percentReading(snapshot.batteryLevel),
            range = units.distanceReading(snapshot.rangeMiles),
            inside = units.temperatureReading(snapshot.insideTempCelsius),
            charging = snapshot.isCharging,
            asleep = snapshot.isAsleep == true && snapshot.charge == null,
        )

        SectionHeader(stringResource(R.string.home_quick_actions))
        SectionGroup {
            LockRow(viewModel, snapshot, running)
            // Front of the car on the left, rear on the right.
            ControlGrid {
                FrunkRow(viewModel, snapshot, running)
                TrunkRow(viewModel, snapshot, running)
                ChargePortRow(viewModel, snapshot, running)
                WindowsRow(viewModel, snapshot, running)
            }
            ClimateRow(viewModel, snapshot, units, running)
        }
        ButtonGrid {
            FlashButton(viewModel, running)
            HonkButton(viewModel, running)
        }

        ButtonWithNote(
            label = stringResource(R.string.home_details),
            onClick = { navigator.to(DetailsRoute) },
            note = stringResource(R.string.home_details_hint),
            modifier = Modifier.padding(top = 12.dp),
        )
        ButtonWithNote(
            label = stringResource(R.string.security_keys),
            onClick = { navigator.to(KeysRoute) },
            note = stringResource(R.string.security_keys_desc),
        )
    }
}

/** Battery, range and cabin temperature as large numbers, each with its label, then one status line. */
@Composable
private fun Summary(battery: Reading, range: Reading, inside: Reading, charging: Boolean?, asleep: Boolean) {
    val unknown = stringResource(R.string.value_unknown)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Side by side normally; stacked when large text would squeeze the numbers.
        if (LocalDensity.current.fontScale < 1.3f) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(stringResource(R.string.home_battery), battery, Modifier.weight(1f))
                Metric(stringResource(R.string.home_range), range, Modifier.weight(1f))
                Metric(stringResource(R.string.home_inside), inside, Modifier.weight(1f))
            }
        } else {
            Metric(stringResource(R.string.home_battery), battery)
            Metric(stringResource(R.string.home_range), range)
            Metric(stringResource(R.string.home_inside), inside)
        }
        // One line that is always there, so the screen doesn't move when the car wakes or sleeps.
        val chargingText = stringResource(R.string.home_charging_now)
        val notChargingText = stringResource(R.string.home_not_charging)
        val asleepText = stringResource(R.string.home_asleep_hint)
        StableText(
            when {
                asleep -> asleepText
                charging == true -> chargingText
                charging == false -> notChargingText
                else -> unknown
            },
            listOf(chargingText, notChargingText, asleepText),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Climate on or off, with the set temperature. */
@Composable
private fun ClimateRow(viewModel: MainViewModel, snapshot: VehicleSnapshot, units: Units, running: String?) {
    val on = snapshot.isClimateOn
    val temperature = snapshot.driverTempSetting?.let { units.temperature(it) }
    val onText = stringResource(R.string.word_on).let { w -> temperature?.let { "$w · $it" } ?: w }
    val offText = stringResource(R.string.word_off).let { w -> temperature?.let { "$w · $it" } ?: w }
    val unknownText = stringResource(R.string.state_unknown)
    ToggleRow(
        title = stringResource(R.string.climate_power),
        checked = on,
        onCheckedChange = { turnOn -> viewModel.send(if (turnOn) "climate-on" else "climate-off") },
        description = when (on) {
            true -> onText
            false -> offText
            null -> unknownText
        },
        descriptions = listOf(onText, offText, unknownText),
        busy = running == "climate-on" || running == "climate-off",
    )
}

@Composable
private fun Metric(label: String, value: Reading, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        ReadingText(value)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
