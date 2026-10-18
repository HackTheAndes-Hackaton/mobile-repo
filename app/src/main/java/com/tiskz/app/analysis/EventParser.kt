package com.tiskz.app.analysis

import com.tiskz.app.model.AgentStatus
import com.tiskz.app.model.Speaker
import com.tiskz.app.model.VoiceMatch
import com.tiskz.app.model.Evidence
import com.tiskz.app.model.Understanding
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
            sequence = (json.opt("sequence") as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE && it % 1 == 0.0 }?.toInt(),
        )

        "alert.updated" -> {
            val value = json.opt("alerta") as? Boolean
            val probability = json.optDouble("probability", Double.NaN)
            if (value == null || !probability.isFinite() || probability !in 0.0..1.0) null
            else AnalysisEvent.Alert(value, probability, json.optInt("revision", 0))
        }

        "understanding.update" -> json.optJSONObject("analysis")?.takeIf { it.optString("rol") == "comprension" }?.let { a ->
            val evidence = a.optJSONArray("evidencias")
            AnalysisEvent.Understood(Understanding(
                summary = a.optString("resumen"),
                mode = a.optStringOrNull("modo"),
                evidence = if (evidence == null) emptyList() else (0 until evidence.length()).mapNotNull { i ->
                    evidence.optJSONObject(i)?.let { Evidence(it.optString("cita"), it.optString("observacion")) }
                },
                observations = strings(a, "datos_observados"), uncertainties = strings(a, "incertidumbres"), limitations = strings(a, "limitaciones"),
            ), json.optInt("revision", 0))
        }

        "risk.update" -> AnalysisEvent.Risk(
            percent = json.optInt("risk_percent"),
            reasons = json.optJSONArray("reasons")?.let { arr -> List(arr.length()) { arr.optString(it) } }.orEmpty(),
            scamType = json.optStringOrNull("scam_type"),
            impersonating = json.optStringOrNull("impersonating"),
            alert = json.opt("alerta") as? Boolean,
            scoreKind = json.optStringOrNull("score_kind"),
            revision = if (json.has("revision")) json.optInt("revision") else null,
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

    private fun strings(json: JSONObject, key: String): List<String> = json.optJSONArray(key)?.let { a ->
        List(a.length()) { a.optString(it) }
    }.orEmpty()

    private fun statusOf(value: String): AgentStatus? = when (value) {
        "connecting" -> AgentStatus.CONNECTING
        "screening" -> AgentStatus.SCREENING
        "analyzing" -> AgentStatus.ANALYZING
        "safe" -> AgentStatus.SAFE
        "suspicious" -> AgentStatus.SUSPICIOUS
        "scam" -> AgentStatus.SCAM
        else -> null
    }

    // Vapi llama "assistant" a marin y "customer" a quien llama (cae en CALLER); se aceptan ambas convenciones.
    private fun speakerOf(value: String): Speaker = when (value.lowercase()) {
        "agent", "assistant", "bot", "ai", "marin" -> Speaker.AGENT
        "user" -> Speaker.USER
        else -> Speaker.CALLER
    }
}
