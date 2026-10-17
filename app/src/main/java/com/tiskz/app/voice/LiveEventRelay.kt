package com.tiskz.app.voice

import org.json.JSONObject

/** El servidor conserva las credenciales y analiza solo los subtítulos de quien llama. */
internal class LiveEventRelay(
    private val sendBackend: (JSONObject) -> Unit,
    private val onStarted: () -> Unit,
    private val onClosed: () -> Unit,
    private val onError: (String) -> Unit,
) {
    fun accept(raw: String) {
        val event = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (event.optString("type")) {
            "session.started" -> onStarted()
            "session.input_transcript.delta", "session.output_transcript.delta", "session.delegation.created" ->
                sendBackend(JSONObject().put("type", "live.event").put("event", event))
            "session.closed" -> {
                sendBackend(JSONObject().put("type", "live.event").put("event", event))
                onClosed()
            }
            "error" -> onError("El servicio de conversación informó un error. No significa ausencia de riesgo.")
        }
    }

    companion object {
        // Solo contexto de los agentes: nunca se reenvían comandos arbitrarios de un observador.
        fun contextCommand(raw: String): JSONObject? {
            val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            if (envelope.optString("type") != "live.command") return null
            val command = envelope.optJSONObject("command") ?: return null
            if (command.optString("type") != "session.thinking.append") return null
            val content = command.opt("content") as? String ?: return null
            if (content.length > 1800) return null
            val id = command.opt("delegation_id")
            if (id != null && id != JSONObject.NULL && (id !is String || id.length !in 1..150)) return null
            return JSONObject().put("type", "session.thinking.append")
                .put("event_id", command.optString("event_id").take(150))
                .put("delegation_id", id ?: JSONObject.NULL).put("content", content)
        }
    }
}
