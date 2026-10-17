package com.tiskz.app.call

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.tiskz.app.analysis.AnalysisSession
import com.tiskz.app.analysis.UserDecision
import com.tiskz.app.data.AppContainer
import com.tiskz.app.data.DemoScenario
import com.tiskz.app.model.ActiveCall
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallPhase
import com.tiskz.app.model.CallRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val telecomCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updatePhase(phaseOf(call))
        }
    }

    fun init(context: Context, appContainer: AppContainer) {
        appContext = context.applicationContext
        container = appContainer
    }

    // --- Entrada desde Telecom ---

    internal fun attachService(service: InCallService?) {
        inCallService = service
    }

    internal fun onTelecomCallAdded(call: Call) {
        if (telecomCall != null || _activeCall.value?.phase?.let { it != CallPhase.DISCONNECTED } == true) {
            // Llamada en espera: el MVP solo muestra una llamada; la segunda se rechaza.
            if (stateOf(call) == Call.STATE_RINGING) call.reject(false, null)
            return
        }
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val direction = if (call.details.callDirection == Call.Details.DIRECTION_OUTGOING) {
            CallDirection.OUTGOING
        } else {
            CallDirection.INCOMING
        }
        if (direction == CallDirection.INCOMING && container.blocklist.isBlocked(number)) {
            call.reject(false, null)
            return
        }
        telecomCall = call
        call.registerCallback(telecomCallback)
        start(ActiveCall(number = number, direction = direction, phase = phaseOf(call), simulated = false), null)
    }

    internal fun onTelecomCallRemoved(call: Call) {
        if (call != telecomCall) return
        call.unregisterCallback(telecomCallback)
        telecomCall = null
        finish()
    }

    internal fun onAudioStateChanged(muted: Boolean, speaker: Boolean) {
        _activeCall.update { it?.copy(muted = muted, speaker = speaker) }
    }

    // --- Demo ---

    fun simulateIncoming(number: String, scenario: DemoScenario) {
        val current = _activeCall.value
        if (current != null && current.phase != CallPhase.DISCONNECTED) return
        start(
            ActiveCall(number = number, direction = CallDirection.INCOMING, phase = CallPhase.RINGING, simulated = true),
            scenario,
        )
    }

    // --- Acciones de la UI ---

    fun answer() {
        session.value?.send(UserDecision.ANSWER)
        val call = _activeCall.value ?: return
        if (call.simulated) updatePhase(CallPhase.ACTIVE) else telecomCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        session.value?.send(UserDecision.REJECT)
        val call = _activeCall.value ?: return
        if (call.simulated) finish() else telecomCall?.reject(false, null)
    }

    fun hangUp() {
        session.value?.send(UserDecision.HANG_UP)
        val call = _activeCall.value ?: return
        if (call.simulated) finish() else telecomCall?.disconnect()
    }

    fun blockAndHangUp() {
        session.value?.send(UserDecision.BLOCK)
        val call = _activeCall.value ?: return
        container.blocklist.block(call.number)
        _activeCall.value = call.copy(blocked = true)
        when {
            call.simulated -> finish()
            call.phase == CallPhase.RINGING -> telecomCall?.reject(false, null)
            else -> telecomCall?.disconnect()
        }
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

    private fun start(call: ActiveCall, forcedScenario: DemoScenario?) {
        session.value?.close()
        _activeCall.value = call
        session.value = if (call.direction == CallDirection.INCOMING) {
            container.openAnalysisSession(call.number, forcedScenario)
        } else {
            null
        }
        CallNotifications.show(appContext, call)
        appContext.startActivity(
            Intent(appContext, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun updatePhase(phase: CallPhase) {
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
        val ended = call.copy(phase = CallPhase.DISCONNECTED, endedAt = System.currentTimeMillis())
        _activeCall.value = ended
        val finalAnalysis = session.value?.state?.value
        session.value?.close()
        container.history.add(CallRecord.from(ended, finalAnalysis))
        CallNotifications.cancel(appContext)
        scope.launch {
            delay(ENDED_SCREEN_MS)
            if (_activeCall.value?.id == ended.id) clear()
        }
    }

    private fun clear() {
        _activeCall.value = null
        session.value = null
    }

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
}
