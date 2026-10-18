package com.tiskz.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiskz.app.data.ArchiveState
import com.tiskz.app.model.Verdict
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.ui.components.RiskBar
import com.tiskz.app.ui.components.StaticTranscript
import com.tiskz.app.ui.components.VerdictBadge
import com.tiskz.app.ui.theme.PhoneWardColors
import com.tiskz.app.util.PhoneNumbers
import com.tiskz.app.util.formatDuration
import com.tiskz.app.util.formatWhen
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(recordId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val records by container.history.records.collectAsStateWithLifecycle()
    val archived by container.archive.states.collectAsStateWithLifecycle()
    val record = records.firstOrNull { it.id == recordId }

    Scaffold(
        containerColor = PhoneWardColors.White,
        topBar = {
            TopAppBar(
                title = { Text("Detalle de la llamada") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PhoneWardColors.White),
            )
        },
    ) { padding ->
        if (record == null) {
            Text(
                text = "No encontramos esta llamada.",
                modifier = Modifier.padding(padding).padding(20.dp),
                color = PhoneWardColors.Muted,
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(record.title, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = buildList {
                    if (record.callerName != null) add(PhoneNumbers.display(record.number))
                    add(formatWhen(record.startedAt))
                    if (record.durationSec > 0) add("Duración ${formatDuration(record.durationSec)}")
                    if (record.blocked) add("Bloqueada")
                    if (record.simulated) add("Simulación")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneWardColors.Muted,
            )
            VerdictBadge(record.verdict)
            archived[record.id]?.let { state ->
                Text(
                    text = when (state) {
                        ArchiveState.SAVED -> "✓ Conversación guardada en el panel"
                        ArchiveState.PENDING -> "Guardando en el panel…"
                        ArchiveState.FAILED -> "No se pudo guardar en el panel. Revisa Ajustes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state) {
                        ArchiveState.SAVED -> PhoneWardColors.EmeraldDark
                        ArchiveState.PENDING -> PhoneWardColors.Muted
                        ArchiveState.FAILED -> PhoneWardColors.Red
                    },
                )
            }

            if (record.verdict != Verdict.NONE) {
                RiskBar(
                    percent = record.riskPercent,
                    headline = record.verdict.label,
                    detail = listOfNotNull(
                        record.scamType,
                        record.impersonating?.let { "se hizo pasar por $it" },
                    ).joinToString(" · ").ifEmpty { record.reasons.firstOrNull() },
                )
            }

            if (record.transcript.isNotEmpty()) {
                Section("Conversación") {
                    StaticTranscript(record.transcript, record.callerName ?: "Quien llama", Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = PhoneWardColors.Blue700)
        content()
    }
}
