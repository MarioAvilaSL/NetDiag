package com.diagnostico.senales.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diagnostico.senales.core.util.NO_DISPONIBLE
import com.diagnostico.senales.core.util.WifiBand
import com.diagnostico.senales.core.util.formatClock
import com.diagnostico.senales.core.util.overlappingChannels
import com.diagnostico.senales.core.util.rssiToQuality
import com.diagnostico.senales.data.model.ScanNetwork
import com.diagnostico.senales.data.model.ScanResultSnapshot
import com.diagnostico.senales.data.wifi.WifiRepository
import com.diagnostico.senales.ui.components.ChannelBar
import com.diagnostico.senales.ui.components.ChannelOccupancyChart
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.SectionCard
import com.diagnostico.senales.ui.components.SignalBarsChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.diagnostico.senales.ui.privacy.masked
import kotlinx.coroutines.withContext

/** Anchos de canal habituales; el sistema no los publica, así que son una suposición del usuario. */
private val WIDTH_OPTIONS = listOf(20, 40, 80, 160)

data class ScanUiState(
    val snapshot: ScanResultSnapshot? = null,
    val isScanning: Boolean = false,
    val selectedBand: WifiBand? = null,
    val selectedSecurity: String? = null,
    val assumedWidthMhz: Int = 20,
) {
    val networks: List<ScanNetwork>
        get() {
            val source = snapshot?.networks.orEmpty()
            return source
                .filter { selectedBand == null || it.band == selectedBand }
                .filter { selectedSecurity == null || it.security == selectedSecurity }
                .sortedWith(compareByDescending<ScanNetwork> { it.isCurrentNetwork }.thenByDescending { it.rssi })
        }

    val securityOptions: List<String>
        get() = snapshot?.networks.orEmpty().map { it.security }.distinct().sorted()
}

class ScanViewModel(
    private val wifi: WifiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    init {
        scan()
    }

    fun scan() {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true) }
            val snapshot = withContext(Dispatchers.IO) { wifi.scan() }
            _state.update { it.copy(snapshot = snapshot, isScanning = false) }
        }
    }

    fun setBand(band: WifiBand?) {
        _state.update { it.copy(selectedBand = band) }
    }

    fun setSecurity(security: String?) {
        _state.update { it.copy(selectedSecurity = security) }
    }

    fun setAssumedWidth(widthMhz: Int) {
        _state.update { it.copy(assumedWidthMhz = widthMhz) }
    }
}

@Composable
fun ScanScreen(
    state: ScanUiState,
    onScan: () -> Unit,
    onSetBand: (WifiBand?) -> Unit,
    onSetSecurity: (String?) -> Unit,
    onSetAssumedWidth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Escáner Wi-Fi",
                icon = Icons.Outlined.Radar,
                subtitle = state.snapshot?.let {
                    "${it.networks.size} redes a las ${formatClock(it.scannedAt)}"
                },
                trailing = {
                    OutlinedButton(onClick = onScan, enabled = !state.isScanning) {
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Escanear")
                        }
                    }
                },
            ) {
                if (state.isScanning && state.snapshot == null) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = "Para calcular la saturación de cada canal hay que saber el " +
                        "ancho de canal que usa cada red. Android no lo publica, así que " +
                        "elige el ancho típico de tu red y el resto se estima.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(WIDTH_OPTIONS.size) { index ->
                        val width = WIDTH_OPTIONS[index]
                        FilterChip(
                            selected = state.assumedWidthMhz == width,
                            onClick = { onSetAssumedWidth(width) },
                            label = { Text("$width MHz") },
                        )
                    }
                }
            }
        }

        item {
            state.snapshot?.message?.let { message ->
                ExplanationCard(text = message, isWarning = state.snapshot.isThrottled)
            }
        }

        item {
            ExplanationCard(
                text = "Desde Android 9 el sistema limita los escaneos a unas 4 lecturas " +
                    "cada 2 minutos por apps. Desde Android 10 la dirección MAC se " +
                    "sustituye por un identificador aleatorio, por lo que no se puede " +
                    "usar para localizar un router concreto.",
                isWarning = true,
            )
        }

        item {
            SectionCard(title = "Filtros") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedBand == null,
                            onClick = { onSetBand(null) },
                            label = { Text("Todas las bandas") },
                        )
                    }
                    items(WifiBand.entries.size) { index ->
                        val band = WifiBand.entries[index]
                        FilterChip(
                            selected = state.selectedBand == band,
                            onClick = { onSetBand(band) },
                            label = { Text(band.label) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedSecurity == null,
                            onClick = { onSetSecurity(null) },
                            label = { Text("Toda seguridad") },
                        )
                    }
                    items(state.securityOptions.size) { index ->
                        val security = state.securityOptions[index]
                        FilterChip(
                            selected = state.selectedSecurity == security,
                            onClick = { onSetSecurity(security) },
                            label = { Text(security) },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Ocupación de canales",
                icon = Icons.Outlined.Radar,
                subtitle = "Intensidad media por canal (${state.assumedWidthMhz} MHz como suposición)",
            ) {
                val networks = state.networks
                if (networks.isEmpty()) {
                    Text(
                        "Sin redes para analizar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val pairs = networks
                        .filter { it.channel != null }
                        .flatMap { network ->
                            overlappingChannels(
                                network.channel ?: 0,
                                network.band,
                                // Si el sistema publica el ancho real se usa ese;
                                // si no, se recurre al ancho supuesto arriba.
                                network.reportedWidthMhz ?: state.assumedWidthMhz,
                            ).map { it to network }
                        }
                    if (pairs.isEmpty()) {
                        Text(
                            "Las redes detectadas no informan de canal.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        val byChannel = pairs.groupBy({ it.first }, { it.second })
                        val bars = byChannel
                            .map { (channel, occupying) ->
                                ChannelBar(
                                    channel = channel,
                                    networkCount = occupying.size,
                                    averageRssi = occupying
                                        .map { it.rssi }
                                        .average()
                                        .roundToInt(),
                                    isRecommended = false,
                                )
                            }
                            .sortedBy { it.channel }
                        val recommended = bars
                            .filter { it.networkCount == bars.minOf { bar -> bar.networkCount } }
                            .maxByOrNull { it.averageRssi ?: Int.MIN_VALUE }
                            ?.channel
                        ChannelOccupancyChart(
                            bars = bars.map {
                                it.copy(isRecommended = it.channel == recommended)
                            },
                            height = 190.dp,
                        )
                        Spacer(Modifier.height(8.dp))
                        VerdictRecommendation(
                            recommended = recommended,
                            worstChannel = bars.maxByOrNull { it.networkCount }?.channel,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Redes detectadas (${state.networks.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }

        items(state.networks, key = { it.bssid }) { network ->
            NetworkCard(network = network, assumedWidthMhz = state.assumedWidthMhz)
        }

        item {
            Text(
                text = "RSSI: -30 dBm es excelente y -90 dBm es muy mala. Cada 3 dB " +
                    "de diferencia equivalen a la mitad de la potencia recibida.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun VerdictRecommendation(recommended: Int?, worstChannel: Int?) {
    val text = buildString {
        append("Canal menos saturado: ${recommended ?: NO_DISPONIBLE}. ")
        append("El más saturado es el ${worstChannel ?: NO_DISPONIBLE}. ")
        append("Si tu router permite cambiar de canal, elige uno de la banda de 5 GHz " +
            "separado de los canales ocupados.")
    }
    ExplanationCard(text = text)
}

@Composable
private fun NetworkCard(network: ScanNetwork, assumedWidthMhz: Int) {
    SectionCard(
        title = masked(network.ssid) ?: "Red sin nombre",
        icon = Icons.Outlined.Wifi,
        subtitle = "${network.band.label} · canal ${network.channel ?: "?"} · " +
            "${network.frequencyMhz} MHz",
        trailing = {
            SignalBarsChart(
                entries = listOf("${network.rssi} dBm" to (rssiToQuality(network.rssi) ?: 0)),
                modifier = Modifier.width(170.dp),
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${network.rssi} dBm",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${rssiToQuality(network.rssi)} %",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        MetricRow(
            metrics = listOf<Pair<String, String?>>(
                "BSSID" to masked(network.bssid),
                "Seguridad" to network.security,
                "Estándar" to (network.standard ?: NO_DISPONIBLE),
                "Ancho de canal" to (network.reportedWidthMhz?.let { "$it MHz (sistema)" }
                    ?: "${assumedWidthMhz} MHz (supuesto)"),
                "Estado" to if (network.isCurrentNetwork) {
                    "Conectado ahora"
                } else {
                    "No conectada"
                },
                "Oculta" to if (network.isHidden) "Sí" else "No",
                "Capacidades del sistema" to network.capabilities,
            ),
        )
        Spacer(Modifier.height(6.dp))
        val widthMhz = network.reportedWidthMhz ?: assumedWidthMhz
        Text(
            text = "Ancho de canal: " +
                (if (network.reportedWidthMhz != null) {
                    "${widthMhz} MHz informado por el sistema"
                } else {
                    "$widthMhz MHz supuesto por ti"
                }) + ". Canales que se solapan con el ${network.channel ?: "?"}: " +
                overlappingChannels(network.channel ?: 0, network.band, widthMhz)
                    .joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}