package com.tiskz.app.call

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.tiskz.app.analysis.CallGuard
import com.tiskz.app.analysis.GuardAlert
import com.tiskz.app.analysis.GuardKind
import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.tiskz.app.analysis.AnalysisSession
import com.tiskz.app.analysis.UserDecision
import com.tiskz.app.data.AppContainer
import com.tiskz.app.data.ContactLookup
import com.tiskz.app.data.DemoScenario
import com.tiskz.app.model.ActiveCall
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallPhase
import com.tiskz.app.model.CallRecord
import com.tiskz.app.model.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fuente única de verdad de la llamada en curso.
 * Recibe llamadas reales desde [TiskzInCallService] y simuladas desde el botón de demo.
 * MVP: maneja una llamada a la vez.
 */
object CallManager {

    private lateinit var appContext: Context
    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _activeCall = MutableStateFlow<ActiveCall?>(null)
    val activeCall: StateFlow<ActiveCall?> = _activeCall.asStateFlow()

    private val session = MutableStateFlow<AnalysisSession?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val analysis: StateFlow<CallAnalysis?> by lazy {
        session
            .flatMapLatest { it?.state ?: flowOf(null) }
            .stateIn(scope, SharingStarted.Eagerly, null)
    }

    private var telecomCall: Call? = null
    private var inCallService: InCallService? = null
    private var automatic: TelecomConferenceRuntime? = null
    private val retired = mutableListOf<Pair<Long, TelecomConferenceRuntime>>()
    private val _conference = MutableStateFlow(ConferenceState())
    val conference: StateFlow<ConferenceState> = _conference.asStateFlow()

    /** Último pedido peligroso de quien llama (código, clave, Yape…), hasta que el titular lo cierre. */
    private val _guard = MutableStateFlow<GuardAlert?>(null)
    val guard: StateFlow<GuardAlert?> = _guard.asStateFlow()
    private val guardedKinds = mutableSetOf<GuardKind>()
    private val guardChecked = HashMap<String, String>()
    /** Veces que se volvió a silenciar en esta llamada: evita un ida y vuelta sin fin con el sistema. */
    private var remutes = 0

    private val telecomCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            // El cierre lo hace finish(): si aquí solo se marcara DISCONNECTED,
            // finish() lo vería como ya cerrado y no guardaría el historial.
            val phase = phaseOf(call)
            if (phase == CallPhase.DISCONNECTED) finish() else updatePhase(phase)
        }

        // Algunos equipos entregan el número después de que la llamada empieza a sonar.
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            val number = numberOf(details)
            val current = _activeCall.value ?: return
            if (number.isNotEmpty() && number != current.number) {
                Log.i(TAG, "Número actualizado por Telecom (…${number.takeLast(3)})")
                _activeCall.value = current.copy(number = number, callerName = null)
                resolveCallerName(current.id, number)
            }
        }
    }

    fun init(context: Context, appContainer: AppContainer) {
        appContext = context.applicationContext
        container = appContainer
        // El aviso de llamada sigue cada cambio: suena, contesta marin, espacio seguro o estafa, en llamada.
        scope.launch {
            combine(_activeCall, analysis, _conference, container.settings.settings) { call, analysis, conference, settings ->
                CallNotifications.noticeFor(call, analysis, conference, settings.answerThreshold)
            }.collect { CallNotifications.render(appContext, it) }
        }
        // Protección durante toda la llamada, también después de que el titular entra.
        scope.launch { analysis.collect { it?.let(::guardAgainst) } }
        // Llamadas que no se pudieron enviar al panel la vez anterior.
        container.archive.flush()
    }

    // --- Entrada desde Telecom ---

    internal fun attachService(service: InCallService?) {
        if (service == null && automatic != null) {
            automatic?.cancelAutomation()
            finish()
        }
        inCallService = service
    }

    internal fun onTelecomCallAdded(call: Call) {
        retired.removeAll { it.first < SystemClock.elapsedRealtime() }
        if (retired.any { it.second.disconnectLateAgent(call) }) return
        automatic?.let { it.add(call); return }
        if (telecomCall != null || _activeCall.value?.phase?.let { it != CallPhase.DISCONNECTED } == true) {
            // Llamada en espera: el MVP solo muestra una llamada; la segunda se rechaza.
            if (stateOf(call) == Call.STATE_RINGING) call.reject(false, null)
            return
        }
        val number = numberOf(call.details)
        val direction = if (call.details.callDirection == Call.Details.DIRECTION_OUTGOING) {
            CallDirection.OUTGOING
        } else {
            CallDirection.INCOMING
        }
        Log.i(
            TAG,
            "Llamada $direction · número ${if (number.isEmpty()) "vacío" else "…${number.takeLast(3)}"} " +
                "· presentación ${call.details.handlePresentation}",
        )
        if (direction == CallDirection.INCOMING && container.blocklist.isBlocked(number)) {
            call.reject(false, null)
            return
        }
        telecomCall = call
        // Los contactos pasan sin fricción: suenan normal, sin marin ni análisis.
        val contactName = if (direction == CallDirection.INCOMING) ContactLookup.findName(appContext, number) else null
        val known = contactName != null
        val attempt = direction == CallDirection.INCOMING && !known && stateOf(call) == Call.STATE_RINGING &&
            ConferenceTelecom.gate(appContext, container, call.details.accountHandle).allowed
        start(ActiveCall(number = number, direction = direction, phase = phaseOf(call), simulated = false,
            callerName = contactName, screening = attempt), null,
            attempt || known || (direction == CallDirection.INCOMING && container.settings.current.automaticConferenceEnabled))
        if (attempt) {
            inCallService?.setMuted(true)
            try {
                automatic = TelecomConferenceRuntime(appContext, container, call,
                    sessionOpened = { session.value = it },
                    stateChanged = { state ->
                        _conference.value = state
                        _activeCall.update { active -> active?.copy(
                            blocked = active.blocked || container.blocklist.isBlocked(active.number),
                            endReason = state.endReason ?: active.endReason,
                        ) }
                    },
                    phaseChanged = { if (it == CallPhase.DISCONNECTED) finish() else updatePhase(it) },
                    ended = ::finish,
                    showCallScreen = ::showCallScreen,
                )
                automatic?.start()
            } catch (_: Exception) {
                automatic?.dispose()
                automatic = null
                _conference.value = ConferenceState(ConferencePhase.FAILED, "Cambió la SIM o la conexión. Contesta manualmente.")
                call.registerCallback(telecomCallback)
            }
        } else {
            call.registerCallback(telecomCallback)
            if (direction == CallDirection.INCOMING && !known && container.settings.current.automaticConferenceEnabled) {
                _conference.value = ConferenceState(ConferencePhase.FAILED,
                    "Protección automática no disponible para esta SIM. Revisa la configuración; contesta manualmente.")
            }
        }
    }

    internal fun onTelecomCallRemoved(call: Call) {
        automatic?.let { it.remove(call); return }
        if (call != telecomCall) return
        call.unregisterCallback(telecomCallback)
        telecomCall = null
        finish()
    }

    internal fun onAudioStateChanged(muted: Boolean, speaker: Boolean) {
        _activeCall.update { it?.copy(muted = muted, speaker = speaker) }
        // Unir la conferencia puede reactivar el micrófono: mientras atiende marin, sigue silenciado.
        if (!muted && _activeCall.value?.screening == true && remutes < MAX_REMUTES) {
            remutes++
            inCallService?.setMuted(true)
        }
    }

    // --- Demo ---

    fun simulateIncoming(number: String, scenario: DemoScenario) {
        val current = _activeCall.value
        if (current != null && current.phase != CallPhase.DISCONNECTED) return
        start(
            ActiveCall(number = number, direction = CallDirection.INCOMING, phase = CallPhase.RINGING, simulated = true, screening = true),
            scenario,
        )
    }

    // --- Acciones de la UI ---

    fun answer() {
        // Tomarla uno mismo mientras suena: deja de estar en modo marin antes de cancelarlo,
        // para que no se vuelva a silenciar el micrófono.
        _activeCall.value?.takeIf { it.screening }?.let {
            _activeCall.value = it.copy(screening = false)
            restoreCallVolume()
            if (!it.simulated) inCallService?.setMuted(false)
        }
        // Si el titular toma la llamada mientras se prepara, no se añade al agente más tarde.
        automatic?.cancelPreparation()
        session.value?.send(UserDecision.ANSWER)
        val call = _activeCall.value ?: return
        if (call.simulated) updatePhase(CallPhase.ACTIVE)
        else if (telecomCall?.let(::stateOf) == Call.STATE_HOLDING) telecomCall?.unhold()
        else telecomCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /**
     * El titular entra a la llamada que atendía marin. El agente no sale de la conferencia:
     * sigue escuchando y analizando hasta que se cuelgue.
     */
    fun join() {
        val call = _activeCall.value ?: return
        if (!call.screening || call.phase == CallPhase.DISCONNECTED) return
        _activeCall.value = call.copy(screening = false)
        restoreCallVolume()
        session.value?.send(UserDecision.ANSWER)
        if (call.simulated) { updatePhase(CallPhase.ACTIVE); return }
        inCallService?.setMuted(false)
        // Si la conferencia no llegó a formarse, se recupera la llamada original.
        when (telecomCall?.let(::stateOf)) {
            Call.STATE_RINGING -> telecomCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
            Call.STATE_HOLDING -> if (_conference.value.phase != ConferencePhase.CONNECTED) telecomCall?.unhold()
        }
    }

    fun reject() {
        automatic?.cancelPreparation()
        session.value?.send(UserDecision.REJECT)
        val call = _activeCall.value ?: return
        if (call.simulated) finish() else telecomCall?.reject(false, null)
    }

    fun hangUp() {
        session.value?.send(UserDecision.HANG_UP)
        val call = _activeCall.value ?: return
        if (call.simulated) finish() else automatic?.let { it.hangUp(); finish() } ?: telecomCall?.disconnect()
    }

    fun blockAndHangUp() {
        session.value?.send(UserDecision.BLOCK)
        val call = _activeCall.value ?: return
        container.blocklist.block(call.number)
        _activeCall.value = call.copy(blocked = true)
        when {
            call.simulated -> finish()
            automatic != null -> { automatic?.hangUp(); finish() }
            call.phase == CallPhase.RINGING -> telecomCall?.reject(false, null)
            else -> telecomCall?.disconnect()
        }
    }

    fun dismissGuard() {
        _guard.value = null
        CallNotifications.cancelGuard(appContext)
    }

    /** Avisa una vez por tipo de peligro cuando quien llama pide códigos, claves, dinero, etc. */
    private fun guardAgainst(analysis: CallAnalysis) {
        val call = _activeCall.value ?: return
        if (call.direction != CallDirection.INCOMING || call.phase == CallPhase.DISCONNECTED) return
        for (line in analysis.transcript) {
            if (line.speaker != Speaker.CALLER) continue
            // Cada actualización trae toda la conversación: solo se revisa lo nuevo o lo que cambió
            // (una transcripción parcial que se completa), así una llamada larga no se vuelve más lenta.
            if (guardChecked.put(line.id, line.text) == line.text) continue
            val kind = CallGuard.check(line.text) ?: continue
            if (!guardedKinds.add(kind)) continue
            val alert = GuardAlert(kind, line.text, line.id)
            _guard.value = alert
            vibrate()
            CallNotifications.showGuard(appContext, alert)
        }
    }

    private fun vibrate() = runCatching {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250, 120, 400), -1))
    }

    fun markNotScam() {
        session.value?.markNotScam()
    }

    fun toggleMute() {
        val call = _activeCall.value ?: return
        if (call.simulated) {
            _activeCall.value = call.copy(muted = !call.muted)
        } else {
            inCallService?.setMuted(!call.muted)
        }
    }

    @Suppress("DEPRECATION") // setAudioRoute sigue funcionando; la API nueva exige API 34.
    fun toggleSpeaker() {
        val call = _activeCall.value ?: return
        if (call.simulated) {
            _activeCall.value = call.copy(speaker = !call.speaker)
        } else {
            inCallService?.setAudioRoute(
                if (call.speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER
            )
        }
    }

    fun dismissEnded() {
        if (_activeCall.value?.phase == CallPhase.DISCONNECTED) clear()
    }

    // --- Interno ---

    private fun start(call: ActiveCall, forcedScenario: DemoScenario?, automaticCall: Boolean = false) {
        session.value?.close()
        _activeCall.value = call
        _conference.value = ConferenceState()
        _guard.value = null
        guardedKinds.clear()
        guardChecked.clear()
        remutes = 0
        resolveCallerName(call.id, call.number)
        if (call.direction == CallDirection.INCOMING && call.callerName == null) checkReputation(call)
        session.value = if (call.direction == CallDirection.INCOMING && !automaticCall && !call.simulated) {
            container.openAnalysisSession(call.number, forcedScenario)
        } else {
            null
        }
        if (call.simulated) scope.launch {
            // En la demo suena unos segundos antes de que marin conteste, como una llamada real.
            delay(SIMULATED_RING_MS)
            if (_activeCall.value?.id == call.id && _activeCall.value?.phase != CallPhase.DISCONNECTED) {
                session.value = container.openAnalysisSession(call.number, forcedScenario)
            }
        }
        // Una llamada entrante no tapa la pantalla: aparece como aviso arriba y el titular decide
        // si abrirla. Las salientes (o sin permiso de avisos) abren la pantalla de llamada.
        val notices = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        if (call.direction == CallDirection.OUTGOING || !notices) {
            appContext.startActivity(
                Intent(appContext, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else if (!call.simulated && call.phase == CallPhase.RINGING && !CallNotifications.screenVisible) {
            // Solo el aviso queda a la vista; la ventana invisible evita que HyperOS cambie de app de teléfono.
            RingingAnchorActivity.show(appContext)
        }
    }

    /** Mientras suena: si la comunidad ya reportó el número, se avisa antes de contestar. */
    private fun checkReputation(call: ActiveCall) {
        scope.launch {
            val reputation = container.reputation.lookup(call.number) ?: return@launch
            if (!reputation.flagged) return@launch
            val current = _activeCall.value
            if (current?.id == call.id && current.phase != CallPhase.DISCONNECTED) {
                _activeCall.value = current.copy(reputation = reputation)
            }
        }
    }

    /** Busca el número en los contactos y, si aparece, muestra el nombre en vez del número. */
    private fun resolveCallerName(callId: String, number: String) {
        if (number.isEmpty()) return
        scope.launch {
            val name = withContext(Dispatchers.IO) { ContactLookup.findName(appContext, number) } ?: return@launch
            val current = _activeCall.value
            if (current?.id != callId || current.number != number) return@launch
            _activeCall.value = current.copy(callerName = name)
        }
    }

    /**
     * HyperOS (Xiaomi) pasa la llamada al teléfono del sistema si la app de teléfono no tiene una ventana.
     * Con el asistente atendiendo basta la ventana invisible; si el titular está en la llamada, se abre la pantalla.
     */
    private fun showCallScreen() {
        if (CallNotifications.screenVisible) return
        // Mientras atiende el asistente solo se ve el aviso: la ventana invisible basta para HyperOS.
        if (_activeCall.value?.screening == true) { RingingAnchorActivity.show(appContext); return }
        runCatching {
            appContext.startActivity(Intent(appContext, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun updatePhase(phase: CallPhase) {
        val current = _activeCall.value
        // Contestada desde fuera de la app (auricular, Bluetooth): igual se muestra Tiskz.
        if (phase == CallPhase.ACTIVE && current?.phase == CallPhase.RINGING && !current.simulated) showCallScreen()
        if (current?.screening == true && !current.simulated && phase != CallPhase.RINGING) {
            // Contestó marin, no el titular: no se le escucha ni oye la conversación hasta que entre.
            inCallService?.setMuted(true)
            lowerCallVolume()
        }
        _activeCall.update { call ->
            when {
                call == null -> null
                phase == CallPhase.ACTIVE && call.answeredAt == null ->
                    call.copy(phase = phase, answeredAt = System.currentTimeMillis())
                else -> call.copy(phase = phase)
            }
        }
    }

    private fun finish() {
        val call = _activeCall.value ?: return
        if (call.phase == CallPhase.DISCONNECTED) return
        val ended = call.copy(phase = CallPhase.DISCONNECTED, endedAt = System.currentTimeMillis(), screening = false)
        _activeCall.value = ended
        restoreCallVolume()
        val finalAnalysis = session.value?.state?.value
        session.value?.close()
        // La conversación queda en Recientes y se guarda en el panel (si la persona no lo desactivó en Ajustes),
        // también la de las llamadas que atendió el asistente.
        val screened = call.screening || automatic != null ||
            finalAnalysis?.transcript?.any { it.speaker == Speaker.AGENT } == true
        val record = CallRecord.from(ended, finalAnalysis, screened)
        container.history.add(record)
        CallNotifications.showAutomaticEnd(appContext, record)
        if (call.direction == CallDirection.INCOMING) container.archive.upload(record)
        automatic?.let {
            it.dispose()
            retired.add(SystemClock.elapsedRealtime() + 60_000 to it)
            while (retired.size > 8) retired.removeAt(0)
            automatic = null
        }
        telecomCall?.unregisterCallback(telecomCallback)
        telecomCall = null
        CallNotifications.cancelGuard(appContext)
        scope.launch {
            delay(ENDED_SCREEN_MS)
            if (_activeCall.value?.id == ended.id) clear()
        }
    }

    private fun clear() {
        _activeCall.value = null
        session.value = null
    }

    private var savedCallVolume: Int? = null

    private fun lowerCallVolume() = runCatching {
        if (savedCallVolume != null) return@runCatching
        val audio = appContext.getSystemService(AudioManager::class.java)
        savedCallVolume = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, audio.getStreamMinVolume(AudioManager.STREAM_VOICE_CALL), 0)
    }

    private fun restoreCallVolume() = runCatching {
        val volume = savedCallVolume ?: return@runCatching
        savedCallVolume = null
        appContext.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_VOICE_CALL, volume, 0)
    }

    private fun numberOf(details: Call.Details): String =
        details.handle?.schemeSpecificPart.orEmpty()

    private fun stateOf(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else @Suppress("DEPRECATION") call.state

    private fun phaseOf(call: Call): CallPhase = when (stateOf(call)) {
        Call.STATE_RINGING -> CallPhase.RINGING
        Call.STATE_ACTIVE -> CallPhase.ACTIVE
        Call.STATE_HOLDING -> CallPhase.HOLDING
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> CallPhase.DISCONNECTED
        else -> CallPhase.DIALING
    }

    private const val ENDED_SCREEN_MS = 2500L
    private const val SIMULATED_RING_MS = 4000L
    private const val MAX_REMUTES = 20
    private const val TAG = "Tiskz"
}
