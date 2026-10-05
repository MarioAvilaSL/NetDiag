package com.diagnostico.senales.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.diagnostico.senales.BuildConfig
import com.diagnostico.senales.ui.components.MetricRow
import com.diagnostico.senales.ui.components.SectionCard
import kotlinx.coroutines.delay

/** Código fuente de la app. */
const val REPOSITORY_URL = "https://github.com/MarioAvilaSL/NetDiag"

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Señales", icon = Icons.Outlined.Info) {
                Text(
                    text = "Diagnóstico de dispositivo, Wi-Fi, red móvil, SIM y GNSS. " +
                        "Todo se calcula en el dispositivo, sin cuenta y sin enviar " +
                        "datos a ningún servidor.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                MetricRow(
                    listOf<Pair<String, String?>>(
                        "Versión" to BuildConfig.VERSION_NAME,
                        "Paquete" to BuildConfig.APPLICATION_ID,
                        "Variante" to if (BuildConfig.DEBUG) "debug" else "release",
                    ),
                )
            }
        }

        item {
            SectionCard(title = "Repositorio", icon = Icons.Outlined.Code) {
                Text(
                    text = "El código fuente está abierto. Puedes consultarlo, reportar " +
                        "un problema o proponer una mejora:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = REPOSITORY_URL,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(REPOSITORY_URL),
                                    ),
                                )
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Abrir en GitHub")
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(REPOSITORY_URL))
                            copied = true
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copiar")
                    }
                }
                if (copied) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Enlace copiado al portapapeles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionCard(title = "Tecnología") {
                MetricRow(
                    listOf<Pair<String, String?>>(
                        "Lenguaje" to "Kotlin 2.2",
                        "Interfaz" to "Jetpack Compose con Material 3",
                        "Compatibilidad" to "Android 7.0 (API 24) o superior",
                        "Dependencias de terceros" to "Ninguna",
                        "Datos identificativos" to "Ninguno: no se leen IMSI, ICCID ni " +
                            "números de línea",
                    ),
                )
            }
        }

        item {
            SectionCard(title = "Privacidad", icon = Icons.Outlined.Shield) {
                Text(
                    text = "Sin analítica, sin publicidad y sin SDK de terceros. El " +
                        "historial se guarda en el almacenamiento interno de la app, " +
                        "que solo lee ella. Las únicas conexiones salientes son las " +
                        "que tú inicias al medir latencia o velocidad, y siempre " +
                        "contra el destino que indiques en Más.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}