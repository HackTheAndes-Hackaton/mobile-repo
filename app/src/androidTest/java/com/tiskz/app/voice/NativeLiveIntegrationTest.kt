package com.tiskz.app.voice

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.tiskz.app.MainActivity
import com.tiskz.app.model.Speaker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Solo WAV ficticio inyectado en emulador, sin captura del micrófono del anfitrión. */
@RunWith(AndroidJUnit4::class)
class NativeLiveIntegrationTest {
    @get:Rule val microphone = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    @Test fun marinAudioAndBothAgents() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val key = File(context.filesDir, "voice-test-code")
        org.junit.Assume.assumeTrue("Falta código privado para prueba externa opcional.", key.isFile)
        val scenario = InstrumentationRegistry.getArguments().getString("voiceScenario", "legitimo")
        require(scenario in setOf("riesgo", "legitimo"))
        val expected = scenario == "riesgo"
        val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val session = WebRtcVoiceSession(context, client, "https://api.maritime.sh/a/ebf0eb81-42c0-4437-a044-1af84af1b755", key.readText().trim(), assistant = true)
        val phase = File(context.filesDir, "voice-test-phase")
        val complete = File(context.filesDir, "voice-test-audio-complete")
        try {
            session.start(true)
            withTimeout(50000) {
                while (session.connection.value != WebRtcVoiceSession.Connection.STREAMING || session.receivedAudioEnergy() <= 0.0) {
                    check(session.state.value.error == null) { session.state.value.error.orEmpty() }; delay(100)
                }
            }
            assertTrue(session.assistantStarted)
            complete.delete();phase.writeText("ready:$scenario")
            withTimeout(80000) { while (!complete.isFile) { check(session.state.value.error == null) { session.state.value.error.orEmpty() }; delay(100) } }
            val ending = if (expected) "No hables con nadie" else "hasta mañana"
            withTimeout(45000) {
                while (true) {
                    val state = session.state.value
                    check(state.error == null) { state.error.orEmpty() }
                    if (state.alert == expected && state.understanding?.mode == "extractivo_sin_identidades_v1" && state.understandingRevision == state.alertRevision && state.transcript.filter { it.speaker != Speaker.AGENT }.joinToString("") { it.text }.contains(ending, true)) break
                    delay(100)
                }
            }
            val state = session.state.value
            val energy = session.receivedAudioEnergy()
            assertTrue(energy > 0)
            assertEquals(0, session.committedSegments)
            assertTrue(state.transcript.any { it.speaker == Speaker.AGENT && Regex("PhoneWard|Tiskz", RegexOption.IGNORE_CASE).containsMatchIn(it.text) })
            assertFalse(Regex("\\b(Ana|Enrique)\\b", RegexOption.IGNORE_CASE).containsMatchIn(state.understanding!!.summary))
            val caller = state.transcript.filter { it.speaker != Speaker.AGENT }.joinToString("") { it.text }
            assertTrue(state.understanding!!.evidence.all { caller.contains(it.quote) })
            instrumentation.runOnMainSync { session.close() }
            withTimeout(5000) { while (session.connection.value != WebRtcVoiceSession.Connection.ENDED) delay(50) }
            assertTrue("El proveedor debe confirmar el cierre", session.providerCloseConfirmed)
            val audio = context.getSystemService(AudioManager::class.java)
            assertTrue("El cierre debe liberar el micrófono", audio.activeRecordingConfigurations.isEmpty())
            val result = JSONObject().put("complete", true).put("native_webrtc", true).put("model", "gpt-live-1").put("voice", "marin")
                .put("cases", JSONArray().put(JSONObject().put("scenario", scenario).put("alert", state.alert).put("caller_transcript", caller)
                    .put("assistant_transcript", state.transcript.filter { it.speaker == Speaker.AGENT }.joinToString("") { it.text })
                    .put("received_audio_energy", energy).put("legacy_commits", session.committedSegments).put("understanding", state.understanding!!.summary)
                    .put("mode", state.understanding!!.mode).put("revision", state.alertRevision).put("microphone_released", true).put("provider_close_confirmed", session.providerCloseConfirmed)))
            File(context.filesDir, "voice-test-result.json").writeText(result.toString())
        } catch (e: Throwable) {
            File(context.filesDir, "voice-test-result.json").writeText(JSONObject().put("complete", false).put("error", e.javaClass.simpleName)
                .put("detail", e.message).put("assistant_started", session.assistantStarted).put("state_error", session.state.value.error)
                .put("transcript", session.state.value.transcript.joinToString("\n") { it.text }).put("alert", session.state.value.alert).toString())
            throw e
        } finally {
            instrumentation.runOnMainSync { session.close(); activity.finish() };key.delete();phase.delete();complete.delete()
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }
}
