package com.diagnostico.senales.data.model

/** Información de una ranura SIM, legible de forma legítima por la app. */
data class SimSlotInfo(
    val slotIndex: Int,
    val slotLabel: String,
    val state: String,
    val isActive: Boolean,
    val isDataSubscription: Boolean,
    val carrierName: String?,
    val countryIso: String?,
    val mcc: String?,
    val mnc: String?,
    val isEmbedded: Boolean?,
)

/** Datos de la red móvil y de la celda en la que está el dispositivo. */
data class CellularSnapshot(
    val telephonySupported: Boolean,
    val hasActiveSubscription: Boolean,
    val dataSubscriptionLabel: String,
    val simState: String,
    val slots: List<SimSlotInfo>,
    val operatorName: String?,
    val countryIso: String?,
    val mcc: String?,
    val mnc: String?,
    val networkTypeLabel: String?,
    val networkGeneration: String?,
    val signalDbm: Int?,
    val signalQualityPercent: Int?,
    val signalLevelLabel: String?,
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Double?,
    val rssi: Int?,
    val cellId: String?,
    val tac: Int?,
    val pci: Int?,
    val frequencyMhz: Int?,
    val cellTypeLabel: String?,
    val isRoaming: Boolean?,
    val dataEnabled: Boolean?,
    val dataState: String?,
    val dataActivityLabel: String?,
    val capturedAt: Long,
    val unavailableNotes: List<String>,
)