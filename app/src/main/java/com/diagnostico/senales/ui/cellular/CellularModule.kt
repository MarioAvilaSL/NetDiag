package com.diagnostico.senales.ui.cellular

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diagnostico.senales.core.util.NO_DISPONIBLE
import com.diagnostico.senales.core.util.dbmToPercent
import com.diagnostico.senales.core.util.formatBytes
import com.diagnostico.senales.core.util.formatClock
import com.diagnostico.senales.core.util.formatDecimal
import com.diagnostico.senales.core.util.formatMs
import com.diagnostico.senales.core.util.formatMbps
import com.diagnostico.senales.core.util.valueOrUnavailable
import com.diagnostico.senales.data.cellular.CellularRepository
import com.diagnostico.senales.data.history.HistoryRepository
import com.diagnostico.senales.data.model.CellularSnapshot
import com.diagnostico.senales.data.model.HistoryRecord
import com.diagnostico.senales.data.model.PingMethod
import com.diagnostico.senales.data.model.PingSummary
import com.diagnostico.senales.data.model.SpeedTestResult
import com.diagnostico.senales.data.model.TestType
import com.diagnostico.senales.data.net.CellularNetworkProvider
import com.diagnostico.senales.data.net.PingEngine
import com.diagnostico.senales.data.net.SpeedTestEngine
import com.diagnostico.senales.data.prefs.AppPreferences
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.Gauge
import com.diagnostico.senales.ui.components.InfoTile
import com.diagnostico.senales.ui.components.LineChart
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.diagnostico.senales.ui.privacy.masked
import com.diagnostico.senales.ui.privacy.maskedOperator
import kotlinx.coroutines.withContext

data class CellularUiState(
    val snapshot: CellularSnapshot? = null,
    val signalHistory: List<Float?> = emptyList(),
    val qualityHistory: List<Float?> = emptyList(),
    val ping: PingSummary? = null,
    val isPinging: Boolean = false,
    val pingMethod: PingMethod = PingMethod.AUTO,
    val pingTarget: String = "",
    val speed: SpeedTestResult? = null,
    val isTestingSpeed: Boolean = false,
    val speedStage: String = "",
    val speedProgress: Float = 0f,
    val forceCellular: Boolean = true,
    val cellularNetworkAvailable: Boolean? = null,
    val downloadUrl: String = "",
    val uploadUrl: String = "",
)

class CellularViewModel(
    private val cellular: CellularRepository,
    private val pingEngine: PingEngine,
    private val speedTestEngine: SpeedTestEngine,
    private val networkProvider: CellularNetworkProvider,
    private val historyRepository: HistoryRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CellularUiState(
            pingTarget = preferences.pingTarget,
            downloadUrl = preferences.downloadUrl,
            uploadUrl = preferences.uploadUrl,
        ),
    )
    val state: StateFlow<CellularUiState> = _state.asStateFlow()

    private var pingJob: Job? = null
    private var speedJob: Job? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                refreshSnapshot()
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshSnapshot() }
    }

    fun setPingMethod(method: PingMethod) {
        _state.update { it.copy(pingMethod = method) }
    }

    fun setForceCellular(force: Boolean) {
        _state.update { it.copy(forceCellular = force) }
    }

    fun setPingTarget(target: String) {
        preferences.pingTarget = target.trim()
        _state.update { it.copy(pingTarget = target.trim()) }
    }

    fun runPing() {
        if (pingJob?.isActive == true) return
        pingJob = viewModelScope.launch {
            _state.update { it.copy(isPinging = true) }
            val network = resolveNetwork()
            val summary = pingEngine.ping(
                host = _state.value.pingTarget,
                count = 10,
                timeoutMillis = 2_500,
                intervalMillis = 400L,
                method = _state.value.pingMethod,
                network = network,
            )
            networkProvider.release(network)
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
            val network = resolveNetwork()
            val result = speedTestEngine.run(
                downloadUrl = current.downloadUrl,
                uploadUrl = current.uploadUrl,
                network = network,
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
            networkProvider.release(network)
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
                        type = TestType.CELL_SPEED,
                        transportLabel = _state.value.snapshot?.networkTypeLabel ?: "Móvil",
                        networkName = _state.value.snapshot?.operatorName,
                        downloadMbps = result.downloadMbps,
                        uploadMbps = result.uploadMbps,
                        pingMs = null,
                        jitterMs = null,
                        lossPercent = null,
                        signalDbm = _state.value.snapshot?.rsrp ?: _state.value.snapshot?.signalDbm,
                        gpsAccuracyMeters = null,
                        satellitesUsed = null,
                        verdict = null,
                        note = _state.value.snapshot?.networkGeneration,
                    ),
                )
            }
        }
    }

    private suspend fun resolveNetwork() =
        if (_state.value.forceCellular) {
            networkProvider.activeCellularNetwork().also { network ->
                _state.update { it.copy(cellularNetworkAvailable = network != null) }
            }
        } else {
            _state.update { it.copy(cellularNetworkAvailable = null) }
            null
        }

    private suspend fun refreshSnapshot() {
        val snapshot = withContext(Dispatchers.IO) {
            runCatching { cellular.snapshot() }.getOrNull()
        }
        _state.update { current ->
            current.copy(
                snapshot = snapshot,
                signalHistory = (current.signalHistory + snapshot?.rsrp?.toFloat()).takeLast(90),
                qualityHistory = (current.qualityHistory + snapshot?.signalQualityPercent?.toFloat())
                    .takeLast(90),
            )
        }
    }

    override fun onCleared() {
        networkProvider.release(null)
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 4_000L
    }
}

@Composable
fun CellularScreen(
    state: CellularUiState,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onSpeedTest: () -> Unit,
    onSetPingMethod: (PingMethod) -> Unit,
    onSetForceCellular: (Boolean) -> Unit,
    onSetPingTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val snapshot = state.snapshot
            SectionCard(
                title = "Red móvil",
                icon = Icons.Outlined.SignalCellularAlt,
                subtitle = snapshot?.networkTypeLabel,
                trailing = {
                    OutlinedButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
                    }
                },
            ) {
                if (snapshot == null) {
                    Text(NO_DISPONIBLE, style = MaterialTheme.typography.bodyMedium)
                } else {
                    if (!snapshot.telephonySupported) {
                        ExplanationCard(
                            text = "Este dispositivo no tiene radio móvil o la app no " +
                                "detecta telephonyManager.",
                            isWarning = true,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Gauge(
                            percent = snapshot.signalQualityPercent
                                ?: snapshot.signalDbm?.let { dbmToPercent(it) } ?: 0,
                            label = "señal",
                            caption = snapshot.signalLevelLabel,
                            size = 118.dp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            MetricRow(
                                metrics = listOf<Pair<String, String?>>(
                                    "Operador" to valueOrUnavailable(maskedOperator(snapshot.operatorName)),
                                    "Tipo de red" to valueOrUnavailable(snapshot.networkTypeLabel),
                                    "Generación" to valueOrUnavailable(snapshot.networkGeneration),
                                    "Itinerancia" to when (snapshot.isRoaming) {
                                        true -> "Sí, la SIM está en itinerancia"
                                        false -> "No"
                                        null -> NO_DISPONIBLE
                                    },
                                    "Datos móviles" to when (snapshot.dataEnabled) {
                                        true -> "Activados"
                                        false -> "Desactivados"
                                        null -> NO_DISPONIBLE
                                    },
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Estado de la SIM" to snapshot.simState,
                            "Estado de los datos" to valueOrUnavailable(snapshot.dataState),
                            "Actividad de datos" to valueOrUnavailable(snapshot.dataActivityLabel),
                            "RSRP" to (snapshot.rsrp?.let { "$it dBm" } ?: NO_DISPONIBLE),
                            "RSRQ" to (snapshot.rsrq?.let {
                                if (snapshot.rsrq > -20) NO_DISPONIBLE else "$it dB"
                            } ?: NO_DISPONIBLE),
                            "SINR" to (snapshot.sinr?.let { formatDecimal(it, 1, " dB") }
                                ?: NO_DISPONIBLE),
                            "RSSI (ASU)" to (snapshot.rssi?.let { "$it ASU" } ?: NO_DISPONIBLE),
                            "Barras del sistema" to valueOrUnavailable(snapshot.signalLevelLabel),
                            "País" to valueOrUnavailable(masked(snapshot.countryIso)),
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    ExplanationCard(
                        text = "RSRP, RSRQ y SINR solo existen en 4G/5G. En 2G y 3G el " +
                            "sistema solo entrega un nivel de barras 0..4 o un valor en " +
                            "ASU, y el porcentaje se estima a partir de él.",
                    )
                }
            }
        }

        item {
            SectionCard(title = "Celda y señal en el tiempo") {
                val snapshot = state.snapshot
                MetricRow(
                    metrics = listOf<Pair<String, String?>>(
                        "Frecuencia" to (snapshot?.frequencyMhz?.let { "$it MHz" }
                            ?: NO_DISPONIBLE),
                        "CELL ID" to valueOrUnavailable(masked(snapshot?.cellId)),
                        "TAC" to (snapshot?.tac?.let { masked(it.toString()) } ?: NO_DISPONIBLE),
                        "PCI" to (snapshot?.pci?.let { masked(it.toString()) } ?: NO_DISPONIBLE),
                        "Tipo de celda" to valueOrUnavailable(snapshot?.cellTypeLabel),
                        "Última lectura" to (snapshot?.let { formatClock(it.capturedAt) }
                            ?: NO_DISPONIBLE),
                    ),
                )
                Spacer(Modifier.height(12.dp))
                LineChart(
                    values = state.signalHistory,
                    yFormatter = { "${it.toInt()}" },
                    height = 150.dp,
                )
                Spacer(Modifier.height(8.dp))
                LineChart(
                    values = state.qualityHistory,
                    color = MaterialTheme.colorScheme.tertiary,
                    yFormatter = { "${it.toInt()} %" },
                    height = 150.dp,
                )
            }
        }

        item {
            val notes = state.snapshot?.unavailableNotes.orEmpty()
            if (notes.isNotEmpty()) {
                SectionCard(title = "Qué falta y por qué") {
                    notes.forEach { note ->
                        ExplanationCard(text = note, isWarning = true)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Pruebas sobre la red móvil",
                icon = Icons.Outlined.Speed,
                subtitle = "Latencia y velocidad forzadas por la red celular",
            ) {
                Switch(
                    checked = state.forceCellular,
                    onCheckedChange = onSetForceCellular,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.forceCellular) {
                        "Las pruebas se enlazarán a la red móvil aunque el Wi-Fi esté " +
                            "conectado."
                    } else {
                        "Las pruebas usarán la red por defecto del dispositivo, que " +
                            "puede ser el Wi-Fi."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.forceCellular && state.cellularNetworkAvailable == false) {
                    Spacer(Modifier.height(6.dp))
                    ExplanationCard(
                        text = "La app no ha conseguido una red móvil activa. Comprueba " +
                            "que los datos móviles estén activados y que haya cobertura.",
                        isWarning = true,
                    )
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(PingMethod.entries.size) { index ->
                        val method = PingMethod.entries[index]
                        FilterChip(
                            selected = state.pingMethod == method,
                            onClick = { onSetPingMethod(method) },
                            label = { Text(method.label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.pingTarget,
                    onValueChange = onSetPingTarget,
                    label = { Text("Destino de la medición") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPing, enabled = !state.isPinging) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Latencia")
                    }
                    Button(onClick = onSpeedTest, enabled = !state.isTestingSpeed) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Velocidad")
                    }
                }
                if (state.isPinging || state.isTestingSpeed) {
                    Spacer(Modifier.height(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
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

                state.ping?.let { ping ->
                    Spacer(Modifier.height(12.dp))
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Destino" to ping.target,
                            "Latencia media" to formatMs(ping.averageMs),
                            "Mínima / máxima" to "${formatMs(ping.minMs)} / " +
                                formatMs(ping.maxMs),
                            "Jitter" to formatMs(ping.jitterMs),
                            "Pérdida" to formatDecimal(ping.lossPercent, 1, " %"),
                            "Método real" to ping.methodLabel,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    LineChart(
                        values = ping.samples.map { it.latencyMs?.toFloat() },
                        height = 140.dp,
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
                    Spacer(Modifier.height(8.dp))
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Datos descargados" to formatBytes(speed.downloadedBytes),
                            "Datos subidos" to formatBytes(speed.uploadedBytes),
                            "Extremo" to valueOrUnavailable(speed.downloadEndpoint),
                        ),
                    )
                }
            }
        }
    }
}