package com.tiskz.app.voice

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LiveEventRelayTest {
    @Test fun delegatesDeltasUnchangedAndNeverCreatesLegacyCommits() {
        val events = mutableListOf<JSONObject>()
        var started = false
        var closed = false
        val relay = LiveEventRelay(events::add, { started = true }, { closed = true }, { fail(it) })
        relay.accept("""{"type":"session.started"}""")
        relay.accept("""{"type":"session.input_transcript.delta","event_id":"a","delta":"No"}""")
        relay.accept("""{"type":"session.input_transcript.delta","event_id":"b","delta":" necesito pagos."}""")
        relay.accept("""{"type":"session.output_transcript.delta","event_id":"c","delta":"Hola"}""")
        relay.accept("""{"type":"unknown"}""")
        relay.accept("""{"type":"session.closed","usage":{"seconds":16}}""")
        assertTrue(started); assertTrue(closed); assertEquals(4, events.size)
        assertEquals("No", events[0].getJSONObject("event").getString("delta"))
        assertEquals(" necesito pagos.", events[1].getJSONObject("event").getString("delta"))
        assertTrue(events.all { it.getString("type") == "live.event" })
    }
    @Test fun commandsOnlyAllowBoundedQuietContext() {
        assertNull(LiveEventRelay.contextCommand("""{"type":"live.command","command":{"type":"session.start","content":"bad"}}"""))
        val command = JSONObject().put("type", "session.thinking.append").put("content", "Análisis provisional").put("delegation_id", JSONObject.NULL)
        val envelope = JSONObject().put("type", "live.command").put("command", command)
        assertEquals("Análisis provisional", LiveEventRelay.contextCommand(envelope.toString())!!.getString("content"))
        command.put("content", "x".repeat(1801))
        assertNull(LiveEventRelay.contextCommand(envelope.toString()))
    }
    @Test fun modelErrorDoesNotInventFalseOrLeakProviderDetails() {
        val events = mutableListOf<JSONObject>(); val errors = mutableListOf<String>()
        val relay = LiveEventRelay(events::add, {}, {}, errors::add)
        relay.accept("""{"type":"error","error":{"message":"secret"}}""")
        assertTrue(events.isEmpty()); assertEquals(1, errors.size); assertFalse(errors.single().contains("secret"))
    }
}
