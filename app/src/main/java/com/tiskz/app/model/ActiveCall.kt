package com.tiskz.app.model

import java.util.UUID

enum class CallDirection { INCOMING, OUTGOING }

enum class CallPhase { RINGING, DIALING, ACTIVE, HOLDING, DISCONNECTED }

/** La llamada que se está mostrando en pantalla (real de Telecom o simulada para la demo). */
data class ActiveCall(
    val number: String,
    val direction: CallDirection,
    val phase: CallPhase,
    val simulated: Boolean,
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long = System.currentTimeMillis(),
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val blocked: Boolean = false,
)
