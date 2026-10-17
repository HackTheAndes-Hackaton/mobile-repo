package com.tiskz.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager

/** Abre la configuración del sistema. La operadora confirma el desvío y su destino. */
object CallForwardingSettings {
    fun open(context: Context): Boolean {
        val actions = listOf(
            TelecomManager.ACTION_SHOW_CALL_SETTINGS,
            TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS,
            Settings.ACTION_WIRELESS_SETTINGS,
        )
        for (action in actions) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                // Cada fabricante expone páginas de llamadas diferentes.
            } catch (_: SecurityException) {
                // Una página restringida puede tener una alternativa pública.
            }
        }
        return false
    }
}
