package com.tiskz.app.voice

import com.tiskz.app.analysis.AnalysisEvent
import com.tiskz.app.analysis.EventParser
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** El cierre remoto debe detener la captura antes de esperar el timeout de ping. */
internal class VoiceEventsListener(
    private val isClosed: () -> Boolean,
    private val onReady: () -> Unit,
    private val onEvent: (AnalysisEvent) -> Unit,
    private val onTerminated: (String) -> Unit,
) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        if (isClosed()) { webSocket.close(1000, "ended"); return }
        webSocket.send("{\"type\":\"session.start\"}")
        onReady()
    }
    override fun onMessage(webSocket: WebSocket, text: String) {
        if (!isClosed()) EventParser.parse(text)?.let(onEvent)
    }
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, "voice ended")
        if (!isClosed()) onTerminated("La sesión de voz terminó. El micrófono se ha detenido.")
    }
    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (!isClosed()) onTerminated("La sesión de voz terminó.")
    }
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (!isClosed()) onTerminated("Sin conexión con los agentes. El micrófono se ha detenido.")
    }
}
