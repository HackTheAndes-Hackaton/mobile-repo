package com.tiskz.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.tiskz.app.model.FraudModality
import com.tiskz.app.model.fraudSources
import com.tiskz.app.ui.theme.TiskzColors

/** Tipo provisional, cita de la llamada y base documental, sin otra inferencia en el móvil. */
@Composable
fun FraudModalityDetails(modalities: List<FraudModality>, expanded: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        modalities.take(if (expanded) 3 else 1).forEach { modality ->
            Text("${if (modality.hypothesis) "Tipo provisional" else "Señal"}: ${modality.label}",
                style = MaterialTheme.typography.labelLarge,
                color = if (modality.hypothesis) TiskzColors.Red else TiskzColors.Ink)
            if (expanded) {
                modality.quotes.take(2).forEach { quote ->
                    Text("Texto recibido: “$quote”", style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
                }
            }
        }
        if (expanded && modalities.isNotEmpty()) {
            Text("Clasificación preventiva; no confirma un delito. Las fuentes orientan los criterios, no certifican esta llamada.",
                style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
            modalities.flatMap { it.sources }.distinct().forEach { id ->
                fraudSources[id]?.let { source ->
                    TextButton(onClick = { uriHandler.openUri(source.url) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Fuente: ${source.label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
