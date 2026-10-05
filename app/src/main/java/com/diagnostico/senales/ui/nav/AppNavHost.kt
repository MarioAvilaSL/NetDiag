package com.diagnostico.senales.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.diagnostico.senales.ui.about.AboutScreen
import com.diagnostico.senales.ui.accuracy.AccuracyScreen
import com.diagnostico.senales.ui.accuracy.AccuracyViewModel
import com.diagnostico.senales.ui.appViewModel
import com.diagnostico.senales.ui.cellular.CellularScreen
import com.diagnostico.senales.ui.cellular.CellularViewModel
import com.diagnostico.senales.ui.dashboard.DashboardScreen
import com.diagnostico.senales.ui.dashboard.DashboardViewModel
import com.diagnostico.senales.ui.device.DeviceScreen
import com.diagnostico.senales.ui.device.DeviceViewModel
import com.diagnostico.senales.ui.gnss.GnssScreen
import com.diagnostico.senales.ui.gnss.GnssViewModel
import com.diagnostico.senales.ui.gnss.SkyPlotScreen
import com.diagnostico.senales.ui.gnss.SkyPlotViewModel
import com.diagnostico.senales.ui.history.HistoryScreen
import com.diagnostico.senales.ui.history.HistoryViewModel
import com.diagnostico.senales.ui.more.MoreScreen
import com.diagnostico.senales.ui.permissions.PermissionsScreen
import com.diagnostico.senales.ui.scan.ScanScreen
import com.diagnostico.senales.ui.scan.ScanViewModel
import com.diagnostico.senales.ui.sim.SimScreen
import com.diagnostico.senales.ui.sim.SimViewModel
import com.diagnostico.senales.data.prefs.AppPreferences
import com.diagnostico.senales.ui.track.TrackScreen
import com.diagnostico.senales.ui.track.TrackViewModel
import com.diagnostico.senales.ui.wifi.WifiScreen
import com.diagnostico.senales.ui.wifi.WifiViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    preferences: AppPreferences,
    themeMode: AppPreferences.ThemeMode,
    onThemeChange: (AppPreferences.ThemeMode) -> Unit,
    privacyMode: Boolean,
    onPrivacyModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.DASHBOARD
    val currentTitle = titleFor(currentRoute)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Señales",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(24.dp),
                )
                HorizontalDivider()
                secondaryDestinations.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Ajustes y límites") },
                    selected = currentRoute == Routes.MORE,
                    onClick = {
                        navController.navigate(Routes.MORE) { launchSingleTop = true }
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
Spacer(Modifier.height(24.dp))
                Text(
                    text = "Todos los datos se calculan en el dispositivo.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        },
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currentTitle)
                            // Candado permanente mientras se ocultan datos: sin él
                            // es fácil olvidar que la captura ya va enmascarada.
                            if (privacyMode) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = "Modo privacidad activo",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Menú")
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = destination.label)
                            },
                            label = { Text(destination.label) },
                        )
                    }
                    NavigationBarItem(
                        selected = currentRoute == Routes.MORE,
                        onClick = { navController.navigate(Routes.MORE) { launchSingleTop = true } },
                        icon = {
                            Icon(Icons.Outlined.Settings, contentDescription = "Más")
                        },
                        label = { Text("Más") },
                    )
                }
            },
        ) { padding ->
            AppNavHost(
                navController = navController,
                padding = padding,
                snackbarHostState = snackbarHostState,
                preferences = preferences,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                privacyMode = privacyMode,
                onPrivacyModeChange = onPrivacyModeChange,
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    padding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    preferences: AppPreferences,
    themeMode: AppPreferences.ThemeMode,
    onThemeChange: (AppPreferences.ThemeMode) -> Unit,
    privacyMode: Boolean,
    onPrivacyModeChange: (Boolean) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = Modifier.padding(padding),
    ) {
        composable(Routes.DASHBOARD) {
            val viewModel: DashboardViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            DashboardScreen(
                state = state,
                onRefresh = viewModel::refreshNow,
                onPing = viewModel::runPing,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(Routes.WIFI) {
            val viewModel: WifiViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            WifiScreen(
                state = state,
                onRefresh = viewModel::refreshConnection,
                onScan = viewModel::refreshScan,
                onPing = viewModel::runPing,
                onSpeedTest = viewModel::runSpeedTest,
                onStartStability = viewModel::startStability,
                onStopStability = viewModel::stopStability,
                onSavePingSettings = viewModel::savePingSettings,
                onSaveSpeedUrls = viewModel::saveSpeedUrls,
                onSetPingMethod = viewModel::setPingMethod,
            )
        }

        composable(Routes.SCAN) {
            val viewModel: ScanViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            ScanScreen(
                state = state,
                onScan = viewModel::scan,
                onSetBand = viewModel::setBand,
                onSetSecurity = viewModel::setSecurity,
                onSetAssumedWidth = viewModel::setAssumedWidth,
            )
        }

        composable(Routes.CELLULAR) {
            val viewModel: CellularViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            CellularScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onPing = viewModel::runPing,
                onSpeedTest = viewModel::runSpeedTest,
                onSetPingMethod = viewModel::setPingMethod,
                onSetForceCellular = viewModel::setForceCellular,
                onSetPingTarget = viewModel::setPingTarget,
            )
        }

        composable(Routes.GNSS) {
            val viewModel: GnssViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            GnssScreen(
                state = state,
                onStart = viewModel::start,
                onStop = viewModel::stop,
                onRefresh = viewModel::refresh,
                onSetConstellation = viewModel::setConstellation,
            )
        }

        composable(Routes.DEVICE) {
            val viewModel: DeviceViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            DeviceScreen(state = state, onRefresh = viewModel::refresh)
        }

        composable(Routes.SIM) {
            val viewModel: SimViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SimScreen(state = state, onRefresh = viewModel::refresh)
        }

        composable(Routes.SKY_PLOT) {
            val viewModel: SkyPlotViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SkyPlotScreen(
                state = state,
                onSetConstellation = viewModel::setConstellation,
                onSetShowUsedOnly = viewModel::setShowUsedOnly,
            )
        }

        composable(Routes.TRACK) {
            val viewModel: TrackViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            TrackScreen(
                state = state,
                onStart = viewModel::start,
                onStop = viewModel::stop,
                onClear = viewModel::clear,
                onSave = viewModel::saveToHistory,
            )
        }

        composable(Routes.ACCURACY) {
            val viewModel: AccuracyViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            AccuracyScreen(
                state = state,
                onStart = viewModel::start,
                onStop = viewModel::stop,
                onClear = viewModel::clear,
            )
        }

        composable(Routes.HISTORY) {
            val viewModel: HistoryViewModel = appViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            HistoryScreen(
                state = state,
                onSetFilter = viewModel::setFilter,
                onDelete = viewModel::delete,
                onClearAll = viewModel::clearAll,
            )
        }

        composable(Routes.PERMISSIONS) {
            PermissionsScreen()
        }

        composable(Routes.ABOUT) {
            AboutScreen()
        }

        composable(Routes.MORE) {
            MoreScreen(
                preferences = preferences,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                onOpenAbout = { navController.navigate(Routes.ABOUT) { launchSingleTop = true } },
                privacyMode = privacyMode,
                onPrivacyModeChange = onPrivacyModeChange,
            )
        }
    }
}

private fun titleFor(route: String): String = when (route) {
    Routes.DASHBOARD -> "Panel de señales"
    Routes.WIFI -> "Wi-Fi"
    Routes.SCAN -> "Escáner de redes"
    Routes.CELLULAR -> "Red móvil"
    Routes.GNSS -> "GNSS"
    Routes.DEVICE -> "Dispositivo"
    Routes.SIM -> "SIM"
    Routes.SKY_PLOT -> "Cielo GNSS"
    Routes.TRACK -> "Mapa y ruta"
    Routes.ACCURACY -> "Precisión GPS"
    Routes.HISTORY -> "Historial"
    Routes.PERMISSIONS -> "Permisos"
    Routes.ABOUT -> "Información"
    Routes.MORE -> "Más"
    else -> "Señales"
}