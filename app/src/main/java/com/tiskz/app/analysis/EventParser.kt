package com.tiskz.app.analysis

import com.tiskz.app.model.AgentStatus
import com.tiskz.app.model.Speaker
import com.tiskz.app.model.VoiceMatch
import com.tiskz.app.model.optStringOrNull
import org.json.JSONException
import org.json.JSONObject

/** Traduce los mensajes JSON del backend a [AnalysisEvent]. Los mensajes desconocidos se ignoran. */
object EventParser {

    fun parse(raw: String): AnalysisEvent? = try {
        parse(JSONObject(raw))
    } catch (e: JSONException) {
        null
    }

    private fun parse(json: JSONObject): AnalysisEvent? = when (json.optString("type")) {
        "agent.status" -> statusOf(json.optString("status"))?.let { AnalysisEvent.Status(it) }

        "transcript.update" -> AnalysisEvent.Transcript(
            id = json.optStringOrNull("id") ?: "line-${System.nanoTime()}",
            speaker = speakerOf(json.optString("speaker")),
            text = json.optString("text"),
            flagged = json.optBoolean("flagged"),
        )

        "risk.update" -> AnalysisEvent.Risk(
            percent = json.optInt("risk_percent"),
            reasons = json.optJSONArray("reasons")?.let { arr -> List(arr.length()) { arr.optString(it) } }.orEmpty(),
            scamType = json.optStringOrNull("scam_type"),
            impersonating = json.optStringOrNull("impersonating"),
        )

        "voiceprint.match" -> AnalysisEvent.Voice(
            VoiceMatch(
                similarity = json.optDouble("similarity", 0.0).toFloat(),
                label = json.optString("label"),
                reports = json.optInt("reports"),
            )
        )

        "call.ready_to_answer" -> AnalysisEvent.ReadyToAnswer

        "call.ended" -> AnalysisEvent.Ended(
            finalPercent = if (json.has("risk_percent")) json.optInt("risk_percent") else null
        )

        "error" -> AnalysisEvent.Error(json.optString("message", "Error en el análisis"))

        else -> null
    }

    private fun statusOf(value: String): AgentStatus? = when (value) {
        "connecting" -> AgentStatus.CONNECTING
        "screening" -> AgentStatus.SCREENING
        "analyzing" -> AgentStatus.ANALYZING
        "safe" -> AgentStatus.SAFE
        "suspicious" -> AgentStatus.SUSPICIOUS
        "scam" -> AgentStatus.SCAM
        else -> null
    }

    private fun speakerOf(value: String): Speaker = when (value) {
        "agent" -> Speaker.AGENT
        "user" -> Speaker.USER
        else -> Speaker.CALLER
    }
}
