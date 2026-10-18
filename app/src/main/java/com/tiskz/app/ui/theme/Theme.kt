package com.tiskz.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tiskz.app.model.RiskLevel
import com.tiskz.app.model.Verdict

object TiskzColors {
    // Marca: azul profundo → azul → celeste.
    val Navy = Color(0xFF0B1B4D)
    val Blue900 = Color(0xFF1E3A8A)
    val Blue700 = Color(0xFF1D4ED8)
    val Blue600 = Color(0xFF2563EB)
    val Sky = Color(0xFF38BDF8)
    val Blue100 = Color(0xFFDBEAFE)
    val Blue50 = Color(0xFFEFF6FF)

    // Neutros. El fondo es azulado para que las tarjetas blancas resalten.
    val White = Color(0xFFFFFFFF)
    val Background = Color(0xFFF3F6FC)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF64748B)
    val Line = Color(0xFFE2E8F0)
    val Surface = Color(0xFFF1F5F9)

    // Semántica de riesgo.
    val Emerald = Color(0xFF10B981)
    val EmeraldDark = Color(0xFF047857)
    val EmeraldContainer = Color(0xFFD1FAE5)
    val Amber = Color(0xFFF59E0B)
    val AmberDark = Color(0xFFB45309)
    val AmberContainer = Color(0xFFFEF3C7)
    val Orange = Color(0xFFF97316)
    val Red = Color(0xFFE11D48)
    val RedDark = Color(0xFF9F1239)
    val RedContainer = Color(0xFFFFE4E6)

    /** Sombra teñida de azul: da profundidad sin ensuciar el blanco. */
    val Shadow = Color(0xFF1E3A8A)
}

object TiskzBrushes {
    val Brand = Brush.linearGradient(listOf(TiskzColors.Blue900, TiskzColors.Blue600, TiskzColors.Sky))
    val Danger = Brush.linearGradient(listOf(TiskzColors.RedDark, TiskzColors.Red, Color(0xFFFB7185)))
    val Safe = Brush.linearGradient(listOf(TiskzColors.EmeraldDark, TiskzColors.Emerald, Color(0xFF6EE7B7)))
    val AgentBubble = Brush.linearGradient(listOf(TiskzColors.Blue700, TiskzColors.Blue600))
}

/** Verde = tranquilo, ámbar/naranja = atención, rojo = estafa. */
fun RiskLevel.color(): Color = when (this) {
    RiskLevel.LOW -> TiskzColors.Emerald
    RiskLevel.MEDIUM -> TiskzColors.Amber
    RiskLevel.HIGH -> TiskzColors.Orange
    RiskLevel.CRITICAL -> TiskzColors.Red
}

fun Verdict.color(): Color = when (this) {
    Verdict.SAFE -> TiskzColors.EmeraldDark
    Verdict.SUSPICIOUS -> TiskzColors.AmberDark
    Verdict.SCAM -> TiskzColors.Red
    Verdict.NONE -> TiskzColors.Muted
}

fun Verdict.containerColor(): Color = when (this) {
    Verdict.SAFE -> TiskzColors.EmeraldContainer
    Verdict.SUSPICIOUS -> TiskzColors.AmberContainer
    Verdict.SCAM -> TiskzColors.RedContainer
    Verdict.NONE -> TiskzColors.Surface
}

private val LightColors = lightColorScheme(
    primary = TiskzColors.Blue600,
    onPrimary = TiskzColors.White,
    primaryContainer = TiskzColors.Blue100,
    onPrimaryContainer = TiskzColors.Blue900,
    secondary = TiskzColors.Blue700,
    onSecondary = TiskzColors.White,
    secondaryContainer = TiskzColors.Blue50,
    onSecondaryContainer = TiskzColors.Blue900,
    background = TiskzColors.Background,
    onBackground = TiskzColors.Ink,
    surface = TiskzColors.White,
    onSurface = TiskzColors.Ink,
    surfaceVariant = TiskzColors.Surface,
    onSurfaceVariant = TiskzColors.Muted,
    surfaceContainerLowest = TiskzColors.White,
    surfaceContainerLow = TiskzColors.Background,
    surfaceContainer = TiskzColors.White,
    surfaceContainerHigh = TiskzColors.White,
    surfaceContainerHighest = TiskzColors.Blue50,
    error = TiskzColors.Red,
    onError = TiskzColors.White,
    errorContainer = TiskzColors.RedContainer,
    onErrorContainer = TiskzColors.RedDark,
    outline = TiskzColors.Line,
    outlineVariant = TiskzColors.Line,
)

// Un punto más grande que la estándar: pensada para adultos mayores.
private val base = Typography()
private val TiskzTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp),
    labelLarge = base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun TiskzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = TiskzTypography, content = content)
}
