package com.tiskz.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiskz.app.call.CallManager
import com.tiskz.app.data.DemoScenario
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallRecord
import com.tiskz.app.model.Verdict
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.ui.components.TiskzLogo
import com.tiskz.app.ui.components.VerdictBadge
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.ui.theme.color
import com.tiskz.app.ui.theme.containerColor
import com.tiskz.app.util.DialerRole
import com.tiskz.app.util.PhoneNumbers
import com.tiskz.app.util.formatWhen

private const val DEMO_SCAM_NUMBER = "+56987654321"
private const val DEMO_SAFE_NUMBER = "+56912345678"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenDialer: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onOpenVoice: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val records by container.history.records.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()

    var roleHeld by remember { mutableStateOf(DialerRole.isHeld(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { roleHeld = DialerRole.isHeld(context) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = DialerRole.isHeld(context)
    }

    Scaffold(
        containerColor = TiskzColors.White,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TiskzLogo(size = 34.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Tiskz", style = MaterialTheme.typography.titleLarge, color = TiskzColors.Blue700)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Ajustes", tint = TiskzColors.Ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TiskzColors.White),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenDialer,
                containerColor = TiskzColors.Blue600,
                contentColor = TiskzColors.White,
            ) {
                Icon(Icons.Rounded.Dialpad, contentDescription = "Marcar")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ProtectionCard(
                    active = roleHeld,
                    demoMode = settings.demoMode,
                    onActivate = { DialerRole.requestIntent(context)?.let(roleLauncher::launch) },
                )
            }
            item {
                Surface(color = TiskzColors.Blue50, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Canal de voz en vivo", style = MaterialTheme.typography.titleMedium)
                        Text("Envía el micrófono a los agentes y recibe transcripción y alertas reales.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onOpenVoice, modifier = Modifier.fillMaxWidth()) { Text("Abrir canal de voz") }
                    }
                }
            }
            item {
                DemoCard(
                    onSimulateScam = { CallManager.simulateIncoming(DEMO_SCAM_NUMBER, DemoScenario.SCAM) },
                    onSimulateSafe = { CallManager.simulateIncoming(DEMO_SAFE_NUMBER, DemoScenario.SAFE) },
                )
            }
            item {
                Text(
                    text = "Llamadas recientes",
                    style = MaterialTheme.typography.titleMedium,
                    color = TiskzColors.Ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (records.isEmpty()) {
                item {
                    Text(
                        text = "Aún no hay llamadas. Prueba con una simulación.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TiskzColors.Muted,
                    )
                }
            } else {
                items(records, key = { it.id }) { record ->
                    CallRecordRow(record, onClick = { onOpenRecord(record.id) })
                }
            }
        }
    }
}

@Composable
private fun ProtectionCard(active: Boolean, demoMode: Boolean, onActivate: () -> Unit) {
    val color = if (active) TiskzColors.Blue600 else TiskzColors.Amber
    Surface(
        color = if (active) TiskzColors.Blue600 else TiskzColors.AmberContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (active) Icons.Rounded.VerifiedUser else Icons.Rounded.GppMaybe,
                    contentDescription = null,
                    tint = if (active) TiskzColors.White else color,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = if (active) "Protección activa" else "Protección desactivada",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (active) TiskzColors.White else TiskzColors.Ink,
                    )
                    Text(
                        text = when {
                            !active -> "Tiskz aún no es tu app de teléfono."
                            demoMode -> "Tiskz atiende tus llamadas · modo demo"
                            else -> "Tiskz atiende tus llamadas"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) TiskzColors.Blue50 else TiskzColors.Ink,
                    )
                }
            }
            if (!active) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onActivate,
                    colors = ButtonDefaults.buttonColors(containerColor = TiskzColors.Blue600),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Activar ahora")
                }
            }
        }
    }
}

@Composable
private fun DemoCard(onSimulateScam: () -> Unit, onSimulateSafe: () -> Unit) {
    Surface(color = TiskzColors.Blue50, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Probar Tiskz", style = MaterialTheme.typography.titleMedium, color = TiskzColors.Blue900)
            Text(
                text = "Simula una llamada entrante y mira cómo Tiskz la analiza.",
                style = MaterialTheme.typography.bodyMedium,
                color = TiskzColors.Ink,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSimulateScam,
                    colors = ButtonDefaults.buttonColors(containerColor = TiskzColors.Red),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text("Estafa")
                }
                OutlinedButton(onClick = onSimulateSafe, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Llamada segura")
                }
            }
        }
    }
}

@Composable
private fun CallRecordRow(record: CallRecord, onClick: () -> Unit) {
    val icon = when (record.verdict) {
        Verdict.SCAM -> Icons.Rounded.GppBad
        Verdict.SAFE -> Icons.Rounded.GppGood
        Verdict.SUSPICIOUS -> Icons.Rounded.GppMaybe
        Verdict.NONE -> Icons.Rounded.Phone
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).background(record.verdict.containerColor(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = record.verdict.color())
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(PhoneNumbers.display(record.number), style = MaterialTheme.typography.titleMedium, color = TiskzColors.Ink)
            val detail = buildList {
                add(if (record.direction == CallDirection.INCOMING) "Entrante" else "Saliente")
                if (record.blocked) add("Bloqueada")
                add(formatWhen(record.startedAt))
            }.joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
        }
        if (record.verdict != Verdict.NONE) {
            Column(horizontalAlignment = Alignment.End) {
                VerdictBadge(record.verdict)
                Text(
                    text = "${record.riskPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TiskzColors.Muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
