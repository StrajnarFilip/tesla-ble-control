package io.github.teslablecontrol.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tesla.generated.carserver.vehicle.Vehicle
import com.tesla.generated.vcsec.Vcsec
import io.github.teslablecontrol.R
import io.github.teslablecontrol.data.UnitSystem
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.ui.BackScaffold
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.UiMessage
import io.github.teslablecontrol.ui.components.ActionRow
import io.github.teslablecontrol.ui.components.InfoRow
import io.github.teslablecontrol.ui.components.SectionCard
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.format.Units
import io.github.teslablecontrol.ui.format.distanceFromMiles
import io.github.teslablecontrol.ui.format.oneDecimal
import io.github.teslablecontrol.ui.format.pressureFromBar
import io.github.teslablecontrol.ui.format.relativeTime
import io.github.teslablecontrol.ui.format.speedFromMph
import io.github.teslablecontrol.ui.format.temperature
import java.util.Locale

/** Everything the car reports, in readable groups, each with when it was read. */
@Composable
fun DetailsScreen(viewModel: MainViewModel, navigator: Navigator) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.controllerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val units = Units.resolve(settings?.units ?: UnitSystem.AUTO)
    val snapshot = state.snapshot
    val reading = state.activity?.commandId == "refresh"
    val unknown = stringResource(R.string.value_unknown)

    LaunchedEffect(Unit) { viewModel.refreshBody() }

    BackScaffold(stringResource(R.string.details_title), navigator) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.refresh(StateCategory.entries, wake = true) },
                    enabled = !reading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.details_load_all)) }
                Text(
                    stringResource(R.string.commands_wakes_car),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Section(stringResource(R.string.details_body), snapshot.bodyUpdatedAtMillis) {
                    val body = snapshot.body
                    InfoRow(stringResource(R.string.controls_locks), yesNo(snapshot.isLocked))
                    InfoRow(stringResource(R.string.status_asleep), yesNo(snapshot.isAsleep))
                    InfoRow("Someone in the car", yesNo(snapshot.isUserPresent))
                    body?.closureStatuses?.let { c ->
                        InfoRow("Driver door", closureText(c.frontDriverDoor))
                        InfoRow("Passenger door", closureText(c.frontPassengerDoor))
                        InfoRow("Rear left door", closureText(c.rearDriverDoor))
                        InfoRow("Rear right door", closureText(c.rearPassengerDoor))
                        InfoRow("Front trunk", closureText(c.frontTrunk))
                        InfoRow("Rear trunk", closureText(c.rearTrunk))
                        InfoRow(stringResource(R.string.charging_port), closureText(c.chargePort))
                        if (c.tonneau != Vcsec.ClosureState_E.CLOSURESTATE_UNKNOWN) InfoRow("Tonneau", closureText(c.tonneau))
                    }
                    snapshot.closures?.let { c ->
                        InfoRow("Driver window", openText(c.takeIf { it.hasWindowOpenDriverFront() }?.windowOpenDriverFront))
                        InfoRow("Passenger window", openText(c.takeIf { it.hasWindowOpenPassengerFront() }?.windowOpenPassengerFront))
                        InfoRow("Rear left window", openText(c.takeIf { it.hasWindowOpenDriverRear() }?.windowOpenDriverRear))
                        InfoRow("Rear right window", openText(c.takeIf { it.hasWindowOpenPassengerRear() }?.windowOpenPassengerRear))
                        InfoRow(stringResource(R.string.security_sentry), yesNo(snapshot.isSentryOn))
                        InfoRow(stringResource(R.string.security_valet), yesNo(snapshot.isValetOn))
                    }
                }

                Section(stringResource(R.string.details_tires), snapshot.categories[StateCategory.TIRE_PRESSURE]?.atMillis) {
                    val t = snapshot.tires
                    fun warn(hard: Boolean, soft: Boolean) = when {
                        hard -> " · check now"
                        soft -> " · low"
                        else -> ""
                    }
                    if (t == null) {
                        InfoRow(stringResource(R.string.details_tires), unknown)
                    } else {
                        InfoRow("Front left", units.pressureFromBar(t.takeIf { it.hasTpmsPressureFl() }?.tpmsPressureFl) + warn(t.tpmsHardWarningFl, t.tpmsSoftWarningFl))
                        InfoRow("Front right", units.pressureFromBar(t.takeIf { it.hasTpmsPressureFr() }?.tpmsPressureFr) + warn(t.tpmsHardWarningFr, t.tpmsSoftWarningFr))
                        InfoRow("Rear left", units.pressureFromBar(t.takeIf { it.hasTpmsPressureRl() }?.tpmsPressureRl) + warn(t.tpmsHardWarningRl, t.tpmsSoftWarningRl))
                        InfoRow("Rear right", units.pressureFromBar(t.takeIf { it.hasTpmsPressureRr() }?.tpmsPressureRr) + warn(t.tpmsHardWarningRr, t.tpmsSoftWarningRr))
                        if (t.hasTpmsRcpFrontValue()) InfoRow("Recommended front", units.pressureFromBar(t.tpmsRcpFrontValue))
                        if (t.hasTpmsRcpRearValue()) InfoRow("Recommended rear", units.pressureFromBar(t.tpmsRcpRearValue))
                    }
                }

                Section(stringResource(R.string.details_location), snapshot.categories[StateCategory.LOCATION]?.atMillis) {
                    val l = snapshot.location
                    if (l == null || !l.hasLatitude()) {
                        InfoRow(stringResource(R.string.details_location), unknown)
                    } else {
                        val coordinates = String.format(Locale.ROOT, "%.5f, %.5f", l.latitude, l.longitude)
                        InfoRow("Coordinates", coordinates)
                        if (l.hasHeading()) InfoRow("Heading", "${l.heading}°")
                        if (l.hasLocationName() && l.locationName.isNotBlank()) InfoRow("Place", l.locationName)
                        ActionRow(
                            title = stringResource(R.string.details_copy_coordinates),
                            icon = Icons.Outlined.ContentCopy,
                            onClick = {
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Coordinates", coordinates))
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) viewModel.emit(UiMessage(resources.getString(R.string.copied)))
                            },
                        )
                    }
                }

                Section(stringResource(R.string.details_drive), snapshot.categories[StateCategory.DRIVE]?.atMillis) {
                    val d = snapshot.drive
                    if (d == null) {
                        InfoRow(stringResource(R.string.details_drive), unknown)
                    } else {
                        InfoRow("Gear", shiftText(d))
                        if (d.hasSpeedFloat()) InfoRow("Speed", units.speedFromMph(d.speedFloat.toDouble()))
                        if (d.hasOdometerInHundredthsOfAMile()) InfoRow("Odometer", units.distanceFromMiles(d.odometerInHundredthsOfAMile / 100f))
                        if (d.hasPower()) InfoRow("Power", "${d.power} kW")
                        if (d.hasActiveRouteDestination() && d.activeRouteDestination.isNotBlank()) {
                            InfoRow("Navigating to", d.activeRouteDestination)
                            if (d.hasActiveRouteMinutesToArrival()) InfoRow("Arrives in", "${d.activeRouteMinutesToArrival.toInt()} min")
                            if (d.hasActiveRouteMilesToArrival()) InfoRow("Distance left", units.distanceFromMiles(d.activeRouteMilesToArrival))
                        }
                    }
                }

                Section(stringResource(R.string.nav_charging), snapshot.categories[StateCategory.CHARGE]?.atMillis) {
                    val c = snapshot.charge
                    if (c == null) {
                        InfoRow(stringResource(R.string.nav_charging), unknown)
                    } else {
                        InfoRow(stringResource(R.string.home_battery), snapshot.batteryLevel?.let { "$it %" } ?: unknown)
                        if (c.hasUsableBatteryLevel()) InfoRow("Usable battery", "${c.usableBatteryLevel} %")
                        InfoRow(stringResource(R.string.home_range), units.distanceFromMiles(snapshot.rangeMiles))
                        if (c.hasEstBatteryRange()) InfoRow("Estimated range", units.distanceFromMiles(c.estBatteryRange))
                        InfoRow(stringResource(R.string.charging_limit), snapshot.chargeLimit?.let { "$it %" } ?: unknown)
                        if (c.hasChargeEnergyAdded()) InfoRow(stringResource(R.string.charging_energy_added), "${oneDecimal(c.chargeEnergyAdded)} kWh")
                        if (c.hasChargerPhases()) InfoRow("Charger phases", c.chargerPhases.toString())
                        if (c.hasFastChargerPresent()) InfoRow("Fast charger", yesNo(c.fastChargerPresent))
                    }
                }

                Section(stringResource(R.string.nav_climate), snapshot.categories[StateCategory.CLIMATE]?.atMillis) {
                    val c = snapshot.climate
                    if (c == null) {
                        InfoRow(stringResource(R.string.nav_climate), unknown)
                    } else {
                        InfoRow(stringResource(R.string.home_inside), units.temperature(snapshot.insideTempCelsius))
                        InfoRow(stringResource(R.string.home_outside), units.temperature(snapshot.outsideTempCelsius))
                        InfoRow(stringResource(R.string.climate_power), yesNo(snapshot.isClimateOn))
                        if (c.hasFanStatus()) InfoRow("Fan speed", c.fanStatus.toString())
                        if (c.hasIsFrontDefrosterOn()) InfoRow("Front defroster", yesNo(c.isFrontDefrosterOn))
                        if (c.hasIsRearDefrosterOn()) InfoRow("Rear defroster", yesNo(c.isRearDefrosterOn))
                        if (c.hasIsPreconditioning()) InfoRow("Preconditioning", yesNo(c.isPreconditioning))
                        if (c.hasBatteryHeater()) InfoRow("Battery heater", yesNo(c.batteryHeater))
                        if (c.hasSideMirrorHeaters()) InfoRow("Mirror heaters", yesNo(c.sideMirrorHeaters))
                        if (c.hasWiperBladeHeater()) InfoRow("Wiper heater", yesNo(c.wiperBladeHeater))
                    }
                }

                Section(stringResource(R.string.controls_media), snapshot.categories[StateCategory.MEDIA]?.atMillis) {
                    val m = snapshot.media
                    val detail = snapshot.mediaDetail
                    if (m == null) {
                        InfoRow(stringResource(R.string.controls_media), unknown)
                    } else {
                        if (m.hasNowPlayingTitle() && m.nowPlayingTitle.isNotBlank()) InfoRow("Title", m.nowPlayingTitle)
                        if (m.hasNowPlayingArtist() && m.nowPlayingArtist.isNotBlank()) InfoRow("Artist", m.nowPlayingArtist)
                        detail?.takeIf { it.hasNowPlayingAlbum() && it.nowPlayingAlbum.isNotBlank() }?.let { InfoRow("Album", it.nowPlayingAlbum) }
                        detail?.takeIf { it.hasNowPlayingStation() && it.nowPlayingStation.isNotBlank() }?.let { InfoRow("Station", it.nowPlayingStation) }
                        if (m.hasAudioVolume()) InfoRow(stringResource(R.string.controls_volume), oneDecimal(m.audioVolume))
                        if (m.hasRemoteControlEnabled()) InfoRow("Remote control allowed", yesNo(m.remoteControlEnabled))
                    }
                }

                Section(stringResource(R.string.details_software), snapshot.categories[StateCategory.SOFTWARE_UPDATE]?.atMillis) {
                    val s = snapshot.softwareUpdate
                    if (s == null) {
                        InfoRow(stringResource(R.string.details_software), unknown)
                    } else {
                        if (s.hasVersion() && s.version.isNotBlank()) InfoRow("Update version", s.version)
                        if (s.hasStatus()) InfoRow(stringResource(R.string.controls_update_status), s.status.typeCase.name.lowercase().replaceFirstChar { it.uppercase() })
                        if (s.hasExpectedDurationSec()) InfoRow("Installation time", "${s.expectedDurationSec / 60} min")
                    }
                }

                Section(stringResource(R.string.security_parental), snapshot.categories[StateCategory.PARENTAL_CONTROLS]?.atMillis) {
                    val p = snapshot.parentalControls
                    if (p == null) {
                        InfoRow(stringResource(R.string.security_parental), unknown)
                    } else {
                        InfoRow("Active", yesNo(p.takeIf { it.hasParentalControlsActive() }?.parentalControlsActive))
                        InfoRow("PIN set", yesNo(p.takeIf { it.hasParentalControlsPinSet() }?.parentalControlsPinSet))
                    }
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, updatedAtMillis: Long?, content: @Composable () -> Unit) {
    SectionHeader(title)
    SectionCard {
        content()
        Text(
            relativeTime(updatedAtMillis)?.let { stringResource(R.string.details_updated, it) } ?: stringResource(R.string.details_never),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun yesNo(value: Boolean?): String = when (value) {
    true -> "Yes"
    false -> "No"
    null -> stringResource(R.string.value_unknown)
}

@Composable
private fun openText(open: Boolean?): String = when (open) {
    true -> stringResource(R.string.state_open)
    false -> stringResource(R.string.state_closed)
    null -> stringResource(R.string.value_unknown)
}

@Composable
private fun closureText(state: Vcsec.ClosureState_E): String = when (state) {
    Vcsec.ClosureState_E.CLOSURESTATE_CLOSED -> stringResource(R.string.state_closed)
    Vcsec.ClosureState_E.CLOSURESTATE_OPEN -> stringResource(R.string.state_open)
    Vcsec.ClosureState_E.CLOSURESTATE_AJAR -> "Ajar"
    Vcsec.ClosureState_E.CLOSURESTATE_OPENING -> "Opening"
    Vcsec.ClosureState_E.CLOSURESTATE_CLOSING -> "Closing"
    Vcsec.ClosureState_E.CLOSURESTATE_FAILED_UNLATCH -> "Didn't unlatch"
    else -> stringResource(R.string.value_unknown)
}

private fun shiftText(drive: Vehicle.DriveState): String = when (drive.takeIf { it.hasShiftState() }?.shiftState?.typeCase) {
    Vehicle.ShiftState.TypeCase.P -> "Park"
    Vehicle.ShiftState.TypeCase.R -> "Reverse"
    Vehicle.ShiftState.TypeCase.N -> "Neutral"
    Vehicle.ShiftState.TypeCase.D -> "Drive"
    else -> "—"
}
