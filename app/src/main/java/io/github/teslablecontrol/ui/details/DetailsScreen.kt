package io.github.teslablecontrol.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.teslablecontrol.ui.components.ButtonWithNote
import io.github.teslablecontrol.ui.components.InfoRow
import io.github.teslablecontrol.ui.components.SectionGroup
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.TeslaButton
import io.github.teslablecontrol.ui.components.inGroup
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
                TeslaButton(
                    label = stringResource(R.string.details_load_all),
                    onClick = { viewModel.refresh(StateCategory.entries, wake = true) },
                    busy = reading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.commands_wakes_car),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Section(stringResource(R.string.details_body), snapshot.bodyUpdatedAtMillis) {
                    val body = snapshot.body
                    InfoRow(stringResource(R.string.controls_locks), yesNo(snapshot.isLocked))
                    InfoRow(stringResource(R.string.status_asleep), yesNo(snapshot.isAsleep))
                    InfoRow(stringResource(R.string.label_someone_in_the_car), yesNo(snapshot.isUserPresent))
                    body?.closureStatuses?.let { c ->
                        InfoRow(stringResource(R.string.label_driver_door), closureText(c.frontDriverDoor))
                        InfoRow(stringResource(R.string.label_passenger_door), closureText(c.frontPassengerDoor))
                        InfoRow(stringResource(R.string.label_rear_left_door), closureText(c.rearDriverDoor))
                        InfoRow(stringResource(R.string.label_rear_right_door), closureText(c.rearPassengerDoor))
                        InfoRow(stringResource(R.string.label_front_trunk), closureText(c.frontTrunk))
                        InfoRow(stringResource(R.string.closure_trunk), closureText(c.rearTrunk))
                        InfoRow(stringResource(R.string.charging_port), closureText(c.chargePort))
                        if (c.tonneau != Vcsec.ClosureState_E.CLOSURESTATE_UNKNOWN) InfoRow(stringResource(R.string.label_tonneau), closureText(c.tonneau))
                    }
                    snapshot.closures?.let { c ->
                        InfoRow(stringResource(R.string.label_driver_window), openText(c.takeIf { it.hasWindowOpenDriverFront() }?.windowOpenDriverFront))
                        InfoRow(stringResource(R.string.label_passenger_window), openText(c.takeIf { it.hasWindowOpenPassengerFront() }?.windowOpenPassengerFront))
                        InfoRow(stringResource(R.string.label_rear_left_window), openText(c.takeIf { it.hasWindowOpenDriverRear() }?.windowOpenDriverRear))
                        InfoRow(stringResource(R.string.label_rear_right_window), openText(c.takeIf { it.hasWindowOpenPassengerRear() }?.windowOpenPassengerRear))
                        InfoRow(stringResource(R.string.security_sentry), yesNo(snapshot.isSentryOn))
                        InfoRow(stringResource(R.string.security_valet), yesNo(snapshot.isValetOn))
                    }
                }

                Section(stringResource(R.string.details_tires), snapshot.categories[StateCategory.TIRE_PRESSURE]?.atMillis) {
                    val t = snapshot.tires
                    fun warn(hard: Boolean, soft: Boolean) = when {
                        hard -> " · " + resources.getString(R.string.label_check_now)
                        soft -> " · " + resources.getString(R.string.level_low)
                        else -> ""
                    }
                    if (t == null) {
                        InfoRow(stringResource(R.string.details_tires), unknown)
                    } else {
                        InfoRow(stringResource(R.string.label_front_left), units.pressureFromBar(t.takeIf { it.hasTpmsPressureFl() }?.tpmsPressureFl) + warn(t.tpmsHardWarningFl, t.tpmsSoftWarningFl))
                        InfoRow(stringResource(R.string.label_front_right), units.pressureFromBar(t.takeIf { it.hasTpmsPressureFr() }?.tpmsPressureFr) + warn(t.tpmsHardWarningFr, t.tpmsSoftWarningFr))
                        InfoRow(stringResource(R.string.label_rear_left), units.pressureFromBar(t.takeIf { it.hasTpmsPressureRl() }?.tpmsPressureRl) + warn(t.tpmsHardWarningRl, t.tpmsSoftWarningRl))
                        InfoRow(stringResource(R.string.label_rear_right), units.pressureFromBar(t.takeIf { it.hasTpmsPressureRr() }?.tpmsPressureRr) + warn(t.tpmsHardWarningRr, t.tpmsSoftWarningRr))
                        if (t.hasTpmsRcpFrontValue()) InfoRow(stringResource(R.string.label_recommended_front), units.pressureFromBar(t.tpmsRcpFrontValue))
                        if (t.hasTpmsRcpRearValue()) InfoRow(stringResource(R.string.label_recommended_rear), units.pressureFromBar(t.tpmsRcpRearValue))
                    }
                }

                Section(stringResource(R.string.details_location), snapshot.categories[StateCategory.LOCATION]?.atMillis) {
                    val l = snapshot.location
                    if (l == null || !l.hasLatitude()) {
                        InfoRow(stringResource(R.string.details_location), unknown)
                    } else {
                        val coordinates = String.format(Locale.ROOT, "%.5f, %.5f", l.latitude, l.longitude)
                        InfoRow(stringResource(R.string.label_coordinates), coordinates)
                        if (l.hasHeading()) InfoRow(stringResource(R.string.label_heading), "${l.heading}°")
                        if (l.hasLocationName() && l.locationName.isNotBlank()) InfoRow(stringResource(R.string.label_place), l.locationName)
                        ButtonWithNote(
                            label = stringResource(R.string.details_copy_coordinates),
                            onClick = {
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.label_coordinates), coordinates))
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) viewModel.emit(UiMessage(resources.getString(R.string.copied)))
                            },
                            modifier = Modifier.inGroup(),
                        )
                    }
                }

                Section(stringResource(R.string.details_drive), snapshot.categories[StateCategory.DRIVE]?.atMillis) {
                    val d = snapshot.drive
                    if (d == null) {
                        InfoRow(stringResource(R.string.details_drive), unknown)
                    } else {
                        InfoRow(stringResource(R.string.label_gear), shiftText(d))
                        if (d.hasSpeedFloat()) InfoRow(stringResource(R.string.label_speed), units.speedFromMph(d.speedFloat.toDouble()))
                        if (d.hasOdometerInHundredthsOfAMile()) InfoRow(stringResource(R.string.label_odometer), units.distanceFromMiles(d.odometerInHundredthsOfAMile / 100f))
                        if (d.hasPower()) InfoRow(stringResource(R.string.label_power), "${d.power} kW")
                        if (d.hasActiveRouteDestination() && d.activeRouteDestination.isNotBlank()) {
                            InfoRow(stringResource(R.string.label_navigating_to), d.activeRouteDestination)
                            if (d.hasActiveRouteMinutesToArrival()) InfoRow(stringResource(R.string.label_arrives_in), "${d.activeRouteMinutesToArrival.toInt()} min")
                            if (d.hasActiveRouteMilesToArrival()) InfoRow(stringResource(R.string.label_distance_left), units.distanceFromMiles(d.activeRouteMilesToArrival))
                        }
                    }
                }

                Section(stringResource(R.string.nav_charging), snapshot.categories[StateCategory.CHARGE]?.atMillis) {
                    val c = snapshot.charge
                    if (c == null) {
                        InfoRow(stringResource(R.string.nav_charging), unknown)
                    } else {
                        InfoRow(stringResource(R.string.home_battery), snapshot.batteryLevel?.let { "$it %" } ?: unknown)
                        if (c.hasUsableBatteryLevel()) InfoRow(stringResource(R.string.label_usable_battery), "${c.usableBatteryLevel} %")
                        InfoRow(stringResource(R.string.home_range), units.distanceFromMiles(snapshot.rangeMiles))
                        if (c.hasEstBatteryRange()) InfoRow(stringResource(R.string.label_estimated_range), units.distanceFromMiles(c.estBatteryRange))
                        InfoRow(stringResource(R.string.charging_limit), snapshot.chargeLimit?.let { "$it %" } ?: unknown)
                        if (c.hasChargeEnergyAdded()) InfoRow(stringResource(R.string.charging_energy_added), "${oneDecimal(c.chargeEnergyAdded)} kWh")
                        if (c.hasChargerPhases()) InfoRow(stringResource(R.string.label_charger_phases), c.chargerPhases.toString())
                        if (c.hasFastChargerPresent()) InfoRow(stringResource(R.string.label_fast_charger), yesNo(c.fastChargerPresent))
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
                        if (c.hasFanStatus()) InfoRow(stringResource(R.string.label_fan_speed), c.fanStatus.toString())
                        if (c.hasIsFrontDefrosterOn()) InfoRow(stringResource(R.string.label_front_defroster), yesNo(c.isFrontDefrosterOn))
                        if (c.hasIsRearDefrosterOn()) InfoRow(stringResource(R.string.label_rear_defroster), yesNo(c.isRearDefrosterOn))
                        if (c.hasIsPreconditioning()) InfoRow(stringResource(R.string.label_preconditioning), yesNo(c.isPreconditioning))
                        if (c.hasBatteryHeater()) InfoRow(stringResource(R.string.label_battery_heater), yesNo(c.batteryHeater))
                        if (c.hasSideMirrorHeaters()) InfoRow(stringResource(R.string.label_mirror_heaters), yesNo(c.sideMirrorHeaters))
                        if (c.hasWiperBladeHeater()) InfoRow(stringResource(R.string.label_wiper_heater), yesNo(c.wiperBladeHeater))
                    }
                }

                Section(stringResource(R.string.controls_media), snapshot.categories[StateCategory.MEDIA]?.atMillis) {
                    val m = snapshot.media
                    val detail = snapshot.mediaDetail
                    if (m == null) {
                        InfoRow(stringResource(R.string.controls_media), unknown)
                    } else {
                        if (m.hasNowPlayingTitle() && m.nowPlayingTitle.isNotBlank()) InfoRow(stringResource(R.string.label_title), m.nowPlayingTitle)
                        if (m.hasNowPlayingArtist() && m.nowPlayingArtist.isNotBlank()) InfoRow(stringResource(R.string.label_artist), m.nowPlayingArtist)
                        detail?.takeIf { it.hasNowPlayingAlbum() && it.nowPlayingAlbum.isNotBlank() }?.let { InfoRow(stringResource(R.string.label_album), it.nowPlayingAlbum) }
                        detail?.takeIf { it.hasNowPlayingStation() && it.nowPlayingStation.isNotBlank() }?.let { InfoRow(stringResource(R.string.label_station), it.nowPlayingStation) }
                        if (m.hasAudioVolume()) InfoRow(stringResource(R.string.controls_volume), oneDecimal(m.audioVolume))
                        if (m.hasRemoteControlEnabled()) InfoRow(stringResource(R.string.label_remote_control_allowed), yesNo(m.remoteControlEnabled))
                    }
                }

                Section(stringResource(R.string.details_software), snapshot.categories[StateCategory.SOFTWARE_UPDATE]?.atMillis) {
                    val s = snapshot.softwareUpdate
                    if (s == null) {
                        InfoRow(stringResource(R.string.details_software), unknown)
                    } else {
                        if (s.hasVersion() && s.version.isNotBlank()) InfoRow(stringResource(R.string.label_update_version), s.version)
                        if (s.hasStatus()) InfoRow(stringResource(R.string.controls_update_status), softwareStatus(s.status.typeCase))
                        if (s.hasExpectedDurationSec()) InfoRow(stringResource(R.string.label_installation_time), "${s.expectedDurationSec / 60} min")
                    }
                }

                Section(stringResource(R.string.security_parental), snapshot.categories[StateCategory.PARENTAL_CONTROLS]?.atMillis) {
                    val p = snapshot.parentalControls
                    if (p == null) {
                        InfoRow(stringResource(R.string.security_parental), unknown)
                    } else {
                        InfoRow(stringResource(R.string.label_active), yesNo(p.takeIf { it.hasParentalControlsActive() }?.parentalControlsActive))
                        InfoRow(stringResource(R.string.label_pin_set), yesNo(p.takeIf { it.hasParentalControlsPinSet() }?.parentalControlsPinSet))
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
    SectionGroup {
        content()
        Text(
            when {
                updatedAtMillis == null -> stringResource(R.string.details_never)
                System.currentTimeMillis() - updatedAtMillis < DateUtils.MINUTE_IN_MILLIS -> stringResource(R.string.details_updated_now)
                else -> stringResource(R.string.details_updated, relativeTime(updatedAtMillis).orEmpty())
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun yesNo(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.label_yes)
    false -> stringResource(R.string.label_no)
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
    Vcsec.ClosureState_E.CLOSURESTATE_AJAR -> stringResource(R.string.label_ajar)
    Vcsec.ClosureState_E.CLOSURESTATE_OPENING -> stringResource(R.string.label_opening)
    Vcsec.ClosureState_E.CLOSURESTATE_CLOSING -> stringResource(R.string.label_closing)
    Vcsec.ClosureState_E.CLOSURESTATE_FAILED_UNLATCH -> stringResource(R.string.label_didn_t_unlatch)
    else -> stringResource(R.string.value_unknown)
}

@Composable
private fun shiftText(drive: Vehicle.DriveState): String = when (drive.takeIf { it.hasShiftState() }?.shiftState?.typeCase) {
    Vehicle.ShiftState.TypeCase.P -> stringResource(R.string.label_park)
    Vehicle.ShiftState.TypeCase.R -> stringResource(R.string.label_reverse)
    Vehicle.ShiftState.TypeCase.N -> stringResource(R.string.label_neutral)
    Vehicle.ShiftState.TypeCase.D -> stringResource(R.string.label_drive)
    else -> "—"
}

@Composable
private fun softwareStatus(status: Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase): String = stringResource(
    when (status) {
        Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase.AVAILABLE -> R.string.controls_update_ready
        Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase.SCHEDULED -> R.string.controls_update_scheduled
        Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase.INSTALLING -> R.string.label_installing
        Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase.DOWNLOADING -> R.string.label_downloading
        Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase.DOWNLOADINGWIFIWAIT -> R.string.controls_update_wifi
        else -> R.string.controls_update_none
    },
)
