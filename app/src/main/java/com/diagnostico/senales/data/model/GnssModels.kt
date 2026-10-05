package com.diagnostico.senales.data.model

enum class Constellation(val label: String, val shortLabel: String) {
    UNKNOWN("Desconocida", "?"),
    GPS("GPS", "GPS"),
    SBAS("SBAS", "SBA"),
    GLONASS("GLONASS", "GLO"),
    QZSS("QZSS", "QZS"),
    BEIDOU("BeiDou", "BDU"),
    GALILEO("Galileo", "GAL"),
    NAVIC("NavIC / IRNSS", "NAV");

    companion object {
        fun fromGnssType(type: Int): Constellation = when (type) {
            1 -> SBAS
            2 -> GLONASS
            3 -> QZSS
            4 -> BEIDOU
            5 -> GALILEO
            6 -> NAVIC
            else -> GPS
        }
    }
}

data class SatelliteInfo(
    val svid: Int,
    val constellation: Constellation,
    val cn0DbHz: Float?,
    val elevationDegrees: Float?,
    val azimuthDegrees: Float?,
    val usedInFix: Boolean,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
) {
    val signalQualityPercent: Int?
        get() = cn0DbHz?.let { c ->
            (((c - 10f) * 100f) / 35f).toInt().coerceIn(0, 100)
        }
}

data class GnssFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val horizontalAccuracyMeters: Float?,
    val verticalAccuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val timeToFirstFixMillis: Long?,
    val fixAgeMillis: Long,
    val elapsedRealtimeNanos: Long,
    val provider: String,
    val timestampMillis: Long,
    val isMock: Boolean,
)

data class GnssProviderStatus(
    val provider: String,
    val enabled: Boolean,
    val requiresNetwork: Boolean,
    val requiresSatellite: Boolean,
)

data class GnssSnapshot(
    val isGnssEngineRunning: Boolean,
    val satellites: List<SatelliteInfo>,
    val fix: GnssFix?,
    val providers: List<GnssProviderStatus>,
    val errorMessage: String?,
    val capturedAt: Long,
) {
    val satellitesUsed: Int get() = satellites.count { it.usedInFix }
    val averageCn0: Float?
        get() = satellites.mapNotNull { it.cn0DbHz }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val timestampMillis: Long,
    val satellitesUsed: Int,
)

data class TrackStats(
    val pointCount: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val averageSpeedMetersPerSecond: Double,
    val maxSpeedMetersPerSecond: Double,
    val minAltitudeMeters: Double?,
    val maxAltitudeMeters: Double?,
    val averageAccuracyMeters: Double?,
)