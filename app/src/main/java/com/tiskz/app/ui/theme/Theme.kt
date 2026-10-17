package com.tiskz.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tiskz.app.model.RiskLevel
import com.tiskz.app.model.Verdict

object TiskzColors {
    val Blue900 = Color(0xFF0B2A6F)
    val Blue700 = Color(0xFF1D4ED8)
    val Blue600 = Color(0xFF2563EB)
    val Blue100 = Color(0xFFDBEAFE)
    val Blue50 = Color(0xFFEFF6FF)
    val White = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF64748B)
    val Line = Color(0xFFE2E8F0)
    val Surface = Color(0xFFF8FAFC)
    val Amber = Color(0xFFD97706)
    val AmberContainer = Color(0xFFFEF3C7)
    val Orange = Color(0xFFEA580C)
    val Red = Color(0xFFDC2626)
    val RedContainer = Color(0xFFFEE2E2)
}

/** Azul = tranquilo. Solo cambia a ámbar/rojo cuando hay riesgo. */
fun RiskLevel.color(): Color = when (this) {
    RiskLevel.LOW -> TiskzColors.Blue600
    RiskLevel.MEDIUM -> TiskzColors.Amber
    RiskLevel.HIGH -> TiskzColors.Orange
    RiskLevel.CRITICAL -> TiskzColors.Red
}

fun Verdict.color(): Color = when (this) {
    Verdict.SAFE -> TiskzColors.Blue600
    Verdict.SUSPICIOUS -> TiskzColors.Amber
    Verdict.SCAM -> TiskzColors.Red
    Verdict.NONE -> TiskzColors.Muted
}

fun Verdict.containerColor(): Color = when (this) {
    Verdict.SAFE -> TiskzColors.Blue50
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
    background = TiskzColors.White,
    onBackground = TiskzColors.Ink,
    surface = TiskzColors.White,
    onSurface = TiskzColors.Ink,
    surfaceVariant = TiskzColors.Surface,
    onSurfaceVariant = TiskzColors.Muted,
    surfaceContainerLowest = TiskzColors.White,
    surfaceContainerLow = TiskzColors.Surface,
    surfaceContainer = TiskzColors.Surface,
    surfaceContainerHigh = TiskzColors.Surface,
    surfaceContainerHighest = TiskzColors.Blue50,
    error = TiskzColors.Red,
    onError = TiskzColors.White,
    errorContainer = TiskzColors.RedContainer,
    onErrorContainer = Color(0xFF7F1D1D),
    outline = TiskzColors.Line,
    outlineVariant = TiskzColors.Line,
)

// Tipografía un punto más grande que la estándar: pensada para adultos mayores.
private val base = Typography()
private val TiskzTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = base.bodySmall.copy(fontSize = 14.sp),
    labelLarge = base.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun TiskzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = TiskzTypography, content = content)
}
