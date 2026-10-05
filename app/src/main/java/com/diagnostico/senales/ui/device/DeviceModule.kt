package com.diagnostico.senales.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
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
import com.diagnostico.senales.core.util.formatBytes
import com.diagnostico.senales.core.util.formatDecimal
import com.diagnostico.senales.core.util.formatDateTime
import com.diagnostico.senales.core.util.formatInt
import com.diagnostico.senales.core.util.valueOrUnavailable
import com.diagnostico.senales.data.device.DeviceRepository
import com.diagnostico.senales.data.model.DeviceSnapshot
import com.diagnostico.senales.data.model.SensorInfo
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.InfoTile
import com.diagnostico.senales.ui.components.LabeledRow
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.diagnostico.senales.ui.privacy.masked
import com.diagnostico.senales.ui.privacy.maskedIp
import kotlinx.coroutines.withContext

data class DeviceUiState(
    val isLoading: Boolean = true,
    val snapshot: DeviceSnapshot? = null,
    val sensorFilter: String? = null,
)

class DeviceViewModel(private val device: DeviceRepository) : ViewModel() {

    private val _state = MutableStateFlow(DeviceUiState())
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val snapshot = withContext(Dispatchers.IO) {
                runCatching { device.snapshot() }.getOrNull()
            }
            _state.update { it.copy(isLoading = false, snapshot = snapshot) }
        }
    }

    fun setSensorFilter(filter: String?) {
        _state.update { it.copy(sensorFilter = filter) }
    }
}

@Composable
fun DeviceScreen(
    state: DeviceUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Identificación",
                icon = Icons.Outlined.Devices,
                subtitle = snapshot?.let { "Actualizado a las ${formatDateTime(it.capturedAt)}" },
                trailing = {
                    OutlinedButton(onClick = onRefresh, enabled = !state.isLoading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
                        Spacer(Modifier.width(6.dp))
                        Text("Actualizar")
                    }
                },
            ) {
                if (snapshot == null) {
                    Text(
                        text = if (state.isLoading) "Leyendo el dispositivo..." else NO_DISPONIBLE,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Fabricante" to valueOrUnavailable(snapshot.manufacturer),
                            "Modelo" to valueOrUnavailable(snapshot.model),
                            "Marca" to valueOrUnavailable(snapshot.brand),
                            "Nombre interno" to valueOrUnavailable(snapshot.device),
                            "Hardware" to valueOrUnavailable(snapshot.hardware),
                            "Placa" to valueOrUnavailable(snapshot.board),
                        ),
                    )
                }
            }
        }

        item {
            SectionCard(title = "Sistema y procesador", icon = Icons.Outlined.Memory) {
                if (snapshot == null) {
                    Text(NO_DISPONIBLE, style = MaterialTheme.typography.bodyMedium)
                } else {
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Versión de Android" to snapshot.androidRelease,
                            "Nivel de API" to formatInt(snapshot.sdkInt),
                            "Parche de seguridad" to valueOrUnavailable(snapshot.securityPatch),
                            "Build" to valueOrUnavailable(snapshot.buildId),
                            "Arquitectura principal" to valueOrUnavailable(snapshot.primaryAbi),
                            "ABI admitidas" to snapshot.supportedAbis.joinToString(", "),
                            "Huella del build" to snapshot.fingerprintShort,
                        ),
                    )
                }
            }
        }

        item {
            SectionCard(title = "Memoria y almacenamiento", icon = Icons.Outlined.Storage) {
                if (snapshot == null) {
                    Text(NO_DISPONIBLE, style = MaterialTheme.typography.bodyMedium)
                } else {
                    val ramUsed = snapshot.totalRamBytes - snapshot.availableRamBytes
                    val storageUsed = snapshot.totalStorageBytes - snapshot.availableStorageBytes
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "RAM total" to formatBytes(snapshot.totalRamBytes),
                            "RAM libre" to formatBytes(snapshot.availableRamBytes),
                            "RAM usada" to formatBytes(ramUsed),
                            "Presión de memoria" to if (snapshot.lowMemory) {
                                "Alta: Android está reclamando memoria"
                            } else {
                                "Normal"
                            },
                            "Almacenamiento total" to formatBytes(snapshot.totalStorageBytes),
                            "Almacenamiento libre" to formatBytes(snapshot.availableStorageBytes),
                            "Almacenamiento usado" to formatBytes(storageUsed),
                        ),
                    )
                }
            }
        }

        item {
            SectionCard(title = "Batería", icon = Icons.Outlined.BatteryFull) {
                if (snapshot == null) {
                    Text(NO_DISPONIBLE, style = MaterialTheme.typography.bodyMedium)
                } else {
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Nivel" to (snapshot.batteryLevelPercent?.let { "$it %" }
                                ?: NO_DISPONIBLE),
                            "Estado" to snapshot.batteryStatus,
                            "Salud" to snapshot.batteryHealth,
                            "Temperatura" to (snapshot.batteryTemperatureCelsius?.let {
                                formatDecimal(it.toDouble(), 1, " °C")
                            } ?: "Solo el sistema puede leerla: muchos fingerprint no la publican"),
                            "Voltaje" to (snapshot.batteryVoltageMilliVolts?.let {
                                formatDecimal(it / 1000.0, 2, " V")
                            } ?: NO_DISPONIBLE),
                            "Corriente" to (snapshot.batteryCurrentMicroAmps?.let {
                                formatDecimal(it / 1000.0, 1, " mA")
                            } ?: "No la publica el fabricante en este dispositivo"),
                            "Cargas consumidas" to (snapshot.batteryChargeCounterMicroAmpHours?.let {
                                formatDecimal(it / 1000.0, 1, " mAh")
                            } ?: NO_DISPONIBLE),
                            "Tecnología" to valueOrUnavailable(snapshot.batteryTechnology),
                            "Fuente de carga" to snapshot.chargingSource,
                        ),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTile(
                    label = "Bluetooth",
                    value = snapshot?.bluetoothState ?: "—",
                    secondary = valueOrUnavailable(snapshot?.bluetoothName),
                    icon = Icons.Outlined.Bluetooth,
                    modifier = Modifier.weight(1f),
                )
                InfoTile(
                    label = "NFC",
                    value = snapshot?.nfcState ?: "—",
                    icon = Icons.Outlined.Nfc,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard(title = "Direcciones de red", icon = Icons.Outlined.Wifi) {
                if (snapshot == null) {
                    Text(NO_DISPONIBLE, style = MaterialTheme.typography.bodyMedium)
                } else {
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "IP local (Wi-Fi)" to valueOrUnavailable(maskedIp(snapshot.wifiInterfaceIpv4)),
                            "IPv6 (Wi-Fi)" to valueOrUnavailable(maskedIp(snapshot.wifiInterfaceIpv6)),
                            "IP local (móvil)" to valueOrUnavailable(maskedIp(snapshot.mobileInterfaceIpv4)),
                            "IPv6 (móvil)" to valueOrUnavailable(maskedIp(snapshot.mobileInterfaceIpv6)),
                            "Cualquier IPv4" to valueOrUnavailable(maskedIp(snapshot.anyInterfaceIpv4)),
                            "Dirección MAC" to valueOrUnavailable(masked(snapshot.macAddress)),
                        ),
                    )
                    snapshot.macNote?.let { note ->
                        Spacer(Modifier.height(8.dp))
                        ExplanationCard(text = note)
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Sensores",
                icon = Icons.Outlined.Sensors,
                subtitle = snapshot?.sensors?.let { "${it.size} sensores detectados" },
            ) {
                if (snapshot == null || snapshot.sensors.isEmpty()) {
                    Text(
                        text = NO_DISPONIBLE,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    val filtered = state.sensorFilter
                        ?.let { filter -> snapshot.sensors.filter { it.typeLabel == filter } }
                        ?: snapshot.sensors
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filtered.forEach { sensor -> SensorRow(sensor) }
                    }
                }
            }
        }

        item {
            ExplanationCard(
                text = "La dirección MAC y la temperatura de la batería dependen del " +
                    "fabricante: Android las enmascara o no las publica. Cuando eso " +
                    "ocurre la app lo indica en lugar de mostrar un valor inventado.",
            )
        }
    }
}

@Composable
private fun SensorRow(sensor: SensorInfo) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LabeledRow(
            label = "${sensor.name} · ${sensor.typeLabel}",
            value = sensor.vendor,
        )
        LabeledRow(
            label = "Rango / resolución",
            value = "${sensor.maxRange} · ${sensor.resolution}",
        )
        LabeledRow(
            label = "Consumo / precisión",
            value = "${sensor.power} · ${sensor.accuracyLabel}",
        )
    }
}