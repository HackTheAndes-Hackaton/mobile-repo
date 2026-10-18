package com.tiskz.app.analysis

import com.tiskz.app.model.AgentStatus
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.model.Speaker
import com.tiskz.app.model.TranscriptLine
import com.tiskz.app.model.VoiceMatch
import com.tiskz.app.model.Understanding
import com.tiskz.app.model.CallAssessment
import kotlin.math.roundToInt

/** Eventos que llegan de los agentes. El formato JSON está en docs/CONTRATO_BACKEND.md. */
sealed interface AnalysisEvent {
    data class Status(val status: AgentStatus) : AnalysisEvent

    /** Si `id` coincide con una línea existente, la reemplaza (transcripción parcial → final). */
    data class Transcript(
        val id: String,
        val speaker: Speaker,
        val text: String,
        val flagged: Boolean,
        val sequence: Int? = null,
    ) : AnalysisEvent

    data class Risk(
        val percent: Int,
        val reasons: List<String>,
        val scamType: String?,
        val impersonating: String?,
        val alert: Boolean? = null,
        val scoreKind: String? = null,
        val revision: Int? = null,
    ) : AnalysisEvent

    data class Alert(val value: Boolean, val probability: Double, val revision: Int) : AnalysisEvent
    data class Understood(val value: Understanding, val revision: Int) : AnalysisEvent
    data class Assessed(val value: CallAssessment, val revision: Int) : AnalysisEvent

    data class Voice(val match: VoiceMatch) : AnalysisEvent

    data object ReadyToAnswer : AnalysisEvent

    data class Ended(val finalPercent: Int?) : AnalysisEvent

    data class Error(val message: String) : AnalysisEvent

    /** El usuario tocó "No es estafa". */
    data object MarkedSafeByUser : AnalysisEvent
}

fun CallAnalysis.reduce(event: AnalysisEvent): CallAnalysis = when (event) {
    is AnalysisEvent.Status -> copy(
        status = event.status,
        readyToAnswer = readyToAnswer || event.status == AgentStatus.SAFE,
        error = null,
    )

    is AnalysisEvent.Transcript -> {
        val index = transcript.indexOfFirst { it.id == event.id }
        val line = TranscriptLine(event.id, event.speaker, event.text, event.flagged, event.sequence ?: transcript.getOrNull(index)?.sequence)
        val next = if (index >= 0) transcript.toMutableList().also { it[index] = line } else transcript + line
        copy(transcript = if (next.any { it.sequence != null }) next.sortedBy { it.sequence ?: Int.MAX_VALUE } else next)
    }

    is AnalysisEvent.Alert -> if (event.revision < alertRevision) this else copy(
        alert = event.value, alertProbability = event.probability, alertRevision = event.revision,
        riskPercent = (event.probability * 100).roundToInt().coerceIn(0, 100),
    )

    is AnalysisEvent.Understood -> if (event.revision < understandingRevision) this else copy(
        understanding = event.value, understandingRevision = event.revision,
    )

    is AnalysisEvent.Assessed -> if (event.revision < maxOf(assessmentRevision, alertRevision, understandingRevision)) this else copy(
        assessment = event.value, assessmentRevision = event.revision,
    )

    is AnalysisEvent.Risk -> if (event.revision != null && event.revision < alertRevision) this else copy(
        riskPercent = event.percent.coerceIn(0, 100),
        alert = event.alert ?: alert,
        alertRevision = event.revision ?: alertRevision,
        scoreKind = event.scoreKind ?: scoreKind,
        reasons = event.reasons,
        scamType = event.scamType ?: scamType,
        impersonating = event.impersonating ?: impersonating,
    )

    is AnalysisEvent.Voice -> copy(voiceMatch = event.match)

    AnalysisEvent.ReadyToAnswer -> copy(readyToAnswer = true)

    is AnalysisEvent.Ended -> copy(riskPercent = event.finalPercent?.coerceIn(0, 100) ?: riskPercent)

    is AnalysisEvent.Error -> copy(
        error = event.message,
        status = if (status == AgentStatus.CONNECTING) AgentStatus.OFFLINE else status,
    )

    AnalysisEvent.MarkedSafeByUser -> copy(markedSafeByUser = true, readyToAnswer = true)
}
