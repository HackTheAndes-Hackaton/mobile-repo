package com.tiskz.app.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.tiskz.app.MainActivity
import com.tiskz.app.R
import com.tiskz.app.analysis.GuardAlert
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
    /** Terminó la revisión inicial o el usuario marcó la llamada como segura: aviso emergente para entrar. */
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
    /** La atiende marin: aviso normal de Tiskz (se puede ignorar), sin botones de contestar ni colgar. */
    val screened: Boolean = false,
    /** Etiqueta de estado en color: "Llamada segura", "Analizando"… */
    val label: String = "",
    val tone: NoticeTone = NoticeTone.INFO,
    /** Riesgo para la barra; null mientras aún no hay conversación que analizar. */
    val risk: Int? = null,
    /** Segunda línea en el aviso expandido: qué puede hacer el titular. */
    val detail: String? = null,
)

enum class NoticeTone(val color: Int) {
    INFO(0xFF2563EB.toInt()),
    SAFE(0xFF059669.toInt()),
    WARN(0xFFD97706.toInt()),
    DANGER(0xFFE11D48.toInt()),
}

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
    private const val GUARD_ID = 1004
    private const val MISSED_CHANNEL_ID = "tiskz_missed"
    private const val MISSED_NOTIFICATION_ID = 1002

    const val EXTRA_ACTION = "com.tiskz.app.CALL_ACTION"
    const val ACTION_ANSWER = "answer"
    const val ACTION_JOIN = "join"

    /** Con la pantalla de llamada abierta, los avisos no emergen: ya se ve todo ahí. */
    @Volatile var screenVisible = false
    private var lastNotice: CallNotice? = null
    /** Etapas que ya emergieron como pop-up en la llamada actual. */
    private val poppedStages = mutableSetOf<CallStage>()

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
            CallNotice(call.id, stage, name, text, verified, call.answeredAt, screened = call.screening)

        if (call.direction == CallDirection.OUTGOING) {
            return notice(CallStage.ONGOING, if (call.phase == CallPhase.ACTIVE) "Llamada en curso" else "Llamando…")
        }
        if (!call.screening) {
            return when (call.phase) {
                CallPhase.RINGING -> notice(CallStage.INCOMING, if (call.callerName != null) "Llamada entrante" else "Llamada entrante · analizando quién llama")
                CallPhase.HOLDING -> notice(CallStage.ONGOING, "Llamada en espera")
                else -> notice(CallStage.ONGOING,
                    if (conference.phase == ConferencePhase.CONNECTED) "En llamada · protegida" else "Llamada en curso")
            }
        }
        // El asistente atiende por el titular. El aviso habla de "tu asistente", sin nombres internos.
        val agentAnswered = if (call.simulated) analysis?.transcript?.isNotEmpty() == true else call.phase != CallPhase.RINGING
        val scam = analysis?.isScam == true || analysis?.alert == true
        val safe = !scam && analysis?.isSafeToAnswer(threshold) == true
        val summary = analysis?.understanding?.summary?.trim()?.trimEnd('.')?.takeIf { it.isNotBlank() }
        val reason = analysis?.reasons?.firstOrNull()?.trim()?.trimEnd('.')?.takeIf { it.isNotBlank() }
        val risk = analysis?.takeIf { it.transcript.isNotEmpty() }?.riskPercent
        fun screened(stage: CallStage, label: String, tone: NoticeTone, text: String, detail: String?, risk: Int? = null) =
            CallNotice(call.id, stage, name, text, verified = false, answeredAt = call.answeredAt,
                screened = true, label = label, tone = tone, risk = risk, detail = detail)
        return when {
            scam -> screened(CallStage.DANGER, "● Posible estafa", NoticeTone.DANGER,
                listOfNotNull(analysis?.scamType, analysis?.impersonating?.let { "se hace pasar por $it" })
                    .joinToString(" · ").ifEmpty { reason ?: "Quien llama muestra señales de estafa" } + ".",
                "Te recomendamos no entrar. Puedes bloquear el número.", analysis?.riskPercent)
            safe -> screened(CallStage.SAFE, "● Puedes entrar", NoticeTone.INFO,
                summary?.let { "La revisión inicial indica que la llamada trata de: $it." } ?: reason?.let { "$it." } ?: "Terminó la revisión inicial de la llamada.",
                "Ya puedes entrar a la llamada. La identidad de quien llama no está verificada.", risk)
            conference.phase == ConferencePhase.FAILED -> screened(CallStage.SAFE, "● Sin protección", NoticeTone.WARN,
                "No pudimos analizar esta llamada.", "Puedes entrar, pero sin protección ni verificación de identidad.")
            // La comunidad ya reportó este número: se avisa antes de que el asistente termine.
            call.reputation?.flagged == true -> screened(
                if (agentAnswered) CallStage.DANGER else CallStage.INCOMING, "● Número reportado", NoticeTone.DANGER,
                "${call.reputation.headline}.",
                if (agentAnswered) "Tu asistente está hablando con quien llama. Te recomendamos no entrar."
                else "Tu asistente va a contestar por ti.", risk)
            !agentAnswered -> screened(CallStage.INCOMING, "● Llamada entrante", NoticeTone.INFO,
                "Tu asistente va a contestar por ti.", "Te avisamos cuando termine la revisión inicial.")
            else -> screened(CallStage.SCREENING, "● Analizando", NoticeTone.INFO,
                "Tu asistente está hablando con quien llama.", "Aún no estás en la llamada.", risk)
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
        // Un solo aviso por llamada, actualizado en el mismo lugar. Durante la llamada la etapa va y viene
        // (cada frase nueva invalida la revisión), así que cada etapa emerge como pop-up solo la primera vez.
        if (previous?.callId != notice.callId) {
            poppedStages.clear()
            manager.cancel(ALERT_ID)
            manager.cancel(QUIET_ID)
        }
        val pop = notice.stage in setOf(CallStage.INCOMING, CallStage.SAFE, CallStage.DANGER) && poppedStages.add(notice.stage)
        // Para que vuelva a emerger hay que publicarlo de nuevo; una actualización con setOnlyAlertOnce no emerge.
        if (pop && previous != null) manager.cancel(ALERT_ID)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(ALERT_ID, build(context, notice, pop, callStyle = !notice.screened))
        } catch (e: IllegalArgumentException) {
            // El sistema rechazó el estilo de llamada: el mismo aviso con botones normales.
            runCatching { manager.notify(ALERT_ID, build(context, notice, pop, callStyle = false)) }
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: se puede abrir la llamada desde Tiskz.
        }
    }

    private fun build(context: Context, notice: CallNotice, alert: Boolean, callStyle: Boolean): android.app.Notification {
        val openCall = activityIntent(context, 0, null)
        val popUp = alert && !screenVisible
        val builder = NotificationCompat.Builder(context, if (popUp) CHANNEL_ID else ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tiskz_shield)
            .setColor(notice.tone.color)
            .setContentTitle(notice.name)
            .setContentText(notice.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.text))
            .setCategory(if (callStyle) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (popUp) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(callStyle)
            .setOnlyAlertOnce(true)
            .setContentIntent(openCall)
        // Requisito de Android para el estilo de llamada. Con el teléfono desbloqueado solo
        // muestra el aviso arriba; con el teléfono bloqueado abre la pantalla de llamada.
        // Los avisos de marin no lo llevan: se pueden ignorar y abrir Tiskz cuando uno quiera.
        if (callStyle) builder.setFullScreenIntent(openCall, popUp)
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
            if (notice.verified) style.setVerificationText("Verificada por PhoneWard")
            builder.setStyle(style)
            when (notice.stage) {
                CallStage.DANGER -> builder.addAction(0, "Bloquear", block)
                CallStage.SCREENING -> builder.addAction(0, "Ver en vivo", openCall)
                else -> Unit
            }
        } else if (notice.screened) {
            // El asistente atiende: nada de contestar o rechazar aquí. Tocar el aviso abre Tiskz.
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(views(context, R.layout.notification_call_small, notice))
                .setCustomBigContentView(views(context, R.layout.notification_call_big, notice))
                // El pop-up tiene poca altura (HyperOS lo recorta): versión compacta con el riesgo en la etiqueta.
                .setCustomHeadsUpContentView(views(context, R.layout.notification_call_small, notice))
            when (notice.stage) {
                CallStage.SCREENING -> builder.addAction(0, "Ver en vivo", openCall)
                CallStage.SAFE -> builder.addAction(0, "Entrar a la llamada", join)
                CallStage.DANGER -> builder.addAction(0, "Bloquear", block)
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

    /** Pop-up rojo cuando quien llama pide algo peligroso; también con el titular ya en la llamada. */
    fun showGuard(context: Context, alert: GuardAlert) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val text = alert.kind.advice
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tiskz_shield)
            .setColor(NoticeTone.DANGER.color)
            .setContentTitle("⚠ ${alert.kind.title}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n\nTe dijeron: “${alert.quote.take(160)}”"))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000)
            .setContentIntent(activityIntent(context, 0, null))
            .build()
        try {
            manager.notify(GUARD_ID, notification)
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: la alerta se ve igual en la pantalla de llamada.
        }
    }

    fun cancelGuard(context: Context) {
        NotificationManagerCompat.from(context).cancel(GUARD_ID)
    }

    /** Vista propia del aviso: estado en color, quién llama, barra de riesgo y qué hacer. */
    private fun views(context: Context, layout: Int, notice: CallNotice) = RemoteViews(context.packageName, layout).apply {
        val big = layout == R.layout.notification_call_big
        // En la versión compacta el riesgo va junto a la etiqueta; en la grande, a la derecha con su barra.
        setTextViewText(R.id.notif_label, if (big) notice.label else notice.label + notice.risk?.let { " · $it%" }.orEmpty())
        setTextColor(R.id.notif_label, notice.tone.color)
        setTextViewText(R.id.notif_name, notice.name)
        setTextViewText(R.id.notif_text, notice.text)
        if (!big) return@apply
        setTextViewText(R.id.notif_detail, notice.detail.orEmpty())
        setViewVisibility(R.id.notif_detail, if (notice.detail != null) View.VISIBLE else View.GONE)
        val risk = notice.risk
        setViewVisibility(R.id.notif_bar, if (risk != null) View.VISIBLE else View.GONE)
        setTextViewText(R.id.notif_risk, risk?.let { "Riesgo $it%" }.orEmpty())
        setTextColor(R.id.notif_risk, notice.tone.color)
        if (risk != null) {
            setProgressBar(R.id.notif_bar, 100, risk.coerceIn(2, 100), false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setColorStateList(R.id.notif_bar, "setProgressTintList", ColorStateList.valueOf(notice.tone.color))
            }
        }
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
