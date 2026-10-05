package com.diagnostico.senales

import android.app.Application
import android.content.Context
import com.diagnostico.senales.data.cellular.CellularRepository
import com.diagnostico.senales.data.connectivity.ConnectivityRepository
import com.diagnostico.senales.data.device.DeviceRepository
import com.diagnostico.senales.data.gnss.GnssRepository
import com.diagnostico.senales.data.history.HistoryRepository
import com.diagnostico.senales.data.net.CellularNetworkProvider
import com.diagnostico.senales.data.net.HttpTransfer
import com.diagnostico.senales.data.net.PingEngine
import com.diagnostico.senales.data.net.SpeedTestEngine
import com.diagnostico.senales.data.prefs.AppPreferences
import com.diagnostico.senales.data.wifi.WifiRepository
import com.diagnostico.senales.ui.components.DismissedNoteStore

/**
 * Contenedor de dependencias. Se crea una sola vez en [DiagnosticsApplication] y
 * los ViewModel lo reciben por constructor: no hay singletons repartidos.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val preferences: AppPreferences = AppPreferences(appContext)
    val deviceRepository: DeviceRepository = DeviceRepository(appContext)
    val connectivityRepository: ConnectivityRepository = ConnectivityRepository(appContext)
    val wifiRepository: WifiRepository = WifiRepository(appContext)
    val cellularRepository: CellularRepository = CellularRepository(appContext)
    val gnssRepository: GnssRepository = GnssRepository(appContext)
    val historyRepository: HistoryRepository = HistoryRepository(appContext)

    val httpTransfer: HttpTransfer = HttpTransfer()
    val pingEngine: PingEngine = PingEngine(httpTransfer)
    val speedTestEngine: SpeedTestEngine = SpeedTestEngine(httpTransfer)
    val cellularNetworkProvider: CellularNetworkProvider = CellularNetworkProvider(appContext)

    /**
     * Avisos y notas ya cerrados por el usuario. Al vivir aquí, y no en disco,
     * se olvidan en cuanto la app se cierra del todo.
     */
    val dismissedNotes: DismissedNoteStore = DismissedNoteStore()
}

class DiagnosticsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Acceso corto al contenedor desde cualquier Composable con contexto. */
val Context.appContainer: AppContainer
    get() = (applicationContext as DiagnosticsApplication).container