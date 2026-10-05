package com.diagnostico.senales.ui.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diagnostico.senales.core.util.NO_DISPONIBLE
import com.diagnostico.senales.core.util.assessWifi
import com.diagnostico.senales.core.util.formatBytes
import com.diagnostico.senales.core.util.formatClock
import com.diagnostico.senales.core.util.formatDecimal
import com.diagnostico.senales.core.util.formatDuration
import com.diagnostico.senales.core.util.formatMs
import com.diagnostico.senales.core.util.formatMbps
import com.diagnostico.senales.core.util.rssiToQuality
import com.diagnostico.senales.core.util.valueOrUnavailable
import com.diagnostico.senales.data.connectivity.ConnectivityRepository
import com.diagnostico.senales.data.history.HistoryRepository
import com.diagnostico.senales.data.model.HistoryRecord
import com.diagnostico.senales.data.model.PingMethod
import com.diagnostico.senales.data.model.PingSummary
import com.diagnostico.senales.data.model.ScanResultSnapshot
import com.diagnostico.senales.data.model.StabilityReport
import com.diagnostico.senales.data.model.StabilitySample
import com.diagnostico.senales.data.model.SpeedTestResult
import com.diagnostico.senales.data.model.TestType
import com.diagnostico.senales.data.model.WifiConnectionInfo
import com.diagnostico.senales.data.net.PingEngine
import com.diagnostico.senales.data.net.SpeedTestEngine
import com.diagnostico.senales.data.prefs.AppPreferences
import com.diagnostico.senales.data.wifi.WifiRepository
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.Gauge
import com.diagnostico.senales.ui.components.InfoTile
import com.diagnostico.senales.ui.components.LineChart
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.SectionCard
import com.diagnostico.senales.ui.components.VerdictCard
import com.diagnostico.senales.ui.components.signalLevelColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.diagnostico.senales.ui.privacy.masked
import com.diagnostico.senales.ui.privacy.maskedIp
import kotlin.math.roundToInt

sealed interface StabilityUiState {
    data object Idle : StabilityUiState
    data class Running(
        val totalSeconds: Int,
        val elapsedSeconds: Int,
        val samples: List<StabilitySample>,
    ) : StabilityUiState

    data class Finished(val report: StabilityReport) : StabilityUiState
}

data class WifiUiState(
    val connection: WifiConnectionInfo? = null,
    val rssiHistory: List<Float?> = emptyList(),
    val scan: ScanResultSnapshot? = null,
    val isScanning: Boolean = false,
    val pingMethod: PingMethod = PingMethod.AUTO,
    val ping: PingSummary? = null,
    val isPinging: Boolean = false,
    val speed: SpeedTestResult? = null,
    val isTestingSpeed: Boolean = false,
    val speedStage: String = "",
    val speedProgress: Float = 0f,
    val stability: StabilityUiState = StabilityUiState.Idle,
    val pingTarget: String = "",
    val pingCount: Int = 10,
    val downloadUrl: String = "",
    val uploadUrl: String = "",
    val message: String? = null,
)

class WifiViewModel(
    private val wifi: WifiRepository,
    private val connectivity: ConnectivityRepository,
    private val pingEngine: PingEngine,
    private val speedTestEngine: SpeedTestEngine,
    private val historyRepository: HistoryRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WifiUiState(
            pingTarget = preferences.pingTarget,
            pingCount = preferences.pingCount,
            downloadUrl = preferences.downloadUrl,
            uploadUrl = preferences.uploadUrl,
        ),
    )
    val state: StateFlow<WifiUiState> = _state.asStateFlow()

    private var stabilityJob: Job? = null
    private var speedJob: Job? = null
    private var pingJob: Job? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                pollConnection()
                delay(POLL_INTERVAL_MILLIS)
            }
        }
        refreshScan()
    }

    fun refreshConnection() {
        viewModelScope.launch { pollConnection() }
    }

    fun refreshScan() {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true) }
            val snapshot = wifi.scan()
            _state.update {
                it.copy(
                    scan = snapshot,
                    isScanning = false,
                    message = snapshot.message,
                )
            }
            pollConnection()
        }
    }

    fun setPingMethod(method: PingMethod) {
        _state.update { it.copy(pingMethod = method) }
    }

    fun savePingSettings(target: String, count: Int) {
        preferences.pingTarget = target.trim()
        preferences.pingCount = count
        _state.update { it.copy(pingTarget = target.trim(), pingCount = count) }
    }

    fun saveSpeedUrls(download: String, upload: String) {
        preferences.downloadUrl = download.trim()
        preferences.uploadUrl = upload.trim()
        _state.update {
            it.copy(downloadUrl = download.trim(), uploadUrl = upload.trim())
        }
    }

    fun runPing() {
        if (pingJob?.isActive == true) return
        pingJob = viewModelScope.launch {
            _state.update { it.copy(isPinging = true) }
            val summary = pingEngine.ping(
                host = _state.value.pingTarget,
                count = _state.value.pingCount,
                timeoutMillis = 2_000,
                intervalMillis = 350L,
                method = _state.value.pingMethod,
            )
            _state.update { it.copy(ping = summary, isPinging = false) }
        }
    }

    fun runSpeedTest() {
        if (speedJob?.isActive == true) return
        val current = _state.value
        speedJob = viewModelScope.launch {
            _state.update {
                it.copy(isTestingSpeed = true, speedStage = "Descargando", speedProgress = 0f)
            }
            val result = speedTestEngine.run(
                downloadUrl = current.downloadUrl,
                uploadUrl = current.uploadUrl,
                onDownloadProgress = { read, total ->
                    _state.update {
                        it.copy(
                            speedStage = "Descargando",
                            speedProgress = if (total > 0) read.toFloat() / total else 0f,
                        )
                    }
                },
                onUploadProgress = { sent, total ->
                    _state.update {
                        it.copy(
                            speedStage = "Subiendo",
                            speedProgress = if (total > 0) sent.toFloat() / total else 0f,
                        )
                    }
                },
            )
            _state.update {
                it.copy(
                    speed = result,
                    isTestingSpeed = false,
                    speedStage = "",
                    speedProgress = 0f,
                )
            }
            if (result.downloadMbps != null) {
                historyRepository.add(
                    HistoryRecord(
                        id = historyRepository.nextId(),
                        timestampMillis = System.currentTimeMillis(),
                        type = TestType.WIFI_SPEED,
                        transportLabel = "Wi-Fi",
                        networkName = _state.value.connection?.ssid,
                        downloadMbps = result.downloadMbps,
                        uploadMbps = result.uploadMbps,
                        pingMs = _state.value.ping?.averageMs,
                        jitterMs = _state.value.ping?.jitterMs,
                        lossPercent = _state.value.ping?.lossPercent,
                        signalDbm = _state.value.connection?.rssi,
                        gpsAccuracyMeters = null,
                        satellitesUsed = null,
                        verdict = null,
                        note = result.downloadError ?: result.uploadError,
                    ),
                )
            }
        }
    }

    fun startStability(seconds: Int) {
        if (stabilityJob?.isActive == true) return
        stabilityJob = viewModelScope.launch {
            val target = _state.value.pingTarget
            val samples = mutableListOf<StabilitySample>()
            var disconnections = 0
            var wasConnected = true

            _state.update {
                it.copy(stability = StabilityUiState.Running(seconds, 0, emptyList()))
            }

            for (second in 1..seconds) {
                if (!isActive) return@launch
                val info = withContext(Dispatchers.IO) { wifi.connectionInfo() }
                val validated = info.isConnected && info.isInternetValidated
                if (!validated) disconnections++
                if (wasConnected && !validated) {
                    // cuenta cada transición, no cada segundo sin señal
                }
                wasConnected = validated

                val probe = pingEngine.ping(
                    host = target,
                    count = 1,
                    timeoutMillis = 1_500,
                    intervalMillis = 0L,
                    method = _state.value.pingMethod,
                )

                val sample = StabilitySample(
                    second = second,
                    rssi = info.rssi,
                    linkSpeedMbps = info.linkSpeedMbps,
                    latencyMs = probe.averageMs,
                    lossPercent = probe.lossPercent,
                    downloadMbps = null,
                    hasInternet = validated,
                    note = if (validated) null else "Sin validación de Internet en este segundo",
                )
                samples += sample
                _state.update {
                    it.copy(
                        stability = StabilityUiState.Running(seconds, second, samples.toList()),
                    )
                }
                delay(STABILITY_TICK_MILLIS)
            }

            val speed = speedTestEngine.run(
                downloadUrl = _state.value.downloadUrl,
                uploadUrl = _state.value.uploadUrl,
                downloadBytes = STABILITY_DOWNLOAD_BYTES,
                uploadBytes = STABILITY_UPLOAD_BYTES,
            )

            val rssiValues = samples.mapNotNull { it.rssi }
            val latencyValues = samples.mapNotNull { it.latencyMs }
            val averageRssi = rssiValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            val averageLatency = latencyValues.takeIf { it.isNotEmpty() }?.average()
            val averageLoss = if (samples.isEmpty()) {
                null
            } else {
                samples.count { it.latencyMs == null } * 100.0 / samples.size
            }
            val assessment = assessWifi(averageRssi, averageLatency, averageLoss)

            val report = StabilityReport(
                target = target,
                requestedSeconds = seconds,
                elapsedSeconds = samples.size,
                averageRssi = averageRssi,
                minRssi = rssiValues.minOrNull(),
                maxRssi = rssiValues.maxOrNull(),
                averageLatencyMs = averageLatency,
                averageLossPercent = averageLoss,
                averageDownloadMbps = speed.downloadMbps,
                disconnections = disconnections,
                samples = samples.toList(),
                assessment = assessment,
                explanation = buildString {
                    append("Se tomó una medición por segundo durante ")
                    append(formatDuration(samples.size * 1000L))
                    append(". ")
                    append(assessment.reasons.joinToString(" "))
                    if (disconnections > 0) {
                        append(" Además hubo ")
                        append(disconnections)
                        append(" segundo(s) sin conexión validada.")
                    } else {
                        append(" No se perdió la conexión en ningún momento.")
                    }
                },
            )

            _state.update { it.copy(stability = StabilityUiState.Finished(report)) }

            historyRepository.add(
                HistoryRecord(
                    id = historyRepository.nextId(),
                    timestampMillis = System.currentTimeMillis(),
                    type = TestType.WIFI_STABILITY,
                    transportLabel = "Wi-Fi",
                    networkName = _state.value.connection?.ssid,
                    downloadMbps = report.averageDownloadMbps,
                    uploadMbps = speed.uploadMbps,
                    pingMs = report.averageLatencyMs,
                    jitterMs = null,
                    lossPercent = report.averageLossPercent,
                    signalDbm = report.averageRssi,
                    gpsAccuracyMeters = null,
                    satellitesUsed = null,
                    verdict = report.assessment.verdict,
                    note = "${report.elapsedSeconds} s, ${report.disconnections} corte(s)",
                ),
            )
        }
    }

    fun stopStability() {
        stabilityJob?.cancel()
        stabilityJob = null
        _state.update { it.copy(stability = StabilityUiState.Idle) }
    }

    fun clearStability() {
        _state.update { it.copy(stability = StabilityUiState.Idle) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun pollConnection() {
        val info = withContext(Dispatchers.IO) {
            runCatching { wifi.connectionInfo() }.getOrNull()
        }
        val connectivityStatus = withContext(Dispatchers.IO) {
            runCatching { connectivity.current() }.getOrNull()
        }
        _state.update { current ->
            val validated = connectivityStatus?.isValidated ?: info?.isInternetValidated ?: false
            current.copy(
                connection = info?.copy(isInternetValidated = validated),
                rssiHistory = (current.rssiHistory + info?.rssi?.toFloat()).takeLast(90),
            )
        }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 3_000L
        const val STABILITY_TICK_MILLIS = 1_000L
        const val STABILITY_DOWNLOAD_BYTES = 6L * 1024L * 1024L
        const val STABILITY_UPLOAD_BYTES = 2L * 1024L * 1024L
    }
}

@Composable
fun WifiScreen(
    state: WifiUiState,
    onRefresh: () -> Unit,
    onScan: () -> Unit,
    onPing: () -> Unit,
    onSpeedTest: () -> Unit,
    onStartStability: (Int) -> Unit,
    onStopStability: () -> Unit,
    onSavePingSettings: (String, Int) -> Unit,
    onSaveSpeedUrls: (String, String) -> Unit,
    onSetPingMethod: (PingMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pingTarget by rememberSaveable { mutableStateOf(state.pingTarget) }
    var pingCount by rememberSaveable { mutableStateOf(state.pingCount.toString()) }
    var downloadUrl by rememberSaveable { mutableStateOf(state.downloadUrl) }
    var uploadUrl by rememberSaveable { mutableStateOf(state.uploadUrl) }
    var stabilitySeconds by rememberSaveable { mutableStateOf(preferencesDefaultSeconds()) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val connection = state.connection
            SectionCard(
                title = "Conexión actual",
                icon = Icons.Outlined.Wifi,
                subtitle = connection?.let { "${it.band.label} · canal ${it.channel ?: "?"}" },
                trailing = {
                    OutlinedButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
                    }
                },
            ) {
                if (connection == null) {
                    Text(NO_DISPONIBLE, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Gauge(
                            percent = rssiToQuality(connection.rssi),
                            label = "señal",
                            caption = connection.rssi?.let { "$it dBm" },
                            size = 118.dp,
                            color = connection.signalLevel?.let { signalLevelColor(it) }
                                ?: MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            MetricRow(
                                metrics = listOf<Pair<String, String?>>(
                                    "SSID" to valueOrUnavailable(masked(connection.ssid)),
                                    "BSSID" to valueOrUnavailable(masked(connection.bssid)),
                                    "Nivel" to (connection.signalLevel?.label ?: NO_DISPONIBLE),
                                    "Internet" to if (connection.isInternetValidated) {
                                        "Validado por el sistema"
                                    } else {
                                        "Sin validación de Internet"
                                    },
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    connection.unavailableNote?.let { note ->
                        ExplanationCard(text = note, isWarning = true)
                        Spacer(Modifier.height(10.dp))
                    }
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Dirección IPv4" to valueOrUnavailable(maskedIp(connection.ipAddress)),
                            "Direcciones IPv6" to if (connection.ipv6Addresses.isEmpty()) {
                                NO_DISPONIBLE
                            } else {
                                maskedIp(connection.ipv6Addresses.joinToString(", "))
                            },
                            "Gateway" to valueOrUnavailable(maskedIp(connection.gateway)),
                            "Máscara (prefijo)" to (connection.subnetPrefixLength?.let { "/$it" }
                                ?: NO_DISPONIBLE),
                            "DNS" to if (connection.dnsServers.isEmpty()) {
                                NO_DISPONIBLE
                            } else {
                                maskedIp(connection.dnsServers.joinToString(", "))
                            },
                            "Frecuencia" to (connection.frequencyMhz?.let { "$it MHz" }
                                ?: NO_DISPONIBLE),
                            "Canal" to (connection.channel?.toString() ?: NO_DISPONIBLE),
                            "Banda" to connection.band.label,
                            "Seguridad" to valueOrUnavailable(connection.security),
                            "Estándar" to valueOrUnavailable(connection.standard),
                            "Velocidad de enlace" to (connection.linkSpeedMbps?.let {
                                "$it Mbps (teórica del enlace Wi-Fi)"
                            } ?: NO_DISPONIBLE),
                            "RSSI" to (connection.rssi?.let { "$it dBm" } ?: NO_DISPONIBLE),
                            "Ancho de canal" to (connection.channelWidthMhz?.let { "$it MHz" }
                                ?: NO_DISPONIBLE),
                            "MAC" to valueOrUnavailable(masked(connection.macAddress)),
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    ExplanationCard(text = "Velocidad de enlace y velocidad de Internet no son lo mismo. El enlace es el máximo teórico entre el router y el dispositivo; la velocidad real se mide transferring datos desde Internet.")
                }
            }
        }

        item {
            SectionCard(
                title = "Señal en el tiempo",
                icon = Icons.Outlined.Timer,
                subtitle = "Últimas lecturas de RSSI",
            ) {
                if (state.rssiHistory.none { it != null }) {
                    Text(
                        "Sin lecturas todavía",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LineChart(
                        values = state.rssiHistory,
                        yFormatter = { "${it.toInt()}" },
                        height = 150.dp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "RSSI en dBm: menos es peor. Por debajo de -75 dBm la " +
                            "conexión empieza a perder estabilidad.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Escaneo de redes",
                icon = Icons.Outlined.Radar,
                subtitle = state.scan?.let {
                    "${it.networks.size} redes · ${formatClock(it.scannedAt)}"
                },
                trailing = {
                    OutlinedButton(onClick = onScan, enabled = !state.isScanning) {
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(16.dp).height(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Escanear")
                        }
                    }
                },
            ) {
                val scan = state.scan
                if (scan == null) {
                    Text(
                        "Sin escaneo todavía",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.message?.let { message ->
                        ExplanationCard(text = message, isWarning = scan.isThrottled)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (scan.networks.isEmpty()) {
                        Text(
                            "El sistema no devolvió redes.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        val current = scan.networks.firstOrNull { it.isCurrentNetwork }
                        current?.let {
                            ExplanationCard(
                                text = "Red conectada: ${masked(it.ssid) ?: "SSID oculto"} en canal " +
                                    "${it.channel ?: "?"} (${it.frequencyMhz} MHz, " +
                                    "${it.security}).",
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Latencia, jitter y pérdida", icon = Icons.Outlined.Speed) {
                OutlinedTextField(
                    value = pingTarget,
                    onValueChange = { pingTarget = it },
                    label = { Text("Destino") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pingCount,
                    onValueChange = { pingCount = it.filter { char -> char.isDigit() }.take(2) },
                    label = { Text("Número de mediciones") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PingMethod.entries.size) { index ->
                        val method = PingMethod.entries[index]
                        FilterChip(
                            selected = state.pingMethod == method,
                            onClick = { onSetPingMethod(method) },
                            label = { Text(method.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onSavePingSettings(
                                pingTarget,
                                pingCount.toIntOrNull()?.coerceIn(3, 50) ?: 10,
                            )
                            onPing()
                        },
                        enabled = !state.isPinging,
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Medir")
                    }
                    if (state.isPinging) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp).height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                state.ping?.let { ping ->
                    Spacer(Modifier.height(12.dp))
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Latencia mínima" to formatMs(ping.minMs),
                            "Latencia máxima" to formatMs(ping.maxMs),
                            "Latencia media" to formatMs(ping.averageMs),
                            "Jitter" to formatMs(ping.jitterMs),
                            "Paquetes enviados" to "${ping.sent}",
                            "Paquetes recibidos" to "${ping.received}",
                            "Pérdida" to formatDecimal(ping.lossPercent, 1, " %"),
                            "Resolución DNS" to formatMs(ping.dnsResolutionMs),
                            "Método real" to ping.methodLabel,
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    LineChart(
                        values = ping.samples.map { it.latencyMs?.toFloat() },
                        yFormatter = { "${it.toInt()}" },
                        height = 150.dp,
                    )
                    ping.note?.let { note ->
                        Spacer(Modifier.height(8.dp))
                        ExplanationCard(text = note)
                    }
                }
            }
        }

        item {
            SectionCard(title = "Velocidad de Internet", icon = Icons.Outlined.Download) {
                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = { Text("URL de descarga") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uploadUrl,
                    onValueChange = { uploadUrl = it },
                    label = { Text("URL de subida") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Los extremos por defecto son públicos y no requieren clave. " +
                        "Si tu red los bloquea, cambia la URL por el equivalente de tu " +
                        "proveedor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSaveSpeedUrls(downloadUrl, uploadUrl)
                            onSpeedTest()
                        },
                        enabled = !state.isTestingSpeed,
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Iniciar")
                    }
                    if (state.isTestingSpeed) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp).height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                if (state.isTestingSpeed) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.speedStage, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.speedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.speed?.let { speed ->
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoTile(
                            label = "Descarga",
                            value = formatMbps(speed.downloadMbps),
                            icon = Icons.Outlined.Download,
                            modifier = Modifier.weight(1f),
                        )
                        InfoTile(
                            label = "Subida",
                            value = formatMbps(speed.uploadMbps),
                            icon = Icons.Outlined.Upload,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Datos descargados" to formatBytes(speed.downloadedBytes),
                            "Datos subidos" to formatBytes(speed.uploadedBytes),
                            "Tiempo de descarga" to formatDecimal(
                                speed.downloadSeconds,
                                2,
" s",
                            ),
                            "Tiempo de subida" to formatDecimal(speed.uploadSeconds, 2, " s"),
                            "Extremo de descarga" to valueOrUnavailable(speed.downloadEndpoint),
                            "Extremo de subida" to valueOrUnavailable(speed.uploadEndpoint),
                        ),
                    )
                    speed.downloadError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        ExplanationCard(text = "Error en la descarga: $error", isWarning = true)
                    }
                    speed.uploadError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        ExplanationCard(text = "Error en la subida: $error", isWarning = true)
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Diagnóstico de estabilidad",
                icon = Icons.Outlined.Timer,
                subtitle = "Registra RSSI, latencia, pérdida y velocidad durante un periodo",
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(STABILITY_OPTIONS.size) { index ->
                        val option = STABILITY_OPTIONS[index]
                        FilterChip(
                            selected = stabilitySeconds == option,
                            onClick = { stabilitySeconds = option },
                            label = { Text(option.toString()) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (val stability = state.stability) {
                    StabilityUiState.Idle -> {
                        Button(onClick = { onStartStability(stabilitySeconds) }) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Iniciar prueba de $stabilitySeconds s")
                        }
                    }

                    is StabilityUiState.Running -> {
                        MetricRow(
                            metrics = listOf<Pair<String, String?>>(
                                "Progreso" to "${stability.elapsedSeconds} de " +
                                    "${stability.totalSeconds} s",
                                "Muestras" to "${stability.samples.size}",
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                stability.elapsedSeconds.toFloat() / stability.totalSeconds
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onStopStability) {
                            Icon(Icons.Outlined.Stop, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Detener")
                        }
                        Spacer(Modifier.height(12.dp))
                        StabilityCharts(samples = stability.samples)
                    }

                    is StabilityUiState.Finished -> {
                        VerdictCard(
                            verdict = stability.report.assessment.verdict,
                            score = stability.report.assessment.score,
                            reasons = stability.report.assessment.reasons,
                        )
                        Spacer(Modifier.height(10.dp))
                        MetricRow(
                            metrics = listOf<Pair<String, String?>>(
                                "Duración real" to formatDuration(
                                    stability.report.elapsedSeconds * 1000L,
                                ),
                                "RSSI medio" to (stability.report.averageRssi?.let { "$it dBm" }
                                    ?: NO_DISPONIBLE),
                                "RSSI mínimo" to (stability.report.minRssi?.let { "$it dBm" }
                                    ?: NO_DISPONIBLE),
                                "RSSI máximo" to (stability.report.maxRssi?.let { "$it dBm" }
                                    ?: NO_DISPONIBLE),
                                "Latencia media" to formatMs(stability.report.averageLatencyMs),
                                "Pérdida media" to formatDecimal(
                                    stability.report.averageLossPercent,
                                    1,
                                    " %",
                                ),
                                "Descarga media" to formatMbps(
                                    stability.report.averageDownloadMbps,
                                ),
                                "Segundos sin conexión" to stability.report.disconnections.toString(),
                                "Muestras registradas" to stability.report.samples.size.toString(),
                            ),
                        )
                        Spacer(Modifier.height(10.dp))
                        ExplanationCard(text = stability.report.explanation)
                        Spacer(Modifier.height(12.dp))
                        StabilityCharts(samples = stability.report.samples)
                    }
                }
            }
        }

        item {
            ExplanationCard(
                text = "Todas las pruebas usan la red tal y como está configurada el " +
                    "dispositivo. Si hay VPN, datos móviles activados o un proxy " +
                    "corporativo, los resultados los reflejan.",
            )
        }
    }
}

@Composable
private fun StabilityCharts(samples: List<StabilitySample>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LineChart(
            values = samples.map { it.rssi?.toFloat() },
            color = MaterialTheme.colorScheme.primary,
            yFormatter = { "${it.toInt()}" },
            height = 140.dp,
        )
        LineChart(
            values = samples.map { it.latencyMs?.toFloat() },
            color = MaterialTheme.colorScheme.secondary,
            yFormatter = { "${it.toInt()}" },
            height = 140.dp,
        )
    }
}

private val STABILITY_OPTIONS = listOf(10, 30, 60, 300)

private fun preferencesDefaultSeconds(): Int = STABILITY_OPTIONS[1]