package com.tiskz.app.ui.call

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tiskz.app.model.ActiveCall
import com.tiskz.app.analysis.GuardAlert
import com.tiskz.app.call.ConferencePhase
import com.tiskz.app.call.ConferenceState
import com.tiskz.app.model.AgentStatus
import com.tiskz.app.model.AssessmentState
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallPhase
import com.tiskz.app.ui.components.CallerAvatar
import com.tiskz.app.ui.components.AssessmentCard
import com.tiskz.app.ui.components.LiveTranscript
import com.tiskz.app.ui.components.PulsingRings
import com.tiskz.app.ui.components.RiskBar
import com.tiskz.app.ui.components.overlapUp
import com.tiskz.app.ui.components.rememberNow
import com.tiskz.app.ui.components.softShadow
import com.tiskz.app.ui.theme.TiskzBrushes
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.util.PhoneNumbers
import com.tiskz.app.util.formatDuration
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Microphone
import compose.icons.tablericons.MicrophoneOff
import compose.icons.tablericons.Phone
import compose.icons.tablericons.PhoneOff
import compose.icons.tablericons.Volume
import kotlin.math.roundToInt

class LiveCallActions(
    val onAnswer: () -> Unit,
    val onJoin: () -> Unit,
    val onReject: () -> Unit,
    val onHangUp: () -> Unit,
    val onBlock: () -> Unit,
    val onNotScam: () -> Unit,
    val onToggleMute: () -> Unit,
    val onToggleSpeaker: () -> Unit,
    val onClose: () -> Unit,
    val onDismissGuard: () -> Unit = {},
)

private enum class Mood { NEUTRAL, SAFE, DANGER }

/**
 * Pantalla de llamada: cabecera con degradado que cambia de color según el riesgo,
 * tarjeta de riesgo flotante, la conversación del agente al centro y los controles abajo.
 */
@Composable
fun LiveCallScreen(
    call: ActiveCall,
    analysis: CallAnalysis?,
    answerThreshold: Int,
    actions: LiveCallActions,
    conference: ConferenceState = ConferenceState(),
    guard: GuardAlert? = null,
) {
    val automatic = conference.phase != ConferencePhase.IDLE
    var showProgress by remember(call.id) { mutableStateOf(false) }
    if (showProgress) {
        AlertDialog(onDismissRequest = { showProgress = false }, title = { Text("Progreso de la conferencia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✓ Android entregó la llamada entrante")
                    Text("${if (conference.callerConnected) "✓" else "○"} Android conectó la llamada original")
                    Text("${if (conference.agentConnected) "✓" else "○"} Android conectó la llamada a Vapi")
                    Text("${if (conference.mergeRequested) "✓" else "○"} Se solicitó unir ambas llamadas")
                    Text("${if (conference.confirmed) "✓" else "○"} Android confirmó la conferencia")
                    Text(conference.message)
                    conference.diagnostic?.let { Text(it) }
                    Text("La confirmación de Android no demuestra por sí sola que ambos extremos escuchen al agente.")
                }
            }, confirmButton = { TextButton(onClick = { showProgress = false }) { Text("Cerrar") } })
    }
    val screened = call.direction == CallDirection.INCOMING && analysis != null
    val mood = when {
        analysis == null -> Mood.NEUTRAL
        analysis.alert == true -> Mood.DANGER
        analysis.currentAssessment?.state == AssessmentState.ALERT -> Mood.DANGER
        analysis.currentAssessment != null -> Mood.NEUTRAL
        analysis.assessment != null -> Mood.NEUTRAL
        automatic && analysis.alert != true && analysis.isSafeToAnswer(answerThreshold) -> Mood.SAFE
        automatic && (conference.phase != ConferencePhase.CONNECTED || analysis.alert == null) -> Mood.NEUTRAL
        automatic && analysis.alert == true -> Mood.DANGER
        automatic && analysis.alert == false -> Mood.SAFE
        analysis.isScam -> Mood.DANGER
        analysis.isSafeToAnswer(answerThreshold) -> Mood.SAFE
        else -> Mood.NEUTRAL
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TiskzColors.Background),
    ) {
        if (screened) {
            CompactHero(call, mood)
            RiskBar(
                percent = analysis!!.riskPercent,
                assessed = !automatic || analysis.alertProbability != null,
                headline = if (automatic) when {
                    conference.phase == ConferencePhase.FAILED -> "Sin monitoreo del agente"
                    analysis.alert == true -> "Alerta detectada"
                    analysis.currentAssessment != null -> "Evaluación provisional"
                    analysis.assessment != null -> "Actualizando análisis"
                    analysis.isSafeToAnswer(answerThreshold) -> "Espacio seguro · puedes entrar"
                    analysis.alert == false -> "Sin señales de alerta detectadas"
                    else -> "Esperando análisis real"
                } else riskHeadline(analysis, answerThreshold),
                // En automático solo se explica lo accionable: un error, por qué falló o el motivo de la alerta.
                detail = if (automatic) when {
                    analysis.error != null -> analysis.error
                    conference.phase == ConferencePhase.FAILED -> conference.message
                    analysis.alert == true -> analysis.reasons.firstOrNull()
                    analysis.alertProbability != null -> "Estimación del modelo; no confirma ni descarta una estafa."
                    else -> "Esperando la primera evaluación de lo que dice quien llama."
                } else riskDetail(analysis, answerThreshold),
                headlineColor = if (mood == Mood.NEUTRAL && analysis.riskPercent < 30) TiskzColors.Blue600 else null,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .overlapUp(HERO_OVERLAP),
            )
            guard?.let { GuardBanner(it, actions.onDismissGuard, Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) }
            AssessmentCard(analysis, compact = true, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LiveTranscript(
                lines = analysis.transcript,
                callerLabel = call.callerName ?: "Quien llama",
                live = if (automatic) conference.phase == ConferencePhase.CONNECTED else call.phase == CallPhase.RINGING && !analysis.isScam,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
        } else {
            FullHero(call, Modifier.weight(1f))
            if (automatic) Text(conference.message, style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted,
                modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center)
        }

        if (!call.screening && call.phase == CallPhase.HOLDING) {
            TextButton(onClick = actions.onAnswer, modifier = Modifier.fillMaxWidth()) { Text("Retomar llamada") }
        }
        // El detalle técnico solo hace falta si la conexión con marin falló.
        if (conference.phase == ConferencePhase.FAILED) {
            TextButton(onClick = { showProgress = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Ver detalle de la conexión", style = MaterialTheme.typography.bodySmall)
            }
        }

        ActionBar(call, analysis, answerThreshold, actions, conference)
    }
}

// --- Cabeceras ---

@Composable
private fun heroBrush(mood: Mood): Brush {
    val (startTarget, endTarget) = when (mood) {
        Mood.NEUTRAL -> TiskzColors.Blue900 to TiskzColors.Blue600
        Mood.SAFE -> TiskzColors.EmeraldDark to TiskzColors.Emerald
        Mood.DANGER -> TiskzColors.RedDark to TiskzColors.Red
    }
    val start by animateColorAsState(startTarget, tween(700), label = "heroStart")
    val end by animateColorAsState(endTarget, tween(700), label = "heroEnd")
    return Brush.linearGradient(listOf(start, end))
}

/** Cabecera compacta: deja el espacio a la conversación. */
@Composable
private fun CompactHero(call: ActiveCall, mood: Mood) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(heroBrush(mood))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp + HERO_OVERLAP),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                if (call.phase == CallPhase.RINGING) PulsingRings(Color.White, Modifier.size(84.dp))
                CallerAvatar(
                    name = call.callerName,
                    size = 60.dp,
                    background = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.15f))),
                    content = Color.White,
                )
            }
            Spacer(Modifier.size(12.dp))
            CallerText(call, Modifier.weight(1f))
        }
    }
}

/** Llamadas sin análisis (salientes): avatar grande centrado. */
@Composable
private fun FullHero(call: ActiveCall, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
            .background(heroBrush(Mood.NEUTRAL))
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                if (call.phase == CallPhase.DIALING || call.phase == CallPhase.RINGING) {
                    PulsingRings(Color.White, Modifier.size(200.dp))
                }
                CallerAvatar(
                    name = call.callerName,
                    size = 112.dp,
                    background = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.15f))),
                    content = Color.White,
                )
            }
            Spacer(Modifier.height(8.dp))
            CallerText(call, Modifier.padding(horizontal = 24.dp), centered = true)
        }
    }
}

@Composable
private fun CallerText(call: ActiveCall, modifier: Modifier = Modifier, centered: Boolean = false) {
    val now by rememberNow()
    val status = if (call.screening && call.phase != CallPhase.DISCONNECTED) "Marin está atendiendo"
    else when (call.phase) {
        CallPhase.RINGING -> "Llamada entrante"
        CallPhase.DIALING -> "Llamando…"
        CallPhase.ACTIVE -> "En llamada · " + formatDuration(((now - (call.answeredAt ?: now)) / 1000).coerceAtLeast(0))
        CallPhase.HOLDING -> "En espera"
        CallPhase.DISCONNECTED -> if (call.blocked) "Número bloqueado" else "Llamada finalizada"
    }
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start

    Column(modifier, horizontalAlignment = align) {
        Text(
            text = if (call.simulated) "$status · Simulación" else status,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
        )
        Text(
            text = call.title,
            style = if (centered) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            if (call.callerName != null) {
                Text(PhoneNumbers.display(call.number), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.size(8.dp))
                GlassChip("Contacto")
            } else if (call.reputation?.flagged == true) {
                ReputationChip("⚠ ${call.reputation.headline}")
            } else {
                GlassChip("No está en tus contactos")
            }
        }
    }
}

/** Reportes de la comunidad: se destaca en rojo sobre la cabecera, aunque el análisis siga en curso. */
@Composable
private fun ReputationChip(text: String) {
    Surface(color = TiskzColors.Red, shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Quien llama pidió algo peligroso: se frena al titular en el momento, con la frase exacta. */
@Composable
private fun GuardBanner(alert: GuardAlert, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = TiskzColors.Red,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth().softShadow(RoundedCornerShape(20.dp), 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TablerIcons.AlertTriangle, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text(alert.kind.title, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Entendido", color = Color.White) }
            }
            Text(alert.kind.advice, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(
                "Te dijeron: “${alert.quote}”",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GlassChip(text: String) {
    Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

private val HERO_OVERLAP = 40.dp
private val SCREENING_BUTTON = 56.dp

// --- Riesgo: una línea de titular y una de detalle ---

private fun riskHeadline(analysis: CallAnalysis, threshold: Int): String = when {
    analysis.currentAssessment != null -> "Evaluación provisional"
    analysis.assessment != null -> "Actualizando análisis"
    analysis.isScam -> "Posible estafa"
    analysis.markedSafeByUser -> "Marcada como segura"
    analysis.isSafeToAnswer(threshold) -> "Llamada segura"
    analysis.status == AgentStatus.OFFLINE -> "Sin análisis"
    analysis.riskPercent >= 30 -> "Sospechosa"
    else -> "Analizando…"
}

private fun riskDetail(analysis: CallAnalysis, threshold: Int): String? = when {
    analysis.assessment != null -> "Estimación provisional; no verifica la identidad ni confirma una estafa."
    analysis.isScam -> listOfNotNull(
        analysis.scamType,
        analysis.impersonating?.let { "se hace pasar por $it" },
        analysis.voiceMatch?.let { "voz reportada ${it.reports} veces (${(it.similarity * 100).roundToInt()}%)" },
    ).joinToString(" · ").ifEmpty { null }
    analysis.isSafeToAnswer(threshold) -> "No hay alerta actual; la identidad no está verificada."
    analysis.status == AgentStatus.OFFLINE -> analysis.error ?: "Contesta con precaución."
    else -> analysis.reasons.firstOrNull() ?: "Tiskz está analizando lo que dice quien llama."
}

// --- Controles ---

@Composable
private fun ActionBar(
    call: ActiveCall,
    analysis: CallAnalysis?,
    answerThreshold: Int,
    actions: LiveCallActions,
    conference: ConferenceState,
) {
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(shape, 24.dp)
            .clip(shape)
            .background(TiskzColors.White)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            call.phase == CallPhase.DISCONNECTED -> Button(
                onClick = actions.onClose,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TiskzColors.Blue600),
            ) {
                Text("Cerrar", style = MaterialTheme.typography.titleMedium)
            }

            call.screening -> ScreeningActions(analysis, answerThreshold, actions, conference)

            call.phase == CallPhase.RINGING && call.direction == CallDirection.INCOMING -> {
                val scam = analysis?.isScam == true
                val safe = analysis == null || analysis.isSafeToAnswer(answerThreshold)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    CircleAction(TablerIcons.PhoneOff, "Rechazar", actions.onReject, TiskzBrushes.Danger, Color.White, glow = TiskzColors.Red)
                    when {
                        scam -> CircleAction(
                            TablerIcons.Ban, "Bloquear", actions.onBlock,
                            Brush.linearGradient(listOf(TiskzColors.Navy, TiskzColors.Blue900)), Color.White, glow = TiskzColors.Navy,
                        )
                        safe -> CircleAction(TablerIcons.Phone, "Contestar", actions.onAnswer, TiskzBrushes.Safe, Color.White, glow = TiskzColors.Emerald)
                        else -> CircleAction(
                            TablerIcons.Phone, "Contestar", actions.onAnswer,
                            Brush.linearGradient(listOf(TiskzColors.Blue50, TiskzColors.Blue100)), TiskzColors.Blue700,
                        )
                    }
                }
                if (scam) {
                    Row(Modifier.padding(top = 6.dp)) {
                        TextButton(onClick = actions.onNotScam) { Text("No es estafa", color = TiskzColors.Muted) }
                        TextButton(onClick = actions.onAnswer) { Text("Contestar igual", color = TiskzColors.Muted) }
                    }
                }
            }

            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
              if (conference.phase == ConferencePhase.CONNECTED) {
                  Text("Marin sigue escuchando y analizando la llamada", style = MaterialTheme.typography.bodySmall,
                      color = TiskzColors.Muted, modifier = Modifier.padding(bottom = 12.dp))
              }
              Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
              ) {
                ToggleAction(
                    icon = if (call.muted) TablerIcons.MicrophoneOff else TablerIcons.Microphone,
                    label = if (call.muted) "Silenciado" else "Silenciar",
                    active = call.muted,
                    onClick = actions.onToggleMute,
                )
                CircleAction(TablerIcons.PhoneOff, "Colgar", actions.onHangUp, TiskzBrushes.Danger, Color.White, glow = TiskzColors.Red)
                ToggleAction(
                    icon = TablerIcons.Volume,
                    label = "Altavoz",
                    active = call.speaker,
                    onClick = actions.onToggleSpeaker,
                )
              }
            }
        }
    }
}

/**
 * Mientras marin atiende, el titular no está en la llamada: no hay contestar, silenciar ni altavoz.
 * Solo puede cancelar o entrar, y entrar se habilita cuando marin confirma un espacio seguro.
 */
@Composable
private fun ScreeningActions(
    analysis: CallAnalysis?,
    answerThreshold: Int,
    actions: LiveCallActions,
    conference: ConferenceState,
) {
    val scam = analysis?.isScam == true || analysis?.alert == true
    val noAgent = conference.phase == ConferencePhase.FAILED
    val safe = !scam && analysis?.isSafeToAnswer(answerThreshold) == true
    val canJoin = safe || noAgent
    Text(
        text = when {
            noAgent -> "Si entras, hablarás sin protección"
            scam -> "Posible estafa · mejor no entres"
            safe -> "Es seguro · ya puedes entrar"
            else -> "Podrás entrar cuando Marin confirme que es seguro"
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (scam) TiskzColors.Red else TiskzColors.Muted,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        CircleAction(TablerIcons.PhoneOff, "Cancelar", actions.onHangUp, TiskzBrushes.Danger, Color.White,
            size = SCREENING_BUTTON, glow = TiskzColors.Red)
        when {
            scam -> CircleAction(
                TablerIcons.Ban, "Bloquear", actions.onBlock,
                Brush.linearGradient(listOf(TiskzColors.Navy, TiskzColors.Blue900)), Color.White,
                size = SCREENING_BUTTON, glow = TiskzColors.Navy,
            )
            canJoin -> CircleAction(TablerIcons.Phone, "Entrar", actions.onJoin, TiskzBrushes.Safe, Color.White,
                size = SCREENING_BUTTON, glow = TiskzColors.Emerald)
            else -> CircleAction(
                TablerIcons.Phone, "Entrar", {},
                Brush.linearGradient(listOf(TiskzColors.Blue50, TiskzColors.Blue50)), TiskzColors.Muted,
                size = SCREENING_BUTTON, enabled = false,
            )
        }
    }
    if (scam) {
        TextButton(onClick = actions.onNotScam, modifier = Modifier.padding(top = 6.dp)) {
            Text("No es estafa", color = TiskzColors.Muted)
        }
    }
}

@Composable
private fun ToggleAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    CircleAction(
        icon = icon,
        label = label,
        onClick = onClick,
        background = if (active) TiskzBrushes.Brand else Brush.linearGradient(listOf(TiskzColors.Blue50, TiskzColors.Blue50)),
        content = if (active) Color.White else TiskzColors.Blue700,
        size = 64.dp,
    )
}

@Composable
private fun CircleAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    background: Brush,
    content: Color,
    size: Dp = 76.dp,
    glow: Color? = null,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier
                .size(size)
                .then(
                    if (glow != null) {
                        Modifier.shadow(16.dp, CircleShape, ambientColor = glow.copy(alpha = 0.3f), spotColor = glow.copy(alpha = 0.6f))
                    } else {
                        Modifier
                    }
                ),
        ) {
            Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(size * 0.42f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = TiskzColors.Ink)
    }
}
