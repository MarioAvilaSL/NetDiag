package com.diagnostico.senales.data.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.diagnostico.senales.core.permission.AppPermissions
import com.diagnostico.senales.core.permission.hasAll
import com.diagnostico.senales.core.util.SignalLevel
import com.diagnostico.senales.core.util.bandOf
import com.diagnostico.senales.core.util.channelOf
import com.diagnostico.senales.core.util.rssiToQuality
import com.diagnostico.senales.core.util.securityLabelFromCapabilities
import com.diagnostico.senales.core.util.wifiStandardLabel
import com.diagnostico.senales.data.model.ScanNetwork
import com.diagnostico.senales.data.model.ScanResultSnapshot
import com.diagnostico.senales.data.model.WifiConnectionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/**
 * Datos de la conexión Wi-Fi actual y escaneo de redes cercanas.
 *
 * Dos límites de Android condicionan este código y se explican en pantalla:
 *  - El SSID/BSSID y los resultados de escaneo exigen permiso de ubicación.
 *  - Desde Android 9 el sistema limita a 4 escaneos por cada 2 minutos y por eso
 *    [scan] devuelve resultados cacheados con `isThrottled = true`.
 */
class WifiRepository(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile
    private var lastScan: ScanResultSnapshot? = null

    fun isEnabled(): Boolean = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)

    fun canScan(): Boolean = appContext.hasAll(
        AppPermissions.location + AppPermissions.nearbyWifi,
    )

    fun lastScanSnapshot(): ScanResultSnapshot? = lastScan

    suspend fun scan(): ScanResultSnapshot = withContext(Dispatchers.IO) {
        if (!canScan()) {
            return@withContext ScanResultSnapshot(
                networks = emptyList(),
                scannedAt = System.currentTimeMillis(),
                isThrottled = false,
                message = "Faltan los permisos de ubicación y de dispositivos Wi-Fi cercanos, " +
                    "obligatorios en Android para leer el SSID y enumerar redes.",
            )
        }

        var throttled = false
        var message: String? = null
        val started = try {
            wifiManager.startScan()
        } catch (_: Exception) {
            false
        }

        if (started) {
            awaitScanResults(timeoutMillis = 12_000L)
        } else {
            throttled = true
            message = THROTTLE_MESSAGE
        }

        val raw = try {
            wifiManager.scanResults
        } catch (_: SecurityException) {
            null
        }

        val snapshot = if (raw == null) {
            ScanResultSnapshot(
                networks = emptyList(),
                scannedAt = System.currentTimeMillis(),
                isThrottled = throttled,
                message = "El sistema no ha devuelto resultados de escaneo.",
            )
        } else {
            val currentBssid = currentBssid()
            ScanResultSnapshot(
                networks = raw
                    .map { it.toScanNetwork(currentBssid) }
                    .sortedByDescending { it.rssi },
                scannedAt = System.currentTimeMillis(),
                isThrottled = throttled,
                message = message,
            )
        }
        lastScan = snapshot
        snapshot
    }

    fun connectionInfo(): WifiConnectionInfo {
        val enabled = isEnabled()
        val activeNetwork = runCatching { connectivityManager.activeNetwork }.getOrNull()
        val capabilities = activeNetwork?.let {
            runCatching { connectivityManager.getNetworkCapabilities(it) }.getOrNull()
        }
        val linkProperties = activeNetwork?.let {
            runCatching { connectivityManager.getLinkProperties(it) }.getOrNull()
        }

        val transportInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { capabilities?.transportInfo as? WifiInfo }.getOrNull()
        } else {
            null
        }
        val legacyInfo = try {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        } catch (_: Exception) {
            null
        }
        val info = transportInfo ?: legacyInfo

        val rawSsid = try {
            info?.ssid
        } catch (_: Exception) {
            null
        }
        val ssidHidden = rawSsid.isNullOrBlank() || rawSsid == WifiManager.UNKNOWN_SSID
        val ssid = rawSsid?.removeSurrounding("\"")?.takeIf { !ssidHidden }

        val bssidRaw = try {
            info?.bssid
        } catch (_: Exception) {
            null
        }
        val bssidMasked = bssidRaw.isNullOrBlank() || bssidRaw == MASKED_MAC
        val bssid = bssidRaw?.takeIf { !bssidMasked }

        val frequency = try {
            info?.frequency?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }

        val rssi = try {
            info?.rssi?.takeIf { it != -127 && it in -126..0 }
        } catch (_: Exception) {
            null
        }

        val linkSpeed = try {
            info?.linkSpeed?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }

        val ipv4Addresses = linkProperties?.linkAddresses
            ?.mapNotNull { it.address as? Inet4Address }
            ?.map { it.hostAddress.orEmpty() }
            .orEmpty()
        val ipv6Addresses = linkProperties?.linkAddresses
            ?.mapNotNull { it.address as? java.net.Inet6Address }
            ?.map { it.hostAddress.orEmpty() }
            .orEmpty()

        val gateway = linkProperties?.routes
            ?.firstOrNull { it.isDefaultRoute }
            ?.gateway
            ?.hostAddress

        val dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()

        val prefixLength = linkProperties?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.prefixLength

        val isConnected = capabilities?.hasTransport(
            android.net.NetworkCapabilities.TRANSPORT_WIFI,
        ) == true

        val currentScanEntry = lastScan?.networks?.firstOrNull {
            bssid != null && it.bssid.equals(bssid, ignoreCase = true)
        }

        val unavailableNote = when {
            !enabled -> "El Wi-Fi está desactivado."
            !isConnected -> "El dispositivo no está conectado a ninguna red Wi-Fi."
            ssidHidden -> "Android no revela el nombre de la red. Suele pasar porque el " +
                "permiso de ubicación se concedió después de conectar: en ese caso hay " +
                "que apagar y encender el Wi-Fi. También ocurre si la red está " +
                "oculta o si el fabricante lo restringe."
            else -> null
        }

        return WifiConnectionInfo(
            ssid = ssid,
            bssid = bssid,
            hiddenSsid = ssidHidden,
            ipAddress = ipv4Addresses.firstOrNull(),
            ipv6Addresses = ipv6Addresses,
            gateway = gateway,
            subnetPrefixLength = prefixLength,
            dnsServers = dnsServers,
            frequencyMhz = frequency,
            channel = frequency?.let { channelOf(it) },
            band = frequency?.let { bandOf(it) } ?: com.diagnostico.senales.core.util.WifiBand.UNKNOWN,
            channelWidthMhz = null,
            channelWidthNote = CHANNEL_WIDTH_NOTE,
            security = currentScanEntry?.security,
            standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    info?.wifiStandard?.let { wifiStandardLabel(it) }
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            },
            linkSpeedMbps = linkSpeed,
            rssi = rssi,
            signalLevel = rssi?.let { SignalLevel.fromDbm(it) },
            macAddress = if (bssidMasked) null else bssid,
            isWifiEnabled = enabled,
            isConnected = isConnected,
            isInternetValidated = capabilities?.hasCapability(
                android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ) == true,
            resultAgeMillis = currentScanEntry?.let { System.currentTimeMillis() - it.timestampMillis },
            unavailableNote = unavailableNote,
            capturedAt = System.currentTimeMillis(),
        )
    }

    fun signalQualityPercent(rssi: Int?): Int? = rssiToQuality(rssi)

    private fun currentBssid(): String? = try {
        @Suppress("DEPRECATION")
        wifiManager.connectionInfo?.bssid?.takeIf { it.isNotBlank() && it != MASKED_MAC }
    } catch (_: Exception) {
        null
    }

    private fun ScanResult.toScanNetwork(currentBssid: String?): ScanNetwork {
        val channel = channelOf(frequency)
        val hidden = SSID.isNullOrBlank()
        return ScanNetwork(
            ssid = if (hidden) HIDDEN_SSID_LABEL else SSID.orEmpty().removeSurrounding("\""),
            bssid = BSSID?.lowercase().orEmpty(),
            rssi = level,
            frequencyMhz = frequency,
            channel = channel,
            band = bandOf(frequency),
            security = securityLabelFromCapabilities(capabilities),
            standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wifiStandardLabel(wifiStandard)
            } else {
                null
            },
            capabilities = capabilities.orEmpty(),
            reportedWidthMhz = reportedWidthMhz(),
            timestampMillis = timestamp,
            isCurrentNetwork = currentBssid != null && BSSID.equals(currentBssid, ignoreCase = true),
            isHidden = hidden,
        )
    }

    /**
     * Ancho de canal según los campos públicos `channelWidth` y `centerFreq0` de
     * `ScanResult`. Cuando el sistema no lo informa devuelve `null` y la interfaz
     * usa el ancho supuesto por el usuario, dejando constancia de ello.
     */
    private fun ScanResult.reportedWidthMhz(): Int? = when (channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> 20
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ -> 160
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            channelWidth == ScanResult.CHANNEL_WIDTH_320MHZ
        ) {
            320
        } else {
            null
        }
    }

    private suspend fun awaitScanResults(timeoutMillis: Long) {
        val context = appContext
        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                var receiver: BroadcastReceiver? = null
                val cleanup = {
                    receiver?.let { runCatching { context.unregisterReceiver(it) } }
                    receiver = null
                }
                receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, intent: Intent?) {
                        cleanup()
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                continuation.invokeOnCancellation { cleanup() }
            }
        }
    }

    companion object {
        const val THROTTLE_MESSAGE =
            "Android limita el escaneo a 4 ejecuciones cada 2 minutos. Se muestran los " +
                "resultados guardados por el sistema, que pueden ser los de un escaneo anterior."

        const val CHANNEL_WIDTH_NOTE =
            "Android no publica el ancho de canal de una red: solo la frecuencia del canal " +
                "central. Los anchos del escáner son estimaciones con el valor supuesto."

        const val MASKED_MAC = "02:00:00:00:00:00"
        const val HIDDEN_SSID_LABEL = "(red oculta)"
    }
}