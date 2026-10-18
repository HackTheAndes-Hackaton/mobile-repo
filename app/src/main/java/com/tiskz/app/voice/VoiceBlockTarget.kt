package com.tiskz.app.voice

import com.tiskz.app.util.PhoneNumbers

/** El destino sale de la llamada original de Telecom, nunca de un número enviado por WebSocket. */
internal fun voiceBlockTarget(incomingNumber: String?, presentationAllowed: Boolean, agentNumber: String): String? {
    if (!presentationAllowed || incomingNumber.isNullOrBlank()) return null
    if (!Regex("^\\+?[0-9 ()-]+$").matches(incomingNumber)) return null
    val digits = PhoneNumbers.digits(incomingNumber)
    if (digits.length !in 9..15 || digits.all { it == '0' }) return null
    if (PhoneNumbers.matchKey(incomingNumber) == PhoneNumbers.matchKey(agentNumber)) return null
    return incomingNumber
}
