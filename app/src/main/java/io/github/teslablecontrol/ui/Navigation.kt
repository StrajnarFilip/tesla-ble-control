@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.teslablecontrol.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.collectLatest
import io.github.teslablecontrol.R
import io.github.teslablecontrol.ble.BlePermissions
import io.github.teslablecontrol.core.FailureKind
import io.github.teslablecontrol.ui.charging.ChargingScreen
import io.github.teslablecontrol.ui.climate.ClimateScreen
import io.github.teslablecontrol.ui.commands.AllCommandsScreen
import io.github.teslablecontrol.ui.commands.CommandFormScreen
import io.github.teslablecontrol.ui.components.ConfirmDialog
import io.github.teslablecontrol.ui.components.windowWidth
import io.github.teslablecontrol.ui.controls.ControlsScreen
import io.github.teslablecontrol.ui.details.DetailsScreen
import io.github.teslablecontrol.ui.format.FailureAction
import io.github.teslablecontrol.ui.guide.SetupGuideScreen
import io.github.teslablecontrol.ui.guide.TroubleTopic
import io.github.teslablecontrol.ui.guide.TroubleshootingContent
import io.github.teslablecontrol.ui.home.HomeScreen
import io.github.teslablecontrol.ui.security.KeysScreen
import io.github.teslablecontrol.ui.security.SecurityScreen
import io.github.teslablecontrol.ui.settings.DiagnosticsScreen
import io.github.teslablecontrol.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object ClimateRoute
@Serializable object ChargingRoute
@Serializable object ControlsRoute
@Serializable object SecurityRoute
@Serializable object AllCommandsRoute
@Serializable data class CommandRoute(val id: String)
@Serializable object DetailsRoute
@Serializable object SettingsRoute
@Serializable object KeysRoute
@Serializable object DiagnosticsRoute
@Serializable data class TroubleshootingRoute(val topic: String? = null)
@Serializable object AddCarRoute

private data class Tab(val route: Any, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(HomeRoute, R.string.nav_home, Icons.Outlined.Home),
    Tab(ClimateRoute, R.string.nav_climate, Icons.Outlined.AcUnit),
    Tab(ChargingRoute, R.string.nav_charging, Icons.Outlined.Bolt),
    Tab(ControlsRoute, R.string.nav_controls, Icons.Outlined.Tune),
    Tab(SecurityRoute, R.string.nav_security, Icons.Outlined.Security),
)

/** Where the app goes for each kind of help. */
fun FailureKind.troubleTopic(): TroubleTopic? = when (this) {
    FailureKind.OUT_OF_RANGE, FailureKind.CONNECTION_FAILED -> TroubleTopic.NOT_FOUND
    FailureKind.TOO_MANY_CONNECTIONS -> TroubleTopic.TOO_MANY_CONNECTIONS
    FailureKind.NOT_PAIRED -> TroubleTopic.NOT_PAIRED
    FailureKind.ASLEEP, FailureKind.TIMEOUT -> TroubleTopic.ASLEEP
    FailureKind.BLUETOOTH_OFF, FailureKind.PERMISSION_MISSING, FailureKind.LOCATION_OFF -> TroubleTopic.BLUETOOTH
    else -> null
}

/** Navigation actions screens may take. */
class Navigator(private val controller: NavHostController) {
    fun to(route: Any) = controller.navigate(route) { launchSingleTop = true }
    fun back() = controller.popBackStack()

    /**
     * Back to Home, dropping everything above it; used when a flow such as pairing ends. (Tab
     * navigation would restore the flow's own saved screen instead.)
     */
    fun home() {
        if (!controller.popBackStack(HomeRoute, inclusive = false)) controller.navigate(HomeRoute)
    }
    fun tab(route: Any) = controller.navigate(route) {
        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MainScaffold(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = TABS.firstOrNull { tab -> backStack?.destination?.hasRoute(tab.route::class) == true }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val wide = windowWidth() >= 600.dp

    LaunchedEffect(viewModel) {
        // The newest message replaces the one showing, so messages never trail behind the car.
        viewModel.messages.collectLatest { message ->
            haptics.performHapticFeedback(if (message.isError) HapticFeedbackType.Reject else HapticFeedbackType.Confirm)
            val actionLabel = message.action?.let { action ->
                resources.getString(
                    when (action) {
                        FailureAction.PAIR -> R.string.action_pair
                        FailureAction.WAKE -> R.string.action_wake
                        FailureAction.BLUETOOTH -> R.string.action_turn_on_bluetooth
                        FailureAction.PERMISSION -> R.string.action_allow
                        FailureAction.LOCATION -> R.string.action_open_settings
                        FailureAction.RETRY -> R.string.action_retry
                        FailureAction.HELP -> R.string.action_help
                    },
                )
            }
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = actionLabel,
                withDismissAction = true,
                duration = if (message.isError) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                when (message.action) {
                    FailureAction.PAIR -> navigator.to(AddCarRoute)
                    FailureAction.WAKE -> viewModel.run("wake")
                    FailureAction.BLUETOOTH -> BlePermissions.requestEnableBluetooth(context)
                    FailureAction.PERMISSION -> context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                    FailureAction.LOCATION -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    FailureAction.RETRY -> message.commandId?.let { viewModel.run(it, message.args) }
                    FailureAction.HELP -> navigator.to(TroubleshootingRoute(message.kind?.troubleTopic()?.name))
                    null -> Unit
                }
            }
        }
    }

    val pending by viewModel.pending.collectAsState()
    pending?.let { command ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_title, command.title),
            text = command.description,
            confirmLabel = command.title,
            onConfirm = viewModel::confirmPending,
            onDismiss = viewModel::dismissPending,
        )
    }

    Row(Modifier.fillMaxSize()) {
        if (wide && currentTab != null) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEach { tab ->
                    NavigationRailItem(
                        selected = tab == currentTab,
                        onClick = { navigator.tab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide && currentTab != null) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == currentTab,
                                onClick = { navigator.tab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                alwaysShowLabel = true,
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            NavHost(navController, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
                composable<HomeRoute> { HomeScreen(viewModel, navigator) }
                composable<ClimateRoute> { ClimateScreen(viewModel, navigator) }
                composable<ChargingRoute> { ChargingScreen(viewModel, navigator) }
                composable<ControlsRoute> { ControlsScreen(viewModel, navigator) }
                composable<SecurityRoute> { SecurityScreen(viewModel, navigator) }
                composable<AllCommandsRoute> { AllCommandsScreen(viewModel, navigator) }
                composable<CommandRoute> { entry -> CommandFormScreen(viewModel, navigator, entry.toRoute<CommandRoute>().id) }
                composable<DetailsRoute> { DetailsScreen(viewModel, navigator) }
                composable<SettingsRoute> { SettingsScreen(viewModel, navigator) }
                composable<KeysRoute> { KeysScreen(viewModel, navigator) }
                composable<DiagnosticsRoute> { DiagnosticsScreen(viewModel, navigator) }
                composable<TroubleshootingRoute> { entry ->
                    val topic = entry.toRoute<TroubleshootingRoute>().topic?.let { name -> TroubleTopic.entries.firstOrNull { it.name == name } }
                    BackScaffold(stringResource(R.string.trouble_title), navigator) { modifier ->
                        TroubleshootingContent(topic, modifier)
                    }
                }
                composable<AddCarRoute> {
                    SetupGuideScreen(
                        viewModel = viewModel,
                        showWelcome = false,
                        onFinished = {
                            viewModel.updateSettings { it.copy(onboardingDone = true, demoMode = false) }
                            navigator.home()
                        },
                        onTryDemo = {},
                        onClose = { navigator.back() },
                    )
                }
            }
        }
    }
}

/** The top bar of the five main screens: car switcher, all commands, settings. */
@Composable
fun MainTopBar(viewModel: MainViewModel, navigator: Navigator, title: String) {
    val profile by viewModel.profile.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val switchLabel = stringResource(R.string.action_switch_car)
    TopAppBar(
        title = {
            Box {
                val switchable = (profiles?.size ?: 0) > 1 || settings?.demoMode == true
                TextButton(
                    onClick = { if (switchable) menuOpen = true },
                    modifier = Modifier.semantics { if (switchable) contentDescription = "${profile?.name ?: title}, $switchLabel" },
                ) {
                    Text(
                        profile?.name ?: title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                    if (switchable) Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    profiles.orEmpty().forEach { car ->
                        DropdownMenuItem(
                            text = { Text(car.name) },
                            onClick = {
                                menuOpen = false
                                viewModel.selectVehicle(car.vin)
                            },
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { navigator.to(AllCommandsRoute) }) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_all_commands))
            }
            IconButton(onClick = { navigator.to(SettingsRoute) }) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** A screen with a back arrow and a title. */
@Composable
fun BackScaffold(title: String, navigator: Navigator, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navigator.back() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding -> content(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) }
}
