package com.tiskz.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tiskz.app.R
import com.tiskz.app.model.Speaker
import com.tiskz.app.model.TranscriptLine
import com.tiskz.app.ui.theme.TiskzBrushes
import com.tiskz.app.ui.theme.TiskzColors
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle

/**
 * Conversación en vivo entre el agente de Tiskz y quien llama. Se desplaza sola a la última frase.
 * @param live muestra la píldora "Tiskz IA escuchando" con ondas animadas.
 */
@Composable
fun LiveTranscript(
    lines: List<TranscriptLine>,
    callerLabel: String,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val lastIndex = lines.lastIndex + if (live) 1 else 0
    LaunchedEffect(lines, live) {
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        if (live) {
            item(key = "live") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(color = TiskzColors.White, shape = RoundedCornerShape(50), modifier = Modifier.softShadow(RoundedCornerShape(50), 6.dp)) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WaveBars(TiskzColors.Blue600)
                            Spacer(Modifier.size(8.dp))
                            Text("PhoneWard IA está atendiendo", style = MaterialTheme.typography.labelLarge, color = TiskzColors.Blue700)
                        }
                    }
                }
            }
        }
        items(lines, key = { it.id }) { line ->
            // Cada frase entra deslizándose desde abajo.
            val visible = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(visibleState = visible, enter = fadeIn() + slideInVertically { it / 3 }) {
                TranscriptBubble(line, callerLabel)
            }
        }
    }
}

/** Versión sin scroll propio, para pantallas que ya hacen scroll (detalle de llamada). */
@Composable
fun StaticTranscript(lines: List<TranscriptLine>, callerLabel: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        lines.forEach { TranscriptBubble(it, callerLabel) }
    }
}

@Composable
private fun TranscriptBubble(line: TranscriptLine, callerLabel: String) {
    val fromAgent = line.speaker == Speaker.AGENT
    val shape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomStart = if (fromAgent) 22.dp else 6.dp,
        bottomEnd = if (fromAgent) 6.dp else 22.dp,
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromAgent) Alignment.End else Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            when {
                fromAgent -> {
                    Icon(painterResource(R.drawable.ic_tiskz_shield), null, tint = TiskzColors.Blue600, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("PhoneWard IA", style = MaterialTheme.typography.labelLarge, color = TiskzColors.Blue600)
                }
                line.flagged -> {
                    Icon(TablerIcons.AlertTriangle, null, tint = TiskzColors.Red, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("$callerLabel · sospechoso", style = MaterialTheme.typography.labelLarge, color = TiskzColors.Red)
                }
                line.speaker == Speaker.USER ->
                    Text("Tú", style = MaterialTheme.typography.labelLarge, color = TiskzColors.Muted)
                else ->
                    Text(callerLabel, style = MaterialTheme.typography.labelLarge, color = TiskzColors.Muted)
            }
        }
        val bubble = Modifier
            .widthIn(max = 300.dp)
            .then(if (fromAgent) Modifier else Modifier.softShadow(shape, 6.dp))
            .clip(shape)
            .then(
                when {
                    fromAgent -> Modifier.background(TiskzBrushes.AgentBubble)
                    line.flagged -> Modifier.background(TiskzColors.RedContainer).border(1.5.dp, TiskzColors.Red.copy(alpha = 0.5f), shape)
                    else -> Modifier.background(TiskzColors.White)
                }
            )
        Box(bubble) {
            Text(
                text = line.text,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    fromAgent -> TiskzColors.White
                    line.flagged -> TiskzColors.RedDark
                    else -> TiskzColors.Ink
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
