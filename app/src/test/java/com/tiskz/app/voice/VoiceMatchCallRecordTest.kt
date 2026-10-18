package com.tiskz.app.voice

import com.tiskz.app.model.*
import org.junit.Assert.*
import org.junit.Test

class VoiceMatchCallRecordTest {
    private val call = ActiveCall("+51900000001", CallDirection.INCOMING, CallPhase.DISCONNECTED,
        simulated = true, blocked = true, endReason = CallEndReason.VOICE_MATCH)
    private val analysis = CallAnalysis(status = AgentStatus.SAFE, riskPercent = 10, alert = false,
        voiceMatch = VoiceMatch(.92f, "Muestra ficticia", 1))

    @Test fun aVoiceCutKeepsItsReasonWithoutTurningSafeContentIntoAFraudVerdict() {
        val record = CallRecord.from(call, analysis)
        assertTrue(record.endedByVoiceMatch)
        assertTrue(record.blocked)
        assertEquals(10, record.riskPercent)
        assertEquals(Verdict.SAFE, record.verdict)
        assertTrue(record.endExplanation!!.contains("92 %"))
        assertTrue(record.endExplanation!!.contains("lista local de bloqueo"))
        assertTrue(record.endExplanation!!.contains("no verifica una identidad"))
    }

    @Test fun localHistoryRetainsTheEndReasonAndTheSeparateSimilarity() {
        val record = CallRecord.fromJson(CallRecord.from(call, analysis).toJson())
        assertEquals(CallEndReason.VOICE_MATCH, record.endReason)
        assertEquals(.92f, record.voiceSimilarity!!, .0001f)
        assertTrue(record.endedByVoiceMatch)
    }

    @Test fun aVoiceNoticeAloneDoesNotInventACutOrANumberBlock() {
        val record = CallRecord.from(call.copy(endReason = null, blocked = false), analysis)
        assertFalse(record.endedByVoiceMatch)
        assertNull(record.endExplanation)
        val hidden = CallRecord.from(call.copy(blocked = false), analysis)
        assertTrue(hidden.endExplanation!!.contains("No se añadió un bloqueo"))
    }

    @Test fun oldHistoryAndUnknownReasonsRemainReadableWithoutGuessingTheCause() {
        val json = CallRecord.from(call, analysis).toJson()
        json.remove("endReason"); json.remove("voiceSimilarity")
        assertNull(CallRecord.fromJson(json).endReason)
        assertNull(CallRecord.fromJson(json).voiceSimilarity)
        json.put("endReason", "unknown").put("voiceSimilarity", 7)
        assertNull(CallRecord.fromJson(json).endReason)
        assertNull(CallRecord.fromJson(json).voiceSimilarity)
    }
}
