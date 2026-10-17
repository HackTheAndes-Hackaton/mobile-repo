package com.tiskz.app.ui.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tiskz.app.model.ActiveCall
import com.tiskz.app.model.AgentStatus
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallPhase
import com.tiskz.app.ui.components.LiveTranscript
import com.tiskz.app.ui.components.RiskGauge
import com.tiskz.app.ui.components.StatusPill
import com.tiskz.app.ui.components.rememberNow
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.util.PhoneNumbers
import com.tiskz.app.util.formatDuration
import kotlin.math.roundToInt

class LiveCallActions(
    val onAnswer: () -> Unit,
    val onReject: () -> Unit,
    val onHangUp: () -> Unit,
    val onBlock: () -> Unit,
    val onNotScam: () -> Unit,
    val onToggleMute: () -> Unit,
    val onToggleSpeaker: () -> Unit,
    val onClose: () -> Unit,
)

@Composable
fun LiveCallScreen(
    call: ActiveCall,
    analysis: CallAnalysis?,
    answerThreshold: Int,
    actions: LiveCallActions,
) {
    val showAnalysis = call.direction == CallDirection.INCOMING && analysis != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TiskzColors.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        CallHeader(call, analysis)

        if (showAnalysis) {
            AnalysisPanel(analysis!!, answerThreshold, Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(140.dp).background(TiskzColors.Blue50, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, null, tint = TiskzColors.Blue600, modifier = Modifier.size(72.dp))
                }
            }
        }

        CallActionBar(call, analysis, answerThreshold, actions)
    }
}

// --- Cabecera ---

@Composable
private fun CallHeader(call: ActiveCall, analysis: CallAnalysis?) {
    val now by rememberNow()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (call.direction == CallDirection.INCOMING && analysis != null && call.phase != CallPhase.DISCONNECTED) {
            AgentStatusPill(analysis)
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = PhoneNumbers.display(call.number),
            style = MaterialTheme.typography.headlineMedium,
            color = TiskzColors.Ink,
        )
        val phaseText = when (call.phase) {
            CallPhase.RINGING -> "Llamada entrante"
            CallPhase.DIALING -> "Llamando…"
            CallPhase.ACTIVE -> "En llamada · ${formatDuration(((now - (call.answeredAt ?: now)) / 1000).coerceAtLeast(0))}"
            CallPhase.HOLDING -> "En espera"
            CallPhase.DISCONNECTED -> if (call.blocked) "Número bloqueado" else "Llamada finalizada"
        }
        Text(
            text = if (call.simulated) "$phaseText · Simulación" else phaseText,
            style = MaterialTheme.typography.bodyMedium,
            color = TiskzColors.Muted,
        )
    }
}

@Composable
private fun AgentStatusPill(analysis: CallAnalysis) {
    val (text, color, container) = when {
        analysis.markedSafeByUser -> Triple("Marcada como segura", TiskzColors.Blue600, TiskzColors.Blue50)
        analysis.isScam -> Triple("Posible estafa", TiskzColors.Red, TiskzColors.RedContainer)
        else -> when (analysis.status) {
            AgentStatus.CONNECTING -> Triple("Conectando con Tiskz…", TiskzColors.Blue600, TiskzColors.Blue50)
            AgentStatus.SCREENING -> Triple("Tiskz está atendiendo", TiskzColors.Blue600, TiskzColors.Blue50)
            AgentStatus.ANALYZING -> Triple("Analizando la conversación", TiskzColors.Blue600, TiskzColors.Blue50)
            AgentStatus.SAFE -> Triple("Llamada segura", TiskzColors.Blue600, TiskzColors.Blue50)
            AgentStatus.SUSPICIOUS -> Triple("Llamada sospechosa", TiskzColors.Amber, TiskzColors.AmberContainer)
            AgentStatus.SCAM -> Triple("Posible estafa", TiskzColors.Red, TiskzColors.RedContainer)
            AgentStatus.OFFLINE -> Triple("Sin conexión con el análisis", TiskzColors.Amber, TiskzColors.AmberContainer)
        }
    }
    val working = analysis.status in setOf(AgentStatus.CONNECTING, AgentStatus.SCREENING, AgentStatus.ANALYZING)
    StatusPill(text, color, container, pulsing = working && !analysis.isScam)
}

// --- Análisis en vivo ---

@Composable
private fun AnalysisPanel(analysis: CallAnalysis, answerThreshold: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        RiskGauge(percent = analysis.riskPercent, size = 184.dp)

        VerdictBanner(analysis, answerThreshold)

        if (analysis.reasons.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(analysis.reasons) { reason ->
                    Surface(color = TiskzColors.Surface, shape = RoundedCornerShape(50)) {
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TiskzColors.Ink,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        analysis.voiceMatch?.let { match ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TiskzColors.Surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Fingerprint, null, tint = TiskzColors.Red, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        text = "Huella de voz: ${(match.similarity * 100).roundToInt()}% de similitud",
                        style = MaterialTheme.typography.titleMedium,
                        color = TiskzColors.Ink,
                    )
                    Text(
                        text = "${match.label} · reportada ${match.reports} ${if (match.reports == 1) "vez" else "veces"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TiskzColors.Muted,
                    )
                }
            }
        }

        LiveTranscript(analysis.transcript, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun VerdictBanner(analysis: CallAnalysis, answerThreshold: Int) {
    val banner: Banner? = when {
        analysis.isScam -> Banner(
            icon = Icons.Rounded.GppBad,
            title = "Posible estafa",
            body = listOfNotNull(
                analysis.scamType,
                analysis.impersonating?.let { "Se hace pasar por $it" },
            ).joinToString(" · ").ifEmpty { "No entregues datos ni dinero." },
            color = TiskzColors.Red,
            container = TiskzColors.RedContainer,
        )
        analysis.markedSafeByUser -> Banner(
            icon = Icons.Rounded.GppGood,
            title = "Marcaste esta llamada como segura",
            body = "Puedes contestar cuando quieras.",
            color = TiskzColors.Blue700,
            container = TiskzColors.Blue50,
        )
        analysis.isSafeToAnswer(answerThreshold) -> Banner(
            icon = Icons.Rounded.GppGood,
            title = "Llamada segura",
            body = "Tiskz verificó el motivo. Puedes contestar.",
            color = TiskzColors.Blue700,
            container = TiskzColors.Blue50,
        )
        analysis.status == AgentStatus.OFFLINE -> Banner(
            icon = Icons.Rounded.WarningAmber,
            title = "No pudimos analizar la llamada",
            body = analysis.error ?: "Contesta con precaución.",
            color = TiskzColors.Amber,
            container = TiskzColors.AmberContainer,
        )
        else -> null
    }

    AnimatedVisibility(visible = banner != null, enter = fadeIn() + expandVertically()) {
        banner?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(it.container, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(it.icon, null, tint = it.color, modifier = Modifier.size(32.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(it.title, style = MaterialTheme.typography.titleMedium, color = it.color)
                    Text(it.body, style = MaterialTheme.typography.bodyMedium, color = TiskzColors.Ink)
                }
            }
        }
    }
}

private class Banner(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val color: Color,
    val container: Color,
)

// --- Acciones ---

@Composable
private fun CallActionBar(
    call: ActiveCall,
    analysis: CallAnalysis?,
    answerThreshold: Int,
    actions: LiveCallActions,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        when {
            call.phase == CallPhase.DISCONNECTED -> BigButton(
                text = "Cerrar",
                icon = null,
                onClick = actions.onClose,
                container = TiskzColors.Blue600,
            )

            call.phase == CallPhase.RINGING && call.direction == CallDirection.INCOMING ->
                IncomingActions(analysis, answerThreshold, actions)

            else -> InCallControls(call, actions)
        }
    }
}

@Composable
private fun IncomingActions(analysis: CallAnalysis?, answerThreshold: Int, actions: LiveCallActions) {
    when {
        analysis?.isScam == true -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Bloquear y colgar", Icons.Rounded.Block, actions.onBlock, TiskzColors.Red)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = actions.onReject, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Rechazar")
                }
                OutlinedButton(onClick = actions.onNotScam, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("No es estafa")
                }
            }
            TextButton(onClick = actions.onAnswer, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Contestar de todos modos", color = TiskzColors.Muted)
            }
        }

        analysis == null || analysis.isSafeToAnswer(answerThreshold) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = actions.onReject,
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TiskzColors.Red),
                ) {
                    Text("Rechazar")
                }
                BigButton("Contestar", Icons.Rounded.Call, actions.onAnswer, TiskzColors.Blue600, Modifier.weight(1.4f))
            }

        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Espera unos segundos: Tiskz está verificando quién llama.",
                style = MaterialTheme.typography.bodyMedium,
                color = TiskzColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = actions.onReject,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TiskzColors.Red),
                ) {
                    Text("Rechazar")
                }
                FilledTonalButton(onClick = actions.onAnswer, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("Contestar ahora")
                }
            }
        }
    }
}

@Composable
private fun InCallControls(call: ActiveCall, actions: LiveCallActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        RoundAction(
            icon = if (call.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
            label = if (call.muted) "Activar micro" else "Silenciar",
            onClick = actions.onToggleMute,
            active = call.muted,
        )
        RoundAction(
            icon = Icons.Rounded.CallEnd,
            label = "Colgar",
            onClick = actions.onHangUp,
            container = TiskzColors.Red,
            content = TiskzColors.White,
            size = 80.dp,
        )
        RoundAction(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            label = "Altavoz",
            onClick = actions.onToggleSpeaker,
            active = call.speaker,
        )
    }
}

@Composable
private fun BigButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = TiskzColors.White),
    ) {
        if (icon != null) {
            Icon(icon, null)
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    container: Color = if (active) TiskzColors.Blue600 else TiskzColors.Blue50,
    content: Color = if (active) TiskzColors.White else TiskzColors.Blue700,
    size: Dp = 68.dp,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = CircleShape, color = container, modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
    }
}
