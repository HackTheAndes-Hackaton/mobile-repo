package com.tiskz.app.call

import com.tiskz.app.model.*
import org.junit.Assert.*
import org.junit.Test

class VoiceMatchNoticeTest {
    private fun call(screening: Boolean) = ActiveCall("+51900000001", CallDirection.INCOMING, CallPhase.ACTIVE,
        simulated = true, callerName = "Contacto ficticio", screening = screening)
    private val match = CallAnalysis(riskPercent = 0, alert = false, readyToAnswer = true,
        voiceMatch = VoiceMatch(.92f, "Coincidencia de prueba", 1))

    @Test fun voiceNoticeTakesPriorityOverReadyAndKeepsTheContentScore() {
        val notice = CallNotifications.noticeFor(call(true), match, ConferenceState(), 50)
        assertEquals(CallStage.DANGER, notice?.stage)
        assertEquals("● Coincidencia de voz", notice?.label)
        assertEquals(0, notice?.risk)
        assertFalse(match.isSafeToAnswer(50))
    }

    @Test fun voiceNoticeRemainsVisibleAfterTheUserJoins() {
        assertEquals(CallStage.DANGER, CallNotifications.noticeFor(call(false), match, ConferenceState(), 50)?.stage)
    }
}
