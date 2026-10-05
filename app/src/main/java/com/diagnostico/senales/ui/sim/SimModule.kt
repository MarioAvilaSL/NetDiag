package com.diagnostico.senales.ui.sim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SimCard
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
import com.diagnostico.senales.core.util.formatClock
import com.diagnostico.senales.core.util.valueOrUnavailable
import com.diagnostico.senales.data.cellular.CellularRepository
import com.diagnostico.senales.data.model.CellularSnapshot
import com.diagnostico.senales.data.model.SimSlotInfo
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.diagnostico.senales.ui.privacy.masked
import com.diagnostico.senales.ui.privacy.maskedOperator
import com.diagnostico.senales.ui.privacy.maskedSim
import kotlinx.coroutines.withContext

data class SimUiState(
    val snapshot: CellularSnapshot? = null,
    val isRefreshing: Boolean = false,
)

class SimViewModel(
    private val cellular: CellularRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SimUiState())
    val state: StateFlow<SimUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val snapshot = withContext(Dispatchers.IO) {
                runCatching { cellular.snapshot() }.getOrNull()
            }
            _state.update { it.copy(snapshot = snapshot, isRefreshing = false) }
        }
    }
}

@Composable
fun SimScreen(
    state: SimUiState,
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
                title = "Tarjetas SIM",
                icon = Icons.Outlined.SimCard,
                subtitle = snapshot?.let { "${it.slots.size} ranura(s) detectada(s)" },
                trailing = {
                    OutlinedButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
                    }
                },
            ) {
                if (snapshot == null) {
                    Text(NO_DISPONIBLE, style = MaterialTheme.typography.bodyMedium)
                } else {
                    MetricRow(
                        metrics = listOf<Pair<String, String?>>(
                            "Estado de la SIM" to snapshot.simState,
                            "Suscripción de datos" to snapshot.dataSubscriptionLabel,
                            "Operador" to valueOrUnavailable(maskedOperator(snapshot.operatorName)),
                            "Identificador de red (MCC+MNC)" to maskedSim(
                                listOfNotNull(snapshot.mcc, snapshot.mnc)
                                    .joinToString("")
                                    .ifBlank { NO_DISPONIBLE },
                            ),
                            "País" to valueOrUnavailable(masked(snapshot.countryIso)),
                            "Última lectura" to formatClock(snapshot.capturedAt),
                        ),
                    )
                }
            }
        }

        if (snapshot != null) {
            items(count = snapshot.slots.size, key = { snapshot.slots[it].slotIndex }) { index ->
                SlotCard(slot = snapshot.slots[index])
            }
        }

        item {
            ExplanationCard(
                text = "Esta app no lee el IMSI, el ICCID ni el número de línea. " +
                    "Android los clasifica como identificadores sensibles y exigirlos " +
                    "para diagnosticar la señal es innecesario.",
                isWarning = true,
            )
        }

        item {
            SectionCard(title = "Qué se puede y qué no se puede ver") {
                MetricRow(
                    metrics = listOf<Pair<String, String?>>(
                        "Operador y país" to "Disponible con permiso de teléfono",
                        "MCC y MNC" to "Disponibles con permiso de teléfono",
                        "Estado de la SIM" to "Disponible con permiso de teléfono",
                        "Ranura activa y de datos" to "Disponible con permiso de teléfono",
                        "Tipo de red y señal" to "Disponible con permiso de teléfono",
                        "Identidad de la SIM" to "No se solicita por privacidad",
                    ),
                )
            }
        }

        item {
            val notes = snapshot?.unavailableNotes.orEmpty()
            if (notes.isNotEmpty()) {
                SectionCard(title = "Notas de esta lectura") {
                    notes.forEach { note ->
                        ExplanationCard(text = note)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotCard(slot: SimSlotInfo) {
    SectionCard(
        title = slot.slotLabel,
        icon = Icons.Outlined.SimCard,
        subtitle = slot.state,
    ) {
        MetricRow(
            metrics = listOf<Pair<String, String?>>(
                "Operador" to valueOrUnavailable(maskedOperator(slot.carrierName)),
                "Activa" to if (slot.isActive) "Sí" else "No",
                "Suscripción de datos" to if (slot.isDataSubscription) "Sí" else "No",
                "País" to valueOrUnavailable(masked(slot.countryIso)),
                "MCC / MNC" to maskedSim(
                    listOfNotNull(slot.mcc, slot.mnc).joinToString(" / ").ifBlank { NO_DISPONIBLE },
                ),
                "SIM integrada (eSIM)" to when (slot.isEmbedded) {
                    true -> "Sí, es una eSIM"
                    false -> "No, es una SIM física"
                    null -> NO_DISPONIBLE
                },
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                slot.isDataSubscription -> "Esta ranura es la que usa los datos móviles."
                slot.isActive -> "Activa, pero no es la suscripción de datos."
                else -> "Esta ranura no está activa."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}