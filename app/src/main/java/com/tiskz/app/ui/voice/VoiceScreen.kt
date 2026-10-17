package com.tiskz.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.voice.WebRtcVoiceSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // No SavedState ni preferencias para el código o el consentimiento.
    var code by remember { mutableStateOf("") }
    var assistant by remember { mutableStateOf(true) }
    var consent by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(container.settings.current.backendUrl) }
    var session by remember { mutableStateOf<WebRtcVoiceSession?>(null) }
    var pendingStart by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    val analysis = session?.state?.collectAsStateWithLifecycle()?.value ?: CallAnalysis()
    val connection = session?.connection?.collectAsStateWithLifecycle()?.value
    val active = session != null && connection != WebRtcVoiceSession.Connection.ENDED
    val currentSession by rememberUpdatedState(session)

    fun begin() {
        pendingStart = false
        if (!consent || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        try {
            session?.close()
            val next = container.openVoiceSession(baseUrl, code.trim(), assistant)
            session = next
            formError = null
            next.start(consent)
        } catch (e: IllegalArgumentException) { formError = e.message ?: "Revisa la URL HTTPS del canal." }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingStart) begin()
        else { pendingStart = false; formError = "Sin permiso de micrófono. Puedes volver a intentar o habilitarlo en Ajustes de Android." }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { pendingStart = false; currentSession?.close() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); currentSession?.close() }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Voz en vivo") }, navigationIcon = {
            IconButton(onClick = { session?.close(); onBack() }) { Icon(TablerIcons.ArrowLeft, "Volver") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(if (assistant) "Conversar con Tiskz · marin" else "Micrófono → agentes → alerta", style = MaterialTheme.typography.titleLarge)
                Text("Analiza audio autorizado del micrófono. La conexión se detiene al salir de esta pantalla o enviar la app al fondo.", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Row {
                    Switch(checked = assistant, onCheckedChange = { assistant = it; consent = false }, enabled = !active)
                    Text("El asistente escucha y responde con voz de IA (GPT-Live 1 · marin).", modifier = Modifier.weight(1f))
                }
                Text("Conversación por internet dentro de la app. Para atender tu número de celular se necesita configurar el desvío a un servicio telefónico.", style = MaterialTheme.typography.bodySmall)
            }
            item { CallForwardingCard(enabled = !active && !pendingStart) }
            item {
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Canal HTTPS") }, enabled = !active, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Código privado del equipo") }, visualTransformation = PasswordVisualTransformation(), enabled = !active, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row {
                    Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !active)
                    Text(if (assistant) "Autorizo enviar este audio a OpenAI, analizarlo y recibir respuestas con voz de IA. La app no guarda grabaciones ni transcripciones en disco." else "Tengo autorización para analizar este audio. OpenAI lo procesa; la app no guarda grabaciones ni transcripciones en disco.", modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        formError = null
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                        else { pendingStart = true; microphonePermission.launch(Manifest.permission.RECORD_AUDIO) }
                    }, enabled = !active && !pendingStart && consent && code.trim().length >= 12, modifier = Modifier.weight(1f)) { Text(if (assistant) "Conversar" else "Iniciar micrófono") }
                    OutlinedButton(onClick = { session?.close() }, enabled = active, modifier = Modifier.weight(1f)) { Text("Detener") }
                }
            }
            item {
                Text(when (connection) {
                    WebRtcVoiceSession.Connection.CONNECTING -> "Conectando el canal…"
                    WebRtcVoiceSession.Connection.STREAMING -> if (assistant) "Conversación activa · voz marin" else "Micrófono activo · enviando audio"
                    WebRtcVoiceSession.Connection.ENDED -> "Sesión terminada · micrófono detenido"
                    null -> "Micrófono detenido"
                }, style = MaterialTheme.typography.labelLarge)
                (formError ?: analysis.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Surface(color = if (analysis.alert == true) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(20.dp).semantics { liveRegion = LiveRegionMode.Assertive }) {
                        Text(when (analysis.alert) { true -> "TRUE · Revisar posible riesgo"; false -> "FALSE · Sin alerta en este fragmento"; null -> "Esperando decisión" }, style = MaterialTheme.typography.titleLarge)
                        Text("Resultado provisional. TRUE no confirma una estafa y FALSE no garantiza seguridad.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text(if (assistant) "Conversación · subtítulos parciales" else "Audio del micrófono · transcripción", style = MaterialTheme.typography.titleMedium) }
            if (analysis.transcript.isEmpty()) item { Text(if (assistant) "El asistente saludará al conectar. Los dos agentes analizan progresivamente lo que tú dices; los subtítulos pueden contener errores." else "Los fragmentos aparecerán al hablar. Los agentes analizan al cerrar una pausa de voz.") }
            items(analysis.transcript, key = { it.id }) { line ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Text((if (assistant) { if (line.speaker == com.tiskz.app.model.Speaker.AGENT) "Tiskz · " else "Interlocutor · " } else "") + line.text, Modifier.fillMaxWidth().padding(14.dp)) }
            }
            item { Text("Comprensión y evidencia", style = MaterialTheme.typography.titleMedium) }
            val understanding = analysis.understanding
            if (understanding == null) item { Text("Sin análisis de comprensión todavía.") }
            else {
                item { Text(understanding.summary) }
                if (understanding.uncertainties.isNotEmpty()) item {
                    Text(understanding.uncertainties.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                }
                items(understanding.evidence) { evidence ->
                    Column { Text("“${evidence.quote}”", style = MaterialTheme.typography.bodyLarge); Text(evidence.observation, style = MaterialTheme.typography.bodySmall) }
                }
                item { Text(understanding.limitations.joinToString("\n"), style = MaterialTheme.typography.bodySmall) }
            }
            item { Text("Este canal no captura audio de llamadas de la operadora ni identifica personas por su voz.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
