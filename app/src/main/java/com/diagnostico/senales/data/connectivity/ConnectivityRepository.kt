package com.diagnostico.senales.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import com.diagnostico.senales.data.model.ConnectivityStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class ConnectivityRepository(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Estado actual, leído en el momento. */
    fun current(): ConnectivityStatus = readStatus(connectivityManager.activeNetwork)

    /**
     * Flujo con el estado de la red por defecto. Emite al suscribirse y cada vez
     * que cambia la red activa.
     */
    fun observe(): Flow<ConnectivityStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(readStatus(network))
            }

            override fun onLost(network: Network) {
                trySend(readStatus(connectivityManager.activeNetwork))
            }

            override fun onUnavailable() {
                trySend(readStatus(null))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        trySend(readStatus(connectivityManager.activeNetwork))
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    private fun readStatus(network: Network?): ConnectivityStatus {
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val linkProperties = network?.let { connectivityManager.getLinkProperties(it) }
        val transports = capabilities?.let(::transportLabels).orEmpty()
        val primary = capabilities
            ?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
            ?.let { "Wi-Fi" }
            ?: capabilities
                ?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }
                ?.let { "Móvil" }
            ?: capabilities
                ?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }
                ?.let { "Ethernet" }
            ?: capabilities
                ?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
                ?.let { "VPN" }

        return ConnectivityStatus(
            isActive = network != null && capabilities != null,
            hasInternetCapability = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
            ) == true,
            isValidated = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ) == true,
            primaryTransport = primary,
            transports = transports,
            availableTransports = availableTransports(),
            isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            isEthernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
            isVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            metered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            downlinkMbps = capabilities?.linkDownstreamBandwidthKbps
                ?.takeIf { it > 0 }
                ?.div(1000),
            uplinkMbps = capabilities?.linkUpstreamBandwidthKbps
                ?.takeIf { it > 0 }
                ?.div(1000),
            interfaceName = linkProperties?.interfaceName,
            linkAddresses = linkProperties?.linkAddresses?.map { it.toString() }.orEmpty(),
            dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
            captivePortal = capabilities?.let { caps ->
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            },
            capturedAt = System.currentTimeMillis(),
        )
    }

    private fun availableTransports(): List<String> = buildList {
        val all = connectivityManager.allNetworks
        all.forEach { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@forEach
            transportLabels(caps).forEach { label ->
                if (label !in this) add(label)
            }
        }
    }

    private fun transportLabels(capabilities: NetworkCapabilities): List<String> = buildList {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Móvil")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("Bluetooth")
    }

    /** Callback auxiliar para detectar cambios de enlace durante una prueba. */
    fun defaultNetworkCallback(onChanged: () -> Unit): ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChanged()
            override fun onLost(network: Network) = onChanged()
            override fun onUnavailable() = onChanged()
        }

    fun registerDefault(callback: ConnectivityManager.NetworkCallback) {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(
                callback,
                Handler(Looper.getMainLooper()),
            )
        }
    }

    fun unregister(callback: ConnectivityManager.NetworkCallback) {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}