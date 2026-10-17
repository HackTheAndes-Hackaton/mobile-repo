package com.tiskz.app.model

enum class Speaker { CALLER, AGENT, USER }

data class TranscriptLine(
    val id: String,
    val speaker: Speaker,
    val text: String,
    val flagged: Boolean = false,
)

/** Qué está haciendo el agente de Tiskz en este momento. */
enum class AgentStatus { CONNECTING, SCREENING, ANALYZING, SAFE, SUSPICIOUS, SCAM, OFFLINE }

enum class RiskLevel(val label: String) {
    LOW("BAJO"),
    MEDIUM("MEDIO"),
    HIGH("ALTO"),
    CRITICAL("CRÍTICO");

    companion object {
        fun of(percent: Int): RiskLevel = when {
            percent < 30 -> LOW
            percent < 60 -> MEDIUM
            percent < 85 -> HIGH
            else -> CRITICAL
        }
    }
}

/** Coincidencia de la voz de quien llama con voces ya reportadas como estafa. */
data class VoiceMatch(val similarity: Float, val label: String, val reports: Int)

/** Estado acumulado del análisis de una llamada, sin importar si viene del backend o de la demo. */
data class CallAnalysis(
    val status: AgentStatus = AgentStatus.CONNECTING,
    val riskPercent: Int = 0,
    val reasons: List<String> = emptyList(),
    val scamType: String? = null,
    val impersonating: String? = null,
    val transcript: List<TranscriptLine> = emptyList(),
    val voiceMatch: VoiceMatch? = null,
    val readyToAnswer: Boolean = false,
    val markedSafeByUser: Boolean = false,
    val error: String? = null,
) {
    val riskLevel: RiskLevel get() = RiskLevel.of(riskPercent)

    val isScam: Boolean
        get() = !markedSafeByUser && (status == AgentStatus.SCAM || riskPercent >= SCAM_THRESHOLD)

    fun isSafeToAnswer(threshold: Int): Boolean =
        markedSafeByUser || ((readyToAnswer || status == AgentStatus.SAFE) && riskPercent < threshold)

    companion object {
        const val SCAM_THRESHOLD = 70
    }
}

enum class Verdict(val label: String) {
    SAFE("Segura"),
    SUSPICIOUS("Sospechosa"),
    SCAM("Estafa"),
    NONE("Sin análisis"),
}

fun CallAnalysis?.verdict(): Verdict = when {
    this == null -> Verdict.NONE
    markedSafeByUser -> Verdict.SAFE
    isScam -> Verdict.SCAM
    transcript.isEmpty() && status in setOf(AgentStatus.CONNECTING, AgentStatus.OFFLINE) -> Verdict.NONE
    riskPercent >= 30 -> Verdict.SUSPICIOUS
    else -> Verdict.SAFE
}
