package com.tiskz.app.call

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
import androidx.core.app.Person
import com.tiskz.app.MainActivity
import com.tiskz.app.R
import com.tiskz.app.model.ActiveCall
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallPhase
import com.tiskz.app.util.PhoneNumbers
import com.tiskz.app.util.DialerRole

/** Etapas del aviso de llamada. Cada una se ve como una llamada del sistema, pero es de Tiskz. */
enum class CallStage {
    /** Suena: Rechazar o Responder (tomarla uno mismo, sin marin). */
    INCOMING,
    /** Marin contestó y el titular no está en la llamada: aviso discreto de llamada en curso. */
    SCREENING,
    /** Marin confirmó un espacio seguro (o no pudo unirse): aviso emergente para entrar. */
    SAFE,
    /** Marin detectó señales de estafa: aviso emergente para colgar o bloquear. */
    DANGER,
    /** El titular está en la llamada (o llamando): aviso discreto con cronómetro. */
    ONGOING,
}

/** Lo que muestra el aviso. Solo se vuelve a publicar cuando cambia. */
data class CallNotice(
    val callId: String,
    val stage: CallStage,
    val name: String,
    val text: String,
    val verified: Boolean = false,
    val answeredAt: Long? = null,
)

/**
 * Avisos de llamada de Tiskz. La pantalla de llamada no tapa el teléfono al sonar:
 * todo empieza en un aviso arriba, como cualquier llamada, y el titular decide si abrirla.
 */
object CallNotifications {

    /** Avisos emergentes: entrante, espacio seguro y estafa. */
    private const val CHANNEL_ID = "tiskz_calls"
    /** Avisos discretos: marin atendiendo y llamada en curso. */
    private const val ONGOING_CHANNEL_ID = "tiskz_calls_ongoing"
    private const val ALERT_ID = 1001
    private const val QUIET_ID = 1003
    private const val MISSED_CHANNEL_ID = "tiskz_missed"
    private const val MISSED_NOTIFICATION_ID = 1002

    const val EXTRA_ACTION = "com.tiskz.app.CALL_ACTION"
    const val ACTION_ANSWER = "answer"
    const val ACTION_JOIN = "join"

    /** Con la pantalla de llamada abierta, los avisos no emergen: ya se ve todo ahí. */
    @Volatile var screenVisible = false
    private var lastNotice: CallNotice? = null

    fun createChannel(context: Context) {
        val missed = NotificationChannel(MISSED_CHANNEL_ID, "Llamadas perdidas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Llamadas que no contestaste"
        }
        val channel = NotificationChannel(CHANNEL_ID, "Llamadas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Llamadas entrantes y avisos de marin: espacio seguro o posible estafa"
            setSound(null, null)
        }
        val ongoing = NotificationChannel(ONGOING_CHANNEL_ID, "Llamada en curso", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Marin atendiendo y llamadas en curso"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(missed, channel, ongoing))
    }

    /** Traduce el estado de la llamada a la etapa del aviso; null si no debe haber aviso. */
    fun noticeFor(call: ActiveCall?, analysis: CallAnalysis?, conference: ConferenceState, threshold: Int): CallNotice? {
        if (call == null || call.phase == CallPhase.DISCONNECTED) return null
        val name = call.title.ifBlank { "Número privado" }
        fun notice(stage: CallStage, text: String, verified: Boolean = false) =
            CallNotice(call.id, stage, name, text, verified, call.answeredAt)

        if (call.direction == CallDirection.OUTGOING) {
            return notice(CallStage.ONGOING, if (call.phase == CallPhase.ACTIVE) "Llamada en curso" else "Llamando…")
        }
        if (!call.screening) {
            return when (call.phase) {
                CallPhase.RINGING -> notice(CallStage.INCOMING, if (call.callerName != null) "Llamada entrante" else "Llamada entrante · Tiskz la está analizando")
                CallPhase.HOLDING -> notice(CallStage.ONGOING, "Llamada en espera")
                else -> notice(CallStage.ONGOING,
                    if (conference.phase == ConferencePhase.CONNECTED) "En llamada · Marin sigue escuchando" else "Llamada en curso")
            }
        }
        // Marin atiende por el titular.
        val agentAnswered = if (call.simulated) analysis?.transcript?.isNotEmpty() == true else call.phase != CallPhase.RINGING
        val scam = analysis?.isScam == true || analysis?.alert == true
        val safe = !scam && analysis?.isSafeToAnswer(threshold) == true
        val summary = analysis?.understanding?.summary?.trim()?.trimEnd('.')?.takeIf { it.isNotBlank() }
        val reason = analysis?.reasons?.firstOrNull()?.trim()?.trimEnd('.')?.takeIf { it.isNotBlank() }
        return when {
            scam -> notice(CallStage.DANGER, listOfNotNull(
                "Marin detectó una posible estafa",
                analysis?.scamType ?: analysis?.reasons?.firstOrNull(),
            ).joinToString(" · "))
            safe -> notice(CallStage.SAFE, when {
                summary != null -> "Tu agente analizó la llamada: $summary. Ya puedes entrar."
                reason != null -> "Tu agente analizó la llamada · $reason. Ya puedes entrar."
                else -> "Tu agente verificó quién llama y para qué. Ya puedes entrar."
            }, verified = true)
            conference.phase == ConferencePhase.FAILED ->
                notice(CallStage.SAFE, "Marin no pudo unirse. Puedes entrar, pero sin protección.")
            !agentAnswered -> notice(CallStage.INCOMING, "Llamada entrante · Marin contestará por ti")
            else -> notice(CallStage.SCREENING, buildString {
                append("Marin está atendiendo")
                analysis?.takeIf { it.transcript.isNotEmpty() }?.let { append(" · riesgo ${it.riskPercent}%") }
                append(" · aún no estás en la llamada")
            })
        }
    }

    fun render(context: Context, notice: CallNotice?) {
        if (notice == lastNotice) return
        val previous = lastNotice
        lastNotice = notice
        val manager = NotificationManagerCompat.from(context)
        if (notice == null) {
            manager.cancel(ALERT_ID)
            manager.cancel(QUIET_ID)
            return
        }
        val alert = notice.stage in setOf(CallStage.INCOMING, CallStage.SAFE, CallStage.DANGER)
        val id = if (alert) ALERT_ID else QUIET_ID
        // Cada cambio de etapa reemplaza el aviso anterior; uno emergente vuelve a salir como pop-up.
        if (previous != null && (previous.stage != notice.stage || previous.callId != notice.callId)) {
            manager.cancel(ALERT_ID)
            manager.cancel(QUIET_ID)
        }
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, build(context, notice, alert, callStyle = true))
        } catch (e: IllegalArgumentException) {
            // El sistema rechazó el estilo de llamada: el mismo aviso con botones normales.
            runCatching { manager.notify(id, build(context, notice, alert, callStyle = false)) }
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: se puede abrir la llamada desde Tiskz.
        }
    }

    private fun build(context: Context, notice: CallNotice, alert: Boolean, callStyle: Boolean): android.app.Notification {
        val openCall = activityIntent(context, 0, null)
        val popUp = alert && !screenVisible
        val builder = NotificationCompat.Builder(context, if (popUp) CHANNEL_ID else ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tiskz_shield)
            .setColor(when (notice.stage) {
                CallStage.SAFE -> 0xFF059669.toInt()
                CallStage.DANGER -> 0xFFDC2626.toInt()
                else -> 0xFF2563EB.toInt()
            })
            .setContentTitle(notice.name)
            .setContentText(notice.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.text))
            .setSubText(if (notice.stage == CallStage.SAFE || notice.stage == CallStage.SCREENING) "Marin · Tiskz" else "Tiskz")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(if (popUp) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openCall)
            // Requisito de Android para el estilo de llamada. Con el teléfono desbloqueado solo
            // muestra el aviso arriba; con el teléfono bloqueado abre la pantalla de llamada.
            .setFullScreenIntent(openCall, popUp)
        notice.answeredAt?.takeIf { notice.stage == CallStage.ONGOING }?.let {
            builder.setWhen(it).setUsesChronometer(true).setShowWhen(true)
        }

        val reject = broadcast(context, 20, CallActionReceiver.ACTION_REJECT)
        val hangUp = broadcast(context, 21, CallActionReceiver.ACTION_HANG_UP)
        val block = broadcast(context, 22, CallActionReceiver.ACTION_BLOCK)
        val answer = activityIntent(context, 10, ACTION_ANSWER)
        val join = activityIntent(context, 11, ACTION_JOIN)

        if (callStyle) {
            val person = Person.Builder().setName(notice.name).setImportant(true).build()
            val style = when (notice.stage) {
                CallStage.INCOMING -> NotificationCompat.CallStyle.forIncomingCall(person, reject, answer)
                // Colgar y Responder: responder es entrar a la llamada que atendía marin.
                CallStage.SAFE -> NotificationCompat.CallStyle.forScreeningCall(person, hangUp, join)
                else -> NotificationCompat.CallStyle.forOngoingCall(person, hangUp)
            }
            if (notice.verified) style.setVerificationText("Verificada por Tiskz")
            builder.setStyle(style)
            when (notice.stage) {
                CallStage.DANGER -> builder.addAction(0, "Bloquear", block)
                CallStage.SCREENING -> builder.addAction(0, "Ver en vivo", openCall)
                else -> Unit
            }
        } else {
            when (notice.stage) {
                CallStage.INCOMING -> builder.addAction(0, "Rechazar", reject).addAction(0, "Responder", answer)
                CallStage.SAFE -> builder.addAction(0, "Colgar", hangUp).addAction(0, "Entrar", join)
                CallStage.DANGER -> builder.addAction(0, "Colgar", hangUp).addAction(0, "Bloquear", block)
                CallStage.SCREENING -> builder.addAction(0, "Cancelar", hangUp).addAction(0, "Ver en vivo", openCall)
                CallStage.ONGOING -> builder.addAction(0, "Colgar", hangUp)
            }
        }
        return builder.build()
    }

    /** Abre la pantalla de llamada; con [action] además contesta o entra (desde un aviso no se puede abrir una Activity desde un receiver). */
    private fun activityIntent(context: Context, requestCode: Int, action: String?): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { action?.let { putExtra(EXTRA_ACTION, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(context: Context, requestCode: Int, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

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
