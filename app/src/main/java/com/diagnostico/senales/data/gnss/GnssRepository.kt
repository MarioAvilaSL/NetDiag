package com.diagnostico.senales.data.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.diagnostico.senales.data.model.Constellation
import com.diagnostico.senales.data.model.GnssFix
import com.diagnostico.senales.data.model.GnssProviderStatus
import com.diagnostico.senales.data.model.GnssSnapshot
import com.diagnostico.senales.data.model.SatelliteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Motor GNSS y estado de los satélites.
 *
 * `GnssStatus` es la única API pública que da constelación, CN0, azimut y
 * elevación. Exige permiso de ubicación fina y, en algunos fabricantes, que el
 * chip GNSS esté habilitado en los ajustes del sistema.
 */
class GnssRepository(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _satellites = MutableStateFlow<List<SatelliteInfo>>(emptyList())
    val satellites: StateFlow<List<SatelliteInfo>> = _satellites.asStateFlow()

    private val _fix = MutableStateFlow<GnssFix?>(null)
    val fix: StateFlow<GnssFix?> = _fix.asStateFlow()

    private val _engineRunning = MutableStateFlow(false)
    val engineRunning: StateFlow<Boolean> = _engineRunning.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _firstFixSatellites = MutableStateFlow<Int?>(null)
    val firstFixSatellites: StateFlow<Int?> = _firstFixSatellites.asStateFlow()

    private var started = false
    private var sessionStartNanos = 0L

    /**
     * La API pública de GNSS es por índice: no expone una clase `Satellite`. Cada
     * lectura se hace con `GnssStatus.get*(index)`, que es la única forma
     * soportada de obtener C/N0, azimut y elevación.
     */
    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onStarted() {
            _engineRunning.value = true
        }

        override fun onFirstFix(satelliteCount: Int) {
            _firstFixSatellites.value = satelliteCount
        }

        override fun onSatelliteStatusChanged(status: GnssStatus) {
            _satellites.value = (0 until status.satelliteCount).map { index ->
                SatelliteInfo(
                    svid = status.getSvid(index),
                    constellation = Constellation.fromGnssType(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            status.getConstellationType(index)
                        } else {
                            GnssStatus.CONSTELLATION_GPS
                        },
                    ),
                    cn0DbHz = status.getCn0DbHz(index).takeIf { it > 0f },
                    elevationDegrees = status.getElevationDegrees(index).takeIf { it >= 0f },
                    azimuthDegrees = status.getAzimuthDegrees(index).takeIf { it >= 0f },
                    usedInFix = status.usedInFix(index),
                    hasAlmanac = status.hasAlmanacData(index),
                    hasEphemeris = status.hasEphemerisData(index),
                )
            }
            _engineRunning.value = true
        }

        override fun onStopped() {
            _engineRunning.value = false
            _satellites.value = emptyList()
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _fix.value = location.toGnssFix(location.provider.orEmpty())
        }

        @Deprecated("Obligatorio en API < 30")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) _engineRunning.value = false
        }
    }

    fun providers(): List<GnssProviderStatus> = runCatching {
        locationManager.getProviders(true).map { provider ->
            val properties = runCatching { locationManager.getProvider(provider) }.getOrNull()
            GnssProviderStatus(
                provider = providerLabel(provider),
                enabled = runCatching { locationManager.isProviderEnabled(provider) }
                    .getOrDefault(false),
                requiresNetwork = properties?.requiresNetwork() ?: false,
                requiresSatellite = properties?.requiresSatellite() ?: false,
            )
        }
    }.getOrDefault(emptyList())

    fun snapshot(): GnssSnapshot = GnssSnapshot(
        isGnssEngineRunning = _engineRunning.value,
        satellites = _satellites.value,
        fix = _fix.value,
        providers = providers(),
        errorMessage = _errorMessage.value,
        capturedAt = System.currentTimeMillis(),
    )

    fun start() {
        if (started) return
        _errorMessage.value = null
        sessionStartNanos = SystemClock.elapsedRealtimeNanos()

        try {
            locationManager.registerGnssStatusCallback(
                ContextCompat.getMainExecutor(appContext),
                gnssCallback,
            )
        } catch (_: SecurityException) {
            _errorMessage.value = "Falta el permiso de ubicación: Android no entrega el " +
                "estado de los satélites sin él."
            return
        } catch (_: Exception) {
            _errorMessage.value = "El dispositivo no expone el estado de los satélites GNSS."
            return
        }

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        providers.forEach { provider ->
            try {
                if (locationManager.allProviders.contains(provider)) {
                    locationManager.requestLocationUpdates(
                        provider,
                        LOCATION_INTERVAL_MILLIS,
                        LOCATION_MIN_DISTANCE_METERS,
                        locationListener,
                        Looper.getMainLooper(),
                    )
                }
            } catch (_: SecurityException) {
                _errorMessage.value = "Falta el permiso de ubicación para leer la posición."
            } catch (_: Exception) {
                // Un proveedor concreto puede no existir; se ignora.
            }
        }
        started = true
    }

    fun stop() {
        if (!started) return
        runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
        runCatching { locationManager.removeUpdates(locationListener) }
        started = false
        _engineRunning.value = false
        _satellites.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun Location.toGnssFix(provider: String): GnssFix {
        val timestamp = time.takeIf { it > 0 } ?: System.currentTimeMillis()
        return GnssFix(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = if (hasAltitude()) altitude else null,
            horizontalAccuracyMeters = if (hasAccuracy()) accuracy else null,
            verticalAccuracyMeters = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy()
            ) {
                verticalAccuracyMeters
            } else {
                null
            },
            speedMetersPerSecond = if (hasSpeed()) speed else null,
            bearingDegrees = if (hasBearing()) bearing else null,
            timeToFirstFixMillis = if (sessionStartNanos > 0L) {
                ((elapsedRealtimeNanos - sessionStartNanos) / 1_000_000L)
                    .takeIf { it in 0..MAX_REASONABLE_TTFF_MILLIS }
            } else {
                null
            },
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            provider = providerLabel(provider),
            timestampMillis = timestamp,
            fixAgeMillis = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L),
            isMock = isFromMockProvider,
        )
    }

    private fun providerLabel(provider: String): String = when (provider) {
        LocationManager.GPS_PROVIDER -> "GPS"
        LocationManager.NETWORK_PROVIDER -> "Red"
        LocationManager.FUSED_PROVIDER -> "Fusionado"
        else -> provider
    }

    private companion object {
        const val LOCATION_INTERVAL_MILLIS = 1_000L
        const val LOCATION_MIN_DISTANCE_METERS = 0f

        // El TTFF no es público: se deriva del reloj del propio proceso. Si el
        // resultado no es creíble se informa como no disponible.
        const val MAX_REASONABLE_TTFF_MILLIS = 300_000L
    }
}