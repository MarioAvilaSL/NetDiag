package com.diagnostico.senales.data.model

import com.diagnostico.senales.core.util.SignalLevel
import com.diagnostico.senales.core.util.WifiBand

/** Datos de la red Wi-Fi a la que está conectado el dispositivo. */
data class WifiConnectionInfo(
    val ssid: String?,
    val bssid: String?,
    val hiddenSsid: Boolean,
    val ipAddress: String?,
    val ipv6Addresses: List<String>,
    val gateway: String?,
    val subnetPrefixLength: Int?,
    val dnsServers: List<String>,
    val frequencyMhz: Int?,
    val channel: Int?,
    val band: WifiBand,
    val channelWidthMhz: Int?,
    val channelWidthNote: String?,
    val security: String?,
    val standard: String?,
    val linkSpeedMbps: Int?,
    val rssi: Int?,
    val signalLevel: SignalLevel?,
    val macAddress: String?,
    val isWifiEnabled: Boolean,
    val isConnected: Boolean,
    val isInternetValidated: Boolean,
    val resultAgeMillis: Long?,
    val unavailableNote: String?,
    val capturedAt: Long,
)

/** Una red detectada por el escáner. */
data class ScanNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val band: WifiBand,
    val security: String,
    val standard: String?,
    /**
     * Ancho de canal declarado por el sistema, cuando lo publica. Es `null` en
     * muchas versiones, y entonces hay que estimarlo a partir del ancho supuesto.
     */
    val reportedWidthMhz: Int?,
    /** Capacidades tal y como las publica el sistema, por ejemplo `[WPA2-PSK-CCMP][ESS]`. */
    val capabilities: String,
    val timestampMillis: Long,
    val isCurrentNetwork: Boolean,
    val isHidden: Boolean,
)

data class ScanResultSnapshot(
    val networks: List<ScanNetwork>,
    val scannedAt: Long,
    val isThrottled: Boolean,
    val message: String?,
)

/** Una red detectada seguida en el tiempo durante el historial del escáner. */
data class ScanTimePoint(
    val timeMillis: Long,
    val networks: List<ScanNetwork>,
) {
    val count: Int get() = networks.size
    val averageRssi: Double?
        get() = networks.map { it.rssi }.takeIf { it.isNotEmpty() }?.average()
    val strongestRssi: Int? get() = networks.maxOfOrNull { it.rssi }
}