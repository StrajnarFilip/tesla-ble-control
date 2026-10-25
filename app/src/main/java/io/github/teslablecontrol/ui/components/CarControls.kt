package io.github.teslablecontrol.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.VehicleSnapshot
import io.github.teslablecontrol.ui.MainViewModel

// Controls shared by Home, Controls and Charging, so each behaves (and keeps its size) the same
// everywhere. Parts of the car with a few states are TeslaSliders: the block shows where the part is
// now, or no block until the car reports it, and choosing the other option sends the command. The
// shut state is always on the left.

/** Locked or unlocked. */
@Composable
fun LockRow(viewModel: MainViewModel, snapshot: VehicleSnapshot, running: String?, modifier: Modifier = Modifier) = ChoiceRow(
    title = stringResource(R.string.closure_doors),
    options = listOf(true to stringResource(R.string.status_locked), false to stringResource(R.string.status_unlocked)),
    selected = snapshot.isLocked,
    onSelect = { lock -> viewModel.send(if (lock) "lock" else "unlock") },
    modifier = modifier,
    busy = running == "lock" || running == "unlock",
)

/** The frunk opens remotely but closes by hand, so an open frunk only shows its state. */
@Composable
fun FrunkRow(viewModel: MainViewModel, snapshot: VehicleSnapshot, running: String?, modifier: Modifier = Modifier) = ClosureRow(
    title = stringResource(R.string.closure_frunk),
    open = snapshot.isFrunkOpen,
    onSet = { open -> if (open) viewModel.send("frunk-open") },
    modifier = modifier,
    busy = running == "frunk-open",
    enabled = snapshot.isFrunkOpen != true,
    note = stringResource(R.string.controls_frunk_note),
)

@Composable
fun TrunkRow(viewModel: MainViewModel, snapshot: VehicleSnapshot, running: String?, modifier: Modifier = Modifier) = ClosureRow(
    title = stringResource(R.string.closure_trunk),
    open = snapshot.isTrunkOpen,
    onSet = { open -> viewModel.send(if (open) "trunk-open" else "trunk-close") },
    modifier = modifier,
    busy = running?.startsWith("trunk-") == true,
)

@Composable
fun ChargePortRow(viewModel: MainViewModel, snapshot: VehicleSnapshot, running: String?, modifier: Modifier = Modifier) = ClosureRow(
    title = stringResource(R.string.charging_port),
    open = snapshot.isChargePortOpen,
    onSet = { open -> viewModel.send(if (open) "charge-port-open" else "charge-port-close") },
    modifier = modifier,
    busy = running?.startsWith("charge-port") == true,
)

@Composable
fun WindowsRow(viewModel: MainViewModel, snapshot: VehicleSnapshot, running: String?, modifier: Modifier = Modifier) = ClosureRow(
    title = stringResource(R.string.closure_windows),
    open = snapshot.areWindowsOpen,
    onSet = { open -> viewModel.send(if (open) "windows-vent" else "windows-close") },
    modifier = modifier,
    busy = running?.startsWith("windows-") == true,
    openLabel = stringResource(R.string.state_vented),
)

/** A part of the car that opens and closes, as a Closed | Open TeslaSlider. */
@Composable
fun ClosureRow(
    title: String,
    open: Boolean?,
    onSet: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
    openLabel: String = stringResource(R.string.state_open),
    note: String? = null,
    notes: List<String> = emptyList(),
) = ChoiceRow(
    title = title,
    options = listOf(false to stringResource(R.string.state_closed), true to openLabel),
    selected = open,
    onSelect = onSet,
    modifier = modifier,
    note = note,
    notes = notes,
    busy = busy,
    enabled = enabled,
)

/**
 * A setting the car doesn't report, as an Off | On TeslaSlider showing what this phone last set; no
 * option is shown chosen until it has been set here.
 */
@Composable
fun SettingRow(name: String, on: Boolean?, onSet: (Boolean) -> Unit, modifier: Modifier = Modifier, busy: Boolean = false) = ChoiceRow(
    title = name,
    options = listOf(false to stringResource(R.string.word_off), true to stringResource(R.string.word_on)),
    selected = on,
    onSelect = onSet,
    modifier = modifier,
    busy = busy,
)

@Composable
fun FlashButton(viewModel: MainViewModel, running: String?, modifier: Modifier = Modifier) = TeslaButton(
    label = stringResource(R.string.home_flash),
    onClick = { viewModel.send("flash-lights") },
    modifier = modifier,
    busy = running == "flash-lights",
)

@Composable
fun HonkButton(viewModel: MainViewModel, running: String?, modifier: Modifier = Modifier) = TeslaButton(
    label = stringResource(R.string.home_honk),
    onClick = { viewModel.send("honk") },
    modifier = modifier,
    busy = running == "honk",
)
