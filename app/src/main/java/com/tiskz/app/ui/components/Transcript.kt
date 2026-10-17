package com.tiskz.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiskz.app.model.Speaker
import com.tiskz.app.model.TranscriptLine
import com.tiskz.app.ui.theme.TiskzColors

/** Transcripción en vivo. Se desplaza sola a la última frase. */
@Composable
fun LiveTranscript(lines: List<TranscriptLine>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(lines, key = { it.id }) { TranscriptBubble(it) }
    }
}

/** Versión sin scroll propio, para pantallas que ya hacen scroll (detalle de llamada). */
@Composable
fun StaticTranscript(lines: List<TranscriptLine>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lines.forEach { TranscriptBubble(it) }
    }
}

@Composable
private fun TranscriptBubble(line: TranscriptLine) {
    val fromAgent = line.speaker == Speaker.AGENT
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (fromAgent) 18.dp else 4.dp,
        bottomEnd = if (fromAgent) 4.dp else 18.dp,
    )
    val label = when (line.speaker) {
        Speaker.AGENT -> "Tiskz"
        Speaker.CALLER -> "Quien llama"
        Speaker.USER -> "Tú"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromAgent) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TiskzColors.Muted,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Surface(
            shape = shape,
            color = when {
                line.flagged -> TiskzColors.RedContainer
                fromAgent -> TiskzColors.Blue100
                else -> TiskzColors.Surface
            },
            modifier = Modifier
                .widthIn(max = 320.dp)
                .then(if (line.flagged) Modifier.border(1.5.dp, TiskzColors.Red, shape) else Modifier),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                if (line.flagged) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = "Frase sospechosa",
                        tint = TiskzColors.Red,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(line.text, style = MaterialTheme.typography.bodyLarge, color = TiskzColors.Ink)
            }
        }
    }
}
