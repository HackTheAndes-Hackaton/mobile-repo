package com.tiskz.app.model

import com.tiskz.app.util.PhoneNumbers
import org.json.JSONArray
import org.json.JSONObject

/** Llamada terminada, guardada en el historial. */
data class CallRecord(
    val id: String,
    val number: String,
    val callerName: String?,
    val direction: CallDirection,
    val startedAt: Long,
    val durationSec: Long,
    val verdict: Verdict,
    val riskPercent: Int,
    val reasons: List<String>,
    val scamType: String?,
    val impersonating: String?,
    val transcript: List<TranscriptLine>,
    val blocked: Boolean,
    val simulated: Boolean,
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    /** La atendió el asistente antes que el titular. */
    val screened: Boolean = false,
    /** De qué trató la llamada, según el análisis. */
    val summary: String? = null,
    val endReason: CallEndReason? = null,
    val voiceSimilarity: Float? = null,
    val modalities: List<FraudModality> = emptyList(),
    val priorVoiceReportType: String? = null,
) {
    val title: String get() = callerName ?: PhoneNumbers.display(number)
    val endedByVoiceMatch: Boolean get() = endReason == CallEndReason.VOICE_MATCH
    val endExplanation: String? get() = if (endedByVoiceMatch) buildString {
        append("Se cortó por similitud con una muestra de voz guardada")
        voiceSimilarity?.let { append(" (${kotlin.math.round(it * 100).toInt()} %)") }
        append(". ")
        append(if (blocked) "El número se añadió a tu lista local de bloqueo. " else "No se añadió un bloqueo de número. ")
        append("La coincidencia no verifica una identidad. El riesgo del contenido se evalúa por separado.")
        priorVoiceReportType?.let { append(" Tipo indicado en reportes previos: $it; no clasifica esta llamada.") }
    } else null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("number", number)
        put("callerName", callerName ?: JSONObject.NULL)
        put("direction", direction.name)
        put("startedAt", startedAt)
        put("durationSec", durationSec)
        put("verdict", verdict.name)
        put("riskPercent", riskPercent)
        put("reasons", JSONArray(reasons))
        put("scamType", scamType ?: JSONObject.NULL)
        put("impersonating", impersonating ?: JSONObject.NULL)
        put("transcript", JSONArray().apply {
            transcript.forEach { line ->
                put(JSONObject().apply {
                    put("id", line.id)
                    put("speaker", line.speaker.name)
                    put("text", line.text)
                    put("flagged", line.flagged)
                })
            }
        })
        put("blocked", blocked)
        put("simulated", simulated)
        put("answeredAt", answeredAt ?: JSONObject.NULL)
        put("endedAt", endedAt ?: JSONObject.NULL)
        put("screened", screened)
        put("summary", summary ?: JSONObject.NULL)
        put("endReason", endReason?.wireValue ?: JSONObject.NULL)
        put("voiceSimilarity", voiceSimilarity ?: JSONObject.NULL)
        put("modalidades", JSONArray(modalities.map { it.toJson() }))
        put("priorVoiceReportType", priorVoiceReportType ?: JSONObject.NULL)
    }

    companion object {
        fun from(call: ActiveCall, analysis: CallAnalysis?, screened: Boolean = false): CallRecord {
            val end = call.endedAt ?: System.currentTimeMillis()
            val duration = call.answeredAt?.let { (end - it) / 1000 } ?: 0
            return CallRecord(
                id = call.id,
                number = call.number,
                callerName = call.callerName,
                direction = call.direction,
                startedAt = call.startedAt,
                durationSec = duration,
                verdict = analysis.verdict(),
                riskPercent = analysis?.riskPercent ?: 0,
                reasons = analysis?.reasons.orEmpty(),
                scamType = analysis?.scamType,
                impersonating = analysis?.impersonating,
                transcript = analysis?.transcript.orEmpty(),
                blocked = call.blocked,
                simulated = call.simulated,
                answeredAt = call.answeredAt,
                endedAt = end,
                screened = screened,
                summary = analysis?.understanding?.summary?.takeIf { it.isNotBlank() },
                endReason = call.endReason,
                voiceSimilarity = analysis?.voiceMatch?.similarity?.takeIf { it.isFinite() && it in 0f..1f },
                modalities = analysis?.currentAssessment?.modalities.orEmpty(),
                priorVoiceReportType = analysis?.voiceMatch?.reportedType,
            )
        }

        fun fromJson(json: JSONObject): CallRecord = CallRecord(
            id = json.getString("id"),
            number = json.getString("number"),
            callerName = json.optStringOrNull("callerName"),
            direction = CallDirection.valueOf(json.getString("direction")),
            startedAt = json.getLong("startedAt"),
            durationSec = json.getLong("durationSec"),
            verdict = Verdict.valueOf(json.getString("verdict")),
            riskPercent = json.getInt("riskPercent"),
            reasons = json.getJSONArray("reasons").let { arr -> List(arr.length()) { arr.getString(it) } },
            scamType = json.optStringOrNull("scamType"),
            impersonating = json.optStringOrNull("impersonating"),
            transcript = json.getJSONArray("transcript").let { arr ->
                List(arr.length()) {
                    val line = arr.getJSONObject(it)
                    TranscriptLine(
                        id = line.getString("id"),
                        speaker = Speaker.valueOf(line.getString("speaker")),
                        text = line.getString("text"),
                        flagged = line.optBoolean("flagged"),
                    )
                }
            },
            blocked = json.optBoolean("blocked"),
            simulated = json.optBoolean("simulated"),
            answeredAt = if (json.isNull("answeredAt")) null else json.getLong("answeredAt"),
            endedAt = if (json.isNull("endedAt")) null else json.getLong("endedAt"),
            screened = json.optBoolean("screened"),
            summary = json.optStringOrNull("summary"),
            endReason = CallEndReason.fromWire(json.optStringOrNull("endReason")),
            voiceSimilarity = json.optDouble("voiceSimilarity", Double.NaN).toFloat().takeIf { it.isFinite() && it in 0f..1f },
            modalities = FraudModality.parse(json.optJSONArray("modalidades")),
            priorVoiceReportType = json.optStringOrNull("priorVoiceReportType")?.takeIf { it.length <= 120 },
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)
