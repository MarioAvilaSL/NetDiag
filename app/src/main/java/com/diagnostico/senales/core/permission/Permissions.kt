package com.diagnostico.senales.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class PermissionGroupId { LOCATION, NEARBY_WIFI, BLUETOOTH, TELEPHONY }

/**
 * Un grupo de permisos con su explicación. La app pide cada grupo justo antes de
 * usarse por primera vez y siempre muestra el motivo.
 */
data class PermissionGroup(
    val id: PermissionGroupId,
    val title: String,
    val why: String,
    val permissions: List<String>,
    val modules: String,
)

object AppPermissions {

    val location: List<String> = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val nearbyWifi: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            emptyList()
        }

    val bluetoothConnect: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH)
        }

    val telephony: List<String> = listOf(Manifest.permission.READ_PHONE_STATE)

    val groups: List<PermissionGroup> = listOf(
        PermissionGroup(
            id = PermissionGroupId.LOCATION,
            title = "Ubicación",
            why = "Android solo revela el SSID y el BSSID de la red Wi-Fi, los resultados " +
                "del escaneo y los datos de los satélites GNSS cuando la app tiene permiso " +
                "de ubicación. Sin él, esos módulos muestran «no disponible».",
            permissions = location,
            modules = "Wi-Fi, escáner de redes, GNSS, mapa y precisión",
        ),
        PermissionGroup(
            id = PermissionGroupId.NEARBY_WIFI,
            title = "Dispositivos Wi-Fi cercanos",
            why = "Desde Android 13, enumerar redes Wi-Fi cercanas exige este permiso. " +
                "Se declara sin «ubicación» porque la app no deduce la posición a partir de él.",
            permissions = nearbyWifi,
            modules = "Escáner de redes Wi-Fi",
        ),
        PermissionGroup(
            id = PermissionGroupId.BLUETOOTH,
            title = "Bluetooth",
            why = "Permite consultar si el adaptador Bluetooth está activo y su nombre. " +
                "La app no empareja ni transfiere datos por Bluetooth.",
            permissions = bluetoothConnect,
            modules = "Diagnóstico del dispositivo",
        ),
        PermissionGroup(
            id = PermissionGroupId.TELEPHONY,
            title = "Estado del teléfono",
            why = "Da acceso al operador, al estado de la SIM y al tipo de red móvil. " +
                "La app nunca lee el IMSI, el ICCID ni números de línea.",
            permissions = telephony,
            modules = "Red móvil y SIM",
        ),
    )

    fun group(id: PermissionGroupId): PermissionGroup = groups.first { it.id == id }

    fun permissionsFor(id: PermissionGroupId): List<String> = group(id).permissions
}

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasAll(permissions: List<String>): Boolean =
    permissions.all { hasPermission(it) }

fun Context.missingPermissions(permissions: List<String>): List<String> =
    permissions.filterNot { hasPermission(it) }