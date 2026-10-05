package com.diagnostico.senales.ui.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Modo privacidad: sustituye los datos identificativos por puntos para poder
 * compartir capturas de pantalla sin filtrar la red, el operador ni la posición.
 *
 * No protege el dato en sí, solo lo que se ve: el historial guardado y la
 * exportación CSV siguen teniendo los valores reales. Para eso están los
 * permisos y el resto de pantallas.
 */
val LocalPrivacyMode = staticCompositionLocalOf { false }

/** Envoltura que reparte el modo a todo el árbol de pantallas. */
@Composable
fun PrivacyModeProvider(enabled: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPrivacyMode provides enabled, content = content)
}

/** Máscaras de longitud fija: no revelan ni el valor ni quantos caracteres tenía. */
object Masks {
    const val TEXT = "••••••"
    const val IPV4 = "•••.•••.•••.•••"
    const val IPV6 = "••:••:••:••:••:••:••:••"
    const val COORDS = "••.••••°, ••.••••°"
    const val COORD = "••.••••°"
    const val OPERATOR = "••••"
    const val SIM = "••••• ••••"
}

/** Texto genérico: SSID, BSSID, MAC, CELL ID, TAC, PCI. */
@Composable
fun masked(value: String?, mask: String = Masks.TEXT): String? =
    if (LocalPrivacyMode.current && !value.isNullOrBlank()) mask else value

/**
 * Direcciones IP, gateway y DNS. Mantiene la forma para que se siga viendo si es
 * IPv4 o IPv6, que no dice nada del entorno concreto.
 */
@Composable
fun maskedIp(value: String?): String? = when {
    !LocalPrivacyMode.current || value.isNullOrBlank() -> value
    value.contains(':') -> Masks.IPV6
    else -> Masks.IPV4
}

/** Nombre del operador. */
@Composable
fun maskedOperator(value: String?): String? = masked(value, Masks.OPERATOR)

/** MCC y MNC, que identifican operador y país. */
@Composable
fun maskedSim(value: String?): String? = masked(value, Masks.SIM)

/** Coordenadas: llegan por separado del GNSS, así que se enmascaran por separado. */
@Composable
fun maskedCoords(latitude: Double?, longitude: Double?): String? =
    if (LocalPrivacyMode.current && latitude != null && longitude != null) Masks.COORDS else null

/** `true` si hay al menos un dato que ocultar, para mostrar el aviso en pantalla. */
@Composable
fun hasHiddenData(): Boolean = LocalPrivacyMode.current