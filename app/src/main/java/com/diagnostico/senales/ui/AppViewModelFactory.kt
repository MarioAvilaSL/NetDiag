package com.diagnostico.senales.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diagnostico.senales.DiagnosticsApplication
import com.diagnostico.senales.ui.accuracy.AccuracyViewModel
import com.diagnostico.senales.ui.cellular.CellularViewModel
import com.diagnostico.senales.ui.dashboard.DashboardViewModel
import com.diagnostico.senales.ui.device.DeviceViewModel
import com.diagnostico.senales.ui.gnss.GnssViewModel
import com.diagnostico.senales.ui.gnss.SkyPlotViewModel
import com.diagnostico.senales.ui.history.HistoryViewModel
import com.diagnostico.senales.ui.scan.ScanViewModel
import com.diagnostico.senales.ui.sim.SimViewModel
import com.diagnostico.senales.ui.track.TrackViewModel
import com.diagnostico.senales.ui.wifi.WifiViewModel

/**
 * Fábrica única de ViewModel. Todos reciben el [com.diagnostico.senales.AppContainer]
 * por constructor, de modo que ninguno busca singletons por su cuenta.
 */
object AppViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
        val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
            as? DiagnosticsApplication
            ?: error("DiagnosticsApplication no está declarada en el manifiesto")
        val container = application.container

        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(
                connectivity = container.connectivityRepository,
                device = container.deviceRepository,
                wifi = container.wifiRepository,
                cellular = container.cellularRepository,
                gnss = container.gnssRepository,
                pingEngine = container.pingEngine,
                historyRepository = container.historyRepository,
            )

            modelClass.isAssignableFrom(DeviceViewModel::class.java) -> DeviceViewModel(
                device = container.deviceRepository,
            )

            modelClass.isAssignableFrom(WifiViewModel::class.java) -> WifiViewModel(
                wifi = container.wifiRepository,
                connectivity = container.connectivityRepository,
                pingEngine = container.pingEngine,
                speedTestEngine = container.speedTestEngine,
                historyRepository = container.historyRepository,
                preferences = container.preferences,
            )

            modelClass.isAssignableFrom(ScanViewModel::class.java) -> ScanViewModel(
                wifi = container.wifiRepository,
            )

            modelClass.isAssignableFrom(CellularViewModel::class.java) -> CellularViewModel(
                cellular = container.cellularRepository,
                pingEngine = container.pingEngine,
                speedTestEngine = container.speedTestEngine,
                networkProvider = container.cellularNetworkProvider,
                historyRepository = container.historyRepository,
                preferences = container.preferences,
            )

            modelClass.isAssignableFrom(SimViewModel::class.java) -> SimViewModel(
                cellular = container.cellularRepository,
            )

            modelClass.isAssignableFrom(GnssViewModel::class.java) -> GnssViewModel(
                gnss = container.gnssRepository,
            )

            modelClass.isAssignableFrom(SkyPlotViewModel::class.java) -> SkyPlotViewModel(
                gnss = container.gnssRepository,
            )

            modelClass.isAssignableFrom(TrackViewModel::class.java) -> TrackViewModel(
                gnss = container.gnssRepository,
                historyRepository = container.historyRepository,
            )

            modelClass.isAssignableFrom(AccuracyViewModel::class.java) -> AccuracyViewModel(
                gnss = container.gnssRepository,
                historyRepository = container.historyRepository,
            )

            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(
                historyRepository = container.historyRepository,
            )

            else -> error("ViewModel no registrado en AppViewModelFactory: ${modelClass.name}")
        } as T
    }
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(): VM = viewModel(factory = AppViewModelFactory)