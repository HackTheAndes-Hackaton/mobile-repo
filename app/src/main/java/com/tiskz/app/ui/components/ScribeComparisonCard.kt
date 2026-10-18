package com.tiskz.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiskz.app.model.ScribeComparison
import kotlin.math.roundToInt

@Composable
fun ScribeComparisonCard(value: ScribeComparison?, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (value == null) return
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text("Scribe · comparación en vivo", style = MaterialTheme.typography.titleSmall)
            Text(when (value.status) {
                "starting" -> "Conectando transcripción paralela…"
                "streaming" -> "Escuchando solo a quien llama"
                "stopped" -> "Comparación terminada"
                else -> "Comparación no disponible · la llamada principal continúa"
            }, style = MaterialTheme.typography.bodySmall)
            value.lines.takeLast(if (compact) 1 else 3).forEach { line ->
                Text((if (line.final) "Confirmado: " else "Provisional: ") + line.text,
                    style = MaterialTheme.typography.bodyMedium, maxLines = if (compact) 3 else 6)
            }
            value.alert?.let { alert ->
                Text("Alerta comparativa: ${if (alert) "Sí" else "No"} · ${((value.probability ?: 0.0) * 100).roundToInt()} %" +
                    (value.analysisMs?.let { " · análisis ${it} ms" } ?: ""), style = MaterialTheme.typography.labelSmall)
            }
            Text("Experimental · no controla el corte", style = MaterialTheme.typography.labelSmall)
        }
    }
}
