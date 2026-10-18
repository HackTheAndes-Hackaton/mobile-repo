package com.tiskz.app.voice

import com.tiskz.app.analysis.AnalysisEvent
import com.tiskz.app.analysis.EventParser
import com.tiskz.app.analysis.reduce
import com.tiskz.app.model.AssessmentState
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.model.Understanding
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AssessmentProtocolTest {
    private fun message(state: String, revision: Any = 3): JSONObject = JSONObject()
        .put("type", "assessment.update").put("revision", revision)
        .put("assessment", JSONObject().put("version", "orientacion_v1").put("estado", state)
            .put("orientacion", "Comprueba por un canal conocido.")
            .put("alerta", JSONObject.NULL).put("acciones_automaticas", false)
            .put("evidencia_textual", org.json.JSONArray().put(JSONObject().put("tipo", "peticion_pago").put("cita", "Necesito dinero."))))

    @Test fun allFourStatesAreDisplayOnlyAndPreserveQuotes() {
        for (state in AssessmentState.entries) {
            val event = EventParser.parse(message(state.wireValue).toString()) as AnalysisEvent.Assessed
            val before = CallAnalysis(alert = false, alertProbability = .1, alertRevision = 3, riskPercent = 10)
            val after = before.reduce(event)
            assertEquals(state, after.currentAssessment!!.state)
            assertEquals("Necesito dinero.", after.currentAssessment!!.evidence.single().quote)
            assertEquals(before.alert, after.alert)
            assertEquals(before.riskPercent, after.riskPercent)
            assertFalse(after.readyToAnswer)
        }
    }

    @Test fun malformedOrUnknownEventsDoNotBecomeSafeOrAnAlert() {
        assertNull(EventParser.parse(message("seguro").toString()))
        for (revision in listOf(-1, 2.5, "3", JSONObject.NULL)) assertNull(EventParser.parse(message("observar", revision).toString()))
        val raw = message("verificar")
        raw.getJSONObject("assessment").put("orientacion", 123)
        assertNull(EventParser.parse(raw.toString()))
        raw.getJSONObject("assessment").put("orientacion", "Comprueba.").put("version", "desconocida")
        assertNull(EventParser.parse(raw.toString()))
    }

    @Test fun lateAssessmentCannotOverwriteNewerResultsAndOldGuidanceHidesWhileUpdating() {
        fun event(state: String, revision: Int) = EventParser.parse(message(state, revision).toString())!!
        var state = CallAnalysis().reduce(event("verificar", 3))
        state = state.reduce(event("observar", 2))
        assertEquals(AssessmentState.VERIFY, state.currentAssessment!!.state)
        state = state.reduce(AnalysisEvent.Alert(false, .1, 4))
        assertNull(state.currentAssessment)
        state = state.reduce(event("alertar", 3))
        assertEquals(3, state.assessmentRevision)
        assertNull(state.currentAssessment)
        state = state.reduce(event("inconcluso", 4))
        assertEquals(AssessmentState.INCONCLUSIVE, state.currentAssessment!!.state)
        state = state.reduce(AnalysisEvent.Understood(Understanding("Contexto.", emptyList(), emptyList(), emptyList(), emptyList()), 5))
        assertNull(state.currentAssessment)
        assertEquals(state, state.reduce(event("observar", 4)))
    }

    @Test fun newOrLegacyBackendWithoutAssessmentStaysPendingWithoutAssumingSafety() {
        assertNull(CallAnalysis().currentAssessment)
        val state = CallAnalysis().reduce(AnalysisEvent.Alert(false, .0, 1))
        assertNull(state.currentAssessment)
        assertEquals(false, state.alert)
        assertNull(EventParser.parse("""{"type":"unknown.update"}"""))
    }
}
