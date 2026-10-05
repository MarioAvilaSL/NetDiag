package com.diagnostico.senales.data.net

import android.net.Network
import com.diagnostico.senales.data.model.PingMethod
import com.diagnostico.senales.data.model.PingSample
import com.diagnostico.senales.data.model.PingSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * Medición de latencia, jitter y pérdida de paquetes.
 *
 * Android no expone una API de ICMP. Se intenta `InetAddress.isReachable()`, que el
 * framework implementa con eco request cuando el sistema lo permite, y si no
 * responde se recurre a la latencia de conexión TCP, que se etiqueta de forma
 * explícita para no confundir una medida con otra.
 */
class PingEngine(private val transfer: HttpTransfer = HttpTransfer()) {

    suspend fun ping(
        host: String,
        count: Int,
        timeoutMillis: Int = 2_000,
        intervalMillis: Long = 400L,
        method: PingMethod = PingMethod.AUTO,
        network: Network? = null,
        onSample: (PingSample) -> Unit = {},
    ): PingSummary = withContext(Dispatchers.IO) {
        val url = UrlSpec.parse(if (host.contains("://")) host else "https://$host")

        val dnsStart = System.nanoTime()
        val address: InetAddress? = try {
            InetAddress.getByName(url?.host ?: host)
        } catch (_: Exception) {
            null
        }
        val dnsMillis = (System.nanoTime() - dnsStart) / 1_000_000.0

        if (address == null) {
            return@withContext PingSummary(
                target = host,
                methodLabel = method.label,
                methodDescription = method.description,
                sent = 0,
                received = 0,
                minMs = null,
                maxMs = null,
                averageMs = null,
                jitterMs = null,
                lossPercent = null,
                dnsResolutionMs = dnsMillis,
                samples = emptyList(),
                note = "No se pudo resolver el nombre «$host».",
            )
        }

        var effectiveMethod = method
        val samples = ArrayList<PingSample>(count)

        for (index in 1..count) {
            if (effectiveMethod == PingMethod.AUTO) {
                val probe = icmpReachable(address, timeoutMillis)
                effectiveMethod = if (probe) PingMethod.ICMP else PingMethod.TCP
            }

            val sample = when (effectiveMethod) {
                PingMethod.TCP -> {
                    val value = url?.let { transfer.tcpLatencyMillis(it, network, timeoutMillis) }
                    if (value == null) {
                        PingSample(index, false, null, "El servidor no aceptó la conexión")
                    } else {
                        PingSample(index, true, value, null)
                    }
                }

                else -> {
                    val start = System.nanoTime()
                    val reachable = try {
                        address.isReachable(timeoutMillis)
                    } catch (_: Exception) {
                        false
                    }
                    if (reachable) {
                        PingSample(index, true, (System.nanoTime() - start) / 1_000_000.0, null)
                    } else {
                        PingSample(index, false, null, "Sin respuesta ICMP")
                    }
                }
            }
            samples += sample
            onSample(sample)
            if (index < count) delay(intervalMillis)
        }

        val latencies = samples.mapNotNull { it.latencyMs }
        val received = latencies.size
        val sent = samples.size
        val loss = if (sent > 0) (sent - received) * 100.0 / sent else null

        PingSummary(
            target = host,
            methodLabel = effectiveMethod.label,
            methodDescription = effectiveMethod.description,
            sent = sent,
            received = received,
            minMs = latencies.minOrNull(),
            maxMs = latencies.maxOrNull(),
            averageMs = latencies.takeIf { it.isNotEmpty() }?.average(),
            jitterMs = jitterOf(latencies),
            lossPercent = loss,
            dnsResolutionMs = dnsMillis,
            samples = samples,
            note = if (effectiveMethod == PingMethod.ICMP) {
                "Android no garantiza que isReachable() use ICMP puro: en algunos equipos " +
                    "responde el enrutador o el sistema y no el host de destino."
            } else {
                "El sistema no respondió al ICMP, por eso la latencia se mide con la " +
                    "conexión TCP y puede incluir el handshake TLS."
            },
        )
    }

    private fun icmpReachable(address: InetAddress, timeoutMillis: Int): Boolean = try {
        address.isReachable(timeoutMillis)
    } catch (_: Exception) {
        false
    }

    companion object {
        /** Jitter medio: desviación absoluta media entre latencias consecutivas. */
        fun jitterOf(latencies: List<Double>): Double? {
            if (latencies.size < 2) return null
            val deltas = latencies.zipWithNext { a, b -> kotlin.math.abs(b - a) }
            return deltas.average()
        }
    }
}