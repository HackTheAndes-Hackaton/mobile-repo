package com.tiskz.app.analysis

import com.tiskz.app.model.AgentStatus
import com.tiskz.app.model.Speaker
import com.tiskz.app.model.VoiceMatch
import com.tiskz.app.model.Evidence
import com.tiskz.app.model.Understanding
import com.tiskz.app.model.CallAssessment
import com.tiskz.app.model.AssessmentState
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
        "audio.capture_result" -> {
            val status = json.optString("status")
            val fragment = json.optString("fragment_id").takeIf { it.matches(Regex("clip_[a-f0-9]{48}")) }
            if (status !in setOf("saving", "saved", "failed") || (status == "saved" && fragment == null)) null
            else AnalysisEvent.AudioEvidence(status, fragment)
        }
        "comparison.status" -> if (json.opt("comparison_only") == true) AnalysisEvent.ComparisonStatus(json.optString("status")) else null
        "transcript.comparison" -> {
            val id = json.optString("id")
            val sequence = json.optInt("sequence", -1)
            val text = json.optString("text")
            val final = json.opt("final") as? Boolean
            if (json.opt("comparison_only") != true || !id.matches(Regex("scribe-\\d{1,3}")) || sequence !in 0..99 ||
                text.isBlank() || text.length > 4000 || final == null) null
            else AnalysisEvent.ComparisonCaption(id, text, final, sequence)
        }
        "comparison.alert" -> {
            val value = json.opt("alerta") as? Boolean
            val probability = json.optDouble("probability", Double.NaN)
            val revision = json.optInt("revision", -1)
            if (json.opt("comparison_only") != true || value == null || !probability.isFinite() || probability !in 0.0..1.0 || revision < 1) null
            else AnalysisEvent.ComparisonAlert(value, probability, revision, json.optInt("analysis_ms", -1).takeIf { it >= 0 })
        }
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

        "assessment.update" -> assessment(json)

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

        // Huella vocal del servidor: una pista revisable, no una identificación.
        "voice.match" -> AnalysisEvent.Voice(
            VoiceMatch(
                similarity = json.optDouble("similarity", 0.0).toFloat(),
                label = if (json.optBoolean("strong")) "Coincidencia fuerte con una voz reportada" else "Voz similar a un reporte previo",
                reports = json.optInt("cases"),
            )
        )

        "voice.collecting" -> json.optString("status").takeIf { it in setOf("started", "saved", "failed", "insufficient_audio", "matched_existing") }
            ?.let { AnalysisEvent.VoiceCollecting(it) }

        "call.ready_to_answer" -> AnalysisEvent.ReadyToAnswer

        "call.ended" -> AnalysisEvent.Ended(
            finalPercent = if (json.has("risk_percent")) json.optInt("risk_percent") else null
        )

        "error" -> AnalysisEvent.Error(json.optString("message", "Error en el análisis"))

        else -> null
    }

    private fun assessment(json: JSONObject): AnalysisEvent.Assessed? {
        val revision = (json.opt("revision") as? Number)?.toDouble()
            ?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE && it % 1 == 0.0 }?.toInt() ?: return null
        val a = json.optJSONObject("assessment") ?: return null
        if (a.optString("version") != "orientacion_v1") return null
        val state = AssessmentState.entries.find { it.wireValue == a.opt("estado") } ?: return null
        val guidance = (a.opt("orientacion") as? String)?.takeIf { it.isNotBlank() && it.length <= 2000 } ?: return null
        val quotes = a.optJSONArray("evidencia_textual")
        if (quotes != null && quotes.length() > 8) return null
        val evidence = if (quotes == null) emptyList() else (0 until quotes.length()).map { i ->
            val item = quotes.optJSONObject(i) ?: return null
            val quote = (item.opt("cita") as? String)?.takeIf { it.isNotBlank() && it.length <= 600 } ?: return null
            Evidence(quote, item.optString("tipo"))
        }
        return AnalysisEvent.Assessed(CallAssessment(state, guidance, evidence), revision)
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
