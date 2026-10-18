package com.tiskz.app.voice

import org.json.JSONObject

data class PhoneHangup(val callId: String, val requestId: String, val revision: Int, val reason: String = "risk_detected", val blockNumber: Boolean = false)

/** Solo una orden autenticada de la llamada vinculada puede cerrar Telecom. */
internal class PhoneCallControl {
    private val uuid = Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")
    private var callId: String? = null
    private var revision = -1
    private var consumed = false

    fun accept(event: JSONObject): PhoneHangup? {
        when (event.optString("type")) {
            "phone.call" -> {
                val next = event.optString("call_id")
                if (callId == null && uuid.matches(next)) { callId = next; revision = -1; consumed = false }
            }
            "call.ended" -> if (event.optString("session_id") == callId) callId = null
            "alert.updated" -> if (event.optString("session_id") == callId) {
                revision = maxOf(revision, event.optInt("revision", -1))
            }
            "call.hangup_requested" -> {
                val id = event.optString("request_id")
                val nextRevision = (event.opt("revision") as? Number)?.toDouble() ?: return null
                if (!consumed && callId != null && event.optString("session_id") == callId &&
                    event.optString("call_id") == callId && event.optString("reason") in setOf("risk_detected", "agent_ended", "voice_match") &&
                    uuid.matches(id) && nextRevision.isFinite() && nextRevision >= maxOf(if (event.optString("reason") == "risk_detected") 1 else 0, revision) &&
                    nextRevision <= Int.MAX_VALUE && nextRevision % 1 == 0.0) {
                    consumed = true
                    val reason = event.optString("reason")
                    return PhoneHangup(requireNotNull(callId), id, nextRevision.toInt(), reason,
                        blockNumber = reason == "voice_match" && event.opt("block_number") == true)
                }
            }
        }
        return null
    }
}
