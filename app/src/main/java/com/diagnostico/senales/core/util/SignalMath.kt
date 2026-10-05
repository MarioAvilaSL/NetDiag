package com.diagnostico.senales.core.util

import android.net.wifi.ScanResult
import android.os.Build
import kotlin.math.roundToInt

/** Nivel cualitativo de una señal en dBm (Wi-Fi o móvil). */
enum class SignalLevel(val label: String, val minDbm: Int) {
    EXCELENTE("Excelente", -55),
    BUENA("Buena", -67),
    REGULAR("Regular", -75),
    DEBIL("Débil", -85),
    MUY_DEBIL("Muy débil", -95);

    companion object {
        fun fromDbm(dbm: Int): SignalLevel = entries.firstOrNull { dbm >= it.minDbm } ?: MUY_DEBIL
    }
}

enum class Verdict(val label: String) {
    EXCELENTE("Excelente"),
    BUENA("Buena"),
    REGULAR("Regular"),
    MALA("Mala"),
}

/** Resultado de una evaluación con su explicación legible. */
data class Assessment(val verdict: Verdict, val score: Int, val reasons: List<String>)

fun rssiToQuality(dbm: Int?): Int? {
    if (dbm == null || dbm == Int.MIN_VALUE || dbm > 0) return null
    // Escala visual: -100 dBm -> 0 %, -30 dBm -> 100 %
    return (((dbm + 100) * 100) / 70).coerceIn(0, 100)
}

fun dbmToPercent(dbm: Int?): Int? {
    if (dbm == null || dbm == Int.MIN_VALUE || dbm > 0) return null
    // Escala habitual de señal móvil: -113 dBm -> 0 %, -51 dBm -> 100 %
    return (((dbm + 113) * 100) / 62).coerceIn(0, 100)
}

/**
 * Calidad GNSS en porcentaje a partir de la precisión horizontal declarada.
 * 5 m o menos se considera 100 % y 40 m o más, 0 %.
 */
fun qualityFromAccuracy(accuracyMeters: Float?): Int? {
    if (accuracyMeters == null || accuracyMeters.isNaN() || accuracyMeters.isInfinite()) return null
    return (((40f - accuracyMeters) * 100f) / 35f).toInt().coerceIn(0, 100)
}

fun levelColorName(level: SignalLevel): String = when (level) {
    SignalLevel.EXCELENTE -> "excelente"
    SignalLevel.BUENA -> "buena"
    SignalLevel.REGULAR -> "regular"
    SignalLevel.DEBIL -> "débil"
    SignalLevel.MUY_DEBIL -> "muy débil"
}

// ---------------------------------------------------------------------------
// Bandas y canales Wi-Fi
// ---------------------------------------------------------------------------

enum class WifiBand(val label: String) {
    BAND_2_4("2.4 GHz"),
    BAND_5("5 GHz"),
    BAND_6("6 GHz"),
    UNKNOWN("Banda no determinada"),
}

fun bandOf(frequencyMhz: Int): WifiBand = when (frequencyMhz) {
    in 2401..2495 -> WifiBand.BAND_2_4
    in 4900..5899 -> WifiBand.BAND_5
    in 5925..7125 -> WifiBand.BAND_6
    else -> WifiBand.UNKNOWN
}

/**
 * Canal a partir de la frecuencia. Android solo expone la frecuencia del
 * canal central, no su ancho; por eso el ancho se estima aparte.
 */
fun channelOf(frequencyMhz: Int): Int? = when (bandOf(frequencyMhz)) {
    WifiBand.BAND_2_4 -> if (frequencyMhz == 2484) 14 else (frequencyMhz - 2407) / 5
    WifiBand.BAND_5 -> (frequencyMhz - 5000) / 5
    WifiBand.BAND_6 -> (frequencyMhz - 5950) / 5
    WifiBand.UNKNOWN -> null
}

fun centerFrequencyOf(channel: Int, band: WifiBand): Int = when (band) {
    WifiBand.BAND_2_4 -> if (channel == 14) 2484 else 2407 + channel * 5
    WifiBand.BAND_5 -> 5000 + channel * 5
    WifiBand.BAND_6 -> 5950 + channel * 5
    WifiBand.UNKNOWN -> 0
}

/**
 * Canales que se solapan con [channel] suponiendo un ancho de [widthMhz].
 * Android no publica el ancho real del canal: es una estimación explícita.
 */
fun overlappingChannels(channel: Int, band: WifiBand, widthMhz: Int): List<Int> {
    if (band == WifiBand.UNKNOWN) return listOf(channel)
    val center = centerFrequencyOf(channel, band)
    val half = widthMhz / 2
    return ((center - half + 4)..(center + half - 4)).mapNotNull { channelOf(it) }.distinct().sorted()
}

/** Anchos de canal habituales para el selector de suposición del escáner. */
fun widthsForBand(band: WifiBand): List<Int> = when (band) {
    WifiBand.BAND_2_4 -> listOf(20)
    WifiBand.BAND_5 -> listOf(20, 40, 80, 160)
    WifiBand.BAND_6 -> listOf(20, 40, 80, 160, 320)
    WifiBand.UNKNOWN -> listOf(20)
}

// ---------------------------------------------------------------------------
// Seguridad Wi-Fi a partir de las capacidades públicas de ScanResult
// ---------------------------------------------------------------------------

/**
 * Android expone las capacidades de una red como texto (`[WPA2-PSK-CCMP][ESS]`),
 * no como bits. Se analiza ese texto; si no indica nada, se informa de que la
 * seguridad es desconocida en lugar de asumir que la red es abierta.
 */
fun securityLabelFromCapabilities(capabilities: String?): String {
    val raw = capabilities?.uppercase().orEmpty()
    if (raw.isBlank()) return "Desconocida"

    val labels = mutableListOf<String>()
    if ("OWE" in raw) labels += "OWE"
    if ("SAE" in raw) labels += "WPA3-SAE"
    if ("WPA3" in raw && "SAE" !in raw) labels += "WPA3"
    if ("WPA2" in raw || "RSN" in raw) labels += "WPA2"
    if (labels.isEmpty() && "WPA" in raw) labels += "WPA"
    if ("EAP" in raw && "WPA" in raw && labels.none { it.contains("Enterprise") }) {
        labels += "WPA-Enterprise"
    }
    if (labels.isEmpty() && "WEP" in raw) labels += "WEP"
    if (labels.isEmpty()) {
        return if ("ESS" in raw || "IBSS" in raw) "Sin cifrado publicado" else "Desconocida"
    }
    return labels.joinToString(" / ")
}

fun wifiStandardLabel(standard: Int): String = when (standard) {
    ScanResult.WIFI_STANDARD_LEGACY -> "Wi-Fi legado (802.11a/b/g)"
    ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
    ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
    ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
    ScanResult.WIFI_STANDARD_11AD -> "WiGig (802.11ad)"
    ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
    ScanResult.WIFI_STANDARD_UNKNOWN -> "Desconocido"
    else -> "Desconocido"
}

// ---------------------------------------------------------------------------
// Evaluaciones automáticas
// ---------------------------------------------------------------------------

private fun verdictFromScore(score: Int): Verdict = when {
    score >= 85 -> Verdict.EXCELENTE
    score >= 65 -> Verdict.BUENA
    score >= 40 -> Verdict.REGULAR
    else -> Verdict.MALA
}

/** Evalúa la calidad de una conexión Wi-Fi a partir de medias de la prueba. */
fun assessWifi(
    averageRssi: Int?,
    averageLatencyMs: Double?,
    lossPercent: Double?,
): Assessment {
    var score = 100
    val reasons = mutableListOf<String>()

    if (averageRssi == null) {
        score -= 15
        reasons += "No se pudo leer el RSSI de la conexión."
    } else {
        val penalty = when {
            averageRssi >= -55 -> 0
            averageRssi >= -67 -> 10
            averageRssi >= -75 -> 25
            averageRssi >= -85 -> 45
            else -> 65
        }
        score -= penalty
        reasons += "RSSI medio de $averageRssi dBm (${SignalLevel.fromDbm(averageRssi).label.lowercase()})."
    }

    if (averageLatencyMs == null) {
        score -= 15
        reasons += "No se pudo medir la latencia."
    } else {
        val penalty = when {
            averageLatencyMs <= 20 -> 0
            averageLatencyMs <= 50 -> 10
            averageLatencyMs <= 100 -> 25
            averageLatencyMs <= 200 -> 40
            else -> 55
        }
        score -= penalty
        reasons += "Latencia media de ${averageLatencyMs.roundToInt()} ms."
    }

    val loss = lossPercent ?: -1.0
    if (lossPercent == null) {
        score -= 15
        reasons += "No se pudo medir la pérdida de paquetes."
    } else {
        val penalty = when {
            loss <= 0.0 -> 0
            loss < 1.0 -> 10
            loss < 3.0 -> 25
            loss < 10.0 -> 40
            else -> 60
        }
        score -= penalty
        reasons += if (loss <= 0.0) {
            "Sin pérdida de paquetes en las mediciones."
        } else {
            String.format(FMT, "Pérdida de paquetes del %.1f %%.", loss)
        }
    }

    return Assessment(verdictFromScore(score.coerceIn(0, 100)), score.coerceIn(0, 100), reasons)
}

/** Evalúa la precisión de una sesión GNSS. */
fun assessGps(
    averageAccuracyMeters: Double?,
    satellitesUsed: Int?,
    averageCn0: Double?,
): Assessment {
    var score = 100
    val reasons = mutableListOf<String>()

    if (averageAccuracyMeters == null) {
        score -= 25
        reasons += "No se obtuvieron fixaciones GNSS válidas."
    } else {
        val penalty = when {
            averageAccuracyMeters <= 5 -> 0
            averageAccuracyMeters <= 10 -> 10
            averageAccuracyMeters <= 20 -> 25
            averageAccuracyMeters <= 50 -> 45
            else -> 65
        }
        score -= penalty
        reasons += String.format(
            FMT,
            "Precisión horizontal media de %.1f m.",
            averageAccuracyMeters,
        )
    }

    if (satellitesUsed == null || satellitesUsed <= 0) {
        score -= 30
        reasons += "Ningún satélite en uso para calcular la posición."
    } else {
        val penalty = when {
            satellitesUsed >= 10 -> 0
            satellitesUsed >= 7 -> 8
            satellitesUsed >= 5 -> 18
            satellitesUsed >= 3 -> 30
            else -> 45
        }
        score -= penalty
        reasons += "$satellitesUsed satélites en uso para la posición."
    }

    if (averageCn0 == null) {
        score -= 10
        reasons += "No se pudo medir el CN0 medio."
    } else {
        val penalty = when {
            averageCn0 >= 45 -> 0
            averageCn0 >= 40 -> 6
            averageCn0 >= 35 -> 12
            averageCn0 >= 30 -> 20
            else -> 30
        }
        score -= penalty
        reasons += String.format(FMT, "CN0 medio de %.1f dB-Hz.", averageCn0)
    }

    return Assessment(verdictFromScore(score.coerceIn(0, 100)), score.coerceIn(0, 100), reasons)
}

/** Distancia en metros entre dos coordenadas, con la fórmula del haversine. */
fun distanceMeters(
    latitude1: Double,
    longitude1: Double,
    latitude2: Double,
    longitude2: Double,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(latitude2 - latitude1)
    val dLon = Math.toRadians(longitude2 - longitude1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}