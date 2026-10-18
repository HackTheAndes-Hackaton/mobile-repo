package com.tiskz.app.voice

import android.util.Log
import com.tiskz.app.BuildConfig
import com.tiskz.app.analysis.AnalysisEvent
import com.tiskz.app.analysis.AnalysisSession
import com.tiskz.app.analysis.EventParser
import com.tiskz.app.analysis.UserDecision
import com.tiskz.app.model.AgentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Solo observa la línea del equipo. No captura audio ni activa desvíos de la SIM. */
class PhoneAlertSession(client: OkHttpClient, baseUrl: String, accessCode: String) : AnalysisSession() {
    private val closed = AtomicBoolean(false)
    private val _connection = MutableStateFlow(WebRtcVoiceSession.Connection.CONNECTING)
    val connection = _connection.asStateFlow()
    private var callId: String? = null
    private var socket: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = VoiceBackendApi(baseUrl, client)
    private val callControl = PhoneCallControl()
    private val _hangupRequests = MutableStateFlow<PhoneHangup?>(null)
    val hangupRequests = _hangupRequests.asStateFlow()

    init {
        require(accessCode.length in 12..512) { "Introduce el código privado del equipo." }
        val base = baseUrl.trim().toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null) { "El canal requiere HTTPS sin credenciales ni parámetros." }
        scope.launch {
          try {
            val request = Request.Builder().url(api.phoneEventsUrl(accessCode)).build()
            if (closed.get()) return@launch
            val connected = client.newBuilder().followRedirects(false).followSslRedirects(false).build().newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed.get()) webSocket.close(1000, "stopped")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (closed.get() || text.length > 20000) return
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                val type = json.optString("type")
                if (type !in setOf("phone.ready", "phone.call") && json.optString("session_id") != callId) return
                callControl.accept(json)?.let { _hangupRequests.value = it }
                if (BuildConfig.DEBUG) runCatching {
                    // Solo forma del evento, sin texto: sirve para revisar etiquetas e ids de la transcripción.
                    Log.d("TiskzPhone", "${json.optString("type")} speaker=${json.optString("speaker")} " +
                        "id=${json.optString("id")} seq=${json.opt("sequence")} final=${json.opt("final")} " +
                        "len=${json.optString("text").length} lines=${json.optString("text").count { it == '\n' } + 1} " +
                        "source=${json.optString("source")} serverSentMs=${json.optLong("server_sent_at_ms")} receivedMs=${System.currentTimeMillis()}")
                }
                when (json.optString("type")) {
                    "phone.ready" -> _connection.value = WebRtcVoiceSession.Connection.STREAMING
                    "phone.call" -> {
                        val next = json.optString("call_id")
                        if (next.isNotBlank() && next != callId) {
                            callId = next; resetAnalysis()
                            dispatch(AnalysisEvent.Status(AgentStatus.SCREENING))
                        }
                    }
                    "call.hangup_result" -> if (json.optString("session_id") == callId && json.optString("status") == "failed") {
                        dispatch(AnalysisEvent.Error("No se confirmó el corte de Vapi. Cuelga desde el teléfono."))
                    }
                    "call.ready_to_answer" -> if (json.optString("session_id") == callId) EventParser.parse(text)?.let(::dispatch)
                    "assessment.update" -> if (json.optString("session_id") == callId) EventParser.parse(text)?.let(::dispatch)
                    else -> EventParser.parse(text)?.let(::dispatch)
                }
                if (type in setOf("alert.updated", "comparison.alert") && json.optString("event_id").isNotBlank()) {
                    webSocket.send(JSONObject().put("type", "event.received").put("session_id", callId)
                        .put("event_id", json.optString("event_id")).toString())
                }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, "stopped"); terminated()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { terminated() }
            private fun terminated() {
                if (!closed.get()) dispatch(AnalysisEvent.Error("Sin conexión con las alertas telefónicas. Vuelve a conectar esta pantalla."))
                _connection.value = WebRtcVoiceSession.Connection.ENDED
            }
            })
            socket = connected
            if (closed.get()) connected.close(1000, "stopped")
          } catch (e: Exception) {
            if (!closed.get()) {
                dispatch(AnalysisEvent.Error("No se pudo conectar con las alertas telefónicas. Revisa el canal y el código."))
                _connection.value = WebRtcVoiceSession.Connection.ENDED
            }
          }
        }
    }
    override fun send(decision: UserDecision) = Unit
    override fun close() {
        if (closed.compareAndSet(false, true)) { socket?.close(1000, "monitor stopped"); scope.cancel(); api.cancelPending() }
        _connection.value = WebRtcVoiceSession.Connection.ENDED
    }
}
