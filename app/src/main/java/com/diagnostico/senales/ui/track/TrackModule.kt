package com.diagnostico.senales.ui.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diagnostico.senales.core.util.NO_DISPONIBLE
import com.diagnostico.senales.core.util.assessGps
import com.diagnostico.senales.core.util.distanceMeters
import com.diagnostico.senales.core.util.formatAltitude
import com.diagnostico.senales.core.util.formatClock
import com.diagnostico.senales.core.util.formatDistance
import com.diagnostico.senales.core.util.formatDuration
import com.diagnostico.senales.core.util.formatSpeed
import com.diagnostico.senales.core.util.valueOrUnavailable
import com.diagnostico.senales.data.gnss.GnssRepository
import com.diagnostico.senales.data.history.HistoryRepository
import com.diagnostico.senales.data.model.HistoryRecord
import com.diagnostico.senales.data.model.TestType
import com.diagnostico.senales.data.model.TrackPoint
import com.diagnostico.senales.data.model.TrackStats
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.LineChart
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.SectionCard
import com.diagnostico.senales.ui.components.TrackMap
import com.diagnostico.senales.ui.components.VerdictCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.diagnostico.senales.ui.privacy.maskedCoords
import kotlin.math.roundToInt

data class TrackUiState(
    val isRecording: Boolean = false,
    val points: List<TrackPoint> = emptyList(),
    val stats: TrackStats? = null,
    val startedAtMillis: Long? = null,
    val message: String? = null,
)

class TrackViewModel(
    private val gnss: GnssRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackUiState())
    val state: StateFlow<TrackUiState> = _state.asStateFlow()

    private var recordJob: Job? = null

    fun start() {
        if (recordJob?.isActive == true) return
        recordJob = viewModelScope.launch {
            gnss.start()
            _state.update {
                it.copy(
                    isRecording = true,
                    points = emptyList(),
                    stats = null,
                    startedAtMillis = System.currentTimeMillis(),
                    message = null,
                )
            }
            gnss.fix.collect { fix ->
                if (fix == null) return@collect
                val point = TrackPoint(
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    altitudeMeters = fix.altitudeMeters,
                    accuracyMeters = fix.horizontalAccuracyMeters,
                    speedMetersPerSecond = fix.speedMetersPerSecond,
                    bearingDegrees = fix.bearingDegrees,
                    timestampMillis = fix.timestampMillis,
                    satellitesUsed = gnss.satellites.value.count { it.usedInFix },
                )
                _state.update { current ->
                    val updated = current.points + point
                    current.copy(points = updated, stats = computeStats(updated))
                }
            }
        }
    }

    fun stop() {
        recordJob?.cancel()
        recordJob = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { gnss.stop() } }
            _state.update { current ->
                current.copy(isRecording = false, stats = computeStats(current.points))
            }
        }
    }

    fun clear() {
        _state.update { TrackUiState() }
    }

    fun saveToHistory() {
        val current = _state.value
        val stats = current.stats ?: return
        viewModelScope.launch {
            historyRepository.add(
                HistoryRecord(
                    id = historyRepository.nextId(),
                    timestampMillis = System.currentTimeMillis(),
                    type = TestType.GPS_ROUTE,
                    transportLabel = "GNSS",
                    networkName = null,
                    downloadMbps = null,
                    uploadMbps = null,
                    pingMs = null,
                    jitterMs = null,
                    lossPercent = null,
                    signalDbm = null,
                    gpsAccuracyMeters = stats.averageAccuracyMeters,
                    satellitesUsed = current.points.map { it.satellitesUsed }
                        .takeIf { it.isNotEmpty() }
                        ?.average()
                        ?.roundToInt(),
                    verdict = assessGps(
                        averageAccuracyMeters = stats.averageAccuracyMeters,
                        satellitesUsed = current.points.map { it.satellitesUsed }
                            .takeIf { it.isNotEmpty() }
                            ?.average()
                            ?.roundToInt(),
                        averageCn0 = null,
                    ).verdict,
                    note = "${formatDistance(stats.distanceMeters)} en " +
                        "${stats.pointCount} puntos",
                ),
            )
            _state.update { it.copy(message = "Trayectoria guardada en el historial.") }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun computeStats(points: List<TrackPoint>): TrackStats? {
        if (points.isEmpty()) return null
        var distance = 0.0
        points.zipWithNext().forEach { (a, b) ->
            distance += distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        val duration = points.last().timestampMillis - points.first().timestampMillis
        val speeds = points.mapNotNull { it.speedMetersPerSecond?.toDouble() }
        return TrackStats(
            pointCount = points.size,
            distanceMeters = distance,
            durationMillis = duration.coerceAtLeast(0L),
            averageSpeedMetersPerSecond = if (duration > 0) {
                distance / (duration / 1000.0)
            } else {
                0.0
            },
            maxSpeedMetersPerSecond = speeds.maxOrNull() ?: 0.0,
            minAltitudeMeters = points.mapNotNull { it.altitudeMeters }.minOrNull(),
            maxAltitudeMeters = points.mapNotNull { it.altitudeMeters }.maxOrNull(),
            averageAccuracyMeters = points.mapNotNull { it.accuracyMeters?.toDouble() }
                .takeIf { it.isNotEmpty() }
                ?.average(),
        )
    }

    override fun onCleared() {
        recordJob?.cancel()
        super.onCleared()
    }
}

@Composable
fun TrackScreen(
    state: TrackUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Registro de trayectoria",
                icon = Icons.Outlined.Map,
                subtitle = state.startedAtMillis?.let { "Inicio a las ${formatClock(it)}" },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStart, enabled = !state.isRecording) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Text("Iniciar")
                    }
                    OutlinedButton(onClick = onStop, enabled = state.isRecording) {
                        Icon(Icons.Outlined.Stop, contentDescription = null)
                        Text("Detener")
                    }
                }
                Spacer(Modifier.height(8.dp))
                state.message?.let { message ->
                    ExplanationCard(text = message)
                }
                ExplanationCard(
                    text = "El registro usa el proveedor de ubicación que esté activo. " +
                        "Para una trayectoria fiable, activa el GPS y deja el dispositivo " +
                        "a la vista del cielo.",
                )
            }
        }

        item {
            SectionCard(title = "Mapa de la ruta") {
                TrackMap(
                    points = state.points,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Representación offline en coordenadas locales, sin mapas " +
                        "de terceros. Para mapas con fondo real hace falta una clave de " +
                        "Google Maps o una fuente de teselas open source.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.stats?.let { stats ->
            item {
                SectionCard(title = "Resumen de la sesión") {
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Puntos registrados" to stats.pointCount.toString(),
                            "Distancia total" to formatDistance(stats.distanceMeters),
                            "Duración" to formatDuration(stats.durationMillis),
                            "Velocidad media" to formatSpeed(stats.averageSpeedMetersPerSecond),
                            "Velocidad máxima" to formatSpeed(stats.maxSpeedMetersPerSecond),
                            "Altitud mínima" to (stats.minAltitudeMeters?.let {
                                formatAltitude(it)
                            } ?: NO_DISPONIBLE),
                            "Altitud máxima" to (stats.maxAltitudeMeters?.let {
                                formatAltitude(it)
                            } ?: NO_DISPONIBLE),
                            "Precisión media" to (stats.averageAccuracyMeters?.let {
                                formatDistance(it)
                            } ?: NO_DISPONIBLE),
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onSave,
                            enabled = state.points.isNotEmpty() && !state.isRecording,
                        ) {
                            Text("Guardar en historial")
                        }
                        OutlinedButton(
                            onClick = onClear,
                            enabled = state.points.isNotEmpty() && !state.isRecording,
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Text("Borrar")
                        }
                    }
                }
            }

            item {
                val assessment = assessGps(
                    averageAccuracyMeters = stats.averageAccuracyMeters,
                    satellitesUsed = state.points.map { it.satellitesUsed }
                        .takeIf { it.isNotEmpty() }
                        ?.average()
                        ?.roundToInt(),
                    averageCn0 = null,
                )
                SectionCard(title = "Veredicto de la sesión") {
                    VerdictCard(
                        verdict = assessment.verdict,
                        score = assessment.score,
                        reasons = assessment.reasons,
                    )
                }
            }

            item {
                SectionCard(title = "Evolución") {
                    LineChart(
                        values = state.points.map { it.accuracyMeters },
                        yFormatter = { "${it.toInt()} m" },
                        height = 150.dp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Precisión reportada en cada punto",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    LineChart(
                        values = state.points.map { it.speedMetersPerSecond },
                        color = MaterialTheme.colorScheme.secondary,
                        yFormatter = { "${it.toInt()}" },
                        height = 150.dp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Velocidad en metros por segundo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column {
                val last = state.points.lastOrNull()
                Text(
                    text = if (last == null) {
                        "Sin puntos registrados todavía."
                    } else {
                        "Último punto: " + (maskedCoords(last.latitude, last.longitude) ?:
                            "${last.latitude}, ${last.longitude}") + " · " +
                            "${valueOrUnavailable(last.accuracyMeters?.toString())} m de " +
                            "precisión"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}