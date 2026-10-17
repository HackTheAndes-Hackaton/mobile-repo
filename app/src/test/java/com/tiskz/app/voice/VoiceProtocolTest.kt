package com.tiskz.app.voice

import com.tiskz.app.analysis.AnalysisEvent
import com.tiskz.app.analysis.EventParser
import com.tiskz.app.analysis.reduce
import com.tiskz.app.model.CallAnalysis
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import okhttp3.Request
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class VoiceProtocolTest {
    private val id = "12345678-1234-1234-1234-123456789012"
    private val master = "master-test-code-only"
    private val producer = "producer-test-ticket-only"

    private fun server(block: (MockWebServer, VoiceBackendApi) -> Unit) {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        try { block(server, VoiceBackendApi(server.url("/a/voice-id").toString(), client)) }
        finally { server.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }

    @Test fun roomUsesProxyHeaderAndPreservesMaritimePath() = server { server, api -> runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(JSONObject().put("session_id", id).put("producer_token", producer).toString()))
        val room = api.createRoom(master, true)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/a/voice-id/v1/sessions", request.path)
        assertEquals(master, request.getHeader("X-Voice-Token"))
        assertNull(request.getHeader("Authorization"))
        assertTrue(JSONObject(request.body.readUtf8()).getBoolean("consentimiento_analisis"))
        assertTrue(api.eventsUrl(room).startsWith("wss://localhost:"))
        assertTrue(api.eventsUrl(room).contains("/a/voice-id/ws/calls?session=$id&ticket=$producer"))
        assertFalse(room.toString().contains(producer))
    } }

    @Test fun offerAndEndUseRoomTicketInsteadOfMaster() = server { server, api -> runBlocking {
        val room = VoiceRoom(id, producer)
        server.enqueue(MockResponse().setBody(JSONObject().put("sdp", "v=0\r\nm=audio 9 RTP/AVP 0").toString()))
        api.offer(room, "v=0\r\nm=audio 9 RTP/AVP 0")
        server.enqueue(MockResponse().setBody("{\"ok\":true}"))
        api.end(room)
        for (suffix in listOf("offer", "end")) {
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/a/voice-id/v1/sessions/$id/$suffix", request.path)
            assertEquals(producer, request.getHeader("X-Voice-Token"))
        }
    } }

    @Test fun consentAndHttpsAreRequiredBeforeAnyRequest() = server { server, api -> runBlocking {
        assertTrue(runCatching { api.createRoom(master, false) }.isFailure)
        assertEquals(0, server.requestCount)
        for (url in listOf("http://example.com", "https://user:password@example.com", "https://example.com?ticket=secret", "https://example.com#secret")) {
            assertTrue(runCatching { VoiceBackendApi(url, OkHttpClient()) }.isFailure)
        }
    } }

    @Test fun serviceErrorDoesNotEchoTokensOrMeanFalse() = server { server, api -> runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"$master\"}"))
        val error = runCatching { api.createRoom(master, true) }.exceptionOrNull()!!
        assertTrue(error.message!!.contains("Código"))
        assertFalse(error.message!!.contains(master))
        val state = CallAnalysis().reduce(AnalysisEvent.Error(error.message!!))
        assertNull(state.alert)
        assertNotNull(state.error)
    } }

    @Test fun cancellingRequestDoesNotRetryRoomCreation() = server { server, api -> runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async { api.createRoom(master, true) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(1, server.requestCount)
    } }

    @Test fun redirectsCannotForwardPrivateAccessCode() = server { server, api -> runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/another-service")))
        val error = runCatching { api.createRoom(master, true) }.exceptionOrNull()!!
        assertTrue(error.message!!.contains("302"))
        assertEquals(1, server.requestCount)
    } }

    @Test fun parserRequiresBooleanAndRejectsInvalidProbability() {
        assertNull(EventParser.parse("""{"type":"alert.updated","alerta":"false","probability":0.1}"""))
        assertNull(EventParser.parse("""{"type":"alert.updated","alerta":false,"probability":4}"""))
        val event = EventParser.parse("""{"type":"alert.updated","alerta":false,"probability":0.2,"revision":3}""") as AnalysisEvent.Alert
        assertFalse(event.value)
        assertEquals(3, event.revision)
    }

    @Test fun oldRevisionsCannotReplaceNewerAlertsAndErrorsPreserveLastDecision() {
        var state = CallAnalysis().reduce(AnalysisEvent.Alert(true, .9, 4))
        state = state.reduce(AnalysisEvent.Alert(false, .1, 3))
        state = state.reduce(AnalysisEvent.Error("transcripción indisponible"))
        assertEquals(true, state.alert)
        assertEquals(4, state.alertRevision)
        assertNotNull(state.error)
    }

    @Test fun understandingAndTranscriptReplacePartialWithoutDuplicatingLines() {
        var state = CallAnalysis().reduce(AnalysisEvent.Transcript("one", com.tiskz.app.model.Speaker.USER, "No necesito", false))
        state = state.reduce(AnalysisEvent.Transcript("one", com.tiskz.app.model.Speaker.USER, "No necesito pagos ni datos personales.", false))
        val understood = EventParser.parse("""{"type":"understanding.update","revision":2,"analysis":{"rol":"comprension","resumen":"No se solicitan pagos.","modo":"extractivo_sin_identidades_v1","evidencias":[{"cita":"No necesito pagos","observacion":"Negación"}],"limitaciones":["Identidad no verificada"]}}""")!!
        state = state.reduce(understood)
        assertEquals(1, state.transcript.size)
        assertTrue(state.transcript.single().text.contains("ni datos"))
        assertEquals("No necesito pagos", state.understanding!!.evidence.single().quote)
        assertEquals("extractivo_sin_identidades_v1", state.understanding!!.mode)
    }

    @Test fun relayPreservesOrderAndFinalDeduplication() {
        val sent = mutableListOf<JSONObject>()
        val relay = RealtimeTranscriptRelay(sent::add) { fail(it) }
        relay.accept("""{"type":"input_audio_buffer.committed","item_id":"first"}""")
        relay.accept("""{"type":"input_audio_buffer.committed","item_id":"second"}""")
        relay.accept("""{"type":"conversation.item.input_audio_transcription.completed","item_id":"second","transcript":"Ni datos personales."}""")
        relay.accept("""{"type":"conversation.item.input_audio_transcription.completed","item_id":"first","transcript":"No necesito pagos."}""")
        relay.accept("""{"type":"conversation.item.input_audio_transcription.completed","item_id":"first","transcript":"texto duplicado"}""")
        assertEquals(2, sent.size)
        assertEquals(1, sent.first().getInt("sequence"))
        assertEquals(0, sent.last().getInt("sequence"))
        assertEquals("No necesito pagos.", sent.last().getString("text"))
        assertTrue(sent.all { it.getBoolean("final") })
    }

    @Test fun transcriptionErrorsDoNotCreateAnAlertOrFakeTranscript() {
        val sent = mutableListOf<JSONObject>(); val errors = mutableListOf<String>()
        val relay = RealtimeTranscriptRelay(sent::add, errors::add)
        relay.accept("""{"type":"conversation.item.input_audio_transcription.failed"}""")
        assertTrue(sent.isEmpty()); assertEquals(1, errors.size)
    }

    @Test fun vadNeverCutsContinuousSpeechAndCommitsOnlyOnceDuringSilence() {
        val vad = VoiceActivityDetector()
        for (time in 0L..20000L step 10) assertFalse(vad.accept(.1, time))
        assertFalse(vad.accept(0.0, 20100))
        assertTrue(vad.accept(0.0, 20200))
        assertFalse(vad.accept(0.0, 21000))
        assertEquals(1.0, VoiceActivityDetector.pcm16Rms(byteArrayOf(0, -128, 0, -128)), .00001)
        assertEquals(0.0, VoiceActivityDetector.pcm16Rms(byteArrayOf(0, 0)), .00001)
    }
    @Test fun remoteCloseStopsCaptureHookWithoutCallEndedOrPingTimeout() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.close(1000, "remote shutdown") }
        }))
        server.start()
        val client = OkHttpClient()
        val closed = AtomicBoolean(false)
        val terminated = CountDownLatch(1)
        val socket = client.newWebSocket(Request.Builder().url(server.url("/events")).build(), VoiceEventsListener(
            isClosed = closed::get, onReady = {}, onEvent = { fail("No se esperaba call.ended") },
            onTerminated = { closed.set(true); terminated.countDown() },
        ))
        try { assertTrue("El cierre remoto debe detener la captura inmediatamente", terminated.await(3, TimeUnit.SECONDS)) }
        finally { socket.cancel(); server.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }

    @Test fun displayPreservesCommitOrderWhenFinalsArriveBackwards() {
        var state = CallAnalysis()
        for ((id, text, sequence) in listOf(Triple("second", "Ni datos personales.", 1), Triple("first", "No necesito pagos.", 0))) {
            state = state.reduce(EventParser.parse(JSONObject().put("type", "transcript.update").put("id", id).put("text", text).put("sequence", sequence).toString())!!)
        }
        assertEquals(listOf("first", "second"), state.transcript.map { it.id })
        assertEquals("No necesito pagos. Ni datos personales.", state.transcript.joinToString(" ") { it.text })
        state = state.reduce(AnalysisEvent.Transcript("first", com.tiskz.app.model.Speaker.USER, "No necesito pagos.", false))
        assertEquals(0, state.transcript.first().sequence)
        assertEquals(2, state.transcript.size)
    }

}
