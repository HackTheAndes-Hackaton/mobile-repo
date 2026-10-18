package com.tiskz.app.voice

import java.time.Instant
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AudioCaptureProtocolTest {
    private val session = UUID.randomUUID().toString()
    private val now = 1800000000000L
    private fun command(id: String = UUID.randomUUID().toString()) = JSONObject()
        .put("type", "audio.capture_requested").put("session_id", session).put("request_id", id)
        .put("revision", 2).put("window_ms", 10000).put("expires_at", Instant.ofEpochMilli(now + 30000).toString())

    @Test fun authorizedCommandIsAcceptedOnceAndConfirmationMustMatch() {
        val protocol = AudioCaptureProtocol(session, true) { now }
        val json = command()
        val request = requireNotNull(protocol.request(json.toString()))
        assertEquals(json.getString("request_id"), request.id)
        assertNull(protocol.request(json.toString()))
        val result = JSONObject().put("type", "audio.capture_result").put("session_id", session)
            .put("request_id", request.id).put("revision", 2).put("status", "saved").put("fragment_id", "clip_" + "a".repeat(48))
        assertNull(protocol.result(JSONObject(result.toString()).put("revision", 1).toString()))
        assertNull(protocol.result(JSONObject(result.toString()).put("session_id", UUID.randomUUID().toString()).toString()))
        assertTrue(requireNotNull(protocol.result(result.toString())).saved)
        assertNull(protocol.result(result.toString()))
    }

    @Test fun refusesConsentlessExpiredForeignOrUnboundedCommands() {
        val json = command()
        assertNull(AudioCaptureProtocol(session, false) { now }.request(json.toString()))
        val protocol = AudioCaptureProtocol(session, true) { now }
        assertNull(protocol.request(JSONObject(json.toString()).put("session_id", UUID.randomUUID().toString()).toString()))
        assertNull(protocol.request(JSONObject(json.toString()).put("expires_at", Instant.ofEpochMilli(now - 1).toString()).toString()))
        assertNull(protocol.request(JSONObject(json.toString()).put("window_ms", 60000).toString()))
        assertNull(protocol.request(JSONObject(json.toString()).put("request_id", "invalid").toString()))
        repeat(3) { assertNotNull(protocol.request(command().toString())) }
        assertNull(protocol.request(command().toString()))
    }
}
