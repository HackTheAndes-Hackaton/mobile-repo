package com.tiskz.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiskz.app.data.ArchiveState
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.ui.theme.TiskzColors

/** Guardar la conversación de cada llamada en el panel web (Recientes y casos del panel). */
@Composable
fun PanelSettings() {
    val container = LocalAppContainer.current
    val repo = container.settings
    val settings by repo.settings.collectAsStateWithLifecycle()
    val states by container.archive.states.collectAsStateWithLifecycle()
    val lastError by container.archive.lastError.collectAsStateWithLifecycle()
    var keyReady by remember { mutableStateOf(container.panelCredentials.read(settings.panelUrl) != null) }
    LaunchedEffect(settings.panelUrl) { keyReady = container.panelCredentials.read(settings.panelUrl) != null }
    // Nunca rememberSaveable: la clave sin cifrar no va al estado guardado de la Activity.
    var key by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var org by rememberSaveable { mutableStateOf(settings.panelOrg) }
    val pending = states.count { it.value == ArchiveState.PENDING }
    val failed = states.count { it.value == ArchiveState.FAILED }
    val saved = states.count { it.value == ArchiveState.SAVED }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Historial en el panel", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Al colgar, PhoneWard guarda la conversación de la llamada (tu asistente, quien llama y tú) en el panel de la organización.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TiskzColors.Muted,
                )
            }
            Switch(checked = settings.panelSync, onCheckedChange = { on -> repo.update { it.copy(panelSync = on) } })
        }
        if (!settings.panelSync) return@Column

        UrlField("Panel (API)", settings.panelUrl) { url -> repo.update { it.copy(panelUrl = url) } }
        OutlinedTextField(
            value = org,
            onValueChange = { value ->
                org = value
                repo.update { it.copy(panelOrg = value.trim()) }
            },
            label = { Text("Organización del panel") },
            supportingText = { Text("ID de la organización que recibe tus llamadas, por ejemplo org_uav.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (keyReady) {
            Text("✓ Clave del panel guardada y cifrada en este celular")
            OutlinedButton(onClick = {
                container.panelCredentials.clear()
                keyReady = false
            }) { Text("Quitar clave") }
        } else {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API key del panel (tkz_…)") },
                supportingText = { Text("Créala en el panel: Integraciones → Nueva clave.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                message = runCatching {
                    container.panelCredentials.save(settings.panelUrl, key.trim())
                    key = ""
                    keyReady = true
                    container.archive.retryFailed()
                    "Clave guardada. Las llamadas pendientes se enviarán ahora."
                }.getOrElse { "No se pudo guardar: la URL del panel debe empezar con https://." }
            }, enabled = key.trim().length in 12..512) { Text("Guardar clave") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Text(
            buildString {
                append("$saved guardadas en el panel")
                if (pending > 0) append(" · $pending pendientes")
                if (failed > 0) append(" · $failed rechazadas")
            },
            style = MaterialTheme.typography.bodySmall,
            color = TiskzColors.Muted,
        )
        lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TiskzColors.Red) }
        if (pending + failed > 0) {
            OutlinedButton(onClick = container.archive::retryFailed, enabled = keyReady) { Text("Enviar pendientes") }
        }
    }
}
