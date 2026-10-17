package com.tiskz.app.ui.voice

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tiskz.app.util.CallForwardingSettings

@Composable
internal fun CallForwardingCard(enabled: Boolean) {
    val context = LocalContext.current
    var showGuide by remember { mutableStateOf(false) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Atender llamadas de mi línea", style = MaterialTheme.typography.titleMedium)
        Text("El desvío necesita un número telefónico receptor conectado al agente. La operadora confirma su activación.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { settingsUnavailable = false; showGuide = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("Configurar desvío de llamadas")
        }
    }
    if (showGuide) AlertDialog(
        onDismissRequest = { showGuide = false },
        title = { Text("Desvío con tu operadora") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Ten listo un número receptor habilitado y conectado al agente. El canal HTTPS de Maritime no es un número telefónico.")
                Text("2. En los ajustes de llamadas, selecciona tu SIM. Si usas Entel, elige la SIM Entel y busca Desvío de llamadas.")
                Text("3. Elige cuándo desviar, introduce el número receptor y espera la confirmación de la operadora. Comprueba el resultado llamando desde otro celular.")
                Text("La ubicación de estos ajustes depende del modelo de celular. Para desactivar el desvío, vuelve a la misma configuración.", style = MaterialTheme.typography.bodySmall)
                if (settingsUnavailable) Text("Abre Teléfono → Ajustes → Cuentas de llamadas → tu SIM → Desvío de llamadas. No se pudieron abrir los ajustes desde Tiskz.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (CallForwardingSettings.open(context)) showGuide = false
                else settingsUnavailable = true
            }) { Text("Abrir ajustes de llamadas") }
        },
        dismissButton = { TextButton(onClick = { showGuide = false }) { Text("Volver") } },
    )
}
