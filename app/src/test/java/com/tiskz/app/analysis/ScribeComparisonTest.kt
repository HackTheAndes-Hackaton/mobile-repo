package com.tiskz.app.analysis

import com.tiskz.app.model.CallAnalysis
import org.junit.Assert.*
import org.junit.Test

class ScribeComparisonTest {
    @Test fun comparisonNeverChangesThePrimaryRiskOrTranscript() {
        val original = CallAnalysis(riskPercent = 3, alert = false)
        val updated = original.reduce(AnalysisEvent.ComparisonAlert(true, .99, 1, 120))
        assertEquals(3, updated.riskPercent)
        assertEquals(false, updated.alert)
        assertTrue(updated.transcript.isEmpty())
        assertEquals(true, updated.scribe?.alert)
    }

    @Test fun partialIsReplacedByFinalAndCannotOverwriteItLater() {
        var a = CallAnalysis().reduce(AnalysisEvent.ComparisonCaption("scribe-0", "No", false, 0))
        a = a.reduce(AnalysisEvent.ComparisonCaption("scribe-0", "No necesito pagos.", true, 0))
        a = a.reduce(AnalysisEvent.ComparisonCaption("scribe-0", "Necesito pagos", false, 0))
        assertEquals(1, a.scribe?.lines?.size)
        assertEquals("No necesito pagos.", a.scribe?.lines?.first()?.text)
        assertTrue(a.transcript.isEmpty())
    }

    @Test fun parserRequiresExplicitComparisonAndValidProbability() {
        assertNull(EventParser.parse("""{"type":"comparison.alert","alerta":true,"probability":0.9,"revision":1}"""))
        assertNull(EventParser.parse("""{"type":"comparison.alert","comparison_only":true,"alerta":true,"probability":5,"revision":1}"""))
        assertTrue(EventParser.parse("""{"type":"comparison.alert","comparison_only":true,"alerta":false,"probability":0.1,"revision":1,"analysis_ms":250}""") is AnalysisEvent.ComparisonAlert)
    }

    @Test fun oldComparisonDecisionCannotOverwriteTheLatest() {
        val a = CallAnalysis().reduce(AnalysisEvent.ComparisonAlert(false, .1, 2, 100))
            .reduce(AnalysisEvent.ComparisonAlert(true, .99, 1, 100))
        assertEquals(false, a.scribe?.alert)
    }
}
