package com.tiskz.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiskz.app.R
import com.tiskz.app.model.Verdict
import com.tiskz.app.ui.theme.TiskzBrushes
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.ui.theme.color
import com.tiskz.app.ui.theme.containerColor
import compose.icons.TablerIcons
import compose.icons.tablericons.User
import kotlinx.coroutines.delay

/** Sombra suave teñida de azul, usada por todas las tarjetas. */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 14.dp): Modifier =
    shadow(elevation, shape, ambientColor = TiskzColors.Shadow.copy(alpha = 0.08f), spotColor = TiskzColors.Shadow.copy(alpha = 0.18f))

/** Sube el elemento [amount] sobre el anterior sin dejar hueco debajo. */
fun Modifier.overlapUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
        placeable.place(0, -shift)
    }
}

/** Tarjeta blanca redondeada con sombra suave. */
@Composable
fun TiskzCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .softShadow(shape)
            .clip(shape)
            .background(TiskzColors.White),
    ) { content() }
}

@Composable
fun TiskzLogo(size: Dp = 72.dp, onDark: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(if (onDark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.12f))) else TiskzBrushes.Brand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tiskz_shield),
            contentDescription = null,
            tint = TiskzColors.White,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** Círculo con las iniciales del contacto, o un ícono de persona si el número no está guardado. */
@Composable
fun CallerAvatar(
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    background: Brush = Brush.linearGradient(listOf(TiskzColors.Blue100, TiskzColors.Blue50)),
    content: Color = TiskzColors.Blue700,
) {
    val initials = name
        ?.split(' ')
        // Solo palabras que empiezan con letra o número: un emoji no sirve como inicial.
        ?.filter { it.isNotBlank() && it.first().isLetterOrDigit() }
        ?.take(2)
        ?.joinToString("") { it.first().uppercase() }
        ?.takeIf { it.isNotEmpty() }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null) {
            Text(
                text = initials,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.Bold,
                color = content,
            )
        } else {
            Icon(TablerIcons.User, null, tint = content, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** Anillos que se expanden y desvanecen: "algo está pasando" (escudo activo, llamada sonando). */
@Composable
fun PulsingRings(color: Color, modifier: Modifier = Modifier, rings: Int = 3) {
    val transition = rememberInfiniteTransition(label = "rings")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "ringProgress",
    )
    Canvas(modifier) {
        val maxRadius = size.minDimension / 2
        repeat(rings) { i ->
            val p = (progress + i.toFloat() / rings) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.35f),
                radius = maxRadius * (0.45f + 0.55f * p),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/** Barras de audio animadas: el agente está escuchando. */
@Composable
fun WaveBars(color: Color, modifier: Modifier = Modifier, bars: Int = 4) {
    val transition = rememberInfiniteTransition(label = "wave")
    Row(modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(bars) { i ->
            val fraction by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(420 + i * 110), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
fun VerdictBadge(verdict: Verdict, modifier: Modifier = Modifier, text: String = verdict.label) {
    Surface(color = verdict.containerColor(), shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = verdict.color(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
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
