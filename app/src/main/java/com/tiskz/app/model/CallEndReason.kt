package com.tiskz.app.model

/** Motivo de una orden autenticada; independiente del riesgo del contenido. */
enum class CallEndReason(val wireValue: String, val label: String) {
    VOICE_MATCH("voice_match", "Cortada por coincidencia de voz"),
    CONTENT_RISK("risk_detected", "Cortada por alerta del contenido"),
    AGENT_ENDED("agent_ended", "El agente terminó la llamada");

    companion object {
        fun fromWire(value: String?): CallEndReason? = entries.firstOrNull { it.wireValue == value }
    }
}
