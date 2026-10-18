package com.tiskz.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Botones del aviso de llamada que no abren pantalla: rechazar, colgar y bloquear. */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REJECT -> CallManager.reject()
            ACTION_HANG_UP -> CallManager.hangUp()
            ACTION_BLOCK -> CallManager.blockAndHangUp()
        }
    }

    companion object {
        const val ACTION_REJECT = "com.tiskz.app.call.REJECT"
        const val ACTION_HANG_UP = "com.tiskz.app.call.HANG_UP"
        const val ACTION_BLOCK = "com.tiskz.app.call.BLOCK"
    }
}
