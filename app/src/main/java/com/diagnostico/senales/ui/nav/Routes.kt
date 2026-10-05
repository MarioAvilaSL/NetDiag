package com.diagnostico.senales.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val DASHBOARD = "dashboard"
    const val WIFI = "wifi"
    const val SCAN = "scan"
    const val CELLULAR = "cellular"
    const val GNSS = "gnss"
    const val DEVICE = "device"
    const val SIM = "sim"
    const val SKY_PLOT = "skyplot"
    const val TRACK = "track"
    const val ACCURACY = "accuracy"
    const val HISTORY = "history"
    const val PERMISSIONS = "permissions"
    const val ABOUT = "about"
    const val MORE = "more"
}

data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDestination(Routes.DASHBOARD, "Panel", Icons.Outlined.Dashboard),
    TopLevelDestination(Routes.WIFI, "Wi-Fi", Icons.Outlined.Wifi),
    TopLevelDestination(Routes.SCAN, "Escáner", Icons.Outlined.Radar),
    TopLevelDestination(Routes.CELLULAR, "Móvil", Icons.Outlined.SignalCellularAlt),
    TopLevelDestination(Routes.GNSS, "GNSS", Icons.Outlined.SatelliteAlt),
)

/** Destinos secundarios, alcanzables desde el menú "Más" y desde el panel. */
data class SecondaryDestination(
    val route: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
)

val secondaryDestinations = listOf(
    SecondaryDestination(
        Routes.DEVICE,
        "Dispositivo",
        "Hardware, batería, sensores y direcciones de red",
        Icons.Outlined.Devices,
    ),
    SecondaryDestination(
        Routes.SIM,
        "SIM",
        "Estado de las tarjetas SIM y del operador",
        Icons.Outlined.SimCard,
    ),
    SecondaryDestination(
        Routes.SKY_PLOT,
        "Cielo GNSS",
        "Sky plot con constelaciones, azimut y elevación",
        Icons.Outlined.SettingsInputComponent,
    ),
    SecondaryDestination(
        Routes.TRACK,
        "Mapa y ruta",
        "Trayectoria, precisión y estadísticas del recorrido",
        Icons.Outlined.Map,
    ),
    SecondaryDestination(
        Routes.ACCURACY,
        "Precisión GPS",
        "Prueba cronometrada de precisión y clasificación",
        Icons.Outlined.Timeline,
    ),
    SecondaryDestination(
        Routes.HISTORY,
        "Historial",
        "Todas las pruebas guardadas en el dispositivo",
        Icons.Outlined.History,
    ),
    SecondaryDestination(
        Routes.PERMISSIONS,
        "Permisos",
        "Qué se pide, para qué y qué pasa si se deniega",
        Icons.Outlined.MyLocation,
    ),
    SecondaryDestination(
        Routes.ABOUT,
        "Información",
        "Versión, repositorio y privacidad",
        Icons.Outlined.Code,
    ),
)

val moreDestinations = listOf(
    TopLevelDestination(Routes.MORE, "Más", Icons.Outlined.GridView),
)