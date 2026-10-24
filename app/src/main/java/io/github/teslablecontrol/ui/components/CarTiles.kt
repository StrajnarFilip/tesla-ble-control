package io.github.teslablecontrol.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.VehicleSnapshot
import io.github.teslablecontrol.ui.MainViewModel

// Tiles shared by Home, Controls and Charging, so each behaves (and keeps its size) the same everywhere.

/** Lock or unlock. While the lock state is unknown, locking is offered, as the safe choice. */
@Composable
fun LockTile(viewModel: MainViewModel, snapshot: VehicleSnapshot, running: String?, modifier: Modifier) {
    val locked = snapshot.isLocked
    val lockTitle = stringResource(R.string.action_lock)
    val unlockTitle = stringResource(R.string.action_unlock)
    val lockedState = stringResource(R.string.status_locked)
    val unlockedState = stringResource(R.string.status_unlocked)
    val unknownState = stringResource(R.string.state_unknown)
    ActionTile(
        icon = if (locked == false) R.drawable.ic_action_unlocked else R.drawable.ic_action_locked,
        title = if (locked == true) unlockTitle else lockTitle,
        state = when (locked) {
            true -> lockedState
            false -> unlockedState
            null -> unknownState
        },
        onClick = { viewModel.send(if (locked == true) "unlock" else "lock") },
        modifier = modifier,
        busy = running == "lock" || running == "unlock",
        highlighted = locked == false,
        titles = listOf(lockTitle, unlockTitle),
        states = listOf(lockedState, unlockedState, unknownState),
    )
}

/** The frunk opens remotely but closes by hand, so an open frunk only shows its state. */
@Composable
fun FrunkTile(viewModel: MainViewModel, snapshot: VehicleSnapshot, running: String?, modifier: Modifier) {
    val open = snapshot.isFrunkOpen == true
    val openTitle = stringResource(R.string.home_frunk)
    val isOpenTitle = stringResource(R.string.controls_frunk_is_open)
    val closedState = stringResource(R.string.state_closed)
    val byHandState = stringResource(R.string.controls_frunk_close_by_hand)
    val unknownState = stringResource(R.string.state_unknown)
    ActionTile(
        icon = if (open) R.drawable.ic_action_frunk_open else R.drawable.ic_action_frunk_closed,
        title = if (open) isOpenTitle else openTitle,
        state = when (snapshot.isFrunkOpen) {
            true -> byHandState
            false -> closedState
            null -> unknownState
        },
        onClick = { viewModel.send("frunk-open") },
        modifier = modifier,
        busy = running == "frunk-open",
        highlighted = open,
        enabled = !open,
        titles = listOf(openTitle, isOpenTitle),
        states = listOf(closedState, byHandState, unknownState),
    )
}

@Composable
fun TrunkTile(viewModel: MainViewModel, snapshot: VehicleSnapshot, running: String?, modifier: Modifier) = ClosureTile(
    name = stringResource(R.string.closure_trunk),
    open = snapshot.isTrunkOpen,
    openIcon = R.drawable.ic_action_trunk_open,
    closedIcon = R.drawable.ic_action_trunk_closed,
    openTitle = stringResource(R.string.controls_trunk_open),
    closeTitle = stringResource(R.string.controls_trunk_close),
    onOpen = { viewModel.send("trunk-open") },
    onClose = { viewModel.send("trunk-close") },
    modifier = modifier,
    busy = running?.startsWith("trunk-") == true,
)

@Composable
fun ChargePortTile(viewModel: MainViewModel, snapshot: VehicleSnapshot, running: String?, modifier: Modifier) = ClosureTile(
    name = stringResource(R.string.charging_port),
    open = snapshot.isChargePortOpen,
    openIcon = R.drawable.ic_action_port_open,
    closedIcon = R.drawable.ic_action_port_closed,
    openTitle = stringResource(R.string.charging_port_open),
    closeTitle = stringResource(R.string.charging_port_close),
    onOpen = { viewModel.send("charge-port-open") },
    onClose = { viewModel.send("charge-port-close") },
    modifier = modifier,
    busy = running?.startsWith("charge-port") == true,
)

@Composable
fun WindowsTile(viewModel: MainViewModel, snapshot: VehicleSnapshot, running: String?, modifier: Modifier) = ClosureTile(
    name = stringResource(R.string.closure_windows),
    open = snapshot.areWindowsOpen,
    openIcon = R.drawable.ic_action_windows_vented,
    closedIcon = R.drawable.ic_action_windows_closed,
    openTitle = stringResource(R.string.home_windows_vent),
    closeTitle = stringResource(R.string.home_windows_close),
    onOpen = { viewModel.send("windows-vent") },
    onClose = { viewModel.send("windows-close") },
    modifier = modifier,
    busy = running?.startsWith("windows-") == true,
    openState = stringResource(R.string.state_vented),
)

@Composable
fun FlashTile(viewModel: MainViewModel, running: String?, modifier: Modifier) = ActionTile(
    icon = R.drawable.ic_action_flash,
    title = stringResource(R.string.home_flash),
    state = null,
    onClick = { viewModel.send("flash-lights") },
    modifier = modifier,
    busy = running == "flash-lights",
)

@Composable
fun HonkTile(viewModel: MainViewModel, running: String?, modifier: Modifier) = ActionTile(
    icon = R.drawable.ic_action_honk,
    title = stringResource(R.string.home_honk),
    state = null,
    onClick = { viewModel.send("honk") },
    modifier = modifier,
    busy = running == "honk",
)

/**
 * A setting the car doesn't report: shows what this phone last set and switches it. Until it has
 * been set here, tapping asks which way.
 */
@Composable
fun SettingTile(
    name: String,
    on: Boolean?,
    @DrawableRes onIcon: Int,
    @DrawableRes offIcon: Int,
    onSet: (Boolean) -> Unit,
    modifier: Modifier,
    busy: Boolean = false,
) {
    val onState = stringResource(R.string.word_on)
    val offState = stringResource(R.string.word_off)
    val unknownState = stringResource(R.string.state_unknown)
    var asking by remember { mutableStateOf(false) }
    ActionTile(
        icon = if (on == false) offIcon else onIcon,
        title = name,
        state = when (on) {
            true -> onState
            false -> offState
            null -> unknownState
        },
        onClick = { if (on == null) asking = true else onSet(!on) },
        modifier = modifier,
        busy = busy,
        highlighted = on == true,
        states = listOf(onState, offState, unknownState),
    )
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(name) },
            text = { Text(stringResource(R.string.setting_unknown_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { asking = false; onSet(true) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.setting_turn_on))
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false; onSet(false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.setting_turn_off))
                }
            },
        )
    }
}
