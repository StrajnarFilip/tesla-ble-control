package io.github.teslablecontrol.ui.controls

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.tesla.generated.carserver.vehicle.Vehicle
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.data.CarModel
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.ui.CommandRoute
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.components.ButtonGrid
import io.github.teslablecontrol.ui.components.ClosureRow
import io.github.teslablecontrol.ui.components.ControlGrid
import io.github.teslablecontrol.ui.components.FlashButton
import io.github.teslablecontrol.ui.components.FrunkRow
import io.github.teslablecontrol.ui.components.HonkButton
import io.github.teslablecontrol.ui.components.InfoRow
import io.github.teslablecontrol.ui.components.LockRow
import io.github.teslablecontrol.ui.components.MainScreenFrame
import io.github.teslablecontrol.ui.components.SectionGroup
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.SliderControl
import io.github.teslablecontrol.ui.components.StableText
import io.github.teslablecontrol.ui.components.TeslaButton
import io.github.teslablecontrol.ui.components.TeslaIconButton
import io.github.teslablecontrol.ui.components.TrunkRow
import io.github.teslablecontrol.ui.components.WindowsRow
import io.github.teslablecontrol.ui.format.oneDecimal
import java.util.Locale

private val CONTROLS_CATEGORIES = listOf(StateCategory.CLOSURES, StateCategory.LOCATION, StateCategory.MEDIA, StateCategory.SOFTWARE_UPDATE)

@Composable
fun ControlsScreen(viewModel: MainViewModel, navigator: Navigator) {
    val state by viewModel.controllerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val running by viewModel.running.collectAsState()
    val snapshot = state.snapshot
    val showAll = settings?.showAllControls == true
    val model = profile?.model

    fun busy(vararg ids: String) = running in ids

    MainScreenFrame(viewModel, navigator, stringResource(R.string.controls_title), CONTROLS_CATEGORIES) {
        SectionHeader(stringResource(R.string.controls_doors))
        SectionGroup {
            LockRow(viewModel, snapshot, running)
            // Front of the car on the left, rear on the right.
            ControlGrid {
                FrunkRow(viewModel, snapshot, running)
                TrunkRow(viewModel, snapshot, running)
                if (showAll || model == CarModel.CYBERTRUCK) {
                    ClosureRow(
                        title = stringResource(R.string.closure_tonneau),
                        open = snapshot.isTonneauOpen,
                        onSet = { open -> viewModel.send(if (open) "tonneau-open" else "tonneau-close") },
                        busy = busy("tonneau-open", "tonneau-close"),
                    )
                }
            }
        }
        if (showAll || model == CarModel.CYBERTRUCK || model == CarModel.MODEL_X) {
            ButtonGrid {
                if (showAll || model == CarModel.CYBERTRUCK) {
                    TeslaButton(
                        label = stringResource(R.string.controls_tonneau_stop),
                        onClick = { viewModel.send("tonneau-stop") },
                        busy = busy("tonneau-stop"),
                    )
                }
                if (showAll || model == CarModel.MODEL_X) {
                    TeslaButton(
                        label = stringResource(R.string.controls_autosecure),
                        onClick = { viewModel.send("autosecure-modelx") },
                        busy = busy("autosecure-modelx"),
                    )
                }
            }
        }

        SectionHeader(stringResource(R.string.controls_windows))
        val closures = snapshot.closures
        val hasSunroof = closures?.takeIf { it.hasSunRoofState() }?.sunRoofState?.typeCase
            ?.let { it != Vehicle.ClosuresState.SunRoofState.TypeCase.TYPE_NOT_SET } == true
        val showSunroof = showAll || hasSunroof || model == CarModel.MODEL_S || model == CarModel.MODEL_X
        SectionGroup {
            ControlGrid {
                WindowsRow(viewModel, snapshot, running)
                if (showSunroof) {
                    val percent = closures?.takeIf { it.hasSunRoofPercentOpen() }?.sunRoofPercentOpen
                    ClosureRow(
                        title = stringResource(R.string.closure_sunroof),
                        open = percent?.let { it > 0 },
                        onSet = { open -> viewModel.send("sunroof", mapOf("LEVEL" to if (open) "100" else "0")) },
                        busy = busy("sunroof"),
                        // How far, when it's part-way open.
                        note = percent?.takeIf { it in 1..99 }?.let { stringResource(R.string.controls_sunroof_percent, it) },
                        notes = listOf(stringResource(R.string.controls_sunroof_percent, 99)),
                    )
                }
            }
        }
        if (showSunroof) {
            TeslaButton(
                label = stringResource(R.string.controls_sunroof_other),
                onClick = { navigator.to(CommandRoute("sunroof")) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionHeader(stringResource(R.string.controls_alerts))
        val location = snapshot.location
        val homelinkNearby = location?.takeIf { it.hasHomelinkNearby() }?.homelinkNearby == true
        ButtonGrid {
            FlashButton(viewModel, running)
            HonkButton(viewModel, running)
            TeslaButton(
                label = stringResource(R.string.controls_homelink),
                onClick = {
                    val lat = location?.takeIf { it.hasLatitude() }?.latitude
                    val lon = location?.takeIf { it.hasLongitude() }?.longitude
                    if (lat != null && lon != null && (lat != 0f || lon != 0f)) {
                        viewModel.send("homelink", mapOf("LATITUDE" to lat.toString(), "LONGITUDE" to lon.toString()))
                    } else {
                        navigator.to(CommandRoute("homelink"))
                    }
                },
                busy = busy("homelink"),
            )
        }
        val homelinkNearbyText = stringResource(R.string.controls_homelink_nearby)
        val homelinkText = stringResource(R.string.controls_homelink_desc)
        Caption(if (homelinkNearby) homelinkNearbyText else homelinkText, listOf(homelinkNearbyText, homelinkText))

        SectionHeader(stringResource(R.string.controls_media))
        MediaControls(viewModel, snapshot.media, running)

        SectionHeader(stringResource(R.string.controls_drive))
        ButtonGrid {
            TeslaButton(
                label = stringResource(R.string.controls_remote_start),
                onClick = { viewModel.send("drive") },
                busy = busy("drive"),
            )
        }
        Caption(stringResource(R.string.controls_remote_start_desc))

        SectionHeader(stringResource(R.string.controls_software))
        // Two lines kept for the status, so the buttons below stay put as it changes.
        Text(updateText(snapshot.softwareUpdate), style = MaterialTheme.typography.bodyLarge, minLines = 2)
        ButtonGrid {
            TeslaButton(
                label = stringResource(R.string.controls_install_short),
                onClick = { navigator.to(CommandRoute("software-update-start")) },
            )
            TeslaButton(
                label = stringResource(R.string.controls_cancel_short),
                onClick = { viewModel.send("software-update-cancel") },
                busy = busy("software-update-cancel"),
            )
        }

        SectionHeader(stringResource(R.string.controls_vehicle))
        ButtonGrid {
            TeslaButton(
                label = stringResource(R.string.action_wake),
                onClick = { viewModel.send("wake") },
                busy = busy("wake"),
            )
            TeslaButton(
                label = stringResource(R.string.controls_ping_short),
                onClick = { viewModel.send("ping") },
                busy = busy("ping"),
            )
        }
        TeslaButton(
            label = stringResource(R.string.controls_rename),
            onClick = { navigator.to(CommandRoute("set-vehicle-name")) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A short explanation under a control, with room kept for every one of [variants]. */
@Composable
private fun Caption(text: String, variants: List<String> = emptyList()) {
    StableText(text, variants, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MediaControls(viewModel: MainViewModel, media: Vehicle.MediaState?, running: String?) {
    val title = media?.takeIf { it.hasNowPlayingTitle() }?.nowPlayingTitle?.takeIf { it.isNotBlank() }
    val artist = media?.takeIf { it.hasNowPlayingArtist() }?.nowPlayingArtist?.takeIf { it.isNotBlank() }
    SectionGroup {
        InfoRow(
            stringResource(R.string.controls_now_playing),
            listOfNotNull(title, artist).joinToString(" · ").ifEmpty { stringResource(R.string.controls_nothing_playing) },
        )
    }
    // The car's own media buttons are symbols only.
    ButtonGrid(columns = 3) {
        TeslaIconButton(painterResource(R.drawable.ic_action_previous), stringResource(R.string.media_previous), { viewModel.send("media-previous-track") }, busy = running == "media-previous-track")
        TeslaIconButton(painterResource(R.drawable.ic_action_play_pause), stringResource(R.string.media_play_pause), { viewModel.send("media-toggle-playback") }, busy = running == "media-toggle-playback")
        TeslaIconButton(painterResource(R.drawable.ic_action_next), stringResource(R.string.media_next), { viewModel.send("media-next-track") }, busy = running == "media-next-track")
    }
    ButtonGrid {
        TeslaButton(
            stringResource(R.string.media_previous_favourite), { viewModel.send("media-previous-favorite") },
            busy = running == "media-previous-favorite",
        )
        TeslaButton(
            stringResource(R.string.media_next_favourite), { viewModel.send("media-next-favorite") },
            busy = running == "media-next-favorite",
        )
    }
    SectionGroup { VolumeControl(viewModel, media, busy = running == "media-set-volume") }
}

/** Volume as a slider, sent when released; returns to the car's level if refused. */
@Composable
private fun VolumeControl(viewModel: MainViewModel, media: Vehicle.MediaState?, busy: Boolean) {
    val carVolume = media?.takeIf { it.hasAudioVolume() }?.audioVolume
    val max = media?.takeIf { it.hasAudioVolumeMax() }?.audioVolumeMax?.takeIf { it > 0f }?.coerceAtMost(10f) ?: 10f
    val step = media?.takeIf { it.hasAudioVolumeIncrement() }?.audioVolumeIncrement?.takeIf { it in 0.05f..2f } ?: 0.5f
    var failures by remember { mutableIntStateOf(0) }
    var volume by remember(carVolume, failures) { mutableFloatStateOf((carVolume ?: 5f).coerceIn(0f, max)) }
    SliderControl(
        title = stringResource(R.string.controls_volume),
        valueText = oneDecimal(volume),
        value = volume,
        onValueChange = { volume = it },
        onCommit = {
            if (volume != carVolume) {
                val args = mapOf("VOLUME" to String.format(Locale.ROOT, "%.2f", volume))
                viewModel.send("media-set-volume", args) { if (it is CommandOutcome.Failure) failures++ }
            }
        },
        valueRange = 0f..max,
        step = step,
        decreaseLabel = stringResource(R.string.controls_volume_down),
        increaseLabel = stringResource(R.string.controls_volume_up),
        busy = busy,
    )
}

@Composable
private fun updateText(update: Vehicle.SoftwareUpdateState?): String {
    val case = update?.takeIf { it.hasStatus() }?.status?.typeCase
    val version = update?.takeIf { it.hasVersion() }?.version?.takeIf { it.isNotBlank() } ?: ""
    return when (case) {
        Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase.AVAILABLE ->
            if (version.isEmpty()) stringResource(R.string.controls_update_ready) else stringResource(R.string.controls_update_available, version)
        Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase.INSTALLING -> stringResource(R.string.controls_update_installing, update.installPerc)
        Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase.DOWNLOADING -> stringResource(R.string.controls_update_downloading, update.downloadPerc)
        Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase.DOWNLOADINGWIFIWAIT -> stringResource(R.string.controls_update_wifi)
        Vehicle.SoftwareUpdateState.SoftwareUpdateStatus.TypeCase.SCHEDULED -> stringResource(R.string.controls_update_scheduled)
        null -> stringResource(R.string.details_never)
        else -> stringResource(R.string.controls_update_none)
    }
}
