package com.tiskz.app.voice

import android.Manifest
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.tiskz.app.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Opcional: requiere un emulador sin audio del host y un inyector gRPC de WAV sintético.
 * La clave se suministra en el almacenamiento privado de la app; nunca forma parte del APK.
 */
@RunWith(AndroidJUnit4::class)
class NativeVoiceIntegrationTest {
    @get:Rule val microphone: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test fun microphoneWebRtcTranscriptionAndBothAgents() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyFile = File(context.filesDir, "voice-test-code")
        org.junit.Assume.assumeTrue("Prueba externa opcional: falta el código privado de voz.", keyFile.isFile)
        val code = keyFile.readText().trim()
        val phase = File(context.filesDir, "voice-test-phase")
        val results = JSONArray()
        val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val scenario = InstrumentationRegistry.getArguments().getString("voiceScenario")
            require(scenario == null || scenario in setOf("riesgo", "legitimo"))
            val cases = listOf(Triple("riesgo", true, "haré daño a tu familia"), Triple("legitimo", false, "No necesito pagos ni datos personales"))
                .filter { scenario == null || it.first == scenario }
            for ((name, expected, needle) in cases) {
                val session = WebRtcVoiceSession(context, client, "https://api.maritime.sh/a/ebf0eb81-42c0-4437-a044-1af84af1b755", code)
                try {
                    session.start(true)
                    withTimeout(50000) {
                        while (session.connection.value != WebRtcVoiceSession.Connection.STREAMING) {
                            check(session.state.value.error == null) { session.state.value.error.orEmpty() }
                            delay(100)
                        }
                    }
                    phase.writeText("ready:$name")
                    withTimeout(45000) {
                        while (true) {
                            val state = session.state.value
                            check(state.error == null) { state.error.orEmpty() }
                            val transcript = state.transcript.joinToString(" ") { it.text }
                            if (state.alert == expected && state.understanding != null && transcript.contains(needle, ignoreCase = true)) break
                            delay(100)
                        }
                    }
                    val state = session.state.value
                    assertEquals(expected, state.alert)
                    assertNotNull(state.understanding)
                    results.put(JSONObject().put("scenario", name).put("alert", state.alert)
                        .put("transcript", state.transcript.joinToString(" ") { it.text })
                        .put("captured_peak_rms", session.capturedPeakRms).put("committed_segments", session.committedSegments)
                        .put("understanding", state.understanding!!.summary))
                    File(context.filesDir, "voice-test-result.json").writeText(JSONObject()
                        .put("native_webrtc", true).put("complete", false).put("cases", results).toString(2))
                } catch (e: Exception) {
                    val state = session.state.value
                    File(context.filesDir, "voice-test-result.json").writeText(JSONObject()
                        .put("native_webrtc", false).put("scenario", name)
                        .put("error", state.error ?: e.javaClass.simpleName)
                        .put("captured_peak_rms", session.capturedPeakRms).put("committed_segments", session.committedSegments)
                        .put("alert", state.alert ?: JSONObject.NULL)
                        .put("transcript", state.transcript.joinToString(" ") { it.text })
                        .put("understanding", state.understanding?.summary ?: "").toString(2))
                    throw e
                } finally {
                    instrumentation.runOnMainSync { session.close() }
                    assertEquals(WebRtcVoiceSession.Connection.ENDED, session.connection.value)
                    phase.writeText("closed:$name")
                }
            }
            File(context.filesDir, "voice-test-result.json").writeText(JSONObject().put("native_webrtc", true).put("complete", true).put("cases", results).toString(2))
            phase.writeText("done")
        } finally {
            keyFile.delete()
            instrumentation.runOnMainSync { activity.finish() }
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
