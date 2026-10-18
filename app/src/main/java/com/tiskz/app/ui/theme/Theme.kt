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

object PhoneWardColors {
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

object PhoneWardBrushes {
    val Brand = Brush.linearGradient(listOf(PhoneWardColors.Blue900, PhoneWardColors.Blue600, PhoneWardColors.Sky))
    val Danger = Brush.linearGradient(listOf(PhoneWardColors.RedDark, PhoneWardColors.Red, Color(0xFFFB7185)))
    val Safe = Brush.linearGradient(listOf(PhoneWardColors.EmeraldDark, PhoneWardColors.Emerald, Color(0xFF6EE7B7)))
    val AgentBubble = Brush.linearGradient(listOf(PhoneWardColors.Blue700, PhoneWardColors.Blue600))
}

/** Verde = tranquilo, ámbar/naranja = atención, rojo = estafa. */
fun RiskLevel.color(): Color = when (this) {
    RiskLevel.LOW -> PhoneWardColors.Emerald
    RiskLevel.MEDIUM -> PhoneWardColors.Amber
    RiskLevel.HIGH -> PhoneWardColors.Orange
    RiskLevel.CRITICAL -> PhoneWardColors.Red
}

fun Verdict.color(): Color = when (this) {
    Verdict.SAFE -> PhoneWardColors.EmeraldDark
    Verdict.SUSPICIOUS -> PhoneWardColors.AmberDark
    Verdict.SCAM -> PhoneWardColors.Red
    Verdict.NONE -> PhoneWardColors.Muted
}

fun Verdict.containerColor(): Color = when (this) {
    Verdict.SAFE -> PhoneWardColors.EmeraldContainer
    Verdict.SUSPICIOUS -> PhoneWardColors.AmberContainer
    Verdict.SCAM -> PhoneWardColors.RedContainer
    Verdict.NONE -> PhoneWardColors.Surface
}

private val LightColors = lightColorScheme(
    primary = PhoneWardColors.Blue600,
    onPrimary = PhoneWardColors.White,
    primaryContainer = PhoneWardColors.Blue100,
    onPrimaryContainer = PhoneWardColors.Blue900,
    secondary = PhoneWardColors.Blue700,
    onSecondary = PhoneWardColors.White,
    secondaryContainer = PhoneWardColors.Blue50,
    onSecondaryContainer = PhoneWardColors.Blue900,
    background = PhoneWardColors.Background,
    onBackground = PhoneWardColors.Ink,
    surface = PhoneWardColors.White,
    onSurface = PhoneWardColors.Ink,
    surfaceVariant = PhoneWardColors.Surface,
    onSurfaceVariant = PhoneWardColors.Muted,
    surfaceContainerLowest = PhoneWardColors.White,
    surfaceContainerLow = PhoneWardColors.Background,
    surfaceContainer = PhoneWardColors.White,
    surfaceContainerHigh = PhoneWardColors.White,
    surfaceContainerHighest = PhoneWardColors.Blue50,
    error = PhoneWardColors.Red,
    onError = PhoneWardColors.White,
    errorContainer = PhoneWardColors.RedContainer,
    onErrorContainer = PhoneWardColors.RedDark,
    outline = PhoneWardColors.Line,
    outlineVariant = PhoneWardColors.Line,
)

// Un punto más grande que la estándar: pensada para adultos mayores.
private val base = Typography()
private val PhoneWardTypography = Typography(
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
fun PhoneWardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = PhoneWardTypography, content = content)
}
