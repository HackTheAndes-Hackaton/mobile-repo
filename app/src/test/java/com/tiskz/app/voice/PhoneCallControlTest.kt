package com.tiskz.app.voice

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

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
}
