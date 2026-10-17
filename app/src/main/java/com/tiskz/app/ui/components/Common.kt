package com.tiskz.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tiskz.app.R
import com.tiskz.app.model.Verdict
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.ui.theme.color
import com.tiskz.app.ui.theme.containerColor
import kotlinx.coroutines.delay

@Composable
fun TiskzLogo(size: Dp = 72.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(TiskzColors.Blue600, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tiskz_shield),
            contentDescription = null,
            tint = TiskzColors.White,
            modifier = Modifier.size(size * 0.58f),
        )
    }
}

@Composable
fun VerdictBadge(verdict: Verdict, modifier: Modifier = Modifier) {
    Surface(color = verdict.containerColor(), shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text = verdict.label,
            style = MaterialTheme.typography.labelLarge,
            color = verdict.color(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Píldora de estado con un punto que late mientras el agente trabaja. */
@Composable
fun StatusPill(text: String, color: Color, container: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Surface(color = container, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .alpha(if (pulsing) pulse else 1f)
                    .background(color, CircleShape)
            )
            Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
fun rememberNow(intervalMs: Long = 1000L): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(intervalMs)
        value = System.currentTimeMillis()
    }
}
