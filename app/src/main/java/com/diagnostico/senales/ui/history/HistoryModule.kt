package com.diagnostico.senales.ui.history

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diagnostico.senales.core.util.NO_DISPONIBLE
import com.diagnostico.senales.core.util.formatDateTime
import com.diagnostico.senales.core.util.formatDecimal
import com.diagnostico.senales.core.util.formatDistance
import com.diagnostico.senales.core.util.formatMbps
import com.diagnostico.senales.core.util.formatMs
import com.diagnostico.senales.core.util.valueOrUnavailable
import com.diagnostico.senales.data.history.HistoryRepository
import com.diagnostico.senales.data.model.HistoryRecord
import com.diagnostico.senales.data.model.TestType
import com.diagnostico.senales.ui.components.ChartSeries
import com.diagnostico.senales.ui.components.EmptyState
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.MultiLineChart
import com.diagnostico.senales.ui.components.SectionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.diagnostico.senales.ui.privacy.masked
import kotlinx.coroutines.launch

data class HistoryUiState(
    val records: List<HistoryRecord> = emptyList(),
    val filter: TestType? = null,
) {
    val visibleRecords: List<HistoryRecord>
        get() = filter?.let { type -> records.filter { it.type == type } } ?: records
}

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.load()
            historyRepository.records.collect { records ->
                _state.update { it.copy(records = records) }
            }
        }
    }

    fun setFilter(type: TestType?) {
        _state.update { it.copy(filter = type) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { historyRepository.delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { historyRepository.clear() }
    }

    fun asCsv(): String = historyCsv(_state.value.records)
}

private const val CSV_HEADER =
    "fecha;prueba;transporte;red;descarga_mbps;subida_mbps;latencia_ms;" +
        "jitter_ms;perdida_pct;senal_dbm;precision_m;satelites;veredicto;nota"

private fun historyCsv(records: List<HistoryRecord>): String = buildString {
    appendLine(CSV_HEADER)
    records.forEach { record ->
        appendLine(
            listOf(
                record.timestampMillis.toString(),
                record.type.label,
                record.transportLabel,
                record.networkName.orEmpty(),
                record.downloadMbps?.toString().orEmpty(),
                record.uploadMbps?.toString().orEmpty(),
                record.pingMs?.toString().orEmpty(),
                record.jitterMs?.toString().orEmpty(),
                record.lossPercent?.toString().orEmpty(),
                record.signalDbm?.toString().orEmpty(),
                record.gpsAccuracyMeters?.toString().orEmpty(),
                record.satellitesUsed?.toString().orEmpty(),
                record.verdict?.label.orEmpty(),
                record.note.orEmpty(),
            ).joinToString(";")
        )
    }
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onSetFilter: (TestType?) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Historial de pruebas",
                icon = Icons.Outlined.History,
                subtitle = "${state.records.size} registros guardados en el dispositivo",
            ) {
                if (state.records.isEmpty()) {
                    EmptyState(
                        "Todavía no hay pruebas guardadas. Ejecuta una prueba de " +
                            "velocidad, estabilidad o precisión y aparecerá aquí."
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val text = historyCsv(state.records)
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(
                                                Intent.EXTRA_SUBJECT,
                                                "Historial de pruebas de Señales",
                                            )
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        },
                                        "Compartir historial",
                                    )
                                )
                            },
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Compartir")
                        }
                        OutlinedButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Borrar todo")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ExplanationCard(
                        text = "El historial se guarda como un único archivo JSON en el " +
                            "almacenamiento interno de la app. No se sube a ningún " +
                            "servidor y no incluye claves de Wi-Fi ni identificadores " +
                            "de la SIM.",
                    )
                }
            }
        }

        if (state.records.isNotEmpty()) {
            item {
                SectionCard(title = "Filtros") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.filter == null,
                                onClick = { onSetFilter(null) },
                                label = { Text("Todas") },
                            )
                        }
                        items(TestType.entries.size) { index ->
                            val type = TestType.entries[index]
                            val count = state.records.count { it.type == type }
                            FilterChip(
                                selected = state.filter == type,
                                onClick = { onSetFilter(type) },
                                label = { Text("${type.shortLabel} ($count)") },
                            )
                        }
                    }
                }
            }

            item {
                val records = state.visibleRecords.sortedBy { it.timestampMillis }
                SectionCard(
                    title = "Evolución de descargas y subidas",
                    subtitle = "${records.size} prueba(s)",
                ) {
                    val down = records.mapNotNull { it.downloadMbps?.toFloat() }
                    val up = records.mapNotNull { it.uploadMbps?.toFloat() }
                    if (down.isEmpty() && up.isEmpty()) {
                        Text(
                            "No hay medidas de velocidad en el filtro actual.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        MultiLineChart(
                            series = listOfNotNull(
                                if (down.isEmpty()) {
                                    null
                                } else {
                                    ChartSeries(
                                        label = "Descarga",
                                        points = down,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                if (up.isEmpty()) {
                                    null
                                } else {
                                    ChartSeries(
                                        label = "Subida",
                                        points = up,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                },
                            ),
                            yFormatter = { "${it.toInt()}" },
                            xLabels = records.map { formatDateTime(it.timestampMillis) },
                            height = 200.dp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Mbps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                val records = state.visibleRecords.sortedBy { it.timestampMillis }
                SectionCard(title = "Latencia medida") {
                    val latency = records.mapNotNull { it.pingMs?.toFloat() }
                    if (latency.isEmpty()) {
                        Text(
                            "No hay medidas de latencia en el filtro actual.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        MultiLineChart(
                            series = listOf(
                                ChartSeries(
                                    label = "Latencia",
                                    points = latency,
                                    color = MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                            yFormatter = { "${it.toInt()} ms" },
                            xLabels = records.map { formatDateTime(it.timestampMillis) },
                            height = 180.dp,
                        )
                    }
                }
            }

            items(state.visibleRecords, key = { it.id }) { record ->
                HistoryCard(record = record, onDelete = { onDelete(record.id) })
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            text = {
                Text("Se eliminarán los ${state.records.size} registros guardados. " +
                    "Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        confirmClear = false
                    },
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun HistoryCard(record: HistoryRecord, onDelete: () -> Unit) {
    SectionCard(
        title = record.type.label,
        icon = Icons.Outlined.History,
        subtitle = formatDateTime(record.timestampMillis),
        trailing = {
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Borrar")
            }
        },
    ) {
        Column {
            record.verdict?.let { verdict ->
                Text(
                    text = "Veredicto: ${verdict.label}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
            }
            MetricRow(
                metrics = listOf<Pair<String, String?>>(
                    "Transporte" to valueOrUnavailable(record.transportLabel),
                    "Red" to valueOrUnavailable(masked(record.networkName)),
                    "Descarga" to formatMbps(record.downloadMbps),
                    "Subida" to formatMbps(record.uploadMbps),
                    "Latencia" to formatMs(record.pingMs),
                    "Jitter" to formatMs(record.jitterMs),
                    "Pérdida" to (record.lossPercent?.let {
                        formatDecimal(it, 1, " %")
                    } ?: NO_DISPONIBLE),
                    "Señal" to (record.signalDbm?.let { "$it dBm" } ?: NO_DISPONIBLE),
                    "Precisión GNSS" to (record.gpsAccuracyMeters?.let {
                        formatDistance(it)
                    } ?: NO_DISPONIBLE),
                    "Satélites" to (record.satellitesUsed?.toString() ?: NO_DISPONIBLE),
                    "Nota" to valueOrUnavailable(record.note),
                ),
            )
        }
    }
}