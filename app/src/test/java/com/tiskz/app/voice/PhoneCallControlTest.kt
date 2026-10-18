package com.tiskz.app.voice

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.tiskz.app.analysis.AnalysisEvent
import com.tiskz.app.analysis.EventParser
import com.tiskz.app.analysis.reduce
import com.tiskz.app.model.CallAnalysis

class PhoneCallControlTest {
    private val call = "11111111-2222-3333-4444-555555555555"
    private val request = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private fun order(revision: Int = 2) = JSONObject().put("type", "call.hangup_requested")
        .put("session_id", call).put("call_id", call).put("request_id", request)
        .put("revision", revision).put("reason", "risk_detected")
    private fun start(control: PhoneCallControl) {
        control.accept(JSONObject().put("type", "phone.call").put("call_id", call))
        control.accept(JSONObject().put("type", "alert.updated").put("session_id", call).put("revision", 2))
    }
    @Test fun acceptsMatchingCommandOnlyOnce() {
        val control = PhoneCallControl(); start(control)
        assertEquals(PhoneHangup(call, request, 2), control.accept(order()))
        assertNull(control.accept(order()))
        assertNull(control.accept(order().put("request_id", "bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee")))
    }
    @Test fun rejectsUnboundForeignMalformedAndOldCommands() {
        val control = PhoneCallControl()
        assertNull(control.accept(order()))
        start(control)
        assertNull(control.accept(order(1)))
        assertNull(control.accept(order().put("call_id", request)))
        assertNull(control.accept(order().put("session_id", request)))
        assertNull(control.accept(order().put("reason", "arbitrary")))
        assertNull(control.accept(order().put("revision", 2.5)))
        assertNull(control.accept(order().put("request_id", "invalid")))
        assertNotNull(control.accept(order()))
    }
    @Test fun cannotRetargetActiveCallAndRejectsLateCommandsAfterEnd() {
        val control = PhoneCallControl(); start(control)
        control.accept(JSONObject().put("type", "phone.call").put("call_id", request))
        assertNotNull(control.accept(order()))
        control.accept(JSONObject().put("type", "call.ended").put("session_id", call))
        assertNull(control.accept(order()))
    }
    @Test fun alertAloneIsNeverAnInstructionToDisconnect() {
        val control = PhoneCallControl(); start(control)
        assertNull(control.accept(JSONObject().put("type", "alert.updated").put("session_id", call).put("alerta", true).put("revision", 3)))
        assertNull(control.accept(order(2)))
        assertNotNull(control.accept(order(3)))
    }

    @Test fun agentEndClosesBoundConferenceWithoutClaimingRisk() {
        val control = PhoneCallControl(); start(control)
        val result = control.accept(order().put("reason", "agent_ended"))
        assertEquals("agent_ended", result?.reason)
        assertNull(control.accept(order().put("reason", "agent_ended")))
    }
    @Test fun agentMayEndBeforeFirstCallerTurnButRiskCannot() {
        val control = PhoneCallControl()
        control.accept(JSONObject().put("type", "phone.call").put("call_id", call))
        assertNull(control.accept(order(0)))
        assertNotNull(control.accept(order(0).put("reason", "agent_ended")))
    }

    @Test fun voiceMatchMayCloseBeforeTextAndRequestsOnlyAnExplicitLocalBlock() {
        val control = PhoneCallControl()
        control.accept(JSONObject().put("type", "phone.call").put("call_id", call))
        val result = control.accept(order(0).put("reason", "voice_match").put("block_number", true))
        assertEquals("voice_match", result?.reason)
        assertEquals(true, result?.blockNumber)
        assertNull(control.accept(order(0).put("reason", "voice_match").put("block_number", true)))
    }

    @Test fun aRiskCommandCannotInventANumberBlockAndTheFlagMustBeBoolean() {
        val control = PhoneCallControl(); start(control)
        assertEquals(false, control.accept(order().put("block_number", true))?.blockNumber)
        val other = PhoneCallControl(); start(other)
        assertEquals(false, other.accept(order().put("reason", "voice_match").put("block_number", "true"))?.blockNumber)
    }

    @Test fun evidenceIsSavedOnlyAfterValidServerConfirmationAndDoesNotChangeRisk() {
        val saving = EventParser.parse("""{"type":"audio.capture_result","status":"saving"}""")!!
        val saved = EventParser.parse("""{"type":"audio.capture_result","status":"saved","fragment_id":"clip_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}""")!!
        var state = CallAnalysis().reduce(saving)
        assertEquals("saving", state.audioEvidenceStatus)
        assertNull(state.audioFragmentId)
        state = state.reduce(saved)
        assertEquals("saved", state.audioEvidenceStatus)
        assertEquals(false, state.alert == true)
        assertNull(EventParser.parse("""{"type":"audio.capture_result","status":"saved"}"""))
    }
}
