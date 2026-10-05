package com.diagnostico.senales.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diagnostico.senales.core.util.NO_DISPONIBLE
import com.diagnostico.senales.core.util.formatAge
import com.diagnostico.senales.core.util.formatBytes
import com.diagnostico.senales.core.util.formatClock
import com.diagnostico.senales.core.util.formatDecimal
import com.diagnostico.senales.core.util.formatDistance
import com.diagnostico.senales.core.util.formatMs
import com.diagnostico.senales.core.util.formatMbps
import com.diagnostico.senales.core.util.qualityFromAccuracy
import com.diagnostico.senales.core.util.rssiToQuality
import com.diagnostico.senales.data.cellular.CellularRepository
import com.diagnostico.senales.data.connectivity.ConnectivityRepository
import com.diagnostico.senales.data.device.DeviceRepository
import com.diagnostico.senales.data.gnss.GnssRepository
import com.diagnostico.senales.data.history.HistoryRepository
import com.diagnostico.senales.data.model.CellularSnapshot
import com.diagnostico.senales.data.model.ConnectivityStatus
import com.diagnostico.senales.data.model.DeviceSnapshot
import com.diagnostico.senales.data.model.GnssFix
import com.diagnostico.senales.data.model.PingSummary
import com.diagnostico.senales.data.model.SatelliteInfo
import com.diagnostico.senales.data.model.WifiConnectionInfo
import com.diagnostico.senales.data.net.PingEngine
import com.diagnostico.senales.data.net.SpeedTestEngine
import com.diagnostico.senales.data.wifi.WifiRepository
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.Gauge
import com.diagnostico.senales.ui.components.InfoTile
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.SectionCard
import com.diagnostico.senales.ui.components.signalLevelColor
import com.diagnostico.senales.ui.nav.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.diagnostico.senales.ui.privacy.masked
import com.diagnostico.senales.ui.privacy.maskedIp
import com.diagnostico.senales.ui.privacy.maskedOperator
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val isRefreshing: Boolean = true,
    val connectivity: ConnectivityStatus? = null,
    val device: DeviceSnapshot? = null,
    val wifi: WifiConnectionInfo? = null,
    val cellular: CellularSnapshot? = null,
    val gnssFix: GnssFix? = null,
    val satellitesVisible: Int = 0,
    val satellitesUsed: Int = 0,
    val ping: PingSummary? = null,
    val isPinging: Boolean = false,
    val lastDownloadMbps: Double? = null,
    val lastSpeedLabel: String? = null,
)

class DashboardViewModel(
    private val connectivity: ConnectivityRepository,
    private val device: DeviceRepository,
    private val wifi: WifiRepository,
    private val cellular: CellularRepository,
    private val gnss: GnssRepository,
    private val pingEngine: PingEngine,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { historyRepository.load() }
        viewModelScope.launch {
            runCatching { gnss.start() }
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    override fun onCleared() {
        gnss.stop()
        super.onCleared()
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    fun runPing() {
        if (_state.value.isPinging) return
        viewModelScope.launch {
            _state.update { it.copy(isPinging = true) }
            val summary = pingEngine.ping(
                host = SpeedTestEngine.DEFAULT_PING_TARGET,
                count = 8,
                timeoutMillis = 1_500,
                intervalMillis = 350L,
            )
            _state.update { it.copy(ping = summary, isPinging = false) }
        }
    }

    private suspend fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        val payload = withContext(Dispatchers.IO) {
            RefreshPayload(
                connectivity = runCatching { connectivity.current() }.getOrNull(),
                device = runCatching { device.snapshot() }.getOrNull(),
                wifi = runCatching { wifi.connectionInfo() }.getOrNull(),
                cellular = runCatching { cellular.snapshot() }.getOrNull(),
                satellites = gnss.satellites.value,
                fix = gnss.fix.value,
            )
        }

        val latestSpeed = historyRepository.records.value
            .firstOrNull { it.downloadMbps != null }
            ?.takeIf { System.currentTimeMillis() - it.timestampMillis < SPEED_FRESHNESS_MILLIS }

        _state.update { current ->
            current.copy(
                isRefreshing = false,
                connectivity = payload.connectivity,
                device = payload.device,
                wifi = payload.wifi,
                cellular = payload.cellular,
                satellitesVisible = payload.satellites.size,
                satellitesUsed = payload.satellites.count { it.usedInFix },
                gnssFix = payload.fix,
                lastDownloadMbps = latestSpeed?.downloadMbps,
                lastSpeedLabel = latestSpeed?.let {
                    "${it.type.shortLabel} · ${formatClock(it.timestampMillis)}"
                },
            )
        }
    }

    private data class RefreshPayload(
        val connectivity: ConnectivityStatus?,
        val device: DeviceSnapshot?,
        val wifi: WifiConnectionInfo?,
        val cellular: CellularSnapshot?,
        val satellites: List<SatelliteInfo>,
        val fix: GnssFix?,
    )

    private companion object {
        const val REFRESH_INTERVAL_MILLIS = 3_000L
        const val SPEED_FRESHNESS_MILLIS = 30 * 60 * 1000L
    }
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Estado general",
                icon = Icons.Outlined.Bolt,
                subtitle = state.connectivity?.let {
                    "${it.internetStatusLabel} · ${formatAge(System.currentTimeMillis() - it.capturedAt)}"
                },
                trailing = {
                    OutlinedButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Actualizar")
                    }
                },
            ) {
                val connectivity = state.connectivity
                val transports = connectivity?.transports?.joinToString(", ")
                    .orEmpty()
                    .ifEmpty { "Ninguna" }
                MetricRow(
                    metrics = listOf<Pair<String, String?>>(
                        "Transporte activo" to (connectivity?.primaryTransport ?: "Sin red"),
                        "Transportes disponibles" to transports,
                        "Interfaz de red" to (connectivity?.interfaceName ?: NO_DISPONIBLE),
                        "DNS" to (maskedIp(connectivity?.dnsServers?.joinToString(", ")) ?: NO_DISPONIBLE),
                        "Red con coste" to if (connectivity?.metered == true) "Sí" else "No",
                    ),
                )
                if (connectivity != null && connectivity.isActive && !connectivity.isValidated) {
                    Spacer(Modifier.height(10.dp))
                    ExplanationCard(
                        text = "Hay red activa pero el sistema no ha validado el acceso a " +
                            "Internet: puede ser un portal cautivo o un corte de la salida.",
                        isWarning = true,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
val wifi = state.wifi
                InfoTile(
                    label = "Wi-Fi",
                    // Sin SSID no significa sin conexión: si hay IP ya estamos
                    // conectados y lo que Android oculta es el nombre de la red.
                    value = when {
                        wifi == null -> NO_DISPONIBLE
                        wifi.ssid != null -> masked(wifi.ssid) ?: NO_DISPONIBLE
                        wifi.isConnected -> "Conectado · SSID oculto"
                        wifi.isWifiEnabled -> "Sin conectar"
                        else -> "Wi-Fi desactivado"
                    },
                    secondary = wifi?.let {
                        "${it.rssi?.toString() ?: "—"} dBm · ${it.signalLevel?.label ?: "—"}"
                    } ?: NO_DISPONIBLE,
                    icon = Icons.Outlined.Wifi,
                    accent = wifi?.signalLevel?.let { level -> signalLevelColor(level) },
                    modifier = Modifier.weight(1f),
                )
                val cellular = state.cellular
                InfoTile(
                    label = "Red móvil",
                    value = cellular?.networkGeneration?.let { "Generación $it" }
                        ?: maskedOperator(cellular?.operatorName)
                        ?: NO_DISPONIBLE,
                    secondary = cellular?.signalDbm?.let { "$it dBm" } ?: NO_DISPONIBLE,
                    icon = Icons.Outlined.SignalCellularAlt,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTile(
                    label = "Intensidad Wi-Fi",
                    value = "${rssiToQuality(state.wifi?.rssi)?.toString() ?: "—"} %",
                    secondary = state.wifi?.frequencyMhz?.let { "$it MHz" } ?: NO_DISPONIBLE,
                    icon = Icons.Outlined.Wifi,
                    modifier = Modifier.weight(1f),
                )
                InfoTile(
                    label = "Intensidad móvil",
                    value = "${state.cellular?.signalQualityPercent?.toString() ?: "—"} %",
                    secondary = state.cellular?.networkTypeLabel ?: NO_DISPONIBLE,
                    icon = Icons.Outlined.SignalCellularAlt,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard(
                title = "Latencia",
                icon = Icons.Outlined.Speed,
                subtitle = "Objetivo ${SpeedTestEngine.DEFAULT_PING_TARGET}",
                trailing = {
                    Button(onClick = onPing, enabled = !state.isPinging) {
                        if (state.isPinging) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Medir")
                        }
                    }
                },
            ) {
                val ping = state.ping
                if (ping == null) {
                    Text(
                        text = "Sin medición todavía.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Latencia media" to formatMs(ping.averageMs),
                            "Mínima / máxima" to "${formatMs(ping.minMs)} / ${formatMs(ping.maxMs)}",
                            "Jitter" to formatMs(ping.jitterMs),
                            "Pérdida de paquetes" to formatDecimal(ping.lossPercent, 1, " %"),
                            "Método" to ping.methodLabel,
                            "Destino" to ping.target,
                        ),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTile(
                    label = "Velocidad de descarga",
                    value = formatMbps(state.lastDownloadMbps),
                    secondary = state.lastSpeedLabel ?: "Ejecuta una prueba de velocidad",
                    icon = Icons.Outlined.Speed,
                    modifier = Modifier.weight(1f),
                )
                InfoTile(
                    label = "GNSS",
                    value = "${state.satellitesUsed} en uso",
                    secondary = "${state.satellitesVisible} visibles",
                    icon = Icons.Outlined.SatelliteAlt,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard(title = "Precisión GNSS", icon = Icons.Outlined.MyLocation) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(
                        percent = qualityFromAccuracy(
                            state.gnssFix?.horizontalAccuracyMeters,
                        ),
                        label = "calidad",
                        size = 110.dp,
                        color = MaterialTheme.colorScheme.secondary,
                        caption = "estimada",
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        MetricRow(
                            metrics = listOf<Pair<String, String?>>(
                                "Precisión horizontal" to formatDistance(
                                    state.gnssFix?.horizontalAccuracyMeters?.toDouble(),
                                ),
                                "Precisión vertical" to formatDistance(
                                    state.gnssFix?.verticalAccuracyMeters?.toDouble(),
                                ),
                                "Satélites en uso" to state.satellitesUsed.toString(),
                                "Antigüedad de la fixación" to formatAge(
                                    state.gnssFix?.fixAgeMillis ?: 0L,
                                ),
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "El porcentaje se deriva de la precisión que declara " +
                                "el sistema, no del CN0 de cada satélite.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Resumen del dispositivo", icon = Icons.Outlined.Bluetooth) {
                val device = state.device
                MetricRow(
                    metrics = listOf<Pair<String, String?>>(
                        "Modelo" to listOfNotNull(
                            device?.manufacturer?.takeIf { it.isNotBlank() },
                            device?.model?.takeIf { it.isNotBlank() },
                        ).joinToString(" ").ifEmpty { NO_DISPONIBLE },
                        "Android" to (device?.androidRelease?.let { "$it (API ${device.sdkInt})" }
                            ?: NO_DISPONIBLE),
                        "Batería" to (device?.batteryLevelPercent?.let { "$it %" }
                            ?: NO_DISPONIBLE),
                        "Bluetooth" to (device?.bluetoothState ?: NO_DISPONIBLE),
                        "Memoria libre" to (device?.let { formatBytes(it.availableRamBytes) }
                            ?: NO_DISPONIBLE),
                    ),
                )
            }
        }

        item {
            SectionCard(title = "Ir a un módulo", icon = Icons.Outlined.Sensors) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Routes.DEVICE to "Diagnóstico del dispositivo",
                        Routes.SIM to "Información de la SIM",
                        Routes.SKY_PLOT to "Cielo GNSS (sky plot)",
                        Routes.TRACK to "Mapa GPS y ruta",
                        Routes.ACCURACY to "Prueba de precisión GPS",
                        Routes.HISTORY to "Historial de pruebas",
                    ).forEach { (route, label) ->
                        OutlinedButton(
                            onClick = { onNavigate(route) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        item {
            ExplanationCard(
                text = "Las pruebas de velocidad, estabilidad y precisión se guardan en el " +
                    "historial con fecha, red utilizada y veredicto. Los datos solo " +
                    "permanecen en este dispositivo.",
            )
        }
    }
}