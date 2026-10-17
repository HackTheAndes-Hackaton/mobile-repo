package com.tiskz.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tiskz.app.model.RiskLevel
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.ui.theme.color
import kotlin.math.roundToInt

/** Medidor circular del porcentaje de estafa. Se anima cuando el backend actualiza el riesgo. */
@Composable
fun RiskGauge(
    percent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val progress by animateFloatAsState(percent.coerceIn(0, 100) / 100f, tween(800), label = "risk")
    val level = RiskLevel.of((progress * 100).roundToInt())
    val color by animateColorAsState(level.color(), tween(500), label = "riskColor")

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = TiskzColors.Blue50,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.displayMedium,
                color = color,
            )
            Text(
                text = "Riesgo de estafa",
                style = MaterialTheme.typography.bodyMedium,
                color = TiskzColors.Muted,
            )
            Text(
                text = level.label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
    }
}

private const val START_ANGLE = 140f
private const val SWEEP = 260f
