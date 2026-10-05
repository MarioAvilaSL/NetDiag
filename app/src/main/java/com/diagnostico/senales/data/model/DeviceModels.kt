package com.diagnostico.senales.data.model

/** Estado general de la conectividad según ConnectivityManager. */
data class ConnectivityStatus(
    val isActive: Boolean,
    val hasInternetCapability: Boolean,
    val isValidated: Boolean,
    val primaryTransport: String?,
    val transports: List<String>,
    val availableTransports: List<String>,
    val isWifi: Boolean,
    val isCellular: Boolean,
    val isEthernet: Boolean,
    val isVpn: Boolean,
    val metered: Boolean,
    val downlinkMbps: Int?,
    val uplinkMbps: Int?,
    val interfaceName: String?,
    val linkAddresses: List<String>,
    val dnsServers: List<String>,
    val captivePortal: Boolean?,
    val capturedAt: Long,
) {
    val internetStatusLabel: String
        get() = when {
            !isActive -> "Sin red"
            isValidated -> "Internet disponible"
            hasInternetCapability -> "Red sin acceso a Internet"
            else -> "Red activa sin Internet"
        }
}

data class SensorInfo(
    val name: String,
    val typeLabel: String,
    val vendor: String,
    val version: Int,
    val maxRange: String,
    val power: String,
    val resolution: String,
    val accuracyLabel: String,
    val isDynamic: Boolean?,
    val isWakeUpSensor: Boolean?,
)

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val hardware: String,
    val board: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildId: String,
    val fingerprintShort: String,
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val lowMemory: Boolean,
    val totalStorageBytes: Long,
    val availableStorageBytes: Long,
    val batteryLevelPercent: Int?,
    val batteryStatus: String,
    val batteryHealth: String,
    val batteryTemperatureCelsius: Float?,
    val batteryVoltageMilliVolts: Int?,
    val batteryCurrentMicroAmps: Int?,
    val batteryChargeCounterMicroAmpHours: Int?,
    val batteryTechnology: String?,
    val chargingSource: String,
    val bluetoothSupported: Boolean,
    val bluetoothState: String,
    val bluetoothName: String?,
    val nfcSupported: Boolean,
    val nfcState: String,
    val sensors: List<SensorInfo>,
    val wifiInterfaceIpv4: String?,
    val wifiInterfaceIpv6: String?,
    val mobileInterfaceIpv4: String?,
    val mobileInterfaceIpv6: String?,
    val anyInterfaceIpv4: String?,
    val macAddress: String?,
    val macNote: String?,
    val capturedAt: Long,
)