package com.diagnostico.senales.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diagnostico.senales.BuildConfig
import com.diagnostico.senales.core.util.widthsForBand
import com.diagnostico.senales.core.util.WifiBand
import com.diagnostico.senales.data.prefs.AppPreferences
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.SectionCard

@Composable
fun MoreScreen(
    preferences: AppPreferences,
    themeMode: AppPreferences.ThemeMode,
    onThemeChange: (AppPreferences.ThemeMode) -> Unit,
    onOpenAbout: () -> Unit,
    privacyMode: Boolean,
    onPrivacyModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pingTarget by rememberSaveable { mutableStateOf(preferences.pingTarget) }
    var pingCount by rememberSaveable { mutableStateOf(preferences.pingCount.toString()) }
    var downloadUrl by rememberSaveable { mutableStateOf(preferences.downloadUrl) }
    var uploadUrl by rememberSaveable { mutableStateOf(preferences.uploadUrl) }
    var channelWidth by rememberSaveable {
        mutableStateOf(preferences.assumedChannelWidthMHz.toString())
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Apariencia", icon = Icons.Outlined.Settings) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppPreferences.ThemeMode.entries.size) { index ->
                        val mode = AppPreferences.ThemeMode.entries[index]
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "El tema usa Material 3 con color dinámico cuando el " +
                        "dispositivo lo ofrece (Android 12 o superior).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Valores por defecto de las pruebas") {
                OutlinedTextField(
                    value = pingTarget,
                    onValueChange = { pingTarget = it },
                    label = { Text("Destino de latencia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pingCount,
                    onValueChange = { pingCount = it.filter { char -> char.isDigit() }.take(2) },
                    label = { Text("Número de mediciones de latencia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = { Text("URL de descarga") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uploadUrl,
                    onValueChange = { uploadUrl = it },
                    label = { Text("URL de subida") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ancho de canal supuesto (5 GHz)",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = widthsForBand(WifiBand.BAND_5)
                    items(options.size) { index ->
                        val option = options[index]
                        FilterChip(
                            selected = channelWidth == option.toString(),
                            onClick = {
                                channelWidth = option.toString()
                                preferences.assumedChannelWidthMHz = option
                            },
                            label = { Text("$option MHz") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Estos valores se guardan al pulsar el botón y se usan como " +
                        "punto de partida en las pantallas de Wi-Fi y red móvil.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Guardar valores") {
                Button(
                    onClick = {
                        preferences.pingTarget = pingTarget.trim()
                        preferences.pingCount = pingCount.toIntOrNull() ?: 10
                        preferences.downloadUrl = downloadUrl.trim()
                        preferences.uploadUrl = uploadUrl.trim()
                    },
                ) {
                    Text("Guardar")
                }
            }
        }

        item {
            SectionCard(
                title = "Modo privacidad",
                icon = Icons.Outlined.VisibilityOff,
                subtitle = "Oculta los datos identificativos para poder capturar la pantalla",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = privacyMode,
                        onCheckedChange = onPrivacyModeChange,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (privacyMode) {
                            "Activo: SSID, BSSID, MAC, direcciones IP, DNS, gateway, " +
                                "operador, MCC/MNC, identificadores de celda y " +
                                "coordenadas salen como puntos."
                        } else {
                            "Inactivo: se muestran todos los datos."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Los niveles, las bandas, los canales y los veredictos siguen " +
                        "visibles, así que las capturas sirven para diagnosticar sin " +
                        "revelar tu red. El ajuste se recuerda al reabrir la app y un " +
                        "candado aparece en la barra superior mientras esté activo. " +
                        "Ojo: el historial guardado y el CSV que se comparte mantienen " +
                        "los valores reales.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Privacidad", icon = Icons.Outlined.Info) {
                Text(
                    text = "Todos los datos se calculan en el dispositivo. La app no " +
                        "envía información a servidores propios ni incluye publicidad ni " +
                        "seguimiento. Las únicas conexiones salientes son las que tú " +
                        "inicias al medir latencia o velocidad, siempre contra el destino " +
                        "que se indique.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            SectionCard(title = "Límites reales de Android") {
                Text(
                    text = "Estos límites son del sistema operativo y la app los muestra " +
                        "en lugar de inventar datos:\n\n" +
                        "• El ancho de canal Wi-Fi no es público: se estima con el valor " +
                        "que elijas.\n" +
                        "• Android limita los escaneos Wi-Fi a unas 4 lecturas cada 2 " +
                        "minutos.\n" +
                        "• Desde Android 10 la MAC se sustituye por un identificador " +
                        "aleatorio por red.\n" +
                        "• No existe API pública de ICMP: la latencia se obtiene con " +
                        "echo request o, si no, con conexión TCP, y se indica cuál se ha " +
                        "usado.\n" +
                        "• RSRP, RSRQ y SINR solo existen en 4G y 5G.\n" +
                        "• El primer fix antes de Android 12 no expone tiempo de " +
                        "arranque.\n" +
                        "• Los mapas son offline: no hay teselas de fondo porque " +
                        "requerirían una clave o una fuente externa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Acerca de") {
                Text(
                    text = "Señales · versión ${BuildConfig.VERSION_NAME}\n" +
                        "Diagnóstico de dispositivo, Wi-Fi, red móvil y GNSS.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenAbout) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ver información")
                }
            }
        }
    }
}