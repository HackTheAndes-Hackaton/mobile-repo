package com.tiskz.app.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/** Android nos vincula este servicio por ser la app de teléfono predeterminada. */
class PhoneWardInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        CallManager.attachService(this)
        CallManager.onTelecomCallAdded(call)
    }

    override fun onCallRemoved(call: Call) {
        CallManager.onTelecomCallRemoved(call)
    }

    @Deprecated("Reemplazado en API 34 por onCallEndpointChanged/onMuteStateChanged")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.onAudioStateChanged(
            muted = audioState.isMuted,
            speaker = audioState.route == CallAudioState.ROUTE_SPEAKER,
        )
    }

    override fun onDestroy() {
        CallManager.attachService(null)
        super.onDestroy()
    }
}
