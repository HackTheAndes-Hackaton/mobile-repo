package com.tiskz.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiskz.app.call.CallManager
import com.tiskz.app.call.CarrierDialPlan
import com.tiskz.app.call.ConferenceTelecom
import com.tiskz.app.model.CallPhase
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.util.DialerRole

/** Preparación única. No marca números ni habilita desvíos al conceder permisos/configurar acceso. */
@Composable
fun AutomaticConferenceSettings() {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val repo = container.settings
    val settings by repo.settings.collectAsStateWithLifecycle()
    val accessConfigured by container.conferenceCredentials.configured.collectAsStateWithLifecycle()
    val call by CallManager.activeCall.collectAsStateWithLifecycle()
    val busy = call?.phase?.let { it != CallPhase.DISCONNECTED } == true
    var defaultDialer by remember { mutableStateOf(DialerRole.isHeld(context)) }
    var permitted by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf(emptyList<Pair<PhoneAccountHandle, String>>()) }
    var accessReady by remember { mutableStateOf(container.conferenceCredentials.read(settings.backendUrl) != null) }
    // Nunca rememberSaveable: el texto sin cifrar no va al estado guardado de la Activity.
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf(settings.conferenceDestination) }

    fun refresh() {
        defaultDialer = DialerRole.isHeld(context)
        permitted = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        accounts = if (permitted) runCatching {
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom.callCapablePhoneAccounts.mapNotNull { handle ->
                telecom.getPhoneAccount(handle)?.takeIf { it.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) }
                    ?.let { handle to it.label.toString() }
            }
        }.getOrDefault(emptyList()) else emptyList()
        accessReady = container.conferenceCredentials.read(settings.backendUrl) != null
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    LaunchedEffect(settings.backendUrl) { accessReady = container.conferenceCredentials.read(settings.backendUrl) != null }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        if (defaultDialer && !permitted) permissions.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
    }
    val accountPresent = accounts.any { ConferenceTelecom.accountKey(it.first) == settings.conferenceAccount }
    val selected = accounts.firstOrNull { ConferenceTelecom.accountKey(it.first) == settings.conferenceAccount }
    val plan = selected?.let { ConferenceTelecom.dialPlan(context, it.first, settings.conferenceDestination) }
    val confirmed = settings.conferenceCostsConfirmed && plan != null && plan.confirmationKey == settings.conferenceConfirmedRoute
    val ready = defaultDialer && permitted && accountPresent && confirmed && accessReady
    LaunchedEffect(permitted, accountPresent, plan?.confirmationKey) {
        if (!busy && permitted && accountPresent && settings.conferenceCostsConfirmed && !confirmed) {
            repo.update { it.copy(automaticConferenceEnabled = false, conferenceCostsConfirmed = false, conferenceConfirmedRoute = "") }
        }
    }
    LaunchedEffect(accounts) {
        // Con una sola SIM se selecciona sin habilitar ninguna llamada ni confirmar tarifas.
        if (accounts.size == 1 && settings.conferenceAccount.isEmpty()) {
            repo.update { it.copy(conferenceAccount = ConferenceTelecom.accountKey(accounts.single().first)) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Filtro de llamadas", style = MaterialTheme.typography.titleLarge)
        Text("Cuando llame un número desconocido, tu asistente contestará primero y las alertas aparecerán en la pantalla de llamada.")
        Text("Preparación inicial · no se realizará ninguna llamada al configurar esto.", style = MaterialTheme.typography.bodySmall)
        Text("Mientras tu asistente atiende, tu micrófono queda silenciado. Entras cuando se confirma que es seguro, y sigue escuchando hasta que cuelguen.", style = MaterialTheme.typography.bodySmall)
        if (!defaultDialer || !permitted) {
            Button(onClick = {
                if (!defaultDialer) DialerRole.requestIntent(context)?.let(role::launch)
                else permissions.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (!defaultDialer) "Elegir PhoneWard como teléfono" else "Permitir gestionar llamadas")
            }
        } else Text("✓ PhoneWard puede gestionar tus llamadas")

        Text("Tu línea móvil", style = MaterialTheme.typography.titleMedium)
        accounts.forEach { (handle, label) ->
            val key = ConferenceTelecom.accountKey(handle)
            val detected = ConferenceTelecom.dialPlan(context, handle, settings.conferenceDestination)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.conferenceAccount == key, enabled = !busy, onClick = {
                    repo.update { it.copy(conferenceAccount = key, conferenceCostsConfirmed = false, automaticConferenceEnabled = false) }
                })
                Text(if (detected != null) "$label · ${detected.carrier.label} · prefijo ${detected.carrier.prefix}" else "$label · operadora o red no compatibles")
            }
        }
        if (accounts.isEmpty()) Text("Concede los permisos y comprueba que la SIM esté activa.", style = MaterialTheme.typography.bodySmall)
        Text("Número del agente", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = destination, onValueChange = { value ->
            destination = value
            repo.update { it.copy(conferenceDestination = CarrierDialPlan.destination(value) ?: value.trim()) }
        }, label = { Text("Número internacional con + y código de país") }, singleLine = true,
            enabled = !busy, modifier = Modifier.fillMaxWidth(), isError = CarrierDialPlan.destination(destination) == null)
        Text(if (plan != null) "${plan.carrier.label} detectado en la SIM · marcación automática: ${plan.target.dialNumber}"
            else "No se puede determinar una marcación segura. Revisa la SIM, cobertura en Perú y el número; no se marcará automáticamente.", style = MaterialTheme.typography.bodySmall)
        Text("El número debe atenderlo el agente marin conectado a este canal de alertas. Cambiarlo no configura al proveedor.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmed, enabled = plan != null && !busy,
                onCheckedChange = { value -> repo.update { it.copy(conferenceCostsConfirmed = value,
                    conferenceConfirmedRoute = if (value) plan!!.confirmationKey else "", automaticConferenceEnabled = false) } })
            Text("Mi operadora confirmó que esta SIM incluye la llamada al destino mostrado y la conferencia sin cargos adicionales.", modifier = Modifier.weight(1f))
        }
        Text("Los créditos de Vapi y Maritime no cubren la llamada de tu operadora. La red debe permitir poner en espera y unir llamadas. La detección usa la SIM, nunca el prefijo de tu número.", style = MaterialTheme.typography.bodySmall)

        if (accessReady && accessConfigured) {
            Text("✓ Acceso del equipo configurado y cifrado en este celular")
            OutlinedButton(onClick = {
                repo.update { it.copy(automaticConferenceEnabled = false) }
                container.conferenceCredentials.clear()
                accessReady = false
            }, enabled = !busy) { Text("Quitar acceso del equipo") }
        } else {
            Text("Acceso del equipo · solo la primera vez", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Código privado del equipo") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !busy,
                modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                message = runCatching {
                    container.conferenceCredentials.save(settings.backendUrl, code.trim())
                    code = ""
                    accessReady = true
                    "Acceso guardado. Se validará la conexión antes de contestar cada llamada."
                }.getOrElse { "No se pudo guardar el acceso. Revisa el código y el canal HTTPS en Ajustes." }
            }, enabled = !busy && code.trim().length in 12..512) { Text("Guardar acceso una vez") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (settings.automaticConferenceEnabled) "Protección automática activada" else "Protección automática desactivada", style = MaterialTheme.typography.titleMedium)
                Text("Al activar autorizas contestar, llamar a Vapi con esta SIM y analizar la conferencia con IA. Marin solicitará continuar; informa a la otra persona de la prueba.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.automaticConferenceEnabled, enabled = !busy && (ready || settings.automaticConferenceEnabled),
                onCheckedChange = { enabled -> repo.update { it.copy(automaticConferenceEnabled = enabled && ready) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Guardar hasta 10 s de la llamada al detectar riesgo en Red Vecinal Segura. Revisión pendiente; no identifica a quien llama.", Modifier.weight(1f))
            Switch(checked = settings.phoneEvidenceConsent, enabled = !busy,
                onCheckedChange = { enabled -> repo.update { it.copy(phoneEvidenceConsent = enabled) } })
        }

        if (!confirmed) Text("Bloqueado hasta confirmar los costos para esta SIM, operadora y destino.", color = MaterialTheme.colorScheme.error)
        if (busy) Text("Finaliza la llamada para cambiar esta configuración.", style = MaterialTheme.typography.bodySmall)
    }
}
