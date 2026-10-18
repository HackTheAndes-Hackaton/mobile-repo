package com.tiskz.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiskz.app.BuildConfig
import com.tiskz.app.data.DemoScenario
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.ui.theme.PhoneWardColors
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val blocked by container.blocklist.blocked.collectAsStateWithLifecycle()
    val records by container.history.records.collectAsStateWithLifecycle()
    val repo = container.settings

    Scaffold(
        containerColor = PhoneWardColors.White,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PhoneWardColors.White),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AutomaticConferenceSettings()
            HorizontalDivider(color = PhoneWardColors.Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo demo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Analiza las llamadas con guiones de ejemplo, sin backend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PhoneWardColors.Muted,
                    )
                }
                Switch(checked = settings.demoMode, onCheckedChange = { on -> repo.update { it.copy(demoMode = on) } })
            }

            if (settings.demoMode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escenario para llamadas reales", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        DemoScenario.entries.forEachIndexed { index, scenario ->
                            SegmentedButton(
                                selected = settings.demoScenario == scenario,
                                onClick = { repo.update { it.copy(demoScenario = scenario) } },
                                shape = SegmentedButtonDefaults.itemShape(index, DemoScenario.entries.size),
                            ) {
                                Text(scenario.label)
                            }
                        }
                    }
                    Text(
                        text = "Automático: contactos guardados = segura; otros números terminados en par = segura, impar = estafa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PhoneWardColors.Muted,
                    )
                }
            }

            HorizontalDivider(color = PhoneWardColors.Line)

            Text("Conexión", style = MaterialTheme.typography.titleMedium)
            UrlField("WebSocket de los agentes", settings.wsUrl) { url -> repo.update { it.copy(wsUrl = url) } }
            UrlField("Backend (REST)", settings.backendUrl) { url -> repo.update { it.copy(backendUrl = url) } }

            HorizontalDivider(color = PhoneWardColors.Line)

            PanelSettings()

            HorizontalDivider(color = PhoneWardColors.Line)

            Column {
                Text("Umbral para contestar: ${settings.answerThreshold}%", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "PhoneWard destaca \"Contestar\" solo si el riesgo es menor a este valor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PhoneWardColors.Muted,
                )
                Slider(
                    value = settings.answerThreshold.toFloat(),
                    onValueChange = { v -> repo.update { it.copy(answerThreshold = v.roundToInt()) } },
                    valueRange = 10f..60f,
                    steps = 9,
                )
            }

            HorizontalDivider(color = PhoneWardColors.Line)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = container.blocklist::clear, enabled = blocked.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Desbloquear (${blocked.size})")
                }
                OutlinedButton(onClick = container.history::clear, enabled = records.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Borrar historial")
                }
            }

            Text(
                text = "PhoneWard ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = PhoneWardColors.Muted,
            )
        }
    }
}

/** Campo con estado local: escribir directo sobre el StateFlow hace que se pierdan teclas. */
@Composable
internal fun UrlField(label: String, initial: String, onChange: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it.trim())
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}
