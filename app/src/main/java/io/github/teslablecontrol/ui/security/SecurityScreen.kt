package io.github.teslablecontrol.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.data.UnitSystem
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.ui.KeysRoute
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.commands.CommandInfos
import io.github.teslablecontrol.ui.commands.onOff
import io.github.teslablecontrol.ui.components.ActionRow
import io.github.teslablecontrol.ui.components.ActionTile
import io.github.teslablecontrol.ui.components.MainScreenFrame
import io.github.teslablecontrol.ui.components.PinDialog
import io.github.teslablecontrol.ui.components.SectionCard
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.SettingTile
import io.github.teslablecontrol.ui.components.SliderControl
import io.github.teslablecontrol.ui.components.Stepper
import io.github.teslablecontrol.ui.components.TileGrid
import io.github.teslablecontrol.ui.components.ToggleRow
import io.github.teslablecontrol.ui.format.Units
import io.github.teslablecontrol.ui.format.speedFromMph
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

private val SECURITY_CATEGORIES = listOf(StateCategory.CLOSURES, StateCategory.PARENTAL_CONTROLS)

private const val KMH_PER_MPH = 1.609344

@Composable
fun SecurityScreen(viewModel: MainViewModel, navigator: Navigator) {
    val state by viewModel.controllerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val lastSet by viewModel.lastSet.collectAsState()
    val running by viewModel.running.collectAsState()
    val units = Units.resolve(settings?.units ?: UnitSystem.AUTO)
    val snapshot = state.snapshot
    val closures = snapshot.closures
    // The command waiting for its PIN, if the PIN dialog is open.
    var pinFor by rememberSaveable { mutableStateOf<String?>(null) }

    fun busy(vararg ids: String) = running in ids

    LaunchedEffect(profile?.vin) { viewModel.refresh(SECURITY_CATEGORIES) }

    pinFor?.let { commandId ->
        PinDialog(
            title = CommandInfos.get(commandId).title,
            onSubmit = { pin ->
                pinFor = null
                viewModel.send(commandId, mapOf("PIN" to pin))
            },
            onDismiss = { pinFor = null },
        )
    }

    MainScreenFrame(viewModel, navigator, stringResource(R.string.security_title), onRefresh = { viewModel.refresh(SECURITY_CATEGORIES) }) {
        SectionHeader(stringResource(R.string.security_modes))
        SectionCard {
            ToggleRow(
                title = stringResource(R.string.security_sentry),
                description = stringResource(R.string.security_sentry_desc),
                checked = snapshot.isSentryOn,
                icon = painterResource(R.drawable.ic_action_sentry),
                busy = busy("sentry-mode"),
                onCheckedChange = { on -> viewModel.send("sentry-mode", mapOf("STATE" to onOff(on))) },
            )
            ToggleRow(
                title = stringResource(R.string.security_valet),
                description = stringResource(R.string.security_valet_desc),
                checked = snapshot.isValetOn,
                icon = Icons.Outlined.Person,
                busy = busy("valet-mode-on", "valet-mode-off"),
                onCheckedChange = { on -> if (on) pinFor = "valet-mode-on" else viewModel.send("valet-mode-off") },
            )
            ActionRow(
                title = stringResource(R.string.security_valet_reset),
                icon = Icons.Outlined.LockReset,
                busy = busy("valet-pin-reset"),
                onClick = { viewModel.send("valet-pin-reset") },
            )
        }

        SectionHeader(stringResource(R.string.security_speed_limit))
        SectionCard {
            val mode = closures?.takeIf { it.hasSpeedLimitMode() }?.speedLimitMode
            val active = mode?.takeIf { it.hasActive() }?.active
            ToggleRow(
                title = stringResource(R.string.security_speed_limit_mode),
                description = stringResource(R.string.security_speed_limit_desc),
                checked = active,
                icon = painterResource(R.drawable.ic_action_speed_limit),
                busy = busy("speed-limit-activate", "speed-limit-deactivate"),
                onCheckedChange = { on -> pinFor = if (on) "speed-limit-activate" else "speed-limit-deactivate" },
            )
            SpeedEditor(
                units = units,
                currentMph = mode?.takeIf { it.hasCurrentLimitMph() }?.currentLimitMph?.toDouble()?.takeIf { it > 0 },
                minMph = mode?.takeIf { it.hasMinLimitMph() }?.minLimitMph?.toDouble()?.takeIf { it > 0 } ?: 50.0,
                maxMph = mode?.takeIf { it.hasMaxLimitMph() }?.maxLimitMph?.toDouble()?.takeIf { it > 0 } ?: 120.0,
                busy = busy("speed-limit-set"),
                onSet = { mph, done -> viewModel.send("speed-limit-set", mapOf("MPH" to String.format(Locale.ROOT, "%.2f", mph)), onResult = done) },
            )
            ActionRow(
                title = stringResource(R.string.security_clear_pin),
                description = stringResource(R.string.security_clear_pin_desc),
                icon = Icons.Outlined.LockReset,
                busy = busy("speed-limit-clear-pin"),
                onClick = { pinFor = "speed-limit-clear-pin" },
            )
            ActionRow(
                title = stringResource(R.string.security_clear_pin_admin),
                description = stringResource(R.string.security_owner_only),
                icon = Icons.Outlined.LockReset,
                busy = busy("speed-limit-clear-pin-admin"),
                onClick = { viewModel.send("speed-limit-clear-pin-admin") },
            )
        }

        SectionHeader(stringResource(R.string.security_pin))
        SectionCard {
            ActionRow(
                title = stringResource(R.string.security_pin_to_drive_off),
                description = stringResource(R.string.security_pin_to_drive_desc),
                icon = Icons.Outlined.Key,
                busy = busy("pin-to-drive-clear"),
                onClick = { viewModel.send("pin-to-drive-clear") },
            )
        }

        SectionHeader(stringResource(R.string.security_guest))
        Text(stringResource(R.string.security_guest_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TileGrid { cell ->
            SettingTile(
                name = stringResource(R.string.security_guest),
                on = lastSet["guest-mode"],
                onIcon = R.drawable.ic_action_guest,
                offIcon = R.drawable.ic_action_guest_off,
                onSet = { on -> viewModel.send(if (on) "guest-mode-on" else "guest-mode-off") },
                modifier = cell,
                busy = busy("guest-mode-on", "guest-mode-off"),
            )
        }
        SectionCard {
            ActionRow(
                title = stringResource(R.string.security_erase_guest),
                icon = Icons.Outlined.Delete,
                destructive = true,
                busy = busy("erase-guest-data"),
                onClick = { viewModel.send("erase-guest-data") },
            )
        }

        SectionHeader(stringResource(R.string.security_parental))
        SectionCard {
            val parental = snapshot.parentalControls
            val rules = parental?.takeIf { it.hasParentalControlsSettings() }?.parentalControlsSettings
            ToggleRow(
                title = stringResource(R.string.security_parental),
                description = stringResource(R.string.security_parental_desc),
                checked = parental?.takeIf { it.hasParentalControlsActive() }?.parentalControlsActive,
                icon = painterResource(R.drawable.ic_action_parental),
                busy = busy("parental-controls-on", "parental-controls-off"),
                onCheckedChange = { on -> pinFor = if (on) "parental-controls-on" else "parental-controls-off" },
            )
            SpeedEditor(
                units = units,
                title = stringResource(R.string.security_parental_speed),
                currentMph = rules?.takeIf { it.hasCurrentLimitMph() }?.currentLimitMph?.toDouble()?.takeIf { it > 0 },
                minMph = rules?.takeIf { it.hasMinLimitMph() }?.minLimitMph?.toDouble()?.takeIf { it > 0 } ?: 50.0,
                maxMph = rules?.takeIf { it.hasMaxLimitMph() }?.maxLimitMph?.toDouble()?.takeIf { it > 0 } ?: 120.0,
                busy = busy("parental-controls-set-speed-limit"),
                onSet = { mph, done ->
                    viewModel.send("parental-controls-set-speed-limit", mapOf("MPH" to String.format(Locale.ROOT, "%.2f", mph)), onResult = done)
                },
            )
            SectionHeader(stringResource(R.string.security_parental_rules), Modifier.padding(horizontal = 16.dp))
            val known: Map<String, Boolean?> = mapOf(
                "speed-limit" to rules?.takeIf { it.hasSpeedLimitEnabled() }?.speedLimitEnabled,
                "acceleration" to rules?.takeIf { it.hasChillAccelerationEnabled() }?.chillAccelerationEnabled,
                "safety-features" to rules?.takeIf { it.hasRequireSafetySettingsEnabled() }?.requireSafetySettingsEnabled,
                "curfew" to rules?.takeIf { it.hasCurfewEnabled() }?.curfewEnabled,
            )
            PARENTAL_RULES.forEach { (id, label) ->
                ToggleRow(
                    title = label,
                    checked = known[id],
                    busy = busy("parental-controls-enable-setting"),
                    onCheckedChange = { on ->
                        viewModel.send("parental-controls-enable-setting", mapOf("SETTING" to id, "STATE" to onOff(on)))
                    },
                )
            }
            ActionRow(
                title = stringResource(R.string.security_parental_clear),
                description = stringResource(R.string.security_owner_only),
                icon = Icons.Outlined.LockReset,
                busy = busy("parental-controls-clear-pin-admin"),
                onClick = { viewModel.send("parental-controls-clear-pin-admin") },
            )
        }

        SectionCard {
            ActionRow(
                title = stringResource(R.string.security_keys),
                description = stringResource(R.string.security_keys_desc),
                icon = Icons.Outlined.Key,
                onClick = { navigator.to(KeysRoute) },
            )
        }
    }
}

/** Parental controls rules, by catalog id. */
private val PARENTAL_RULES = listOf(
    "speed-limit" to "Limit top speed",
    "acceleration" to "Chill acceleration",
    "safety-features" to "Require safety features",
    "curfew" to "Driving curfew",
    "browser-blocked" to "Block the web browser",
    "theater-blocked" to "Block Theater",
    "arcade-blocked" to "Block Arcade",
)

/**
 * A speed limit as a slider in the user's units (1 mph or 5 km/h steps), sent in mph as the car
 * expects when released; returns to the car's limit if refused.
 */
@Composable
private fun SpeedEditor(
    units: Units,
    currentMph: Double?,
    minMph: Double,
    maxMph: Double,
    busy: Boolean,
    onSet: (Double, (CommandOutcome) -> Unit) -> Unit,
    title: String = stringResource(R.string.security_max_speed),
) {
    val perMph = if (units.miles) 1.0 else KMH_PER_MPH
    val step = if (units.miles) 1f else 5f
    // Rounded to the nearest step, as the car's own screen does (50 mph shows as 80 km/h).
    val start = ((minMph * perMph / step).roundToInt() * step)
    val end = (floor(maxMph * perMph / step) * step).toFloat().coerceAtLeast(start + step)
    fun toDisplay(mph: Double) = ((mph * perMph / step).roundToInt() * step).coerceIn(start, end)
    var failures by remember { mutableIntStateOf(0) }
    var shown by remember(currentMph, failures) { mutableFloatStateOf(toDisplay(currentMph ?: 65.0)) }
    SliderControl(
        title = title,
        valueText = units.speedFromMph(shown / perMph),
        value = shown,
        onValueChange = { shown = it },
        onCommit = {
            if (currentMph == null || shown != toDisplay(currentMph)) {
                onSet((shown / perMph).coerceIn(minMph, maxMph)) { if (it is CommandOutcome.Failure) failures++ }
            }
        },
        valueRange = start..end,
        step = step,
        decreaseLabel = stringResource(R.string.security_decrease_speed),
        increaseLabel = stringResource(R.string.security_increase_speed),
        busy = busy,
    )
}
