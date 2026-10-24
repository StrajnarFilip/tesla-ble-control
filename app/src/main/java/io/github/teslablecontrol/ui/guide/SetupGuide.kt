@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.teslablecontrol.ui.guide

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.teslablecontrol.R
import io.github.teslablecontrol.ble.BlePermissions
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.core.FailureKind
import io.github.teslablecontrol.core.LinkException
import io.github.teslablecontrol.core.LinkFailure
import io.github.teslablecontrol.data.CarModel
import io.github.teslablecontrol.data.VehicleProfile
import io.github.teslablecontrol.keys.KeyManager
import io.github.teslablecontrol.tile.LockTileService
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.UiMessage
import io.github.teslablecontrol.ui.components.OptionChips
import io.github.teslablecontrol.ui.components.StableText
import io.github.teslablecontrol.ui.components.ToggleRow
import io.github.teslablecontrol.ui.format.failureMessage
import io.github.teslablecontrol.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private enum class Step { WELCOME, NEED, VIN, PERMISSIONS, FIND, ROLE, PAIR, DONE }

private val VIN_PATTERN = Regex("^[A-HJ-NPR-Z0-9]{17}$")

fun isValidVin(vin: String): Boolean = VIN_PATTERN.matches(vin)

/** The first VIN in copied text such as "VIN: 5YJ3E1EA1PF000001", ignoring case and spaces around it. */
fun findVin(text: String): String? = Regex("(?<![A-Z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-Z0-9])").find(text.uppercase())?.value

/**
 * The illustrated setup: VIN, permissions, finding the car, choosing access, key-card pairing and the
 * background connection. Each step explains itself in words; pictures only support the text.
 */
@Composable
fun SetupGuideScreen(
    viewModel: MainViewModel,
    showWelcome: Boolean,
    onFinished: () -> Unit,
    onTryDemo: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val steps = remember(showWelcome) { if (showWelcome) Step.entries else Step.entries.drop(1) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[stepIndex]
    var vin by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf(CarModel.MODEL_3) }
    var role by rememberSaveable { mutableStateOf("owner") }
    var paired by rememberSaveable { mutableStateOf(false) }
    val defaultName = stringResource(R.string.guide_name_default)

    fun back() {
        if (stepIndex > 0) stepIndex-- else onClose?.invoke()
    }
    BackHandler(enabled = stepIndex > 0 || onClose != null) { back() }

    val canContinue = when (step) {
        Step.VIN -> isValidVin(vin)
        Step.PAIR -> paired
        else -> true
    }

    fun next() {
        if (step == Step.VIN) {
            val existing = viewModel.app.profiles.find(vin)
            viewModel.saveProfile(
                (existing ?: VehicleProfile(vin = vin, name = name.ifBlank { defaultName }))
                    .copy(name = name.ifBlank { existing?.name ?: defaultName }, model = model),
            )
            viewModel.selectVehicle(vin)
        }
        if (stepIndex < steps.lastIndex) stepIndex++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (step != Step.WELCOME) {
                        Text(stringResource(R.string.guide_step_count, stepIndex + if (showWelcome) 0 else 1, Step.entries.size - 1))
                    }
                },
                navigationIcon = {
                    if (stepIndex > 0 || onClose != null) {
                        IconButton(onClick = { back() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (step != Step.WELCOME) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        // Above the keyboard while typing, so Back and Next stay reachable.
                        Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = { back() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_back))
                        }
                        Button(
                            onClick = { if (step == Step.DONE) onFinished() else next() },
                            enabled = canContinue,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) {
                            Text(stringResource(if (step == Step.DONE) R.string.guide_finish else R.string.action_next))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                Step.WELCOME -> WelcomeStep(onStart = { next() }, onTryDemo = onTryDemo)
                Step.NEED -> NeedStep()
                Step.VIN -> VinStep(vin, { vin = it }, name, { name = it }, model, { model = it })
                Step.PERMISSIONS -> PermissionsStep()
                Step.FIND -> FindStep(viewModel, vin)
                Step.ROLE -> RoleStep(role) { role = it }
                Step.PAIR -> PairStep(viewModel, vin, role, paired) { paired = it }
                Step.DONE -> DoneStep(viewModel, name.ifBlank { defaultName })
            }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

/** A status line with an icon; announced by TalkBack when it changes. */
@Composable
private fun StatusLine(text: String, ok: Boolean?, busy: Boolean = false, variants: List<String> = emptyList()) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        // A fixed slot for the indicator, so the text never shifts sideways.
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                ok == true -> Icon(Icons.Outlined.CheckCircle, null, tint = LocalStatusColors.current.success, modifier = Modifier.size(26.dp))
                ok == false -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        StableText(text, variants, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit, onTryDemo: () -> Unit) {
    Spacer(Modifier.size(12.dp))
    Illustration(Picture.WELCOME, stringResource(R.string.guide_welcome_picture))
    StepTitle(stringResource(R.string.guide_welcome_title))
    Body(stringResource(R.string.guide_welcome_body))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.guide_start)) }
    OutlinedButton(onClick = onTryDemo, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.guide_try_demo)) }
}

@Composable
private fun NeedStep() {
    Illustration(Picture.WHAT_YOU_NEED, stringResource(R.string.guide_need_picture))
    StepTitle(stringResource(R.string.guide_need_title))
    listOf(R.string.guide_need_1, R.string.guide_need_2, R.string.guide_need_3, R.string.guide_need_4).forEach {
        Row {
            Text("•  ", style = MaterialTheme.typography.bodyLarge)
            Body(stringResource(it))
        }
    }
}

@Composable
private fun VinStep(
    vin: String,
    onVin: (String) -> Unit,
    name: String,
    onName: (String) -> Unit,
    model: CarModel,
    onModel: (CarModel) -> Unit,
) {
    Illustration(Picture.VIN, stringResource(R.string.guide_vin_picture))
    StepTitle(stringResource(R.string.guide_vin_title))
    Body(stringResource(R.string.guide_vin_body))
    Body(stringResource(R.string.guide_vin_from_app))
    Body(stringResource(R.string.guide_vin_from_car))
    val context = LocalContext.current
    var pasteFailed by remember { mutableStateOf(false) }
    val valid = isValidVin(vin)
    OutlinedTextField(
        value = vin,
        onValueChange = {
            pasteFailed = false
            onVin(it.uppercase().filter(Char::isLetterOrDigit).take(17))
        },
        label = { Text(stringResource(R.string.guide_vin_label)) },
        singleLine = true,
        isError = vin.length == 17 && !valid,
        supportingText = {
            Text(
                when {
                    valid -> stringResource(R.string.guide_vin_ok)
                    else -> stringResource(R.string.guide_vin_invalid) + "  (${vin.length}/17)"
                },
            )
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = {
            val copied = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            val found = copied?.let(::findVin)
            pasteFailed = found == null
            if (found != null) onVin(found)
        },
        modifier = Modifier.heightIn(min = 52.dp),
    ) {
        Icon(Icons.Outlined.ContentPaste, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.guide_vin_paste))
    }
    if (pasteFailed) {
        Text(
            stringResource(R.string.guide_vin_paste_none),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    OutlinedTextField(
        value = name,
        onValueChange = { onName(it.take(40)) },
        label = { Text(stringResource(R.string.guide_name_label)) },
        placeholder = { Text(stringResource(R.string.guide_name_default)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.guide_model_label), style = MaterialTheme.typography.titleSmall)
    OptionChips(CarModel.entries.map { it to it.label }, model, onModel)
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val granted = remember(tick) { BlePermissions.canScan(context) }
    val locationOn = remember(tick) { BlePermissions.isLocationOn(context) }
    val bluetoothOn = remember(tick) { BlePermissions.isBluetoothOn(context) }

    Illustration(Picture.PERMISSIONS, stringResource(R.string.guide_permissions_picture))
    StepTitle(stringResource(R.string.guide_permissions_title))
    Body(stringResource(R.string.guide_permissions_body))
    // Each item keeps its status line and button; a button is only disabled once its item is done,
    // so nothing moves as permissions are granted.
    val grantedText = stringResource(R.string.guide_permission_granted)
    val neededText = stringResource(R.string.guide_permission_needed)
    StatusLine(if (granted) grantedText else neededText, granted, variants = listOf(grantedText, neededText))
    Button(
        onClick = { launcher.launch(BlePermissions.required.toTypedArray()) },
        enabled = !granted,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.action_allow)) }
    TextButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }) { Text(stringResource(R.string.guide_permission_settings)) }
    val bluetoothOnText = stringResource(R.string.guide_bluetooth_on)
    val bluetoothOffText = stringResource(R.string.guide_bluetooth_off)
    StatusLine(if (bluetoothOn) bluetoothOnText else bluetoothOffText, bluetoothOn, variants = listOf(bluetoothOnText, bluetoothOffText))
    OutlinedButton(
        onClick = { BlePermissions.requestEnableBluetooth(context) },
        enabled = !bluetoothOn,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.action_turn_on_bluetooth)) }
    val locationOnText = stringResource(R.string.guide_location_on)
    val locationOffText = stringResource(R.string.guide_location_off)
    StatusLine(if (locationOn) locationOnText else locationOffText, locationOn, variants = listOf(locationOnText, locationOffText))
    OutlinedButton(
        onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        enabled = !locationOn,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.guide_turn_on_location)) }
}

private sealed interface FindState {
    data object Searching : FindState
    data class Found(val rssi: Int, val connectable: Boolean) : FindState
    data class Failed(val message: String) : FindState
}

@Composable
private fun FindStep(viewModel: MainViewModel, vin: String) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<FindState>(FindState.Searching) }
    val notFound = stringResource(R.string.guide_find_not_found)
    LaunchedEffect(attempt) {
        state = FindState.Searching
        state = try {
            val sighting = viewModel.app.scanner.find(vin, 20.seconds)
            if (sighting == null) {
                FindState.Failed(notFound)
            } else {
                viewModel.app.profiles.update(vin) { it.copy(bleAddress = sighting.device.address) }
                FindState.Found(sighting.rssi, sighting.connectable)
            }
        } catch (e: LinkException) {
            FindState.Failed(
                failureMessage(
                    context,
                    CommandOutcome.Failure(
                        when (e.failure) {
                            LinkFailure.BLUETOOTH_OFF -> FailureKind.BLUETOOTH_OFF
                            LinkFailure.PERMISSION_MISSING -> FailureKind.PERMISSION_MISSING
                            LinkFailure.LOCATION_OFF -> FailureKind.LOCATION_OFF
                            else -> FailureKind.CONNECTION_FAILED
                        },
                        e.message ?: "",
                    ),
                ),
            )
        }
    }

    Illustration(Picture.SCANNING, stringResource(R.string.guide_find_picture))
    StepTitle(stringResource(R.string.guide_find_title))
    Body(stringResource(R.string.guide_find_body))
    // The status line, progress bar and button are always there; only their content changes.
    val searchingText = stringResource(R.string.guide_find_searching)
    val fullText = stringResource(R.string.guide_find_full)
    val signals = listOf(R.string.guide_signal_strong, R.string.guide_signal_good, R.string.guide_signal_weak).map { stringResource(it) }
    val foundTexts = signals.map { stringResource(R.string.guide_find_found, it) }
    val variants = listOf(searchingText, fullText, notFound) + foundTexts
    when (val s = state) {
        FindState.Searching -> StatusLine(searchingText, ok = null, busy = true, variants = variants)
        is FindState.Found -> if (s.connectable) {
            val signal = when {
                s.rssi >= -65 -> 0
                s.rssi >= -80 -> 1
                else -> 2
            }
            StatusLine(foundTexts[signal], ok = true, variants = variants)
        } else {
            StatusLine(fullText, ok = false, variants = variants)
        }
        is FindState.Failed -> StatusLine(s.message, ok = false, variants = variants)
    }
    LinearProgressIndicator(Modifier.fillMaxWidth().alpha(if (state is FindState.Searching) 1f else 0f))
    OutlinedButton(
        onClick = { attempt++ },
        enabled = state !is FindState.Searching,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.guide_find_again)) }
    // Last on the step, so showing it moves nothing.
    if ((state as? FindState.Found)?.connectable == false) {
        Illustration(Picture.TOO_MANY_CONNECTIONS, stringResource(R.string.trouble_full_picture))
    }
}

@Composable
private fun RoleStep(role: String, onRole: (String) -> Unit) {
    Illustration(Picture.ROLES, stringResource(R.string.guide_role_picture))
    StepTitle(stringResource(R.string.guide_role_title))
    Body(stringResource(R.string.guide_role_body))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RoleOption("owner", role, R.string.guide_role_owner, R.string.guide_role_owner_desc, onRole)
        RoleOption("driver", role, R.string.guide_role_driver, R.string.guide_role_driver_desc, onRole)
    }
}

@Composable
private fun RoleOption(value: String, selected: String, title: Int, description: Int, onSelect: (String) -> Unit) {
    val picture = if (value == "owner") R.drawable.ic_action_role_owner else R.drawable.ic_action_role_driver
    val isSelected = value == selected
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) }),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isSelected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Icon(painterResource(picture), contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private sealed interface PairState {
    data object Idle : PairState
    data object Sending : PairState
    data class Waiting(val sinceMillis: Long) : PairState
    data object Done : PairState
    data class Failed(val message: String) : PairState
}

@Composable
private fun PairStep(viewModel: MainViewModel, vin: String, role: String, paired: Boolean, onPaired: (Boolean) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val keyStatus by viewModel.keyStatus.collectAsState()
    var state by remember { mutableStateOf<PairState>(if (paired) PairState.Done else PairState.Idle) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val timeoutText = stringResource(R.string.guide_pair_timeout)
    val controller = remember(vin) { viewModel.app.controllerFor(vin) }

    LaunchedEffect(state) {
        while (state is PairState.Waiting) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    fun markPaired() {
        state = PairState.Done
        onPaired(true)
        viewModel.markPaired(vin, role)
    }

    fun request() {
        scope.launch {
            state = PairState.Sending
            if (keyStatus == KeyManager.Status.UNUSABLE) viewModel.app.keys.replaceWithNewKey() else viewModel.app.keys.getOrCreateKey()
            if (controller.checkPaired() == true) return@launch markPaired()
            when (val outcome = controller.requestPairing(role)) {
                is CommandOutcome.Failure -> state = PairState.Failed(failureMessage(context, outcome))
                is CommandOutcome.Success -> {
                    val started = System.currentTimeMillis()
                    state = PairState.Waiting(started)
                    // Poll until the key-card tap enrolls the key (the car sends nothing back).
                    while (System.currentTimeMillis() - started < 120_000) {
                        delay(2000)
                        if (controller.checkPaired() == true) return@launch markPaired()
                    }
                    state = PairState.Failed(timeoutText)
                }
            }
        }
    }

    StepTitle(stringResource(R.string.guide_pair_title))
    Body(stringResource(R.string.guide_pair_step1))
    Body(stringResource(R.string.guide_pair_step2))
    // The buttons stay where they are while waiting, and the status line below has room for every
    // message, so nothing on this step moves as pairing goes on.
    val idle = state is PairState.Idle || state is PairState.Failed
    Button(onClick = { request() }, enabled = idle, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.guide_pair_send))
    }
    TextButton(
        enabled = idle,
        onClick = {
            scope.launch {
                state = PairState.Sending
                state = if (controller.checkPaired() == true) {
                    markPaired()
                    PairState.Done
                } else {
                    PairState.Failed(resources.getString(R.string.failure_not_paired))
                }
            }
        },
    ) { Text(stringResource(R.string.guide_skip_pairing)) }
    val connectingText = stringResource(R.string.status_connecting)
    val doneText = stringResource(R.string.guide_pair_done)
    val waitingSample = stringResource(R.string.guide_pair_waiting, "0:00")
    val statusVariants = listOf(connectingText, doneText, waitingSample, timeoutText, stringResource(R.string.failure_not_paired))
    when (val s = state) {
        PairState.Idle -> StatusLine("", ok = null, variants = statusVariants)
        PairState.Sending -> StatusLine(connectingText, ok = null, busy = true, variants = statusVariants)
        is PairState.Waiting -> {
            val elapsed = ((now - s.sinceMillis) / 1000).coerceAtLeast(0)
            StatusLine(stringResource(R.string.guide_pair_waiting, "%d:%02d".format(elapsed / 60, elapsed % 60)), ok = null, busy = true, variants = statusVariants)
        }
        PairState.Done -> StatusLine(doneText, ok = true, variants = statusVariants)
        is PairState.Failed -> StatusLine(s.message, ok = false, variants = statusVariants)
    }
    Body(stringResource(R.string.guide_pair_step3))
    ReaderSpots()
    Body(stringResource(R.string.guide_pair_step4))
    // Some cars show nothing on screen, so after a while point at the other reader spot. It comes
    // last, where appearing moves nothing.
    val waitingFor = (state as? PairState.Waiting)?.let { (now - it.sinceMillis) / 1000 } ?: 0
    if (waitingFor >= 20) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                stringResource(R.string.guide_pair_hint),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** The two places a key card reader can be, each with a picture and the cars it applies to. */
@Composable
private fun ReaderSpots() {
    Text(stringResource(R.string.guide_pair_where), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    val spots = listOf(
        Triple(Picture.KEY_CARD_CHARGER, R.string.guide_pair_picture_charger, R.string.guide_pair_where_charger),
        Triple(Picture.KEY_CARD_CONSOLE, R.string.guide_pair_picture_console, R.string.guide_pair_where_console),
    )
    // Side by side normally; stacked when large text would leave the captions too narrow.
    if (LocalDensity.current.fontScale < 1.3f) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            spots.forEach { (picture, description, caption) -> ReaderSpot(picture, description, caption, Modifier.weight(1f)) }
        }
    } else {
        spots.forEach { (picture, description, caption) -> ReaderSpot(picture, description, caption) }
    }
    Body(stringResource(R.string.guide_pair_where_other))
}

@Composable
private fun ReaderSpot(picture: Picture, description: Int, caption: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Illustration(picture, stringResource(description), height = 110.dp)
        Text(stringResource(caption), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DoneStep(viewModel: MainViewModel, carName: String) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val settings by viewModel.settings.collectAsState()
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Illustration(Picture.SUCCESS, stringResource(R.string.guide_pair_done))
    StepTitle(stringResource(R.string.guide_done_title))
    Body(stringResource(R.string.guide_done_body, carName))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        ToggleRow(
            title = stringResource(R.string.settings_stay_connected),
            description = stringResource(R.string.settings_stay_connected_desc),
            checked = settings?.stayConnected ?: true,
            onCheckedChange = { on ->
                viewModel.updateSettings { it.copy(stayConnected = on) }
                val permission = BlePermissions.notifications
                if (on && permission != null && !BlePermissions.granted(context, permission)) notificationLauncher.launch(permission)
            },
        )
    }
    Illustration(Picture.TILE, stringResource(R.string.guide_done_picture))
    if (LockTileService.canRequestAdd) {
        OutlinedButton(
            onClick = {
                LockTileService.requestAdd(context) { result ->
                    if (result == LockTileService.Companion.AddResult.ALREADY_ADDED) {
                        viewModel.emit(UiMessage(resources.getString(R.string.tile_already_added)))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.guide_add_tile)) }
    }
    Body(stringResource(R.string.guide_tile_manual))
}
