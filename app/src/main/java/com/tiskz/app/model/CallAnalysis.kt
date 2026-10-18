package com.tiskz.app.model

enum class Speaker { CALLER, AGENT, USER }

data class TranscriptLine(
    val id: String,
    val speaker: Speaker,
    val text: String,
    val flagged: Boolean = false,
    val sequence: Int? = null,
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

data class Evidence(val quote: String, val observation: String)
data class Understanding(
    val summary: String,
    val evidence: List<Evidence>,
    val observations: List<String>,
    val uncertainties: List<String>,
    val limitations: List<String>,
    val mode: String? = null,
)

enum class AssessmentState(val wireValue: String, val label: String) {
    OBSERVE("observar", "Observar"),
    VERIFY("verificar", "Verificar"),
    ALERT("alertar", "Alertar"),
    INCONCLUSIVE("inconcluso", "Inconcluso"),
}

/** Orientación provisional del servidor; no autoriza acciones ni verifica identidades. */
data class CallAssessment(
    val state: AssessmentState,
    val guidance: String,
    val evidence: List<Evidence>,
)

data class ComparisonLine(val id: String, val text: String, val final: Boolean, val sequence: Int)
data class ScribeComparison(
    val status: String = "starting",
    val lines: List<ComparisonLine> = emptyList(),
    val alert: Boolean? = null,
    val probability: Double? = null,
    val revision: Int = -1,
    val analysisMs: Int? = null,
)

/** Estado acumulado del análisis de una llamada, sin importar si viene del backend o de la demo. */
fun audioEvidenceMessage(status: String?): String? = when (status) {
    "saving" -> "Guardando fragmento de voz; puede completarse después del corte."
    "saved" -> "Fragmento de voz guardado · pendiente de revisión."
    "failed" -> "No se pudo guardar el fragmento de voz."
    else -> null
}

data class CallAnalysis(
    val audioEvidenceStatus: String? = null,
    val audioFragmentId: String? = null,
    val status: AgentStatus = AgentStatus.CONNECTING,
    val riskPercent: Int = 0,
    val alert: Boolean? = null,
    val alertProbability: Double? = null,
    val alertRevision: Int = -1,
    val understanding: Understanding? = null,
    val understandingRevision: Int = -1,
    val scoreKind: String? = null,
    val reasons: List<String> = emptyList(),
    val scamType: String? = null,
    val impersonating: String? = null,
    val transcript: List<TranscriptLine> = emptyList(),
    val voiceMatch: VoiceMatch? = null,
    /** El servidor permite incorporarse después de la revisión inicial; no implica identidad verificada. */
    val readyToAnswer: Boolean = false,
    val markedSafeByUser: Boolean = false,
    val error: String? = null,
    val assessment: CallAssessment? = null,
    val assessmentRevision: Int = -1,
    val scribe: ScribeComparison? = null,
) {
    /** Una orientación anterior no describe el último fragmento ya analizado. */
    val currentAssessment: CallAssessment?
        get() = assessment?.takeIf { assessmentRevision >= maxOf(alertRevision, understandingRevision) }

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
