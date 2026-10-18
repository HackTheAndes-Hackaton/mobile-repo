package com.tiskz.app.model

import com.tiskz.app.util.PhoneNumbers
import java.util.UUID

enum class CallDirection { INCOMING, OUTGOING }

enum class CallPhase { RINGING, DIALING, ACTIVE, HOLDING, DISCONNECTED }

/** La llamada que se está mostrando en pantalla (real de Telecom o simulada para la demo). */
data class ActiveCall(
    val number: String,
    val direction: CallDirection,
    val phase: CallPhase,
    val simulated: Boolean,
    /** Nombre en los contactos del teléfono; null si el número no está guardado. */
    val callerName: String? = null,
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long = System.currentTimeMillis(),
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val blocked: Boolean = false,
    /** Marin atiende y el titular todavía no entró: micrófono silenciado, solo Cancelar o Entrar. */
    val screening: Boolean = false,
    /** Reportes de la comunidad sobre este número; null si no se consultó o no hay panel. */
    val reputation: NumberReputation? = null,
) {
    /** Lo que se muestra en grande: el nombre del contacto o, si no está guardado, el número. */
    val title: String get() = callerName ?: PhoneNumbers.display(number)
}

/** Lo que la comunidad sabe del número: solo totales, nunca quién lo reportó. */
data class NumberReputation(
    val reports: Int,
    val scam: Int,
    val mentionedIn: Int,
    val scamType: String?,
    val impersonating: String?,
) {
    val flagged: Boolean get() = reports > 0 || mentionedIn > 0

    /** "Reportado 3 veces · se hace pasar por BCP" */
    val headline: String get() = buildList {
        if (reports > 0) add(if (reports == 1) "Reportado 1 vez" else "Reportado $reports veces")
        else if (mentionedIn > 0) add("Mencionado en ${if (mentionedIn == 1) "1 estafa" else "$mentionedIn estafas"}")
        impersonating?.let { add("se hace pasar por $it") } ?: scamType?.let { add(it) }
    }.joinToString(" · ")
}
