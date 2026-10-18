package com.tiskz.app.voice

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.json.JSONObject
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import com.tiskz.app.model.Speaker
import com.tiskz.app.model.AssessmentState

class PhoneAlertSessionTest {
    @Test fun authenticatesWithoutUrlSecretsAndClearsPreviousCallDecision() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        val remote = AtomicReference<WebSocket>()
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\"ticket\":\"observer-ticket-test-only\"}"))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                remote.set(webSocket)
                webSocket.send("""{"type":"phone.ready"}""")
                webSocket.send("""{"type":"phone.call","call_id":"11111111-2222-3333-4444-555555555555"}""")
                webSocket.send("""{"type":"transcript.update","session_id":"11111111-2222-3333-4444-555555555555","id":"phone-agent-10","speaker":"agent","text":"Hola.","sequence":0,"final":true}""")
                webSocket.send("""{"type":"transcript.update","session_id":"11111111-2222-3333-4444-555555555555","id":"phone-caller-20","speaker":"caller","text":"Necesito ayuda.","sequence":1,"final":true}""")
                webSocket.send("""{"type":"transcript.update","session_id":"11111111-2222-3333-4444-555555555555","id":"phone-agent-30","speaker":"agent","text":"¿Qué necesitas?","sequence":2,"final":true}""")
                webSocket.send("""{"type":"transcript.update","session_id":"11111111-2222-3333-4444-555555555555","id":"phone-caller-40","speaker":"caller","text":"Quiero verificar un pago.","sequence":3,"final":true}""")
                webSocket.send("""{"type":"alert.updated","session_id":"11111111-2222-3333-4444-555555555555","alerta":false,"probability":0.37,"revision":7}""")
                webSocket.send("""{"type":"assessment.update","session_id":"11111111-2222-3333-4444-555555555555","revision":7,"assessment":{"version":"orientacion_v1","estado":"verificar","orientacion":"Comprueba por un canal conocido.","evidencia_textual":[]}}""")
            }
        }))
        server.start()
        val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        val session = PhoneAlertSession(client, server.url("/a/agent").toString(), "private-test-code")
        try {
            val request = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/a/agent/v1/phone/monitor", request.path)
            assertEquals("private-test-code", request.getHeader("X-Voice-Token"))
            assertTrue(JSONObject(request.body.readUtf8()).getBoolean("consentimiento_corte_automatico"))
            val upgrade = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/a/agent/ws/calls?channel=phone&ticket=observer-ticket-test-only", upgrade.path)
            assertNull(upgrade.getHeader("X-Voice-Token"))
            assertFalse(upgrade.path!!.contains("private-test-code"))
            await { session.state.value.currentAssessment?.state == AssessmentState.VERIFY }
            assertEquals(37, session.state.value.riskPercent)
            assertEquals(listOf(Speaker.AGENT, Speaker.CALLER, Speaker.AGENT, Speaker.CALLER), session.state.value.transcript.map { it.speaker })
            assertEquals("Quiero verificar un pago.", session.state.value.transcript.last().text)
            remote.get().send("""{"type":"assessment.update","session_id":"otra-llamada","revision":99,"assessment":{"version":"orientacion_v1","estado":"alertar","orientacion":"No transferir.","evidencia_textual":[]}}""")
            remote.get().send("""{"type":"alert.updated","session_id":"11111111-2222-3333-4444-555555555555","alerta":true,"probability":0.9,"revision":8}""")
            await { session.state.value.alert == true }
            assertEquals(7, session.state.value.assessmentRevision)
            assertNull(session.state.value.currentAssessment)
            assertEquals(90, session.state.value.riskPercent)
            remote.get().send("""{"type":"call.hangup_requested","session_id":"11111111-2222-3333-4444-555555555555","call_id":"11111111-2222-3333-4444-555555555555","request_id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","revision":8,"reason":"risk_detected"}""")
            await { session.hangupRequests.value != null }
            assertEquals(8, session.hangupRequests.value!!.revision)
            remote.get().send("""{"type":"call.ended","session_id":"11111111-2222-3333-4444-555555555555"}""")
            remote.get().send("""{"type":"phone.call","call_id":"second"}""")
            await { session.state.value.alert == null }
            assertEquals(-1, session.state.value.alertRevision)
            assertTrue(session.state.value.transcript.isEmpty())
            assertNull(session.state.value.assessment)
            remote.get().send("""{"type":"comparison.alert","comparison_only":true,"session_id":"11111111-2222-3333-4444-555555555555","alerta":true,"probability":0.99,"revision":99}""")
            Thread.sleep(30)
            assertNull(session.state.value.scribe)
            session.close()
            assertEquals(WebRtcVoiceSession.Connection.ENDED, session.connection.value)
        } finally {
            session.close(); remote.get()?.close(1000, "test done"); server.shutdown()
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("No se recibió el estado esperado", predicate())
    }
}
