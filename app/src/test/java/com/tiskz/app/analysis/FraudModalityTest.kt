package com.tiskz.app.analysis

import com.tiskz.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FraudModalityTest {
    private val quote = "Paga el cupo o le haré daño a tu familia."
    private fun event() = JSONObject().put("type", "assessment.update").put("revision", 2).put("assessment",
        JSONObject().put("version", "orientacion_v1").put("estado", "alertar").put("orientacion", "Verifica por un canal conocido.")
            .put("evidencia_textual", JSONArray().put(JSONObject().put("tipo", "amenaza").put("cita", quote)))
            .put("modalidades", JSONArray().put(JSONObject().put("categoria", "posible_extorsion")
                .put("interpretacion", "hipotesis_preventiva").put("fuentes", JSONArray().put("lazo_rivas_2022"))
                .put("evidencias", JSONArray().put(JSONObject().put("cita", quote)).put(JSONObject().put("cita", quote))))))

    @Test fun anchoredTypeAndSourcesSurviveHistoryWithoutChangingRisk() {
        val analysis = CallAnalysis(riskPercent = 93, alert = true, alertRevision = 2).reduce(EventParser.parse(event().toString())!!)
        val m = analysis.currentAssessment!!.modalities.single()
        assertEquals("Posible extorsión", m.label)
        assertEquals(listOf(quote), m.quotes)
        assertEquals("https://doi.org/10.15381/lengsoc.v21i2.22535", fraudSources[m.sources.single()]!!.url)
        assertEquals(93, analysis.riskPercent)
        val call = ActiveCall("+51900000001", CallDirection.INCOMING, CallPhase.DISCONNECTED, simulated = true)
        val record = CallRecord.fromJson(CallRecord.from(call, analysis).toJson())
        assertEquals(m, record.modalities.single())
    }

    @Test fun unanchoredQuotesAndUnknownCategoriesAreNotDisplayedAsEvidence() {
        val e = event()
        val m = e.getJSONObject("assessment").getJSONArray("modalidades").getJSONObject(0)
        m.put("evidencias", JSONArray().put(JSONObject().put("cita", "Una cita inventada.")))
        val analysis = CallAnalysis().reduce(EventParser.parse(e.toString())!!)
        assertTrue(analysis.currentAssessment!!.modalities.isEmpty())
        m.put("categoria", "delito_confirmado")
        assertTrue(CallAnalysis().reduce(EventParser.parse(e.toString())!!).currentAssessment!!.modalities.isEmpty())
    }

    @Test fun aNewRevisionClearsTheOldTypeAndNeverSavesAnOutdatedAssessment() {
        val initial = CallAnalysis(scamType = "Posible extorsión", alertRevision = 2).reduce(EventParser.parse(event().toString())!!)
        val updated = initial.reduce(AnalysisEvent.Risk(5, emptyList(), null, null, alert = false, revision = 3))
        assertNull(updated.scamType)
        assertNull(updated.currentAssessment)
        val call = ActiveCall("+51900000001", CallDirection.INCOMING, CallPhase.DISCONNECTED, simulated = true)
        assertTrue(CallRecord.from(call, updated).modalities.isEmpty())
    }

    @Test fun aPreviousVoiceReportCannotClassifyTheCurrentCallOrSupplyAnExternalLink() {
        val a = CallAnalysis(riskPercent = 0, alert = false).reduce(EventParser.parse("""{"type":"voice.match","similarity":0.9,"strong":true,"cases":2,"scam_type":"Extorsión en reporte previo"}""")!!)
        assertEquals("Extorsión en reporte previo", a.voiceMatch?.reportedType)
        assertNull(a.scamType)
        val e = event()
        e.getJSONObject("assessment").getJSONArray("modalidades").getJSONObject(0).put("fuentes", JSONArray().put("https://enlace-no-autorizado.invalid"))
        assertTrue(CallAnalysis().reduce(EventParser.parse(e.toString())!!).currentAssessment!!.modalities.single().sources.isEmpty())
    }
}
