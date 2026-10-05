package com.diagnostico.senales.ui.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.diagnostico.senales.core.permission.AppPermissions
import com.diagnostico.senales.core.permission.PermissionGroup
import com.diagnostico.senales.core.permission.hasAll
import com.diagnostico.senales.ui.components.ExplanationCard
import com.diagnostico.senales.ui.components.SectionCard

@Composable
fun PermissionsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refreshToken by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshToken++ }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ExplanationCard(
                text = "La app pide cada permiso justo antes de necesitarlo y solo con " +
                    "la finalidad que indica la tarjeta correspondiente. Si deniegas " +
                    "algo, el módulo afectado sigue funcionando y muestra la información " +
                    "que sí puede leer.",
            )
        }

        items(
            count = AppPermissions.groups.size,
            key = { AppPermissions.groups[it].id },
        ) { index ->
            val group = AppPermissions.groups[index]
            PermissionCard(
                group = group,
                granted = group.permissions.isNotEmpty() &&
                    context.hasAll(group.permissions),
                onRequest = { launcher.launch(group.permissions.toTypedArray()) },
            )
        }

        item {
            SectionCard(title = "Por qué no hay más permisos") {
                Text(
                    text = "No se piden cámara, micrófono, contactos, SMS ni acceso al " +
                        "almacenamiento externo. Tampoco se lee la identidad de la SIM " +
                        "ni se guardan claves de Wi-Fi: no hacen falta para diagnosticar " +
                        "la señal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    group: PermissionGroup,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    SectionCard(
        title = group.title,
        icon = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
        subtitle = if (granted) "Concedido" else "Pendiente",
    ) {
        Text(group.why, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Módulos afectados: ${group.modules}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (group.permissions.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            ExplanationCard(
                text = "Este permiso no es necesario en la versión de Android que usa el " +
                    "dispositivo.",
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRequest) {
                Text(if (granted) "Comprobar de nuevo" else "Conceder permiso")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = group.permissions.joinToString("\n") { it.substringAfterLast('.') },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}