package com.tiskz.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import com.tiskz.app.data.ContactLookup

/**
 * Como app de teléfono predeterminada, Telecom nos delega las llamadas perdidas:
 * así el aviso sale de PhoneWard y no del teléfono del sistema.
 */
class MissedCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        if (count <= 0) {
            CallNotifications.cancelMissed(context)
            return
        }
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER).orEmpty()
        val pending = goAsync()
        Thread {
            try {
                val name = ContactLookup.findName(context, number)
                CallNotifications.showMissed(context, count, number, name)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
