package io.github.teslablecontrol.ui.security

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import io.github.teslablecontrol.ui.components.ButtonWithNote
import io.github.teslablecontrol.ui.components.MainScreenFrame
import io.github.teslablecontrol.ui.components.PinDialog
import io.github.teslablecontrol.ui.components.SectionGroup
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.SettingRow
import io.github.teslablecontrol.ui.components.SliderControl
import io.github.teslablecontrol.ui.components.ToggleRow
import io.github.teslablecontrol.ui.components.inGroup
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
    // The parental rule being sent, so only its switch shows progress; the rules share one command.
    var sendingRule by remember { mutableStateOf<String?>(null) }

    fun busy(vararg ids: String) = running in ids

    pinFor?.let { commandId ->
        PinDialog(
            title = stringResource(CommandInfos.get(commandId).title),
            onSubmit = { pin ->
                pinFor = null
                viewModel.send(commandId, mapOf("PIN" to pin))
            },
            onDismiss = { pinFor = null },
        )
    }

    MainScreenFrame(viewModel, navigator, stringResource(R.string.security_title), SECURITY_CATEGORIES) {
        SectionHeader(stringResource(R.string.security_modes))
        SectionGroup {
            ToggleRow(
                title = stringResource(R.string.security_sentry),
                description = stringResource(R.string.security_sentry_desc),
                checked = snapshot.isSentryOn,
                busy = busy("sentry-mode"),
                onCheckedChange = { on -> viewModel.send("sentry-mode", mapOf("STATE" to onOff(on))) },
            )
            ToggleRow(
                title = stringResource(R.string.security_valet),
                description = stringResource(R.string.security_valet_desc),
                checked = snapshot.isValetOn,
                busy = busy("valet-mode-on", "valet-mode-off"),
                onCheckedChange = { on -> if (on) pinFor = "valet-mode-on" else viewModel.send("valet-mode-off") },
            )
            ButtonWithNote(
                label = stringResource(R.string.security_valet_reset),
                onClick = { viewModel.send("valet-pin-reset") },
                busy = busy("valet-pin-reset"),
                modifier = Modifier.inGroup(),
            )
        }

        SectionHeader(stringResource(R.string.security_speed_limit))
        SectionGroup {
            val mode = closures?.takeIf { it.hasSpeedLimitMode() }?.speedLimitMode
            val active = mode?.takeIf { it.hasActive() }?.active
            ToggleRow(
                title = stringResource(R.string.security_speed_limit_mode),
                description = stringResource(R.string.security_speed_limit_desc),
                checked = active,
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
            ButtonWithNote(
                label = stringResource(R.string.security_clear_pin),
                onClick = { pinFor = "speed-limit-clear-pin" },
                note = stringResource(R.string.security_clear_pin_desc),
                busy = busy("speed-limit-clear-pin"),
                modifier = Modifier.inGroup(),
            )
            ButtonWithNote(
                label = stringResource(R.string.security_clear_pin_admin),
                onClick = { viewModel.send("speed-limit-clear-pin-admin") },
                note = stringResource(R.string.security_owner_only),
                busy = busy("speed-limit-clear-pin-admin"),
                modifier = Modifier.inGroup(),
            )
        }

        SectionHeader(stringResource(R.string.security_pin))
        SectionGroup {
            ButtonWithNote(
                label = stringResource(R.string.security_pin_to_drive_off),
                onClick = { viewModel.send("pin-to-drive-clear") },
                note = stringResource(R.string.security_pin_to_drive_desc),
                busy = busy("pin-to-drive-clear"),
                modifier = Modifier.inGroup(),
            )
        }

        SectionHeader(stringResource(R.string.security_guest))
        Text(stringResource(R.string.security_guest_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionGroup {
            SettingRow(
                name = stringResource(R.string.security_guest),
                on = lastSet["guest-mode"],
                onSet = { on -> viewModel.send(if (on) "guest-mode-on" else "guest-mode-off") },
                busy = busy("guest-mode-on", "guest-mode-off"),
            )
            ButtonWithNote(
                label = stringResource(R.string.security_erase_guest),
                onClick = { viewModel.send("erase-guest-data") },
                destructive = true,
                busy = busy("erase-guest-data"),
                modifier = Modifier.inGroup(),
            )
        }

        SectionHeader(stringResource(R.string.security_parental))
        SectionGroup {
            val parental = snapshot.parentalControls
            val rules = parental?.takeIf { it.hasParentalControlsSettings() }?.parentalControlsSettings
            ToggleRow(
                title = stringResource(R.string.security_parental),
                description = stringResource(R.string.security_parental_desc),
                checked = parental?.takeIf { it.hasParentalControlsActive() }?.parentalControlsActive,
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
                    title = stringResource(label),
                    checked = known[id],
                    busy = busy("parental-controls-enable-setting") && sendingRule == id,
                    onCheckedChange = { on ->
                        sendingRule = id
                        viewModel.send("parental-controls-enable-setting", mapOf("SETTING" to id, "STATE" to onOff(on)))
                    },
                )
            }
            ButtonWithNote(
                label = stringResource(R.string.security_parental_clear),
                onClick = { viewModel.send("parental-controls-clear-pin-admin") },
                note = stringResource(R.string.security_owner_only),
                busy = busy("parental-controls-clear-pin-admin"),
                modifier = Modifier.inGroup(),
            )
        }

        SectionGroup {
            ButtonWithNote(
                label = stringResource(R.string.security_keys),
                onClick = { navigator.to(KeysRoute) },
                note = stringResource(R.string.security_keys_desc),
                modifier = Modifier.inGroup(),
            )
        }
    }
}

/** Parental controls rules, by catalog id. */
private val PARENTAL_RULES = listOf(
    "speed-limit" to R.string.label_limit_top_speed,
    "acceleration" to R.string.label_chill_acceleration,
    "safety-features" to R.string.label_require_safety_features,
    "curfew" to R.string.label_driving_curfew,
    "browser-blocked" to R.string.label_block_the_web_browser,
    "theater-blocked" to R.string.label_block_theater,
    "arcade-blocked" to R.string.label_block_arcade,
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
