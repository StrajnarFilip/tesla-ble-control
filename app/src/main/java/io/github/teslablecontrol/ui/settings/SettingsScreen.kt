package io.github.teslablecontrol.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.R
import io.github.teslablecontrol.ble.BlePermissions
import io.github.teslablecontrol.data.AppSettings
import io.github.teslablecontrol.data.ThemeMode
import io.github.teslablecontrol.data.UnitSystem
import io.github.teslablecontrol.data.VehicleProfile
import io.github.teslablecontrol.tile.LockTileService
import io.github.teslablecontrol.tile.TileSetupDialog
import io.github.teslablecontrol.ui.AddCarRoute
import io.github.teslablecontrol.ui.BackScaffold
import io.github.teslablecontrol.ui.DiagnosticsRoute
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.TroubleshootingRoute
import io.github.teslablecontrol.ui.UiMessage
import io.github.teslablecontrol.ui.components.ActionRow
import io.github.teslablecontrol.ui.components.ChoiceRow
import io.github.teslablecontrol.ui.components.ConfirmDialog
import io.github.teslablecontrol.ui.components.SectionCard
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.ToggleRow

@Composable
fun SettingsScreen(viewModel: MainViewModel, navigator: Navigator) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val active by viewModel.profile.collectAsState()
    val s = settings ?: AppSettings()
    var removing by remember { mutableStateOf<VehicleProfile?>(null) }
    var tileSetup by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    fun update(transform: (AppSettings) -> AppSettings) = viewModel.updateSettings(transform)

    if (tileSetup) {
        TileSetupDialog(
            onResult = { result ->
                val text = when (result) {
                    LockTileService.Companion.AddResult.ADDED -> R.string.tile_added
                    LockTileService.Companion.AddResult.ALREADY_ADDED -> R.string.tile_already_added
                    LockTileService.Companion.AddResult.DECLINED -> null
                    LockTileService.Companion.AddResult.UNAVAILABLE -> R.string.tile_add_unavailable
                }
                if (text != null) {
                    tileSetup = false
                    viewModel.emit(UiMessage(resources.getString(text)))
                }
            },
            onDismiss = { tileSetup = false },
        )
    }

    removing?.let { car ->
        ConfirmDialog(
            title = stringResource(R.string.settings_remove_car_title, car.name),
            text = stringResource(R.string.settings_remove_car_body),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                removing = null
                viewModel.removeVehicle(car.vin)
            },
            onDismiss = { removing = null },
        )
    }

    BackScaffold(stringResource(R.string.settings_title), navigator) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.settings_cars))
                SectionCard {
                    Column(Modifier.selectableGroup()) {
                        profiles.orEmpty().forEach { car ->
                            CarRow(car, selected = !s.demoMode && car.vin == active?.vin, onSelect = { viewModel.selectVehicle(car.vin) })
                        }
                    }
                    ActionRow(
                        title = stringResource(R.string.settings_add_car),
                        icon = Icons.Outlined.Add,
                        onClick = { navigator.to(AddCarRoute) },
                    )
                    val current = active?.takeIf { !s.demoMode && profiles.orEmpty().any { p -> p.vin == it.vin } }
                    if (current != null) {
                        ActionRow(
                            title = stringResource(R.string.settings_remove_car),
                            description = current.name,
                            icon = Icons.Outlined.Delete,
                            destructive = true,
                            onClick = { removing = current },
                        )
                    }
                }

                SectionHeader(stringResource(R.string.settings_connection))
                SectionCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_stay_connected),
                        description = stringResource(R.string.settings_stay_connected_desc),
                        checked = s.stayConnected,
                        onCheckedChange = { on ->
                            update { it.copy(stayConnected = on) }
                            val permission = BlePermissions.notifications
                            if (on && permission != null && !BlePermissions.granted(context, permission)) notificationLauncher.launch(permission)
                        },
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_start_on_boot),
                        description = stringResource(R.string.settings_start_on_boot_desc),
                        checked = s.startOnBoot,
                        enabled = s.stayConnected,
                        onCheckedChange = { on -> update { it.copy(startOnBoot = on) } },
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_auto_wake),
                        description = stringResource(R.string.settings_auto_wake_desc),
                        checked = s.autoWake,
                        onCheckedChange = { on -> update { it.copy(autoWake = on) } },
                    )
                }

                SectionHeader(stringResource(R.string.settings_appearance))
                SectionCard {
                    ChoiceRow(
                        title = stringResource(R.string.settings_theme),
                        description = stringResource(R.string.settings_theme_desc),
                        options = listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                            ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                        ),
                        selected = s.theme,
                        icons = { mode ->
                            when (mode) {
                                ThemeMode.SYSTEM -> R.drawable.ic_action_theme_system
                                ThemeMode.DARK -> R.drawable.ic_action_theme_dark
                                ThemeMode.LIGHT -> R.drawable.ic_action_theme_light
                            }
                        },
                        onSelect = { mode -> update { it.copy(theme = mode) } },
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_units),
                        description = stringResource(R.string.settings_units_desc),
                        options = listOf(
                            UnitSystem.AUTO to stringResource(R.string.settings_units_auto),
                            UnitSystem.METRIC to stringResource(R.string.settings_units_metric),
                            UnitSystem.IMPERIAL to stringResource(R.string.settings_units_imperial),
                        ),
                        selected = s.units,
                        icons = { units ->
                            when (units) {
                                UnitSystem.AUTO -> R.drawable.ic_action_units_auto
                                UnitSystem.METRIC -> R.drawable.ic_action_units_metric
                                UnitSystem.IMPERIAL -> R.drawable.ic_action_units_imperial
                            }
                        },
                        onSelect = { units -> update { it.copy(units = units) } },
                    )
                }

                SectionHeader(stringResource(R.string.settings_safety))
                SectionCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_confirm),
                        description = stringResource(R.string.settings_confirm_desc),
                        checked = s.confirmRisky,
                        onCheckedChange = { on -> update { it.copy(confirmRisky = on) } },
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_command_messages),
                        description = stringResource(R.string.settings_command_messages_desc),
                        checked = s.commandMessages,
                        onCheckedChange = { on -> update { it.copy(commandMessages = on) } },
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_show_all),
                        description = stringResource(R.string.settings_show_all_desc),
                        checked = s.showAllControls,
                        onCheckedChange = { on -> update { it.copy(showAllControls = on) } },
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_demo),
                        description = stringResource(R.string.settings_demo_desc),
                        checked = s.demoMode,
                        onCheckedChange = { on -> update { it.copy(demoMode = on) } },
                    )
                }

                SectionHeader(stringResource(R.string.settings_help))
                SectionCard {
                    ActionRow(
                        title = stringResource(R.string.settings_guide),
                        description = stringResource(R.string.settings_guide_desc),
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        onClick = { navigator.to(AddCarRoute) },
                    )
                    ActionRow(
                        title = stringResource(R.string.settings_troubleshooting),
                        description = stringResource(R.string.settings_troubleshooting_desc),
                        icon = Icons.AutoMirrored.Outlined.Help,
                        onClick = { navigator.to(TroubleshootingRoute()) },
                    )
                    ActionRow(
                        title = stringResource(R.string.settings_quick_tile),
                        description = stringResource(R.string.settings_tile_desc),
                        icon = Icons.Outlined.ToggleOn,
                        onClick = { tileSetup = true },
                    )
                    ActionRow(
                        title = stringResource(R.string.settings_diagnostics),
                        description = stringResource(R.string.settings_diagnostics_desc),
                        icon = Icons.AutoMirrored.Outlined.ListAlt,
                        onClick = { navigator.to(DiagnosticsRoute) },
                    )
                }

                SectionHeader(stringResource(R.string.settings_about))
                SectionCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall)
                        if (version.isNotEmpty()) {
                            Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            stringResource(R.string.settings_about_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

/** One car as a radio row: name, model and whether this phone is paired with it. */
@Composable
private fun CarRow(car: VehicleProfile, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(car.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${car.model.label} · ${stringResource(if (car.paired) R.string.settings_car_paired else R.string.settings_car_not_paired)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(car.vin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
