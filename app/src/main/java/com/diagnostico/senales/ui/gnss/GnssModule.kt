package com.diagnostico.senales.ui.gnss

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diagnostico.senales.core.util.NO_DISPONIBLE
import com.diagnostico.senales.core.util.formatAltitude
import com.diagnostico.senales.core.util.formatClock
import com.diagnostico.senales.core.util.formatCoordinate
import com.diagnostico.senales.core.util.formatDecimal
import com.diagnostico.senales.core.util.formatDistance
import com.diagnostico.senales.core.util.formatDuration
import com.diagnostico.senales.core.util.formatSpeed
import com.diagnostico.senales.core.util.qualityFromAccuracy
import com.diagnostico.senales.core.util.valueOrUnavailable
import com.diagnostico.senales.data.gnss.GnssRepository
import com.diagnostico.senales.data.model.Constellation
import com.diagnostico.senales.data.model.GnssSnapshot
import com.diagnostico.senales.data.model.SatelliteInfo
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.Gauge
import com.diagnostico.senales.ui.components.InfoTile
import com.diagnostico.senales.ui.components.LineChart
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.SectionCard
import com.diagnostico.senales.ui.components.SkyPlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.diagnostico.senales.ui.privacy.masked
import com.diagnostico.senales.ui.privacy.Masks
import kotlinx.coroutines.withContext

data class GnssUiState(
    val snapshot: GnssSnapshot? = null,
    val isStarting: Boolean = false,
    val selectedConstellation: Constellation? = null,
    val accuracyHistory: List<Float?> = emptyList(),
    val satelliteCountHistory: List<Float?> = emptyList(),
) {
    val satellites: List<SatelliteInfo>
        get() {
            val all = snapshot?.satellites.orEmpty()
            val filtered = selectedConstellation
                ?.let { constellation -> all.filter { it.constellation == constellation } }
                ?: all
            return filtered.sortedWith(
                compareByDescending<SatelliteInfo> { it.usedInFix }.thenByDescending { it.cn0DbHz },
            )
        }

    val constellationOptions: List<Constellation>
        get() = snapshot?.satellites.orEmpty().map { it.constellation }.distinct()
}

class GnssViewModel(
    private val gnss: GnssRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GnssUiState())
    val state: StateFlow<GnssUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                runCatching { gnss.snapshot() }.getOrNull()
            }
            _state.update { current ->
                current.copy(
                    snapshot = snapshot,
                    accuracyHistory = (
                        current.accuracyHistory +
                            snapshot?.fix?.horizontalAccuracyMeters
                        ).takeLast(90),
                    satelliteCountHistory = (
                        current.satelliteCountHistory + snapshot?.satellitesUsed?.toFloat()
                        ).takeLast(90),
                )
            }
        }
    }

    fun start() {
        if (gnss.engineRunning.value) return
        _state.update { it.copy(isStarting = true) }
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { runCatching { gnss.start() }.exceptionOrNull() }
            _state.update { it.copy(isStarting = false) }
            if (error != null) {
                _state.update {
                    it.copy(
                        snapshot = it.snapshot?.copy(
                            errorMessage = error.message ?: NO_DISPONIBLE,
                        ),
                    )
                }
            }
            refresh()
        }
    }

    fun stop() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { gnss.stop() } }
            refresh()
        }
    }

    fun setConstellation(constellation: Constellation?) {
        _state.update { it.copy(selectedConstellation = constellation) }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 2_000L
    }
}

data class SkyPlotUiState(
    val snapshot: GnssSnapshot? = null,
    val selectedConstellation: Constellation? = null,
    val showUsedOnly: Boolean = false,
) {
    val satellites: List<SatelliteInfo>
        get() {
            val all = snapshot?.satellites.orEmpty()
            val filtered = all.filter { satellite ->
                selectedConstellation == null || satellite.constellation == selectedConstellation
            }
            return filtered.filter { !showUsedOnly || it.usedInFix }
        }
}

class SkyPlotViewModel(
    private val gnss: GnssRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SkyPlotUiState())
    val state: StateFlow<SkyPlotUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val snapshot = withContext(Dispatchers.IO) {
                    runCatching { gnss.snapshot() }.getOrNull()
                }
                _state.update { it.copy(snapshot = snapshot) }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    fun setConstellation(constellation: Constellation?) {
        _state.update { it.copy(selectedConstellation = constellation) }
    }

    fun setShowUsedOnly(value: Boolean) {
        _state.update { it.copy(showUsedOnly = value) }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 2_000L
    }
}

@Composable
fun GnssScreen(
    state: GnssUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onSetConstellation: (Constellation?) -> Unit,
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
                title = "Estado del GNSS",
                icon = Icons.Outlined.SatelliteAlt,
                subtitle = snapshot?.let {
                    if (it.isGnssEngineRunning) "Motor en marcha" else "Motor detenido"
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStart,
                        enabled = snapshot?.isGnssEngineRunning != true && !state.isStarting,
                    ) {
                        if (state.isStarting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Activar")
                        }
                    }
                    OutlinedButton(
                        onClick = onStop,
                        enabled = snapshot?.isGnssEngineRunning == true,
                    ) {
                        Text("Detener")
                    }
                    OutlinedButton(onClick = onRefresh) {
                        Text("Actualizar")
                    }
                }
                snapshot?.errorMessage?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    ExplanationCard(text = error, isWarning = true)
                }
                Spacer(Modifier.height(12.dp))
                MetricRow(
                    metrics = listOf<Pair<String, String?>>(
                        "Satélites en vista" to (snapshot?.satellites?.size ?: 0).toString(),
                        "Usados en la solución" to (snapshot?.satellitesUsed ?: 0).toString(),
                        "C/N0 medio" to (snapshot?.averageCn0?.let {
                            formatDecimal(it.toDouble(), 1, " dB-Hz")
                        } ?: NO_DISPONIBLE),
                        "Proveedores" to (snapshot?.providers?.size ?: 0).toString(),
                        "Última lectura" to (snapshot?.let { formatClock(it.capturedAt) }
                            ?: NO_DISPONIBLE),
                    ),
                )
            }
        }

        item {
            val fix = state.snapshot?.fix
            SectionCard(title = "Posición y precisión", icon = Icons.Outlined.MyLocation) {
                if (fix == null) {
                    ExplanationCard(
                        text = "Todavía no hay posición válida. Salga al exterior o cerca de " +
                            "una ventana y espere a que el motor obtenga un primer fix.",
                        isWarning = true,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Gauge(
                            percent = qualityFromAccuracy(fix.horizontalAccuracyMeters),
                            label = "precisión",
                            caption = fix.horizontalAccuracyMeters?.let {
                                formatDistance(it.toDouble())
                            },
                            size = 118.dp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            MetricRow(
                                metrics = listOf<Pair<String, String?>>(
                                    "Latitud" to masked(
                                        formatCoordinate(
                                            fix.latitude,
                                            isLatitude = true,
                                        ),
                                        Masks.COORD,
                                    ),
                                    "Longitud" to masked(
                                        formatCoordinate(
                                            fix.longitude,
                                            isLatitude = false,
                                        ),
                                        Masks.COORD,
                                    ),
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Altitud" to (fix.altitudeMeters?.let { formatAltitude(it) }
                                ?: NO_DISPONIBLE),
                            "Precisión horizontal" to (fix.horizontalAccuracyMeters?.let {
                                formatDistance(it.toDouble())
                            } ?: NO_DISPONIBLE),
                            "Precisión vertical" to (fix.verticalAccuracyMeters?.let {
                                formatDistance(it.toDouble())
                            } ?: NO_DISPONIBLE),
                            "Velocidad" to (fix.speedMetersPerSecond?.let { formatSpeed(it.toDouble()) }
                                ?: NO_DISPONIBLE),
                            "Rumbo" to (fix.bearingDegrees?.let {
                                formatDecimal(it.toDouble(), 1, "°")
                            } ?: NO_DISPONIBLE),
                            "Antigüedad del fix" to formatDuration(fix.fixAgeMillis),
                            "Tiempo hasta el primer fix" to (fix.timeToFirstFixMillis?.let {
                                formatDuration(it)
                            } ?: "Solo disponible antes de Android 12"),
                            "Proveedor" to fix.provider,
                            "Posición simulada" to if (fix.isMock) {
                                "Sí, el sistema marca la ubicación como simulada"
                            } else {
                                "No"
                            },
                        ),
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Precisión y satélites en el tiempo",
            ) {
                LineChart(
                    values = state.accuracyHistory,
                    color = MaterialTheme.colorScheme.primary,
                    yFormatter = { formatDecimal(it.toDouble(), 1, " m") },
                    height = 150.dp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Precisión horizontal en metros",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LineChart(
                    values = state.satelliteCountHistory,
                    color = MaterialTheme.colorScheme.secondary,
                    yFormatter = { it.toInt().toString() },
                    height = 150.dp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Satélites usados en la solución",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(
                title = "Proveedores de ubicación",
                icon = Icons.Outlined.Explore,
            ) {
                val providers = state.snapshot?.providers.orEmpty()
                if (providers.isEmpty()) {
                    Text(NO_DISPONIBLE, style = MaterialTheme.typography.bodyMedium)
                } else {
                    providers.forEach { provider ->
                        MetricRow(
                            metrics = listOf<Pair<String, String?>>(
                                provider.provider to if (provider.enabled) {
                                    "Activado"
                                } else {
                                    "Desactivado"
                                },
                                "Requiere red" to if (provider.requiresNetwork) "Sí" else "No",
                                "Requiere satélite" to if (provider.requiresSatellite) {
                                    "Sí"
                                } else {
                                    "No"
                                },
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    ExplanationCard(
                        text = "El proveedor de red puede dar una posición rápida pero " +
                            "poco precisa. Para una medición fiable usa el proveedor GPS, " +
                            "que necesita ver el cielo abierto.",
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Satélites en vista (${state.satellites.size})",
                icon = Icons.Outlined.SatelliteAlt,
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedConstellation == null,
                            onClick = { onSetConstellation(null) },
                            label = { Text("Todas") },
                        )
                    }
                    items(state.constellationOptions.size) { index ->
                        val constellation = state.constellationOptions[index]
                        FilterChip(
                            selected = state.selectedConstellation == constellation,
                            onClick = { onSetConstellation(constellation) },
                            label = { Text(constellation.shortLabel) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (state.satellites.isEmpty()) {
                    Text(
                        "Sin datos de satélites. Activa el motor GNSS y espera unos " +
                            "segundos.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.satellites.take(MAX_SATELLITES_ROWS).forEach { satellite ->
                        SatelliteRow(satellite)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SkyPlotScreen(
    state: SkyPlotUiState,
    onSetConstellation: (Constellation?) -> Unit,
    onSetShowUsedOnly: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Cielo del hemisferio",
                icon = Icons.Outlined.SatelliteAlt,
                subtitle = "Cada círculo es 10° de elevación sobre el horizonte",
            ) {
                if (state.satellites.isEmpty()) {
                    Text(
                        "Sin satélites que dibujar.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    SkyPlot(
                        satellites = state.satellites,
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                    )
                }
            }
        }

        item {
            SectionCard(title = "Filtros") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedConstellation == null,
                            onClick = { onSetConstellation(null) },
                            label = { Text("Todas las constelaciones") },
                        )
                    }
                    items(Constellation.entries.size) { index ->
                        val constellation = Constellation.entries[index]
                        FilterChip(
                            selected = state.selectedConstellation == constellation,
                            onClick = { onSetConstellation(constellation) },
                            label = { Text(constellation.shortLabel) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Switch(
                    checked = state.showUsedOnly,
                    onCheckedChange = onSetShowUsedOnly,
                )
                Spacer(Modifier.height(4.dp))
                Text("Mostrar solo los satélites usados en la solución")
            }
        }

        item {
            val visible = state.satellites
            SectionCard(title = "Resumen por constelación") {
                if (visible.isEmpty()) {
                    Text(NO_DISPONIBLE, style = MaterialTheme.typography.bodyMedium)
                } else {
                    val grouped = visible.groupBy { it.constellation }
                    grouped.forEach { (constellation, satellites) ->
                        val used = satellites.count { it.usedInFix }
                        val bestCn0 = satellites.mapNotNull { it.cn0DbHz }.maxOrNull()
                        MetricRow(
                            metrics = listOf<Pair<String, String?>>(
                                constellation.label to "${satellites.size} en vista, " +
                                    "$used en uso",
                                "Mejor C/N0" to (bestCn0?.let {
                                    formatDecimal(it.toDouble(), 1, " dB-Hz")
                                } ?: NO_DISPONIBLE),
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    InfoTile(
                        label = "Signal más fuerte",
                        value = visible.mapNotNull { it.cn0DbHz }.maxOrNull()?.let {
                            formatDecimal(it.toDouble(), 1, " dB-Hz")
                        } ?: NO_DISPONIBLE,
                        icon = Icons.Outlined.SatelliteAlt,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            ExplanationCard(
                text = "El C/N0 es la relación señal/ruido de la portadora de cada " +
                    "satélite. Por debajo de 20 dB-Hz la señal suele ser demasiado débil " +
                    "para una solución estable.",
            )
        }
    }
}

@Composable
private fun SatelliteRow(satellite: SatelliteInfo) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MetricRow(
            metrics = listOf<Pair<String, String?>>(
                "${satellite.constellation.shortLabel} ${satellite.svid}" to if (satellite.usedInFix) {
                    "En uso"
                } else {
                    "En vista"
                },
                "C/N0" to (satellite.cn0DbHz?.let {
                    formatDecimal(it.toDouble(), 1, " dB-Hz")
                } ?: NO_DISPONIBLE),
                "Elevación" to (satellite.elevationDegrees?.let {
                    formatDecimal(it.toDouble(), 1, "°")
                } ?: NO_DISPONIBLE),
                "Azimut" to (satellite.azimuthDegrees?.let {
                    formatDecimal(it.toDouble(), 1, "°")
                } ?: NO_DISPONIBLE),
                "Efemérides" to if (satellite.hasEphemeris) "Disponibles" else "No",
                "Almanaque" to if (satellite.hasAlmanac) "Disponible" else "No",
                "Señal sobre el suelo" to if (satellite.hasAlmanac && satellite.hasEphemeris) {
                    "El satélite puede usarse para calcular posición"
                } else {
                    "Faltan datos de ephemeris o almanaque"
                },
            ),
        )
    }
}

private const val MAX_SATELLITES_ROWS = 40