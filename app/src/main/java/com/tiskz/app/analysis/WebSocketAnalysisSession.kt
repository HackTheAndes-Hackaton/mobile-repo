package com.tiskz.app.analysis

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder

/** Sesión real: escucha los eventos que publican los agentes por WebSocket. */
class WebSocketAnalysisSession(
    client: OkHttpClient,
    wsUrl: String,
    private val callerNumber: String,
    private val deviceId: String,
) : AnalysisSession() {

    private val socket: WebSocket? = try {
        val separator = if (wsUrl.contains('?')) '&' else '?'
        val url = "$wsUrl${separator}caller=${URLEncoder.encode(callerNumber, "UTF-8")}"
        client.newWebSocket(Request.Builder().url(url).build(), Listener())
    } catch (e: IllegalArgumentException) {
        dispatch(AnalysisEvent.Error("La URL del análisis no es válida: $wsUrl"))
        null
    }

    override fun send(decision: UserDecision) {
        socket?.send(
            JSONObject()
                .put("type", "user.decision")
                .put("action", decision.wire)
                .toString()
        )
    }

    override fun close() {
        socket?.close(NORMAL_CLOSURE, "call ended")
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(
                JSONObject()
                    .put("type", "session.start")
                    .put("caller_number", callerNumber)
                    .put("device_id", deviceId)
                    .toString()
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            EventParser.parse(text)?.let(::dispatch)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            dispatch(AnalysisEvent.Error("Sin conexión con el análisis de Tiskz"))
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
    }
}
