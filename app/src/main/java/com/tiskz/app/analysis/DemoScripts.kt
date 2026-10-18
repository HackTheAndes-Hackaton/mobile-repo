package com.tiskz.app.analysis

import com.tiskz.app.data.DemoScenario
import com.tiskz.app.model.AgentStatus
import com.tiskz.app.model.Speaker
import com.tiskz.app.model.VoiceMatch

/** Guiones de la demo. Los nombres de bancos y personas son ficticios. */
object DemoScripts {

    data class Step(val delayMs: Long, val event: AnalysisEvent)

    fun forScenario(scenario: DemoScenario): List<Step> = when (scenario) {
        DemoScenario.SAFE -> safeCall
        DemoScenario.SCAM, DemoScenario.AUTO -> bankScam
    }

    private val greeting = AnalysisEvent.Transcript(
        id = "agent-1",
        speaker = Speaker.AGENT,
        text = "Hola, soy PhoneWard, el asistente de esta línea. ¿Quién llama y cuál es el motivo?",
        flagged = false,
    )

    private val bankScam = listOf(
        Step(600, AnalysisEvent.Status(AgentStatus.SCREENING)),
        Step(900, greeting),
        Step(2600, caller("c-1", "Buenas tardes, le hablo del departamento de seguridad del Banco Andino.")),
        Step(700, AnalysisEvent.Status(AgentStatus.ANALYZING)),
        Step(500, AnalysisEvent.Risk(32, listOf("Dice ser de un banco"), null, "Banco Andino")),
        Step(1800, agent("a-2", "¿Me indica su nombre y un número oficial para devolver la llamada?")),
        Step(2600, caller("c-2", "No hay tiempo, detectamos un cargo de 480 mil pesos y hay que bloquearlo ahora mismo.", flagged = true)),
        Step(600, AnalysisEvent.Risk(61, listOf("Dice ser de un banco", "Urgencia artificial", "Evita identificarse"), null, null)),
        Step(1400, AnalysisEvent.Voice(VoiceMatch(0.87f, "Coincide con una voz reportada como estafa", 3))),
        Step(1800, caller("c-3", "Para anular el cargo necesito que me dicte la clave que le llegará por SMS.", flagged = true)),
        Step(600, AnalysisEvent.Risk(93, listOf("Pide código SMS (OTP)", "Urgencia artificial", "Evita identificarse", "Voz reportada antes"), "Phishing bancario", null)),
        Step(300, AnalysisEvent.Status(AgentStatus.SCAM)),
        Step(1200, agent("a-3", "Gracias. Esta llamada no será transferida.")),
    )

    private val safeCall = listOf(
        Step(600, AnalysisEvent.Status(AgentStatus.SCREENING)),
        Step(900, greeting),
        Step(2400, caller("c-1", "Hola, soy Carla, de la farmacia del barrio en Providencia.")),
        Step(700, AnalysisEvent.Status(AgentStatus.ANALYZING)),
        Step(500, AnalysisEvent.Risk(18, listOf("Se identifica con nombre y lugar"), null, null)),
        Step(1700, agent("a-2", "Gracias, Carla. ¿Cuál es el motivo de su llamada?")),
        Step(2400, caller("c-2", "La receta de la señora ya está lista para retiro. Puede pasar cuando quiera, no hay apuro.")),
        Step(600, AnalysisEvent.Risk(7, listOf("Sin pedir dinero ni datos", "Sin urgencia", "Motivo claro"), null, null)),
        Step(400, AnalysisEvent.Status(AgentStatus.SAFE)),
        Step(200, AnalysisEvent.ReadyToAnswer),
        Step(900, agent("a-3", "Perfecto, le comunico ahora.")),
    )

    private fun caller(id: String, text: String, flagged: Boolean = false) =
        AnalysisEvent.Transcript(id, Speaker.CALLER, text, flagged)

    private fun agent(id: String, text: String) =
        AnalysisEvent.Transcript(id, Speaker.AGENT, text, flagged = false)
}
