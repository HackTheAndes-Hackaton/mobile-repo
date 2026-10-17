package com.tiskz.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiskz.app.model.RiskLevel
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.ui.theme.color
import kotlin.math.roundToInt

/** Tarjeta de riesgo: titular, porcentaje grande y barra que cambia de verde a rojo según el nivel. */
@Composable
fun RiskBar(
    percent: Int,
    headline: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    /** Color del titular; por defecto el del nivel de riesgo. */
    headlineColor: Color? = null,
) {
    val progress by animateFloatAsState(percent.coerceIn(0, 100) / 100f, tween(700), label = "risk")
    val color by animateColorAsState(RiskLevel.of(percent).color(), tween(500), label = "riskColor")

    TiskzCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "RIESGO DE ESTAFA",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = TiskzColors.Muted,
                    )
                    Text(headline, style = MaterialTheme.typography.titleLarge, color = headlineColor ?: color)
                }
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(TiskzColors.Surface),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color))),
                )
            }
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TiskzColors.Muted,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
