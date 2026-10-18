package com.tiskz.app.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.tiskz.app.data.AppContainer
import com.tiskz.app.model.CallPhase
import com.tiskz.app.util.DialerRole
import com.tiskz.app.voice.PhoneAlertSession
import com.tiskz.app.voice.WebRtcVoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.IdentityHashMap

object ConferenceTelecom {
    const val REQUEST = "com.tiskz.app.CONFERENCE_REQUEST"
    fun accountKey(handle: PhoneAccountHandle?): String = handle?.let {
        it.componentName.flattenToString() + ":" + it.id + ":" + it.userHandle.hashCode()
    }.orEmpty()

    @SuppressLint("MissingPermission") // El llamador verifica READ_PHONE_STATE; si se revoca, no hay ruta.
    fun dialPlan(context: Context, handle: PhoneAccountHandle?, number: String): CarrierDialPlan? = runCatching {
        if (handle == null || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (!telecom.callCapablePhoneAccounts.contains(handle) ||
            telecom.getPhoneAccount(handle)?.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) != true) return null
        val sim = context.getSystemService(TelephonyManager::class.java).createForPhoneAccountHandle(handle) ?: return null
        CarrierDialPlan.detect(sim.simOperator, sim.simCarrierId, sim.networkCountryIso, sim.isNetworkRoaming, number)
    }.getOrNull()

    fun gate(context: Context, container: AppContainer, incoming: PhoneAccountHandle?) = ConferenceGate(
        enabled = container.settings.current.automaticConferenceEnabled,
        costsConfirmed = container.settings.current.conferenceCostsConfirmed,
        defaultDialer = DialerRole.isHeld(context),
        callPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED,
        credentialsReady = container.conferenceCredentials.read(container.settings.current.backendUrl) != null,
        confirmedAccount = container.settings.current.conferenceAccount,
        incomingAccount = accountKey(incoming),
        confirmedRoute = container.settings.current.conferenceConfirmedRoute,
        currentRoute = dialPlan(context, incoming, container.settings.current.conferenceDestination)?.confirmationKey.orEmpty(),
    )

    fun request(call: Call): String? = call.details.intentExtras?.let {
        it.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)?.getString(REQUEST) ?: it.getString(REQUEST)
    }
}

/** Propietario del monitor y de las dos patas: su duración depende de la llamada, no de una Activity. */
class TelecomConferenceRuntime(
    private val context: Context,
    private val container: AppContainer,
    private val incoming: Call,
    private val sessionOpened: (PhoneAlertSession) -> Unit,
    private val stateChanged: (ConferenceState) -> Unit,
    private val phaseChanged: (CallPhase) -> Unit,
    private val ended: () -> Unit,
) {
    private val account = incoming.details.accountHandle
    private val accountKey = ConferenceTelecom.accountKey(account)
    private val plan = checkNotNull(ConferenceTelecom.dialPlan(context, account, container.settings.current.conferenceDestination))
    private val requestId = UUID.randomUUID().toString()
    private val calls = linkedMapOf<String, Call>()
    private val ids = IdentityHashMap<Call, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var alerts: PhoneAlertSession? = null
    private var monitorJob: Job? = null
    private var disposed = false
    private val callerId = UUID.randomUUID().toString()
    private var lastAgentDisconnect: String? = null

    private val controller = AutomaticConference(callerId, accountKey, requestId, plan.target, object : ConferenceActions {
        override fun answer(id: String) { required(id).answer(VideoProfile.STATE_AUDIO_ONLY) }
        override fun hold(id: String) { required(id).hold() }
        @SuppressLint("MissingPermission") // gate() revisa CALL_PHONE antes de marcar.
        override fun dial(account: String, requestId: String) {
            check(ConferenceTelecom.gate(context, container, this@TelecomConferenceRuntime.account).allowed)
            check(account == accountKey && this@TelecomConferenceRuntime.account != null)
            val telecom = context.getSystemService(TelecomManager::class.java)
            // No se abre el selector de SIM ni se utiliza la SIM predeterminada por accidente.
            check(telecom.callCapablePhoneAccounts.contains(this@TelecomConferenceRuntime.account))
            telecom.placeCall(Uri.fromParts("tel", plan.target.dialNumber, null), Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, this@TelecomConferenceRuntime.account)
                putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, Bundle().apply {
                    putString(ConferenceTelecom.REQUEST, requestId)
                })
            })
        }
        override fun merge(first: String, second: String) {
            check(required(first).conferenceableCalls.contains(required(second)))
            required(first).conference(required(second))
        }
        override fun disconnect(id: String) { calls[id]?.disconnect() }
        override fun unhold(id: String) { calls[id]?.unhold() }
        override fun closeAlerts() {
            monitorJob?.cancel()
            alerts?.close()
        }
    }, authorized = { ConferenceTelecom.gate(context, container, account).allowed },
        clock = SystemClock::elapsedRealtime, changed = { stateChanged(it.copy(diagnostic = lastAgentDisconnect)) })

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) { recordDisconnect(call); refresh() }
        override fun onDetailsChanged(call: Call, details: Call.Details) { recordDisconnect(call); refresh() }
        override fun onParentChanged(call: Call, parent: Call?) = refresh()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = refresh()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = refresh()
    }

    fun start() {
        calls[callerId] = incoming
        ids[incoming] = callerId
        incoming.registerCallback(callback)
        controller.start(snapshot())
        if (!controller.state.running) return
        try {
            val code = container.conferenceCredentials.read(container.settings.current.backendUrl) ?: error("access unavailable")
            alerts = container.openPhoneAlerts(container.settings.current.backendUrl, code).also(sessionOpened)
            monitorJob = scope.launch {
                alerts!!.connection.collect { connection ->
                    if (connection == WebRtcVoiceSession.Connection.STREAMING) controller.alertsReady()
                    if (connection == WebRtcVoiceSession.Connection.ENDED) controller.alertsLost()
                }
            }
            scope.launch {
                while (controller.state.running && !disposed) {
                    delay(250)
                    controller.tick()
                }
            }
        } catch (_: Exception) { controller.alertsLost() }
    }

    fun add(call: Call) {
        if (disposed) { disconnectLateAgent(call); return }
        // No incorporar otras llamadas entrantes ni conferencias ajenas a la sesión.
        if (call.details.callDirection == Call.Details.DIRECTION_INCOMING && call != incoming && call.parent == null) {
            call.reject(false, null)
            return
        }
        if (!calls.containsValue(call)) {
            calls[idOf(call)!!] = call
            call.registerCallback(callback)
        }
        refresh()
    }

    fun remove(call: Call) {
        recordDisconnect(call)
        idOf(call)?.let { calls.remove(it) }
        call.unregisterCallback(callback)
        refresh()
    }

    fun hangUp() = controller.hangUp()
    fun cancelPreparation() { if (controller.state.phase != ConferencePhase.CONNECTED) controller.cancel() }
    fun cancelAutomation() = controller.cancel()

    /** Se conserva brevemente tras terminar para cerrar una respuesta tardía de placeCall. */
    fun disconnectLateAgent(call: Call): Boolean {
        if (ConferenceTelecom.request(call) != requestId ||
            ConferenceTelecom.accountKey(call.details.accountHandle) != accountKey ||
            call.details.callDirection != Call.Details.DIRECTION_OUTGOING ||
            !plan.target.matches(call.details.handle?.schemeSpecificPart.orEmpty())) return false
        runCatching { call.disconnect() }
        return true
    }

    fun dispose() {
        disposed = true
        monitorJob?.cancel()
        alerts?.close()
        scope.cancel()
        calls.values.forEach { it.unregisterCallback(callback) }
        calls.clear()
        ids.clear()
    }

    private fun required(id: String) = checkNotNull(calls[id])
    private fun recordDisconnect(call: Call) {
        if (ConferenceTelecom.request(call) != requestId || stateOf(call) != Call.STATE_DISCONNECTED) return
        val cause = call.details.disconnectCause ?: return
        val description = (cause.description?.toString()?.takeIf { it.isNotBlank() }
            ?: cause.label?.toString()?.takeIf { it.isNotBlank() } ?: "Sin detalle de la red").take(200)
        lastAgentDisconnect = "Llamada a Vapi · código Android ${cause.code} · $description"
        stateChanged(controller.state.copy(diagnostic = lastAgentDisconnect))
    }
    // Mantener la identidad de hijos retirados durante la creación del padre de conferencia.
    private fun idOf(call: Call?): String? = call?.let { ids.getOrPut(it) { UUID.randomUUID().toString() } }
    private fun snapshot() = calls.map { (id, call) ->
        ConferenceLeg(
            id = id, phase = when (stateOf(call)) {
                Call.STATE_RINGING -> LegPhase.RINGING
                Call.STATE_ACTIVE -> LegPhase.ACTIVE
                Call.STATE_HOLDING -> LegPhase.HOLDING
                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> LegPhase.DISCONNECTED
                else -> LegPhase.DIALING
            },
            account = ConferenceTelecom.accountKey(call.details.accountHandle),
            number = call.details.handle?.schemeSpecificPart.orEmpty(),
            outgoing = call.details.callDirection == Call.Details.DIRECTION_OUTGOING,
            canHold = call.details.can(Call.Details.CAPABILITY_HOLD),
            parent = idOf(call.parent), children = call.children.mapNotNull(::idOf).toSet(),
            conferenceable = call.conferenceableCalls.mapNotNull(::idOf).toSet(),
            agentRequest = ConferenceTelecom.request(call),
        )
    }

    private fun refresh() {
        if (disposed) return
        controller.callsChanged(snapshot())
        if (controller.state.phase == ConferencePhase.ENDED) { ended(); return }
        val display = calls[controller.displayCallId] ?: incoming
        phaseChanged(when (stateOf(display)) {
            Call.STATE_ACTIVE -> CallPhase.ACTIVE
            Call.STATE_RINGING -> CallPhase.RINGING
            Call.STATE_HOLDING -> CallPhase.HOLDING
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING ->
                if (controller.state.phase == ConferencePhase.MERGING) CallPhase.HOLDING else CallPhase.DISCONNECTED
            else -> CallPhase.DIALING
        })
    }

    @Suppress("DEPRECATION")
    private fun stateOf(call: Call): Int = call.state
}
