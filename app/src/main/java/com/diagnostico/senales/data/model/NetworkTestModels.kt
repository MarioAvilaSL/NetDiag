package com.diagnostico.senales.data.model

import com.diagnostico.senales.core.util.Assessment

enum class PingMethod(val label: String, val description: String) {
    AUTO(
        "Automático",
        "Intenta ICMP y, si el sistema lo bloquea, usa una conexión TCP como referencia.",
    ),
    ICMP("ICMP", "Usa isReachable(), que internamente emplea eco request si el sistema lo permite."),
    TCP("TCP", "Mide el tiempo de establecimiento de la conexión TCP con el servidor."),
}

data class PingSample(
    val index: Int,
    val ok: Boolean,
    val latencyMs: Double?,
    val detail: String?,
)

data class PingSummary(
    val target: String,
    val methodLabel: String,
    val methodDescription: String,
    val sent: Int,
    val received: Int,
    val minMs: Double?,
    val maxMs: Double?,
    val averageMs: Double?,
    val jitterMs: Double?,
    val lossPercent: Double?,
    val dnsResolutionMs: Double?,
    val samples: List<PingSample>,
    val note: String?,
) {
    val isReachable: Boolean get() = received > 0
}

data class SpeedTestResult(
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val downloadedBytes: Long,
    val uploadedBytes: Long,
    val downloadSeconds: Double,
    val uploadSeconds: Double,
    val downloadEndpoint: String?,
    val uploadEndpoint: String?,
    val downloadError: String?,
    val uploadError: String?,
) {
    val isComplete: Boolean get() = downloadMbps != null || uploadMbps != null
}

data class StabilitySample(
    val second: Int,
    val rssi: Int?,
    val linkSpeedMbps: Int?,
    val latencyMs: Double?,
    val lossPercent: Double?,
    val downloadMbps: Double?,
    val hasInternet: Boolean,
    val note: String?,
)

data class StabilityReport(
    val target: String,
    val requestedSeconds: Int,
    val elapsedSeconds: Int,
    val averageRssi: Int?,
    val minRssi: Int?,
    val maxRssi: Int?,
    val averageLatencyMs: Double?,
    val averageLossPercent: Double?,
    val averageDownloadMbps: Double?,
    val disconnections: Int,
    val samples: List<StabilitySample>,
    val assessment: Assessment,
    val explanation: String,
)

data class AccuracySample(
    val second: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val satellitesVisible: Int,
    val satellitesUsed: Int,
    val averageCn0: Float?,
)

data class AccuracyReport(
    val requestedSeconds: Int,
    val elapsedSeconds: Int,
    val averageAccuracyMeters: Double?,
    val bestAccuracyMeters: Double?,
    val worstAccuracyMeters: Double?,
    val averageSatellitesUsed: Double?,
    val averageCn0: Double?,
    val distanceMovedMeters: Double,
    val samples: List<AccuracySample>,
    val assessment: Assessment,
    val explanation: String,
)