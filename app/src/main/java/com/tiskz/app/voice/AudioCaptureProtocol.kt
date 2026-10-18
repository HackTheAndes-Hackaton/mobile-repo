package com.tiskz.app.voice

import java.time.Instant
import java.util.UUID
import org.json.JSONObject

internal data class AudioCaptureRequest(val id: String, val revision: Int, val expiresAt: Long)
internal data class AudioCaptureResult(val id: String, val saved: Boolean, val fragmentId: String?, val reason: String)

/** Solo ejecuta órdenes acotadas de la sesión propia; nunca URLs ni código remoto. */
internal class AudioCaptureProtocol(
    private val sessionId: String,
    private val consent: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val requests = linkedMapOf<String, Int>()
    private val completed = mutableMapOf<String, String>()
    fun savedFragment(requestId: String): String? = completed[requestId]

    fun request(raw: String): AudioCaptureRequest? = runCatching {
        val json = JSONObject(raw)
        if (!consent || json.optString("type") != "audio.capture_requested" || json.optString("session_id") != sessionId) return null
        val id = json.getString("request_id")
        if (UUID.fromString(id).toString() != id || id in requests || requests.size >= 3) return null
        val revision = json.getInt("revision")
        val expires = Instant.parse(json.getString("expires_at")).toEpochMilli()
        if (revision <= 0 || json.getInt("window_ms") != 10000 || expires <= now() || expires - now() > 60000) return null
        requests[id] = revision
        AudioCaptureRequest(id, revision, expires)
    }.getOrNull()

    fun result(raw: String): AudioCaptureResult? = runCatching {
        val json = JSONObject(raw)
        if (json.optString("type") != "audio.capture_result" || json.optString("session_id") != sessionId) return null
        val id = json.getString("request_id")
        if (requests[id] != json.getInt("revision") || id in completed) return null
        when (json.getString("status")) {
            "saved" -> {
                val fragment = json.getString("fragment_id")
                if (!fragment.matches(Regex("clip_[a-f0-9]{48}"))) return null
                completed[id] = fragment
                AudioCaptureResult(id, true, fragment, "")
            }
            "failed" -> AudioCaptureResult(id, false, null, json.optString("reason"))
            else -> null
        }
    }.getOrNull()
}
