package com.diagnostico.senales.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Texto único para cualquier dato que Android no permite leer. Aparece en toda la
 * app para que el usuario distinga "no disponible" de "aún no medido".
 */
const val NO_DISPONIBLE = "Información no disponible en este dispositivo o versión de Android"

val FMT: Locale = Locale("es", "ES")

private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", FMT)
private val timeFormat = SimpleDateFormat("HH:mm:ss", FMT)

fun valueOrUnavailable(value: String?): String =
    if (value.isNullOrBlank()) NO_DISPONIBLE else value

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0L) return NO_DISPONIBLE
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) {
        String.format(FMT, "%.0f %s", value, units[index])
    } else {
        String.format(FMT, "%.2f %s", value, units[index])
    }
}

fun formatDecimal(value: Double?, decimals: Int = 1, suffix: String = ""): String {
    if (value == null || value.isNaN() || value.isInfinite()) return NO_DISPONIBLE
    return String.format(FMT, "%.${decimals}f%s", value, suffix)
}

fun formatInt(value: Int?, suffix: String = ""): String {
    if (value == null) return NO_DISPONIBLE
    return String.format(FMT, "%,d%s", value, suffix).replace(",", ".")
}

fun formatMs(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) return NO_DISPONIBLE
    return String.format(FMT, "%.0f ms", value)
}

fun formatMbps(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) return NO_DISPONIBLE
    return String.format(FMT, "%.2f Mbps", value)
}

fun formatDbms(value: Int?): String {
    if (value == null || value == Int.MIN_VALUE) return NO_DISPONIBLE
    return String.format(FMT, "%d dBm", value)
}

fun formatFrequency(mhz: Int?): String {
    if (mhz == null || mhz <= 0) return NO_DISPONIBLE
    return if (mhz > 1000) {
        String.format(FMT, "%.3f GHz", mhz / 1000.0)
    } else {
        String.format(FMT, "%d MHz", mhz)
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(FMT, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(FMT, "%02d:%02d", minutes, seconds)
    }
}

fun formatDistance(meters: Double?): String {
    if (meters == null || meters.isNaN() || meters.isInfinite()) return NO_DISPONIBLE
    return if (meters < 1000.0) {
        String.format(FMT, "%.1f m", meters)
    } else {
        String.format(FMT, "%.3f km", meters / 1000.0)
    }
}

fun formatSpeed(metersPerSecond: Double?): String {
    if (metersPerSecond == null || metersPerSecond.isNaN() || metersPerSecond.isInfinite()) {
        return NO_DISPONIBLE
    }
    return String.format(FMT, "%.2f km/h", metersPerSecond * 3.6)
}

fun formatDateTime(epochMillis: Long): String =
    synchronized(dateTimeFormat) { dateTimeFormat.format(Date(epochMillis)) }

fun formatClock(epochMillis: Long): String =
    synchronized(timeFormat) { timeFormat.format(Date(epochMillis)) }

fun formatAge(ageMillis: Long): String = when {
    ageMillis < 0 -> "recién actualizado"
    ageMillis < 1000 -> "hace menos de 1 s"
    ageMillis < 60_000 -> "hace ${ageMillis / 1000} s"
    ageMillis < 3_600_000 -> "hace ${ageMillis / 60_000} min"
    else -> "hace ${ageMillis / 3_600_000} h"
}

fun formatCoordinate(value: Double, isLatitude: Boolean): String {
    val hemisphere = when {
        !isLatitude -> ""
        value >= 0 -> " N"
        else -> " S"
    }
    val magnitude = kotlin.math.abs(value)
    return String.format(FMT, "%.6f°%s", magnitude, hemisphere)
}

fun formatAltitude(meters: Double?): String {
    if (meters == null || meters.isNaN() || meters.isInfinite()) return NO_DISPONIBLE
    return String.format(FMT, "%.1f m", meters)
}

fun formatPercent(value: Int?): String {
    if (value == null) return NO_DISPONIBLE
    return String.format(FMT, "%d %%", value.coerceIn(0, 100))
}