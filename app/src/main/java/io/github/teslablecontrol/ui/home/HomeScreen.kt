package io.github.teslablecontrol.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import io.github.teslablecontrol.ui.components.ActionRow
import io.github.teslablecontrol.ui.components.ActionTile
import io.github.teslablecontrol.ui.components.ChargePortTile
import io.github.teslablecontrol.ui.components.FlashTile
import io.github.teslablecontrol.ui.components.FrunkTile
import io.github.teslablecontrol.ui.components.HonkTile
import io.github.teslablecontrol.ui.components.LockTile
import io.github.teslablecontrol.ui.components.MainScreenFrame
import io.github.teslablecontrol.ui.components.NoticeCard
import io.github.teslablecontrol.ui.components.ReadingText
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.StableText
import io.github.teslablecontrol.ui.components.TileGrid
import io.github.teslablecontrol.ui.components.TrunkTile
import io.github.teslablecontrol.ui.components.WindowsTile
import io.github.teslablecontrol.ui.format.Reading
import io.github.teslablecontrol.ui.format.Units
import io.github.teslablecontrol.ui.format.distanceFromMiles
import io.github.teslablecontrol.ui.format.distanceReading
import io.github.teslablecontrol.ui.format.percentReading
import io.github.teslablecontrol.ui.format.temperature
import io.github.teslablecontrol.ui.format.temperatureReading
import io.github.teslablecontrol.ui.theme.NumberStyle

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

    LaunchedEffect(profile?.vin) { viewModel.refresh(HOME_CATEGORIES) }

    MainScreenFrame(viewModel, navigator, stringResource(R.string.nav_home), onRefresh = { viewModel.refresh(HOME_CATEGORIES) }) {
        if (isDemo) {
            NoticeCard(title = stringResource(R.string.settings_demo), body = stringResource(R.string.status_demo), icon = Icons.Outlined.Info)
        }

        SummaryCard(
            battery = percentReading(snapshot.batteryLevel),
            range = units.distanceReading(snapshot.rangeMiles),
            inside = units.temperatureReading(snapshot.insideTempCelsius),
            charging = snapshot.isCharging,
            asleep = snapshot.isAsleep == true && snapshot.charge == null,
        )

        SectionHeader(stringResource(R.string.home_quick_actions))
        TileGrid { cell ->
            LockTile(viewModel, snapshot, running, cell)
            ClimateTile(viewModel, snapshot, units, running, cell)
            FrunkTile(viewModel, snapshot, running, cell)
            TrunkTile(viewModel, snapshot, running, cell)
            ChargePortTile(viewModel, snapshot, running, cell)
            WindowsTile(viewModel, snapshot, running, cell)
            FlashTile(viewModel, running, cell)
            HonkTile(viewModel, running, cell)
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            ActionRow(
                title = stringResource(R.string.home_details),
                description = stringResource(R.string.home_details_hint),
                icon = Icons.Outlined.Info,
                onClick = { navigator.to(DetailsRoute) },
            )
            ActionRow(
                title = stringResource(R.string.security_keys),
                description = stringResource(R.string.security_keys_desc),
                icon = Icons.Outlined.Key,
                onClick = { navigator.to(KeysRoute) },
            )
        }
    }
}

/** Battery, range and cabin temperature as large numbers, each with its label, then one status line. */
@Composable
private fun SummaryCard(battery: Reading, range: Reading, inside: Reading, charging: Boolean?, asleep: Boolean) {
    val unknown = stringResource(R.string.value_unknown)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

/** Climate on or off, with the set temperature. */
@Composable
private fun ClimateTile(viewModel: MainViewModel, snapshot: VehicleSnapshot, units: Units, running: String?, modifier: Modifier) {
    val on = snapshot.isClimateOn
    val temperature = snapshot.driverTempSetting?.let { units.temperature(it) }
    val onText = stringResource(R.string.word_on).let { w -> temperature?.let { "$w · $it" } ?: w }
    val offText = stringResource(R.string.word_off).let { w -> temperature?.let { "$w · $it" } ?: w }
    val unknownText = stringResource(R.string.state_unknown)
    ActionTile(
        icon = R.drawable.ic_action_climate,
        title = stringResource(R.string.climate_power),
        state = when (on) {
            true -> onText
            false -> offText
            null -> unknownText
        },
        onClick = { viewModel.send(if (on == true) "climate-off" else "climate-on") },
        modifier = modifier,
        busy = running == "climate-on" || running == "climate-off",
        highlighted = on == true,
        states = listOf(onText, offText, unknownText),
    )
}

@Composable
private fun Metric(label: String, value: Reading, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        ReadingText(value)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
