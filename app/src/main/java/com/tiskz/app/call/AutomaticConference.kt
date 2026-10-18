package com.tiskz.app.call

data class ConferenceGate(
    val enabled: Boolean = false,
    val costsConfirmed: Boolean = false,
    val defaultDialer: Boolean = false,
    val callPermission: Boolean = false,
    val credentialsReady: Boolean = false,
    val confirmedAccount: String = "",
    val incomingAccount: String = "",
    val confirmedRoute: String = "",
    val currentRoute: String = "",
) {
    val allowed: Boolean get() = enabled && costsConfirmed && defaultDialer && callPermission &&
        credentialsReady && confirmedAccount.isNotBlank() && confirmedAccount == incomingAccount &&
        currentRoute.isNotBlank() && currentRoute == confirmedRoute
}

enum class ConferencePhase {
    IDLE, CONNECTING_ALERTS, ANSWERING, HOLDING, DIALING, WAITING_MERGE, MERGING, CONNECTED, FAILED, ENDED,
}

data class ConferenceState(
    val phase: ConferencePhase = ConferencePhase.IDLE,
    val message: String = "",
    val callerConnected: Boolean = false,
    val agentConnected: Boolean = false,
    val mergeRequested: Boolean = false,
    val confirmed: Boolean = false,
    val diagnostic: String? = null,
) {
    val running: Boolean get() = phase !in setOf(ConferencePhase.IDLE, ConferencePhase.FAILED, ConferencePhase.ENDED)
}

enum class LegPhase { RINGING, DIALING, ACTIVE, HOLDING, DISCONNECTED }

data class ConferenceLeg(
    val id: String,
    val phase: LegPhase,
    val account: String = "",
    val number: String = "",
    val outgoing: Boolean = false,
    val canHold: Boolean = false,
    val parent: String? = null,
    val children: Set<String> = emptySet(),
    val conferenceable: Set<String> = emptySet(),
    val agentRequest: String? = null,
    /** CAPABILITY_MERGE_CONFERENCE: la red une las llamadas aunque no las liste como conferenciables (VoLTE/IMS). */
    val canMerge: Boolean = false,
    /** PROPERTY_CONFERENCE: es la llamada de conferencia creada por la red. */
    val isConference: Boolean = false,
)

/** Efectos de Telecom separados para probar carreras, cancelaciones y fallos sin llamar. */
interface ConferenceActions {
    fun answer(id: String)
    fun hold(id: String)
    fun dial(account: String, requestId: String)
    fun merge(first: String, second: String)
    fun mergeConference(id: String)
    fun disconnect(id: String)
    fun unhold(id: String)
    fun closeAlerts()
}

/** Una sola tentativa por llamada. Solo el propietario puede armarla, antes de recibirla. */
class AutomaticConference(
    private val callerId: String,
    private val account: String,
    private val requestId: String,
    private val target: ConferenceTarget,
    private val actions: ConferenceActions,
    private val authorized: () -> Boolean,
    private val clock: () -> Long,
    private val changed: (ConferenceState) -> Unit,
) {
    var state = ConferenceState()
        private set
    private var deadline = Long.MAX_VALUE
    private var legs = emptyList<ConferenceLeg>()
    private var agentId: String? = null
    private var conferenceId: String? = null
    private var dialRequested = false
    private var terminal = false
    private val disconnectRequested = mutableSetOf<String>()
    private var unholdRequested = false
    private var callerConnected = false
    private var agentConnected = false
    private var mergeRequested = false
    private var confirmed = false
    /** En VoLTE la red reemplaza las dos patas por una llamada de conferencia nueva, sin hijos visibles. */
    private var networkConference = false

    fun start(initial: List<ConferenceLeg>) {
        if (state.phase != ConferencePhase.IDLE) return
        legs = initial
        if (!authorized() || initial.size != 1 || initial.single().id != callerId ||
            initial.single().phase != LegPhase.RINGING || initial.single().account != account) {
            fail("La protección automática no está autorizada para esta línea.")
            return
        }
        move(ConferencePhase.CONNECTING_ALERTS, "Conectando alertas antes de contestar…", 12_000)
    }

    fun alertsReady() {
        if (state.phase != ConferencePhase.CONNECTING_ALERTS || !checkAuthorization()) return
        val caller = caller() ?: return end()
        move(ConferencePhase.ANSWERING, "Contestando para conectar a marin…", 8_000)
        if (caller.phase == LegPhase.RINGING) effect { actions.answer(callerId) }
        advance()
    }

    fun alertsLost() {
        if (!terminal) fail("Se perdió el canal de alertas. Continúa la llamada desde Tiskz.")
    }

    fun callsChanged(current: List<ConferenceLeg>) {
        legs = current
        // Una creación tardía después de cancelar tampoco puede dejar una llamada de pago huérfana.
        if (terminal) {
            current.filter { isRequestedAgent(it) && it.phase != LegPhase.DISCONNECTED }
                .forEach { disconnectOnce(it.id) }
            if (state.phase == ConferencePhase.FAILED) restoreCaller()
            return
        }
        if (dialRequested && agentId == null) {
            agentId = current.firstOrNull(::isRequestedAgent)?.id
        }
        val parent = current.firstOrNull {
            it.phase == LegPhase.ACTIVE && agentId != null &&
                (it.children.containsAll(setOf(callerId, agentId!!)) ||
                    (caller()?.parent == it.id && agent()?.parent == it.id))
        }
        val network = if (parent == null && state.phase == ConferencePhase.MERGING) current.firstOrNull {
            it.phase == LegPhase.ACTIVE && it.isConference && it.id != callerId
        } else null
        if ((parent ?: network) != null && state.phase in setOf(ConferencePhase.MERGING, ConferencePhase.CONNECTED)) {
            conferenceId = (parent ?: network)!!.id
            if (parent == null) networkConference = true
            if (state.phase != ConferencePhase.CONNECTED) {
                confirmed = true
                // Sin límite: marin escucha toda la llamada, también después de que entre el titular.
                move(ConferencePhase.CONNECTED, "Marin está en la llamada y analiza la conversación")
            }
        }
        if (state.phase == ConferencePhase.CONNECTED) {
            val conference = current.firstOrNull { it.id == conferenceId && it.phase != LegPhase.DISCONNECTED }
            // Sin patas visibles solo se puede seguir la conferencia misma.
            if (networkConference) { if (conference == null) end(); return }
            if (conference == null || caller()?.phase == LegPhase.DISCONNECTED ||
                (callerId !in conference.children && caller()?.parent != conference.id)) end()
            else if (agent()?.phase == LegPhase.DISCONNECTED ||
                (agentId !in conference.children && agent()?.parent != conference.id)) {
                fail("Marin terminó la llamada. Continúa la conversación desde Tiskz.")
            }
            return
        }
        val caller = caller()
        if ((caller == null || caller.phase == LegPhase.DISCONNECTED) && state.phase != ConferencePhase.MERGING) {
            end()
            return
        }
        if (agentId != null && (agent() == null || agent()?.phase == LegPhase.DISCONNECTED) &&
            state.phase != ConferencePhase.MERGING) {
            fail("Vapi no respondió o terminó la llamada. Retoma la conversación.")
            return
        }
        advance()
    }

    fun tick() {
        if (terminal) return
        if (!checkAuthorization()) return
        if (clock() >= deadline) {
            fail(when (state.phase) {
                ConferencePhase.CONNECTING_ALERTS -> "No se conectaron las alertas. Puedes contestar manualmente."
                ConferencePhase.DIALING -> "Vapi no respondió a tiempo. Retoma la llamada original."
                ConferencePhase.MERGING, ConferencePhase.WAITING_MERGE -> "La red no confirmó la conferencia. Retoma la llamada original."
                else -> "Android no completó la conexión. Puedes continuar manualmente."
            })
        }
    }

    /** Cancela la preparación y conserva la llamada original. No se usa para colgar toda la conferencia. */
    fun cancel() { if (!terminal) fail("Automatización cancelada. Continúa la llamada desde Tiskz.") }

    /** El botón Colgar termina todas las patas propias, incluso una conferencia aún en formación. */
    fun hangUp() {
        terminal = true
        move(ConferencePhase.ENDED, "Llamada finalizada")
        (listOfNotNull(conferenceId, callerId, agentId) + legs.filter(::isRequestedAgent).map { it.id })
            .distinct().forEach(::disconnectOnce)
        actions.closeAlerts()
    }

    val displayCallId: String get() = if (state.phase == ConferencePhase.CONNECTED || caller() == null)
        conferenceId ?: callerId else callerId

    private fun caller() = legs.firstOrNull { it.id == callerId }
    private fun agent() = legs.firstOrNull { it.id == agentId }
    private fun isRequestedAgent(leg: ConferenceLeg): Boolean = dialRequested && leg.id != callerId &&
        leg.outgoing && leg.account == account && target.matches(leg.number) &&
        leg.agentRequest == requestId

    private fun advance() {
        if (terminal || !checkAuthorization()) return
        when (state.phase) {
            ConferencePhase.ANSWERING -> if (caller()?.phase == LegPhase.ACTIVE) {
                callerConnected = true
                if (caller()?.canHold != true) return fail("La red no permite poner esta llamada en espera. Continúa manualmente.")
                move(ConferencePhase.HOLDING, "Preparando la segunda llamada…", 8_000)
                effect { actions.hold(callerId) }
            }
            ConferencePhase.HOLDING -> if (caller()?.phase == LegPhase.HOLDING && !dialRequested) {
                dialRequested = true // Antes del efecto: Telecom puede devolver eventos de inmediato.
                move(ConferencePhase.DIALING, "Llamando a marin con la SIM confirmada…", 25_000)
                effect { actions.dial(account, requestId) }
            }
            ConferencePhase.DIALING, ConferencePhase.WAITING_MERGE -> {
                val agent = agent() ?: return
                if (agent.phase == LegPhase.ACTIVE) {
                    agentConnected = true
                    if (state.phase == ConferencePhase.DIALING) {
                        move(ConferencePhase.WAITING_MERGE, "Android conectó la llamada a Vapi · esperando permiso para unir…", 10_000)
                    }
                    val caller = caller() ?: return end()
                    val pair = when {
                        agent.id in caller.conferenceable -> caller.id to agent.id
                        caller.id in agent.conferenceable -> agent.id to caller.id
                        else -> null // Espera onConferenceableCallsChanged; ACTIVE no basta.
                    }
                    if (pair != null) {
                        mergeRequested = true
                        move(ConferencePhase.MERGING, "Uniendo las llamadas; esperando confirmación de la red…", 15_000)
                        effect { actions.merge(pair.first, pair.second) }
                    } else if (agent.canMerge) {
                        // Igual que el botón "Unir" del teléfono: en VoLTE la red no lista llamadas conferenciables.
                        mergeRequested = true
                        move(ConferencePhase.MERGING, "Uniendo las llamadas; esperando confirmación de la red…", 15_000)
                        effect { actions.mergeConference(agent.id) }
                    }
                }
            }
            else -> Unit
        }
    }

    private fun checkAuthorization(): Boolean {
        if (authorized()) return true
        fail("La protección automática se desactivó. Continúa manualmente.")
        return false
    }

    private fun effect(action: () -> Unit) {
        try { action() } catch (_: Exception) {
            fail("Android o la operadora rechazaron la conexión. Continúa manualmente.")
        }
    }

    private fun fail(message: String) {
        if (terminal) return
        terminal = true
        move(ConferencePhase.FAILED, message)
        // No se cuelga a quien llamó ni se cambia la ruta del audio/micrófono.
        (listOfNotNull(agentId) + legs.filter(::isRequestedAgent).map { it.id }).distinct()
            .forEach(::disconnectOnce)
        restoreCaller()
        actions.closeAlerts()
    }

    private fun end() {
        terminal = true
        move(ConferencePhase.ENDED, "Llamada finalizada")
        agentId?.let(::disconnectOnce)
        actions.closeAlerts()
    }

    private fun move(phase: ConferencePhase, message: String, timeout: Long = Long.MAX_VALUE) {
        state = ConferenceState(phase, message, callerConnected, agentConnected, mergeRequested, confirmed)
        deadline = if (timeout == Long.MAX_VALUE) timeout else clock() + timeout
        changed(state)
    }

    private fun disconnectOnce(id: String) {
        if (disconnectRequested.add(id)) runCatching { actions.disconnect(id) }
    }

    private fun restoreCaller() {
        if (!unholdRequested && caller()?.phase == LegPhase.HOLDING) {
            unholdRequested = true
            runCatching { actions.unhold(callerId) }
        }
    }
}
