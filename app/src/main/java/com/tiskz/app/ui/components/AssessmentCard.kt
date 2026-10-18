package com.tiskz.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tiskz.app.model.AssessmentState
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.ui.theme.PhoneWardColors

/** Solo presenta orientación: no cambia alertas, porcentajes ni ejecuta acciones. */
@Composable
fun AssessmentCard(analysis: CallAnalysis, modifier: Modifier = Modifier, compact: Boolean = false) {
    val assessment = analysis.currentAssessment
    val waitingAlert = assessment == null && analysis.alert == true
    val (background, foreground) = when {
        assessment?.state == AssessmentState.ALERT || waitingAlert -> PhoneWardColors.RedContainer to PhoneWardColors.RedDark
        assessment?.state == AssessmentState.VERIFY -> PhoneWardColors.AmberContainer to PhoneWardColors.AmberDark
        assessment?.state == AssessmentState.OBSERVE -> PhoneWardColors.Blue50 to PhoneWardColors.Blue700
        else -> PhoneWardColors.Surface to PhoneWardColors.Ink
    }
    val title = assessment?.state?.label ?: when {
        waitingAlert -> "Alerta preventiva · orientación pendiente"
        analysis.error != null -> "Orientación no disponible"
        analysis.assessment != null -> "Actualizando orientación"
        else -> "Esperando orientación"
    }
    val guidance = assessment?.guidance ?: when {
        waitingAlert -> "Hay una alerta del detector. Espera la explicación y verifica por un canal conocido."
        analysis.error != null -> "No se pudo completar el análisis. Esto no indica ausencia de riesgo."
        else -> "Esperando el análisis del fragmento actual. No se ha confirmado que la llamada sea segura."
    }
    Surface(modifier.fillMaxWidth(), color = background, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(if (compact) 12.dp else 20.dp).semantics {
            liveRegion = if (assessment?.state == AssessmentState.ALERT || waitingAlert) LiveRegionMode.Assertive else LiveRegionMode.Polite
        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, color = foreground)
            Text(guidance, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, color = PhoneWardColors.Ink)
            if (!compact) Text("Orientación provisional. La identidad y el origen de la voz no se han verificado.",
                style = MaterialTheme.typography.bodySmall, color = PhoneWardColors.Muted)
        }
    }
}
