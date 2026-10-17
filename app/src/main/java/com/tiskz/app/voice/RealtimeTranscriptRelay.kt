package com.tiskz.app.voice

import org.json.JSONObject

/** Traduce eventos del DataChannel; el orden de finales de distintos turnos puede variar. */
class RealtimeTranscriptRelay(private val send: (JSONObject) -> Unit, private val onError: (String) -> Unit) {
    private val partials = mutableMapOf<String, String>()
    private val order = mutableMapOf<String, Int>()
    private val finals = mutableSetOf<String>()

    fun accept(raw: String) {
        val event = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val id = event.optString("item_id")
        when (event.optString("type")) {
            "input_audio_buffer.committed" -> if (id.isNotEmpty() && order.size < 100 && id !in order) order[id] = order.size
            "conversation.item.input_audio_transcription.delta" -> {
                if (id.isEmpty() || id in finals || (id !in partials && partials.size >= 100)) return
                val text = ((partials[id] ?: "") + event.optString("delta")).take(4000)
                partials[id] = text
                forward(id, text, false)
            }
            "conversation.item.input_audio_transcription.completed" -> {
                if (id.isEmpty() || id in finals || finals.size >= 100) return
                finals.add(id)
                partials.remove(id)
                forward(id, event.optString("transcript").take(4000), true)
            }
            "conversation.item.input_audio_transcription.failed", "error" -> onError("No se pudo transcribir un fragmento. No interpretes el error como ausencia de riesgo.")
        }
    }

    private fun forward(id: String, text: String, final: Boolean) {
        if (text.isBlank()) return
        val event = JSONObject().put("type", "transcript.update").put("id", id)
            .put("speaker", "user").put("text", text).put("final", final)
        order[id]?.let { event.put("sequence", it) }
        send(event)
    }
}
