package com.tiskz.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.tiskz.app.model.FraudModality
import com.tiskz.app.ui.theme.TiskzColors

/** Tipo y motivo breve para quien recibe la llamada. La bibliografía queda en la documentación. */
@Composable
fun FraudModalityDetails(modalities: List<FraudModality>, expanded: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        modalities.take(if (expanded) 3 else 1).forEach { modality ->
            Text("${if (modality.hypothesis) "Tipo provisional" else "Señal"}: ${modality.label}",
                style = MaterialTheme.typography.labelLarge,
                color = if (modality.hypothesis) TiskzColors.Red else TiskzColors.Ink)
            if (expanded) {
                Text(modality.explanation, style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
            }
        }
        if (expanded && modalities.isNotEmpty()) {
            Text("Evaluación provisional de la llamada.",
                style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
        }
    }
}
