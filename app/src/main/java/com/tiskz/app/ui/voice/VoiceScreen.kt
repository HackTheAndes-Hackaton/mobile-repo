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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.data.TeamProvisioning
import com.tiskz.app.voice.WebRtcVoiceSession
import com.tiskz.app.voice.PhoneAlertSession
import com.tiskz.app.analysis.AnalysisSession
import com.tiskz.app.ui.components.AssessmentCard
import com.tiskz.app.ui.components.ScribeComparisonCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // App final: canal, código y permisos vienen de la configuración del equipo y no se muestran.
    val simple = TeamProvisioning.enabled
    // No SavedState ni preferencias para el código o el consentimiento.
    var baseUrl by remember { mutableStateOf(container.settings.current.backendUrl) }
    var code by remember { mutableStateOf(if (simple) container.conferenceCredentials.read(baseUrl).orEmpty() else "") }
    var assistant by remember { mutableStateOf(true) }
    var phone by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(simple) }
    var evidenceConsent by remember { mutableStateOf(simple && container.settings.current.phoneEvidenceConsent) }
    var session by remember { mutableStateOf<AnalysisSession?>(null) }
    var pendingStart by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    val analysis = session?.state?.collectAsStateWithLifecycle()?.value ?: CallAnalysis()
    val connection = when (val current = session) {
        is WebRtcVoiceSession -> current.connection.collectAsStateWithLifecycle().value
        is PhoneAlertSession -> current.connection.collectAsStateWithLifecycle().value
        else -> null
    }
    val active = session != null && connection != WebRtcVoiceSession.Connection.ENDED
    val currentSession by rememberUpdatedState(session)

    fun begin() {
        pendingStart = false
        if (!consent || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        try {
            session?.close()
            val next = if (phone) container.openPhoneAlerts(baseUrl, code.trim(), evidenceConsent) else container.openVoiceSession(baseUrl, code.trim(), assistant, evidenceConsent)
            session = next
            formError = null
            (next as? WebRtcVoiceSession)?.start(consent)
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
                Text(if (phone) "Llamadas telefónicas · alertas" else if (assistant) "Conversar con PhoneWard · marin" else "Micrófono → agentes → alerta", style = MaterialTheme.typography.titleLarge)
                Text(if (phone) "Vapi atiende la llamada y esta pantalla recibe el análisis. Mantén la app abierta: las alertas se detienen al salir o enviarla al fondo. Detener alertas no cuelga la llamada." else "Analiza audio autorizado del micrófono. La conexión se detiene al salir de esta pantalla o enviar la app al fondo.", style = MaterialTheme.typography.bodyMedium)
            }
            if (!simple) item {
                Row {
                    Switch(checked = phone, onCheckedChange = { phone = it; consent = false; evidenceConsent = false; session?.close(); session = null }, enabled = !active && !pendingStart)
                    Text("Recibir alertas de llamadas que atiende Vapi", modifier = Modifier.weight(1f))
                }
            }
            if (!phone && !simple) item {
                Row {
                    Switch(checked = assistant, onCheckedChange = { assistant = it; consent = false }, enabled = !active)
                    Text("El asistente escucha y responde con voz de IA (GPT-Live 1 · marin).", modifier = Modifier.weight(1f))
                }
                Text("Conversación por internet dentro de la app. Para atender tu número de celular se necesita configurar el desvío a un servicio telefónico.", style = MaterialTheme.typography.bodySmall)
            }
            if (!simple) item { CallForwardingCard(enabled = !active && !pendingStart) }
            if (!simple) item {
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Canal HTTPS") }, enabled = !active, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (!simple) item {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Código privado del equipo") }, visualTransformation = PasswordVisualTransformation(), enabled = !active, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (simple) item {
                Text("Al conversar, el audio se envía a OpenAI para analizarlo. La app no guarda grabaciones ni transcripciones en el teléfono.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (!simple) item {
                Row {
                    Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !active)
                    Text(if (phone) "Autorizo analizar estas llamadas y cortar Vapi al detectar riesgo. La conferencia automática de PhoneWard cierra también la llamada del celular. Esta app no guarda audio ni transcripciones en disco." else if (assistant) "Autorizo enviar este audio a OpenAI, analizarlo y recibir respuestas con voz de IA. La app no guarda grabaciones ni transcripciones en disco." else "Tengo autorización para analizar este audio. OpenAI lo procesa; la app no guarda grabaciones ni transcripciones en disco.", modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        formError = null
                        if (phone || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                        else { pendingStart = true; microphonePermission.launch(Manifest.permission.RECORD_AUDIO) }
                    }, enabled = !active && !pendingStart && consent && code.trim().length >= 12, modifier = Modifier.weight(1f)) { Text(if (phone) "Conectar alertas" else if (assistant) "Conversar" else "Iniciar micrófono") }
                    OutlinedButton(onClick = { session?.close() }, enabled = active, modifier = Modifier.weight(1f)) { Text(if (phone) "Detener alertas" else "Detener") }
                }
            }
            if (!simple) item {
                Row {
                    Checkbox(checked = evidenceConsent, onCheckedChange = { evidenceConsent = it }, enabled = !active && !pendingStart)
                    Text(if (phone) "Autorizo conservar y compartir con Red Vecinal Segura hasta 10 segundos de la llamada cuando se detecte una alerta. Queda pendiente de revisión; no identifica personas." else "Autorizo conservar y compartir con Red Vecinal Segura hasta 10 segundos del micrófono cuando haya una alerta o pulse Guardar. No autoriza identificar personas por su voz.", Modifier.weight(1f))
                }
                Text("Sin esta opción no se conserva audio. Los fragmentos guardados permanecen en el servidor hasta su eliminación; el búfer local se borra al terminar.", style = MaterialTheme.typography.bodySmall)
            }
            val voiceSession = session as? WebRtcVoiceSession
            if (!phone && evidenceConsent && voiceSession != null) item {
                val evidence by voiceSession.evidence.collectAsStateWithLifecycle()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = voiceSession::saveExcerpt, enabled = active && !evidence.busy) { Text("Guardar últimos 10 s") }
                    OutlinedButton(onClick = voiceSession::playExcerpt, enabled = active && evidence.saved && !evidence.busy) { Text("Escuchar guardado") }
                }
                Text(evidence.message)
            }
            item {
                Text(when (connection) {
                    WebRtcVoiceSession.Connection.CONNECTING -> "Conectando el canal…"
                    WebRtcVoiceSession.Connection.STREAMING -> if (phone) "Alertas conectadas · esperando llamada" else if (assistant) "Conversación activa · voz marin" else "Micrófono activo · enviando audio"
                    WebRtcVoiceSession.Connection.ENDED -> if (phone) "Alertas desconectadas" else "Sesión terminada · micrófono detenido"
                    null -> if (phone) "Alertas desconectadas" else "Micrófono detenido"
                }, style = MaterialTheme.typography.labelLarge)
                (formError ?: analysis.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                AssessmentCard(analysis)
            }
            if (phone) item { ScribeComparisonCard(analysis.scribe) }
            item { Text(if (phone) "Interlocutor · llamada atendida por Vapi" else if (assistant) "Conversación · subtítulos parciales" else "Audio del micrófono · transcripción", style = MaterialTheme.typography.titleMedium) }
            if (analysis.transcript.isEmpty()) item { Text(if (phone) "La llamada debe entrar al número configurado en Vapi, directamente o mediante un desvío de la operadora. Esta pantalla no activa el desvío." else if (assistant) "El asistente saludará al conectar. Los dos agentes analizan progresivamente lo que tú dices; los subtítulos pueden contener errores." else "Los fragmentos aparecerán al hablar. Los agentes analizan al cerrar una pausa de voz.") }
            items(analysis.transcript, key = { it.id }) { line ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Text((if (assistant) { if (line.speaker == com.tiskz.app.model.Speaker.AGENT) "PhoneWard · " else "Interlocutor · " } else "") + line.text, Modifier.fillMaxWidth().padding(14.dp)) }
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
            item { Text(if (phone) "Vapi recibe el audio telefónico; esta app recibe sus transcripciones y alertas. No se verifica la identidad de quien llama." else "Este canal no captura audio de llamadas de la operadora ni identifica personas por su voz.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
