package com.tiskz.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material3.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tiskz.app.util.DialerRole
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiskz.app.BuildConfig
import com.tiskz.app.data.DemoScenario
import com.tiskz.app.data.TeamProvisioning
import com.tiskz.app.data.ContactLookup
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.ui.theme.TiskzColors
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenVoice: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val blocked by container.blocklist.blocked.collectAsStateWithLifecycle()
    val records by container.history.records.collectAsStateWithLifecycle()
    val repo = container.settings
    // En la app final los ajustes técnicos no se muestran. El equipo los abre tocando 7 veces la versión.
    var technical by remember { mutableStateOf(!TeamProvisioning.enabled) }
    var taps by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = TiskzColors.White,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TiskzColors.White),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
          if (!technical) ProtectionSettings() else {
            AutomaticConferenceSettings()
            HorizontalDivider(color = TiskzColors.Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo demo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Analiza las llamadas con guiones de ejemplo, sin backend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TiskzColors.Muted,
                    )
                }
                Switch(checked = settings.demoMode, onCheckedChange = { on -> repo.update { it.copy(demoMode = on) } })
            }

            if (settings.demoMode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escenario para llamadas reales", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        DemoScenario.entries.forEachIndexed { index, scenario ->
                            SegmentedButton(
                                selected = settings.demoScenario == scenario,
                                onClick = { repo.update { it.copy(demoScenario = scenario) } },
                                shape = SegmentedButtonDefaults.itemShape(index, DemoScenario.entries.size),
                            ) {
                                Text(scenario.label)
                            }
                        }
                    }
                    Text(
                        text = "Automático: contactos guardados = segura; otros números terminados en par = segura, impar = estafa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TiskzColors.Muted,
                    )
                }
            }

            HorizontalDivider(color = TiskzColors.Line)

            Text("Conexión", style = MaterialTheme.typography.titleMedium)
            UrlField("WebSocket de los agentes", settings.wsUrl) { url -> repo.update { it.copy(wsUrl = url) } }
            UrlField("Backend (REST)", settings.backendUrl) { url -> repo.update { it.copy(backendUrl = url) } }

            HorizontalDivider(color = TiskzColors.Line)

            PanelSettings()

            HorizontalDivider(color = TiskzColors.Line)

            Column {
                Text("Umbral para contestar: ${settings.answerThreshold}%", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "PhoneWard destaca \"Contestar\" solo si el riesgo es menor a este valor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TiskzColors.Muted,
                )
                Slider(
                    value = settings.answerThreshold.toFloat(),
                    onValueChange = { v -> repo.update { it.copy(answerThreshold = v.roundToInt()) } },
                    valueRange = 10f..60f,
                    steps = 9,
                )
            }
          }

            HorizontalDivider(color = TiskzColors.Line)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Analizar una conversación", style = MaterialTheme.typography.titleLarge)
                Text("Usa el micrófono para revisar en vivo una conversación y recibir alertas al instante.")
                Button(onClick = onOpenVoice, modifier = Modifier.fillMaxWidth()) { Text("Empezar a analizar") }
            }

            HorizontalDivider(color = TiskzColors.Line)
            ContactsSettings()

            HorizontalDivider(color = TiskzColors.Line)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = container.blocklist::clear, enabled = blocked.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Desbloquear (${blocked.size})")
                }
                OutlinedButton(onClick = container.history::clear, enabled = records.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Borrar historial")
                }
            }

            Text(
                text = "PhoneWard ${BuildConfig.VERSION_NAME}" + if (technical && TeamProvisioning.enabled) " · modo técnico" else "",
                style = MaterialTheme.typography.bodySmall,
                color = TiskzColors.Muted,
                modifier = Modifier.clickable(enabled = TeamProvisioning.enabled && !technical) { if (++taps >= 7) technical = true },
            )
        }
    }
}

/** Nombres de contactos en las llamadas: permiso de lectura de contactos. */
@Composable
private fun ContactsSettings() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContactLookup.hasPermission(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = ContactLookup.hasPermission(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Contactos", style = MaterialTheme.typography.titleLarge)
        Text("Muestra quién llama, no solo el número. Tus contactos pasan directo, sin el asistente.")
        if (allowed) Text("✓ PhoneWard puede ver los nombres de tus contactos")
        else Button(onClick = { launcher.launch(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.fillMaxWidth()) {
            Text("Permitir ver nombres de contactos")
        }
    }
}

/** Lo único que ve el usuario final: si PhoneWard protege sus llamadas y cómo activarlo. */
@Composable
private fun ProtectionSettings() {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var dialer by remember { mutableStateOf(DialerRole.isHeld(context)) }
    fun refresh(enable: Boolean? = null) {
        dialer = DialerRole.isHeld(context)
        message = TeamProvisioning.ensureProtection(context, container, enable)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        if (dialer) permissions.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Filtro de llamadas", style = MaterialTheme.typography.titleLarge)
        Text("Cuando llama un número que no está en tus contactos, tu asistente contesta primero y te avisa si es seguro antes de que entres.")
        if (!dialer) {
            Button(onClick = { DialerRole.requestIntent(context)?.let(role::launch) }, modifier = Modifier.fillMaxWidth()) {
                Text("Elegir PhoneWard como teléfono")
            }
        } else if (message?.startsWith("Permite") == true) {
            Button(onClick = { permissions.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)) },
                modifier = Modifier.fillMaxWidth()) { Text("Permitir gestionar llamadas") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (settings.automaticConferenceEnabled) "Protección activada" else "Protección desactivada", style = MaterialTheme.typography.titleMedium)
                Text("Mientras tu asistente atiende, tu micrófono queda silenciado.", style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
            }
            Switch(
                checked = settings.automaticConferenceEnabled,
                enabled = message == null || settings.automaticConferenceEnabled,
                onCheckedChange = { on ->
                    if (on) refresh(enable = true) else container.settings.update { it.copy(automaticConferenceEnabled = false) }
                },
            )
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** Campo con estado local: escribir directo sobre el StateFlow hace que se pierdan teclas. */
@Composable
internal fun UrlField(label: String, initial: String, onChange: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it.trim())
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}
