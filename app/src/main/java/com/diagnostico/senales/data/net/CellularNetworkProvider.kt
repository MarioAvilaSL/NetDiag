package com.diagnostico.senales.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Consigue un handle de red móvil para que las pruebas se ejecuten sobre la red
 * celular aunque haya Wi-Fi conectado. Devolver `null` significa que los datos
 * móviles están desactivados o que el sistema no concede la red.
 *
 * El callback se mantiene registrado mientras dura la prueba: es lo que impide
 * que el sistema libere la red solicitada. [release] lo cancela.
 */
class CellularNetworkProvider(context: Context) {

    private val connectivityManager =
        (context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager)
    private val heldCallbacks = ConcurrentHashMap<Network, ConnectivityManager.NetworkCallback>()

    /** Red móvil ya activa, o null si ahora mismo no hay datos móviles en uso. */
    suspend fun activeCellularNetwork(): Network? = withContext(Dispatchers.IO) {
        val active = connectivityManager.activeNetwork
        val capabilities = active?.let { connectivityManager.getNetworkCapabilities(it) }
        if (active != null &&
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        ) {
            active
        } else {
            null
        }
    }

    suspend fun requestCellularNetwork(
        timeoutMillis: Long = REQUEST_TIMEOUT_MILLIS,
    ): Network? = withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            val callback = object : ConnectivityManager.NetworkCallback() {
                private var resolved = false

                override fun onAvailable(network: Network) {
                    resolve(network)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        resolve(network)
                    }
                }

                override fun onUnavailable() {
                    if (resolved) return
                    resolved = true
                    runCatching { connectivityManager.unregisterNetworkCallback(this) }
                    if (continuation.isActive) continuation.resume(null)
                }

                private fun resolve(network: Network) {
                    if (resolved) return
                    resolved = true
                    heldCallbacks[network] = this
                    if (continuation.isActive) continuation.resume(network)
                }
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val registered = try {
                connectivityManager.requestNetwork(request, callback, Handler(Looper.getMainLooper()))
                true
            } catch (_: Exception) {
                false
            }

            if (!registered && continuation.isActive) continuation.resume(null)

            continuation.invokeOnCancellation {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
        }
    }

    fun release(network: Network?) {
        val callback = network?.let { heldCallbacks.remove(it) } ?: return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 10_000L
    }
}