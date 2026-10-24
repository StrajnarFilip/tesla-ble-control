package io.github.teslablecontrol.ui.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tesla.generated.carserver.vehicle.Vehicle
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.data.CarModel
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.ui.CommandRoute
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.components.ActionButton
import io.github.teslablecontrol.ui.components.ActionButtonRow
import io.github.teslablecontrol.ui.components.ActionRow
import io.github.teslablecontrol.ui.components.ActionTile
import io.github.teslablecontrol.ui.components.ClosureTile
import io.github.teslablecontrol.ui.components.FrunkTile
import io.github.teslablecontrol.ui.components.LockTile
import io.github.teslablecontrol.ui.components.TrunkTile
import io.github.teslablecontrol.ui.components.WindowsTile
import io.github.teslablecontrol.ui.components.InfoRow
import io.github.teslablecontrol.ui.components.MainScreenFrame
import io.github.teslablecontrol.ui.components.SectionCard
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.SliderControl
import io.github.teslablecontrol.ui.components.TileGrid
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

    LaunchedEffect(profile?.vin) { viewModel.refresh(CONTROLS_CATEGORIES) }

    MainScreenFrame(viewModel, navigator, stringResource(R.string.controls_title), onRefresh = { viewModel.refresh(CONTROLS_CATEGORIES) }) {
        SectionHeader(stringResource(R.string.controls_doors))
        TileGrid { cell -> LockTile(viewModel, snapshot, running, cell) }
        // Front of the car on the left, rear on the right.
        TileGrid { cell ->
            FrunkTile(viewModel, snapshot, running, cell)
            TrunkTile(viewModel, snapshot, running, cell)
        }
        TileGrid { cell ->
            if (showAll || model == CarModel.CYBERTRUCK) {
                ClosureTile(
                    name = stringResource(R.string.closure_tonneau),
                    open = snapshot.isTonneauOpen,
                    openIcon = R.drawable.ic_action_tonneau_open,
                    closedIcon = R.drawable.ic_action_tonneau_closed,
                    openTitle = stringResource(R.string.controls_tonneau_open),
                    closeTitle = stringResource(R.string.controls_tonneau_close),
                    onOpen = { viewModel.send("tonneau-open") },
                    onClose = { viewModel.send("tonneau-close") },
                    modifier = cell,
                    busy = busy("tonneau-open", "tonneau-close"),
                )
                ActionTile(
                    icon = R.drawable.ic_action_stop,
                    title = stringResource(R.string.controls_tonneau_stop),
                    state = null,
                    onClick = { viewModel.send("tonneau-stop") },
                    modifier = cell,
                    busy = busy("tonneau-stop"),
                )
            }
            if (showAll || model == CarModel.MODEL_X) {
                ActionTile(
                    icon = R.drawable.ic_action_locked,
                    title = stringResource(R.string.controls_autosecure),
                    state = stringResource(R.string.controls_autosecure_desc),
                    onClick = { viewModel.send("autosecure-modelx") },
                    modifier = cell,
                    busy = busy("autosecure-modelx"),
                )
            }
        }

        SectionHeader(stringResource(R.string.controls_windows))
        val closures = snapshot.closures
        val hasSunroof = closures?.takeIf { it.hasSunRoofState() }?.sunRoofState?.typeCase
            ?.let { it != Vehicle.ClosuresState.SunRoofState.TypeCase.TYPE_NOT_SET } == true
        val showSunroof = showAll || hasSunroof || model == CarModel.MODEL_S || model == CarModel.MODEL_X
        TileGrid { cell ->
            WindowsTile(viewModel, snapshot, running, cell)
            if (showSunroof) {
                val percent = closures?.takeIf { it.hasSunRoofPercentOpen() }?.sunRoofPercentOpen
                ClosureTile(
                    name = stringResource(R.string.closure_sunroof),
                    open = percent?.let { it > 0 },
                    openIcon = R.drawable.ic_action_sunroof_open,
                    closedIcon = R.drawable.ic_action_sunroof_closed,
                    openTitle = stringResource(R.string.controls_sunroof_open),
                    closeTitle = stringResource(R.string.controls_sunroof_close),
                    onOpen = { viewModel.send("sunroof", mapOf("LEVEL" to "100")) },
                    onClose = { viewModel.send("sunroof", mapOf("LEVEL" to "0")) },
                    modifier = cell,
                    busy = busy("sunroof"),
                    openState = percent?.let { "$it % open" } ?: stringResource(R.string.state_open),
                )
            }
        }
        if (showSunroof) {
            SectionCard {
                ActionRow(
                    title = stringResource(R.string.controls_sunroof_other),
                    icon = Icons.Outlined.Tune,
                    onClick = { navigator.to(CommandRoute("sunroof")) },
                )
            }
        }

        SectionHeader(stringResource(R.string.controls_alerts))
        val location = snapshot.location
        val homelinkNearby = location?.takeIf { it.hasHomelinkNearby() }?.homelinkNearby == true
        TileGrid { cell ->
            ActionTile(
                icon = R.drawable.ic_action_flash,
                title = stringResource(R.string.home_flash),
                state = null,
                onClick = { viewModel.send("flash-lights") },
                modifier = cell,
                busy = busy("flash-lights"),
            )
            ActionTile(
                icon = R.drawable.ic_action_honk,
                title = stringResource(R.string.home_honk),
                state = null,
                onClick = { viewModel.send("honk") },
                modifier = cell,
                busy = busy("honk"),
            )
            ActionTile(
                icon = R.drawable.ic_action_homelink,
                title = stringResource(R.string.controls_homelink),
                state = stringResource(if (homelinkNearby) R.string.controls_homelink_nearby else R.string.controls_homelink_desc),
                onClick = {
                    val lat = location?.takeIf { it.hasLatitude() }?.latitude
                    val lon = location?.takeIf { it.hasLongitude() }?.longitude
                    if (lat != null && lon != null && (lat != 0f || lon != 0f)) {
                        viewModel.send("homelink", mapOf("LATITUDE" to lat.toString(), "LONGITUDE" to lon.toString()))
                    } else {
                        navigator.to(CommandRoute("homelink"))
                    }
                },
                modifier = cell,
                busy = busy("homelink"),
            )
        }

        SectionHeader(stringResource(R.string.controls_media))
        SectionCard { MediaControls(viewModel, snapshot.media, running) }

        SectionHeader(stringResource(R.string.controls_drive))
        TileGrid { cell ->
            ActionTile(
                icon = R.drawable.ic_action_remote_start,
                title = stringResource(R.string.controls_remote_start),
                state = stringResource(R.string.controls_remote_start_desc),
                onClick = { viewModel.send("drive") },
                modifier = cell,
                busy = busy("drive"),
            )
        }

        SectionHeader(stringResource(R.string.controls_software))
        // Two lines kept for the status, so the buttons below stay put as it changes.
        Text(updateText(snapshot.softwareUpdate), style = MaterialTheme.typography.bodyLarge, minLines = 2, modifier = Modifier.padding(horizontal = 4.dp))
        TileGrid { cell ->
            ActionTile(
                icon = R.drawable.ic_action_update_install,
                title = stringResource(R.string.controls_install_short),
                state = stringResource(R.string.controls_install_update),
                onClick = { navigator.to(CommandRoute("software-update-start")) },
                modifier = cell,
            )
            ActionTile(
                icon = R.drawable.ic_action_update_cancel,
                title = stringResource(R.string.controls_cancel_short),
                state = null,
                onClick = { viewModel.send("software-update-cancel") },
                modifier = cell,
                busy = busy("software-update-cancel"),
            )
        }

        SectionHeader(stringResource(R.string.controls_vehicle))
        TileGrid { cell ->
            ActionTile(
                icon = R.drawable.ic_action_wake,
                title = stringResource(R.string.action_wake),
                state = null,
                onClick = { viewModel.send("wake") },
                modifier = cell,
                busy = busy("wake"),
            )
            ActionTile(
                icon = R.drawable.ic_action_ping,
                title = stringResource(R.string.controls_ping_short),
                state = null,
                onClick = { viewModel.send("ping") },
                modifier = cell,
                busy = busy("ping"),
            )
        }
        SectionCard {
            ActionRow(
                title = stringResource(R.string.controls_rename),
                icon = Icons.Outlined.Edit,
                onClick = { navigator.to(CommandRoute("set-vehicle-name")) },
            )
        }
    }
}

@Composable
private fun MediaControls(viewModel: MainViewModel, media: Vehicle.MediaState?, running: String?) {
    val title = media?.takeIf { it.hasNowPlayingTitle() }?.nowPlayingTitle?.takeIf { it.isNotBlank() }
    val artist = media?.takeIf { it.hasNowPlayingArtist() }?.nowPlayingArtist?.takeIf { it.isNotBlank() }
    InfoRow(
        stringResource(R.string.controls_now_playing),
        listOfNotNull(title, artist).joinToString(" · ").ifEmpty { stringResource(R.string.controls_nothing_playing) },
    )
    ActionButtonRow(
        listOf(
            ActionButton(stringResource(R.string.media_previous), R.drawable.ic_action_previous, { viewModel.send("media-previous-track") }, busy = running == "media-previous-track"),
            ActionButton(stringResource(R.string.media_play_pause), R.drawable.ic_action_play_pause, { viewModel.send("media-toggle-playback") }, busy = running == "media-toggle-playback"),
            ActionButton(stringResource(R.string.media_next), R.drawable.ic_action_next, { viewModel.send("media-next-track") }, busy = running == "media-next-track"),
        ),
    )
    ActionButtonRow(
        listOf(
            ActionButton(stringResource(R.string.media_previous_favourite), R.drawable.ic_action_favorite_previous, { viewModel.send("media-previous-favorite") }, busy = running == "media-previous-favorite"),
            ActionButton(stringResource(R.string.media_next_favourite), R.drawable.ic_action_favorite_next, { viewModel.send("media-next-favorite") }, busy = running == "media-next-favorite"),
        ),
    )
    VolumeControl(viewModel, media, busy = running == "media-set-volume")
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
