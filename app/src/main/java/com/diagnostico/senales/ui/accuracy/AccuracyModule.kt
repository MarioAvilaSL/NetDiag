package com.diagnostico.senales.ui.accuracy

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
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.diagnostico.senales.core.util.assessGps
import com.diagnostico.senales.core.util.distanceMeters
import com.diagnostico.senales.core.util.formatDecimal
import com.diagnostico.senales.core.util.formatDistance
import com.diagnostico.senales.core.util.formatDuration
import com.diagnostico.senales.core.util.qualityFromAccuracy
import com.diagnostico.senales.data.gnss.GnssRepository
import com.diagnostico.senales.data.history.HistoryRepository
import com.diagnostico.senales.data.model.AccuracyReport
import com.diagnostico.senales.data.model.AccuracySample
import com.diagnostico.senales.data.model.HistoryRecord
import com.diagnostico.senales.data.model.TestType
import com.diagnostico.senales.data.model.TrackPoint
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.Gauge
import com.diagnostico.senales.ui.components.LineChart
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.SectionCard
import com.diagnostico.senales.ui.components.TrackMap
import com.diagnostico.senales.ui.components.VerdictCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class AccuracyUiState(
    val isRunning: Boolean = false,
    val totalSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val samples: List<AccuracySample> = emptyList(),
    val report: AccuracyReport? = null,
    val liveAccuracyMeters: Float? = null,
)

class AccuracyViewModel(
    private val gnss: GnssRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccuracyUiState())
    val state: StateFlow<AccuracyUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(seconds: Int) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            gnss.start()
            val samples = mutableListOf<AccuracySample>()
            _state.update {
                it.copy(
                    isRunning = true,
                    totalSeconds = seconds,
                    elapsedSeconds = 0,
                    samples = emptyList(),
                    report = null,
                )
            }

            for (second in 1..seconds) {
                if (!isActive) return@launch
                val fix = gnss.fix.value
                val satellites = gnss.satellites.value
                if (fix != null) {
                    samples += AccuracySample(
                        second = second,
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        accuracyMeters = fix.horizontalAccuracyMeters,
                        altitudeMeters = fix.altitudeMeters,
                        satellitesVisible = satellites.size,
                        satellitesUsed = satellites.count { it.usedInFix },
                        averageCn0 = satellites.mapNotNull { it.cn0DbHz }
                            .takeIf { it.isNotEmpty() }
                            ?.average()
                            ?.toFloat(),
                    )
                }
                _state.update {
                    it.copy(
                        elapsedSeconds = second,
                        samples = samples.toList(),
                        liveAccuracyMeters = fix?.horizontalAccuracyMeters,
                    )
                }
                delay(1_000L)
            }

            gnss.stop()

            val accuracies = samples.mapNotNull { it.accuracyMeters?.toDouble() }
            val used = samples.map { it.satellitesUsed.toDouble() }
            val cn0 = samples.mapNotNull { it.averageCn0?.toDouble() }

            var drift = 0.0
            if (samples.size >= 2) {
                val centerLat = samples.map { it.latitude }.average()
                val centerLon = samples.map { it.longitude }.average()
                drift = samples.maxOf {
                    distanceMeters(centerLat, centerLon, it.latitude, it.longitude)
                }
            }

            val averageAccuracy = accuracies.takeIf { it.isNotEmpty() }?.average()
            val assessment = assessGps(
                averageAccuracyMeters = averageAccuracy,
                satellitesUsed = used.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                averageCn0 = cn0.takeIf { it.isNotEmpty() }?.average(),
            )

            val report = AccuracyReport(
                requestedSeconds = seconds,
                elapsedSeconds = samples.size,
                averageAccuracyMeters = averageAccuracy,
                bestAccuracyMeters = accuracies.minOrNull(),
                worstAccuracyMeters = accuracies.maxOrNull(),
                averageSatellitesUsed = used.takeIf { it.isNotEmpty() }?.average(),
                averageCn0 = cn0.takeIf { it.isNotEmpty() }?.average(),
                distanceMovedMeters = drift,
                samples = samples.toList(),
                assessment = assessment,
                explanation = buildString {
                    append("La prueba compara las posiciones sucesivas mientras el ")
                    append("dispositivo permanece quieto. El valor «desviación máxima» ")
                    append("es la distancia entre el centro de las muestras y la muestra ")
                    append("más alejada, y es la forma más honesta de medir el error de ")
                    append("posicionamiento real. ")
                    append(assessment.reasons.joinToString(" "))
                },
            )

            _state.update { it.copy(isRunning = false, report = report) }

            historyRepository.add(
                HistoryRecord(
                    id = historyRepository.nextId(),
                    timestampMillis = System.currentTimeMillis(),
                    type = TestType.GPS_ACCURACY,
                    transportLabel = "GNSS",
                    networkName = null,
                    downloadMbps = null,
                    uploadMbps = null,
                    pingMs = null,
                    jitterMs = null,
                    lossPercent = null,
                    signalDbm = null,
                    gpsAccuracyMeters = averageAccuracy,
                    satellitesUsed = used.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                    verdict = assessment.verdict,
                    note = "Desviación máxima ${formatDistance(drift)}",
                ),
            )
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        viewModelScope.launch {
            runCatching { gnss.stop() }
        }
        _state.update { it.copy(isRunning = false) }
    }

    fun clear() {
        _state.update { AccuracyUiState() }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}

@Composable
fun AccuracyScreen(
    state: AccuracyUiState,
    onStart: (Int) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSeconds by rememberSaveable { mutableStateOf(DURATION_OPTIONS[2]) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Precisión real de posicionamiento",
                icon = Icons.Outlined.MyLocation,
                subtitle = "Elige un intervalo de medición",
            ) {
                Text(
                    text = "La app no puede saber la posición verdadera: no tiene acceso " +
                        "a un punto de referencia externo. Lo que sí puede, y lo que " +
                        "midemos, es el error de posicionamiento de tu dispositivo " +
                        "calculándolo mientras está quieto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DURATION_OPTIONS.size) { index ->
                        val seconds = DURATION_OPTIONS[index]
                        FilterChip(
                            selected = selectedSeconds == seconds,
                            onClick = { selectedSeconds = seconds },
                            label = { Text(formatDuration(seconds * 1000L)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onStart(selectedSeconds) }, enabled = !state.isRunning) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Empezar")
                    }
                    OutlinedButton(onClick = onStop, enabled = state.isRunning) {
                        Icon(Icons.Outlined.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Detener")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Coloca el dispositivo en un sitio despejado y no lo muevas " +
                        "durante la medición.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.isRunning) {
            item {
                SectionCard(title = "Midiendo") {
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Progreso" to "${state.elapsedSeconds} de " +
                                "${state.totalSeconds} s",
                            "Muestras" to state.samples.size.toString(),
                            "Precisión actual" to (state.liveAccuracyMeters?.let {
                                formatDistance(it.toDouble())
                            } ?: NO_DISPONIBLE),
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            state.elapsedSeconds.toFloat() / state.totalSeconds
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.isRunning) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }

        state.report?.let { report ->
            item {
                SectionCard(title = "Resultado") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Gauge(
                            percent = qualityFromAccuracy(
                                report.averageAccuracyMeters?.toFloat(),
                            ),
                            label = "precisión",
                            caption = report.averageAccuracyMeters?.let {
                                formatDistance(it)
                            },
                            size = 118.dp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            VerdictCard(
                                verdict = report.assessment.verdict,
                                score = report.assessment.score,
                                reasons = report.assessment.reasons,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Duración medida" to formatDuration(
                                report.elapsedSeconds * 1000L,
                            ),
                            "Precisión media" to (report.averageAccuracyMeters?.let {
                                formatDistance(it)
                            } ?: NO_DISPONIBLE),
                            "Mejor precisión" to (report.bestAccuracyMeters?.let {
                                formatDistance(it)
                            } ?: NO_DISPONIBLE),
                            "Peor precisión" to (report.worstAccuracyMeters?.let {
                                formatDistance(it)
                            } ?: NO_DISPONIBLE),
                            "Desviación máxima" to formatDistance(
                                report.distanceMovedMeters,
                            ),
                            "Satélites usados (media)" to (report.averageSatellitesUsed?.let {
                                formatDecimal(it.toDouble(), 1, "")
                            } ?: NO_DISPONIBLE),
                            "C/N0 medio" to (report.averageCn0?.let {
                                formatDecimal(it.toDouble(), 1, " dB-Hz")
                            } ?: NO_DISPONIBLE),
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    ExplanationCard(text = report.explanation)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onClear) {
                        Text("Limpiar resultado")
                    }
                }
            }

            item {
                SectionCard(title = "Precisión declarada por el sistema") {
                    LineChart(
                        values = report.samples.map { it.accuracyMeters },
                        yFormatter = { "${it.toInt()} m" },
                        height = 160.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    LineChart(
                        values = report.samples.map { it.satellitesUsed.toFloat() },
                        color = MaterialTheme.colorScheme.secondary,
                        yFormatter = { it.toInt().toString() },
                        height = 160.dp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Satélites usados en cada muestra",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = "Dispersión de las posiciones") {
                    TrackMap(
                        points = report.samples.map { sample ->
                            TrackPoint(
                                latitude = sample.latitude,
                                longitude = sample.longitude,
                                altitudeMeters = sample.altitudeMeters,
                                accuracyMeters = sample.accuracyMeters,
                                speedMetersPerSecond = null,
                                bearingDegrees = null,
                                timestampMillis = 0L,
                                satellitesUsed = sample.satellitesUsed,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Si el dispositivo está quieto, las muestras deberían " +
                            "caer muy juntas. Un círculo grande significa ruido de " +
                            "posicionamiento alto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!state.isRunning && state.report == null) {
            item {
                ExplanationCard(
                    text = "Mientras no hay medición en curso puedes leer la precisión " +
                        "instantánea y el estado del GNSS en la pantalla GNSS.",
                )
            }
        }
    }
}

private val DURATION_OPTIONS = listOf(60, 120, 300, 600)