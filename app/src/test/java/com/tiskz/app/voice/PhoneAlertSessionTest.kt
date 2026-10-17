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
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
                webSocket.send("""{"type":"phone.call","call_id":"first"}""")
                webSocket.send("""{"type":"alert.updated","alerta":true,"probability":0.9,"revision":8}""")
            }
        }))
        server.start()
        val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        val session = PhoneAlertSession(client, server.url("/a/agent").toString(), "private-test-code")
        try {
            val request = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/a/agent/v1/phone/monitor", request.path)
            assertEquals("private-test-code", request.getHeader("X-Voice-Token"))
            val upgrade = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/a/agent/ws/calls?channel=phone&ticket=observer-ticket-test-only", upgrade.path)
            assertNull(upgrade.getHeader("X-Voice-Token"))
            assertFalse(upgrade.path!!.contains("private-test-code"))
            await { session.state.value.alert == true }
            remote.get().send("""{"type":"phone.call","call_id":"second"}""")
            await { session.state.value.alert == null }
            assertEquals(-1, session.state.value.alertRevision)
            assertTrue(session.state.value.transcript.isEmpty())
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
