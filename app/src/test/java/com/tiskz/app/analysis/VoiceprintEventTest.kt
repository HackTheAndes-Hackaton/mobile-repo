package com.tiskz.app.analysis

import com.tiskz.app.model.CallAnalysis
import org.junit.Assert.*
import org.junit.Test

class VoiceprintEventTest {
    @Test fun voiceMatchShowsCasesWithoutChangingTheRisk() {
        val event = EventParser.parse("""{"type":"voice.match","similarity":0.86,"strong":true,"cases":2,"identity_verified":false}""")
        assertTrue(event is AnalysisEvent.Voice)
        val updated = CallAnalysis(riskPercent = 10, alert = false).reduce(event!!)
        assertEquals(2, updated.voiceMatch?.reports)
        assertEquals("Voz muy parecida a un estafador reportado", updated.voiceMatch?.label)
        assertEquals(10, updated.riskPercent)
        assertEquals(false, updated.alert)
    }

    @Test fun collectingAcceptsOnlyKnownStates() {
        assertNull(EventParser.parse("""{"type":"voice.collecting","status":"otro"}"""))
        val started = CallAnalysis().reduce(EventParser.parse("""{"type":"voice.collecting","status":"started"}""")!!)
        assertEquals("started", started.voiceCollecting)
        val saved = started.reduce(EventParser.parse("""{"type":"voice.collecting","status":"saved","reason":"collected"}""")!!)
        assertEquals("saved", saved.voiceCollecting)
    }
}
