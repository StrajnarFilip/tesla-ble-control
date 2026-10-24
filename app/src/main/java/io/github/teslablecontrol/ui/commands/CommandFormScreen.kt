@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.teslablecontrol.ui.commands

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.core.VehicleSnapshot
import io.github.teslablecontrol.data.UnitSystem
import io.github.teslablecontrol.protocol.commands.CommandCatalog
import io.github.teslablecontrol.protocol.commands.CommandSpec
import io.github.teslablecontrol.protocol.commands.ParamSpec
import io.github.teslablecontrol.protocol.commands.ParamType
import io.github.teslablecontrol.protocol.commands.SessionRequirement
import io.github.teslablecontrol.protocol.toHex
import io.github.teslablecontrol.protocol.vehicle.CommandResult
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.ui.AddCarRoute
import io.github.teslablecontrol.ui.BackScaffold
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.TroubleshootingRoute
import io.github.teslablecontrol.ui.UiMessage
import io.github.teslablecontrol.ui.components.OptionChips
import io.github.teslablecontrol.ui.components.StableText
import io.github.teslablecontrol.ui.format.FailureAction
import io.github.teslablecontrol.ui.format.Units
import io.github.teslablecontrol.ui.format.failureAction
import io.github.teslablecontrol.ui.format.failureMessage
import io.github.teslablecontrol.ui.format.temperatureValue
import io.github.teslablecontrol.ui.troubleTopic
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CommandFormScreen(viewModel: MainViewModel, navigator: Navigator, commandId: String) {
    val spec = CommandCatalog.find(commandId)
    val info = CommandInfos.get(commandId)
    BackScaffold(info.title, navigator) { modifier ->
        if (spec == null) {
            Text(stringResource(R.string.commands_unknown), modifier = modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        } else {
            CommandForm(viewModel, navigator, spec, info, modifier)
        }
    }
}

@Composable
private fun CommandForm(viewModel: MainViewModel, navigator: Navigator, spec: CommandSpec, info: CommandInfo, modifier: Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.controllerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val running by viewModel.running.collectAsState()
    val units = Units.resolve(settings?.units ?: UnitSystem.AUTO)
    val ownKeyHex = remember { viewModel.app.keys.currentKey()?.publicKey?.toHex() }

    var values by rememberSaveable(spec.id) { mutableStateOf(initialValues(spec, state.snapshot, units, ownKeyHex)) }
    var problem by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<CommandOutcome?>(null) }
    var locating by remember { mutableStateOf(false) }

    fun set(name: String, value: String?) {
        values = if (value.isNullOrBlank()) values - name else values + (name to value)
        problem = null
    }

    val location = state.snapshot.location
    LaunchedEffect(locating, location) {
        if (!locating) return@LaunchedEffect
        if (location != null && location.hasLatitude() && location.hasLongitude()) {
            values = values + ("LATITUDE" to location.latitude.toString()) + ("LONGITUDE" to location.longitude.toString())
            locating = false
        } else {
            delay(45_000)
            locating = false
            viewModel.emit(UiMessage(resources.getString(R.string.commands_location_unavailable), isError = true))
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(info.description, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (spec.requirement == SessionRequirement.INFOTAINMENT) R.string.commands_wakes_car else R.string.commands_works_asleep),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (spec.params.isEmpty()) {
                Text(stringResource(R.string.commands_no_options), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            spec.params.forEach { param ->
                ParamField(param, values[param.name], units, onChange = { set(param.name, it) })
                if (param.type == ParamType.PublicKey && ownKeyHex != null) {
                    OutlinedButton(onClick = { set(param.name, ownKeyHex) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Outlined.VpnKey, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.commands_use_own_key))
                    }
                }
                if (param.type == ParamType.Longitude) {
                    OutlinedButton(
                        onClick = {
                            locating = true
                            if (location == null) viewModel.refresh(listOf(StateCategory.LOCATION), wake = true)
                        },
                        enabled = !locating,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.MyLocation, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        StableText(
                            stringResource(if (locating) R.string.commands_reading_location else R.string.commands_use_car_location),
                            listOf(stringResource(R.string.commands_reading_location), stringResource(R.string.commands_use_car_location)),
                        )
                    }
                }
            }

            val busy = running == spec.id
            Button(
                onClick = {
                    problem = validate(spec, values)
                    if (problem == null) {
                        outcome = null
                        viewModel.send(spec.id, values) { outcome = it }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                }
                Text(info.title)
            }
            problem?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }

            outcome?.let { result ->
                ResultCard(
                    result,
                    successText = info.successText,
                    failureText = (result as? CommandOutcome.Failure)?.let { failureMessage(context, it) },
                    onAction = { action ->
                        when (action) {
                            FailureAction.PAIR -> navigator.to(AddCarRoute)
                            FailureAction.WAKE -> viewModel.run("wake")
                            FailureAction.RETRY -> viewModel.send(spec.id, values) { outcome = it }
                            else -> navigator.to(TroubleshootingRoute((result as? CommandOutcome.Failure)?.kind?.troubleTopic()?.name))
                        }
                    },
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

/** What a command returned, in words, with the raw data for state reads. */
@Composable
private fun ResultCard(outcome: CommandOutcome, successText: String, failureText: String?, onAction: (FailureAction) -> Unit) {
    val failure = outcome as? CommandOutcome.Failure
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (failure != null && !failure.mayHaveSucceeded) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.commands_result), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            when (outcome) {
                is CommandOutcome.Success -> {
                    Text(
                        when (outcome.result) {
                            CommandResult.Sent -> stringResource(R.string.commands_sent)
                            else -> successText
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    resultDetails(outcome.result)?.let { details ->
                        SelectionContainer {
                            Text(details, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                is CommandOutcome.Failure -> {
                    Text(failureText ?: outcome.message, style = MaterialTheme.typography.bodyLarge)
                    val action = failureAction(outcome)
                    if (action != null) {
                        val label = stringResource(
                            when (action) {
                                FailureAction.PAIR -> R.string.action_pair
                                FailureAction.WAKE -> R.string.action_wake
                                FailureAction.RETRY -> R.string.action_retry
                                else -> R.string.action_help
                            },
                        )
                        Button(onClick = { onAction(action) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                    }
                }
            }
        }
    }
}

private fun resultDetails(result: CommandResult): String? = when (result) {
    is CommandResult.State -> protoText(result.data.toString())
    is CommandResult.BodyStatus -> protoText(result.status.toString())
    is CommandResult.KeyList -> result.keys.joinToString("\n") { "Slot ${it.slot}: ${it.role.name.removePrefix("ROLE_").lowercase()}, ${keyId(it.publicKey)}" }
    is CommandResult.SessionInfo -> "Counter ${result.info.counter}, epoch ${result.info.epoch.toByteArray().toHex()}"
    else -> null
}

/** Protobuf-lite's text output without its "# ClassName@hash" first line. */
private fun protoText(text: String): String = text.lines().dropWhile { it.startsWith("#") }.joinToString("\n").trim().ifEmpty { "(empty)" }

private fun keyId(publicKey: ByteArray): String = io.github.teslablecontrol.ui.security.keyId(publicKey)

/** Defaults from the catalog, plus what the car last reported where that's the obvious starting point. */
private fun initialValues(spec: CommandSpec, snapshot: VehicleSnapshot, units: Units, ownKeyHex: String?): Map<String, String> {
    val values = HashMap<String, String>()
    spec.params.forEach { p -> p.default?.let { values[p.name] = it } }
    fun put(name: String, value: Any?) {
        if (value != null && spec.params.any { it.name == name }) values[name] = value.toString()
    }
    when (spec.id) {
        "add-key-request", "session-info" -> put("PUBLIC_KEY", ownKeyHex)
        "climate-set-temp" -> snapshot.driverTempSetting?.let { c ->
            put("TEMP", if (units.fahrenheit) "${units.temperatureValue(c).roundToInt()}f" else String.format(Locale.ROOT, "%.1fc", c))
        }
        "charging-set-limit" -> put("PERCENT", snapshot.chargeLimit)
        "charging-set-amps" -> put("AMPS", snapshot.charge?.takeIf { it.hasChargingAmps() }?.chargingAmps)
        "media-set-volume" -> put("VOLUME", snapshot.media?.takeIf { it.hasAudioVolume() }?.audioVolume)
    }
    snapshot.location?.takeIf { it.hasLatitude() && it.hasLongitude() }?.let {
        put("LATITUDE", it.latitude)
        put("LONGITUDE", it.longitude)
    }
    return values
}

/** Checks the values by building the command; returns a message naming the field, or null. */
private fun validate(spec: CommandSpec, values: Map<String, String>): String? {
    val error = runCatching { spec.build(values) }.exceptionOrNull() ?: return null
    val message = error.message ?: return "Check the values"
    val missing = Regex("^missing ([A-Z_]+)$").find(message)
    if (missing != null) return "${ParamLabels.label(missing.groupValues[1])}: please fill this in"
    val named = Regex("^([A-Z_]+) (.*)$").find(message)
    if (named != null && spec.params.any { it.name == named.groupValues[1] }) {
        return "${ParamLabels.label(named.groupValues[1])}: ${named.groupValues[2]}"
    }
    return message
}

@Composable
private fun ParamField(param: ParamSpec, value: String?, units: Units, onChange: (String?) -> Unit) {
    val label = ParamLabels.label(param.name).let { if (!param.required && param.default == null) stringResource(R.string.commands_optional, it) else it }
    when (val type = param.type) {
        ParamType.OnOff -> Labelled(label) {
            OptionChips(
                options = listOf("on" to stringResource(R.string.word_on), "off" to stringResource(R.string.word_off)),
                selected = value?.lowercase(),
                onSelect = { onChange(it) },
                icons = { if (it == "on") R.drawable.ic_action_on else R.drawable.ic_action_off },
            )
        }
        is ParamType.Choice -> Labelled(label) {
            OptionChips(
                options = type.options.map { it to choiceLabel(param.name, it) },
                selected = value?.let { v -> type.options.firstOrNull { it.equals(v, ignoreCase = true) } },
                onSelect = { onChange(it) },
            )
        }
        is ParamType.Integer -> NumberField(label, value, onChange, decimal = false, hint = stringResource(R.string.commands_range, type.min.toString(), type.max.toString()))
        is ParamType.Decimal -> NumberField(label, value, onChange, decimal = true, hint = stringResource(R.string.commands_range, formatNumber(type.min), formatNumber(type.max)))
        ParamType.Identifier -> NumberField(label, value, onChange, decimal = false, hint = null)
        ParamType.Latitude, ParamType.Longitude -> NumberField(label, value, onChange, decimal = true, signed = true, hint = null)
        ParamType.Pin -> OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        is ParamType.Text -> OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = { onChange(it.take(type.maxLength)) },
            label = { Text(label) },
            singleLine = true,
            supportingText = { Text("${value.orEmpty().length} / ${type.maxLength}") },
            modifier = Modifier.fillMaxWidth(),
        )
        ParamType.PublicKey -> OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = { onChange(it) },
            label = { Text(label) },
            minLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        ParamType.Temperature -> {
            val unit = if (units.fahrenheit) "f" else "c"
            OutlinedTextField(
                value = value?.trimEnd('c', 'C', 'f', 'F').orEmpty(),
                onValueChange = { text ->
                    val number = text.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
                    onChange(if (number.isEmpty()) null else number + unit)
                },
                label = { Text(label) },
                suffix = { Text(if (units.fahrenheit) "°F" else "°C") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ParamType.TimeOfDay -> TimeField(label, value, onChange)
        ParamType.TimeRange -> {
            val start = value?.substringBefore('-', "")?.trim().orEmpty()
            val end = value?.substringAfter('-', "")?.trim().orEmpty()
            fun combine(s: String?, e: String?): String? {
                val a = s.orEmpty()
                val b = e.orEmpty()
                return if (a.isEmpty() && b.isEmpty()) null else "$a-$b"
            }
            Labelled(label) {
                Text(stringResource(R.string.commands_time_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TimeField(stringResource(R.string.commands_time_from), start.ifEmpty { null }, onChange = { onChange(combine(it, end)) })
                TimeField(stringResource(R.string.commands_time_until), end.ifEmpty { null }, onChange = { onChange(combine(start, it)) })
            }
        }
        ParamType.Days -> Labelled(label) { DaysField(value, onChange) }
        ParamType.Duration -> Labelled(label) {
            OptionChips(
                options = listOf("0s" to "Now", "10m" to "10 min", "30m" to "30 min", "1h" to "1 hour", "2h" to "2 hours"),
                selected = value,
                onSelect = { onChange(it) },
            )
            OutlinedTextField(
                value = value.orEmpty(),
                onValueChange = { onChange(it.trim()) },
                label = { Text("Other (for example 1h30m)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun NumberField(label: String, value: String?, onChange: (String?) -> Unit, decimal: Boolean, hint: String?, signed: Boolean = false) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { text ->
            val cleaned = text.replace(',', '.').filterIndexed { i, c -> c.isDigit() || (decimal && c == '.') || (signed && c == '-' && i == 0) }
            onChange(cleaned)
        },
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal || signed) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "HH:MM" typed or chosen on a clock. */
@Composable
private fun TimeField(label: String, value: String?, onChange: (String?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == ':' }.take(5)) },
        label = { Text(label) },
        placeholder = { Text("07:30") },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { picking = true }) {
                Icon(Icons.Outlined.Schedule, contentDescription = stringResource(R.string.commands_pick_time))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    if (picking) {
        val parts = value?.split(":")
        val picker = rememberTimePickerState(
            initialHour = parts?.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 7,
            initialMinute = parts?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { picking = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    TimePicker(state = picker)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { picking = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel)) }
                        TextButton(
                            onClick = {
                                picking = false
                                onChange(String.format(Locale.ROOT, "%02d:%02d", picker.hour, picker.minute))
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.action_done)) }
                    }
                }
            }
        }
    }
}

private val DAY_NAMES = listOf("SUN" to "Sun", "MON" to "Mon", "TUE" to "Tue", "WED" to "Wed", "THU" to "Thu", "FRI" to "Fri", "SAT" to "Sat")
private val WEEKDAYS = setOf("MON", "TUE", "WED", "THU", "FRI")

/** Day toggles, kept as the catalog's comma-separated names. */
@Composable
private fun DaysField(value: String?, onChange: (String?) -> Unit) {
    val selected: Set<String> = value.orEmpty().split(",").map { it.trim().uppercase() }.flatMap {
        when (it) {
            "ALL" -> DAY_NAMES.map { d -> d.first }
            "WEEKDAYS" -> WEEKDAYS.toList()
            "TUES" -> listOf("TUE")
            "THURS" -> listOf("THU")
            else -> listOf(it.take(3))
        }
    }.toSet()
    fun save(days: Set<String>) = onChange(DAY_NAMES.map { it.first }.filter { it in days }.joinToString(",").ifEmpty { null })

    OptionChips(
        options = listOf("all" to stringResource(R.string.commands_days_all), "weekdays" to stringResource(R.string.commands_days_weekdays)),
        selected = when (selected) {
            DAY_NAMES.map { it.first }.toSet() -> "all"
            WEEKDAYS -> "weekdays"
            else -> null
        },
        onSelect = { if (it == "all") save(DAY_NAMES.map { d -> d.first }.toSet()) else save(WEEKDAYS) },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DAY_NAMES.forEach { (id, name) ->
            val on = id in selected
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                border = if (on) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { save(if (it) selected + id else selected - id) }),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (on) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(name, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Readable names for the catalog's option ids. */
private fun choiceLabel(param: String, option: String): String = when (param) {
    "SEAT" -> mapOf(
        "front-left" to "Driver", "front-right" to "Passenger", "2nd-row-left" to "Rear left", "2nd-row-left-back" to "Rear left backrest",
        "2nd-row-center" to "Rear centre", "2nd-row-right" to "Rear right", "2nd-row-right-back" to "Rear right backrest",
        "3rd-row-left" to "Third row left", "3rd-row-right" to "Third row right",
    )[option]
    "LEVEL" -> mapOf("off" to "Off", "low" to "Low", "medium" to "Medium", "high" to "High")[option]
    "MODE" -> mapOf("off" to "Off", "on" to "Keep on", "dog" to "Dog mode", "camp" to "Camp mode")[option]
    "ROLE" -> mapOf(
        "owner" to "Owner", "driver" to "Driver", "fm" to "Fleet manager", "vehicle_monitor" to "Vehicle monitor",
        "charging_manager" to "Charging manager",
    )[option]
    "FORM_FACTOR" -> mapOf("nfc_card" to "Key card", "ios_device" to "iPhone", "android_device" to "Android phone", "cloud_key" to "Cloud key")[option]
    "DOMAIN" -> mapOf("vcsec" to "Locks and doors", "infotainment" to "Infotainment (climate, charging, media)")[option]
    "POSITIONS" -> mapOf("L" to "Driver side", "R" to "Passenger side", "LR" to "Both")[option]
    "PRECONDITIONING", "OFF_PEAK" -> mapOf("off" to "Off", "all" to "Every day", "weekdays" to "Weekdays")[option]
    "REPEAT" -> mapOf("weekly" to "Every week", "once" to "Once")[option]
    "TYPE" -> mapOf("home" to "All at home", "work" to "All at work", "other" to "All other places", "id" to "One, by ID")[option]
    "SETTING" -> mapOf(
        "speed-limit" to "Speed limit", "acceleration" to "Chill acceleration", "safety-features" to "Safety features",
        "curfew" to "Curfew", "browser-blocked" to "Block browser", "theater-blocked" to "Block Theater", "arcade-blocked" to "Block Arcade",
    )[option]
    "CATEGORY" -> mapOf(
        "charge" to "Charging", "climate" to "Climate", "drive" to "Driving", "location" to "Location", "closures" to "Doors and windows",
        "charge-schedule" to "Charge schedules", "precondition-schedule" to "Precondition schedules", "tire-pressure" to "Tyre pressure",
        "media" to "Media", "media-detail" to "Media details", "software-update" to "Software update", "parental-controls" to "Parental controls",
    )[option]
    else -> null
} ?: option.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
