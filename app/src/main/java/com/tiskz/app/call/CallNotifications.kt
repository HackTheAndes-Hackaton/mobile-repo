package com.tiskz.app.call

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tiskz.app.MainActivity
import com.tiskz.app.R
import com.tiskz.app.util.PhoneNumbers
import com.tiskz.app.util.DialerRole
import com.tiskz.app.model.ActiveCall
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallPhase

/** Notificación de llamada: abre la pantalla de Tiskz, también con el teléfono bloqueado. */
object CallNotifications {

    /** Entrante con el teléfono bloqueado: aviso emergente y pantalla completa. */
    private const val CHANNEL_ID = "tiskz_calls"
    /** Con la pantalla de Tiskz ya visible: solo un ícono discreto en la barra. */
    private const val ONGOING_CHANNEL_ID = "tiskz_calls_ongoing"
    private const val NOTIFICATION_ID = 1001
    private const val MISSED_CHANNEL_ID = "tiskz_missed"
    private const val MISSED_NOTIFICATION_ID = 1002

    fun createChannel(context: Context) {
        val missed = NotificationChannel(MISSED_CHANNEL_ID, "Llamadas perdidas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Llamadas que no contestaste"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(missed)
        val channel = NotificationChannel(CHANNEL_ID, "Llamadas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Llamadas entrantes y en curso analizadas por Tiskz"
            setSound(null, null)
        }
        val ongoing = NotificationChannel(ONGOING_CHANNEL_ID, "Llamada en curso", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Acceso rápido a la llamada mientras está en curso"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, ongoing))
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
        // Si el teléfono está desbloqueado, CallManager ya abrió la pantalla de Tiskz:
        // un aviso emergente encima solo taparía la cabecera.
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val alert = incoming && call.phase == CallPhase.RINGING && locked
        val builder = NotificationCompat.Builder(context, if (alert) CHANNEL_ID else ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tiskz_shield)
            .setColor(0xFF2563EB.toInt())
            .setContentTitle(call.title)
            .setContentText(when {
                call.screening -> "Marin está atendiendo · aún no estás en la llamada"
                incoming -> "Tiskz está analizando la llamada"
                else -> "Llamada en curso"
            })
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(openCall)
        if (alert) builder.setFullScreenIntent(openCall, true)

        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: la pantalla de llamada se abre igual desde CallManager.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Aviso de llamada perdida con el estilo de Tiskz: abre el historial o vuelve a llamar. */
    fun showMissed(context: Context, count: Int, number: String, name: String?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val who = name ?: number.takeIf { it.isNotBlank() }?.let(PhoneNumbers::display) ?: "Número privado"
        val openApp = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, MISSED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tiskz_shield)
            .setColor(0xFF2563EB.toInt())
            .setContentTitle(if (count > 1) "$count llamadas perdidas" else "Llamada perdida")
            .setContentText(if (count > 1) "La última de $who" else who)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
        if (number.isNotBlank()) {
            val callBack = PendingIntent.getActivity(
                context, 2,
                Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null), context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Llamar", callBack)
        }
        try {
            manager.notify(MISSED_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: la llamada queda igual en el historial.
        }
    }

    /** Al abrir Tiskz se dan por vistas: se quita nuestro aviso y se reinicia el contador de Telecom. */
    @SuppressLint("MissingPermission") // Telecom admite al marcador predeterminado; el rol se comprueba abajo.
    fun cancelMissed(context: Context) {
        NotificationManagerCompat.from(context).cancel(MISSED_NOTIFICATION_ID)
        if (!DialerRole.isHeld(context)) return
        try {
            context.getSystemService(TelecomManager::class.java)?.cancelMissedCallsNotification()
        } catch (_: SecurityException) {
            // El rol puede revocarse entre la comprobación y la llamada al sistema.
        }
    }
}
