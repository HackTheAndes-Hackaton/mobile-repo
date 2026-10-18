package com.tiskz.app.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceBlockTargetTest {
    private val agent = "+12025550123" // Número ficticio, no Vapi real.
    @Test fun selectsOnlyTheOriginalVisibleCaller() {
        assertEquals("+51 900 000 001", voiceBlockTarget("+51 900 000 001", true, agent))
        assertNull(voiceBlockTarget("+51 900 000 001", false, agent))
    }
    @Test fun neverBlocksTheAgentOrAMalformedHiddenNumber() {
        assertNull(voiceBlockTarget(agent, true, agent))
        assertNull(voiceBlockTarget("2025550123", true, agent))
        for (number in listOf(null, "", "privado", "123", "*21#", "000000000")) {
            assertNull(voiceBlockTarget(number, true, agent))
        }
    }
}
