package com.tiskz.app.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tiskz.app.R
import com.tiskz.app.model.ActiveCall
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallPhase
import com.tiskz.app.util.PhoneNumbers

/** Notificación de llamada: abre la pantalla de Tiskz, también con el teléfono bloqueado. */
object CallNotifications {

    private const val CHANNEL_ID = "tiskz_calls"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Llamadas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Llamadas entrantes y en curso analizadas por Tiskz"
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, call: ActiveCall) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val openCall = PendingIntent.getActivity(
            context,
            0,
            Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val incoming = call.direction == CallDirection.INCOMING
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tiskz_shield)
            .setColor(0xFF2563EB.toInt())
            .setContentTitle(PhoneNumbers.display(call.number))
            .setContentText(if (incoming) "Tiskz está analizando la llamada" else "Llamada en curso")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(openCall)
        if (incoming && call.phase == CallPhase.RINGING) builder.setFullScreenIntent(openCall, true)

        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: la pantalla de llamada se abre igual desde CallManager.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
