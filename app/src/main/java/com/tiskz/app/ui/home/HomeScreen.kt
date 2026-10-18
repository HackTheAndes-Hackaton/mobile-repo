package com.tiskz.app.ui.home

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextAlign
import compose.icons.tablericons.ChevronLeft
import compose.icons.tablericons.ChevronRight
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiskz.app.R
import com.tiskz.app.data.ContactLookup
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallRecord
import com.tiskz.app.model.Verdict
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.ui.components.CallerAvatar
import com.tiskz.app.ui.components.PulsingRings
import com.tiskz.app.ui.components.TiskzCard
import com.tiskz.app.ui.components.TiskzLogo
import com.tiskz.app.ui.components.overlapUp
import com.tiskz.app.ui.theme.TiskzBrushes
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.ui.theme.color
import com.tiskz.app.ui.theme.containerColor
import com.tiskz.app.util.DialerRole
import com.tiskz.app.util.PhoneNumbers
import com.tiskz.app.util.formatWhen
import compose.icons.TablerIcons
import compose.icons.tablericons.Activity
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.GridDots
import compose.icons.tablericons.Phone
import compose.icons.tablericons.PhoneIncoming
import compose.icons.tablericons.PhoneOutgoing
import compose.icons.tablericons.Settings
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.ShieldOff
import compose.icons.tablericons.ShieldX
import compose.icons.tablericons.Users

private val STATS_OVERLAP = 44.dp

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
    var contactsAllowed by remember { mutableStateOf(ContactLookup.hasPermission(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        roleHeld = DialerRole.isHeld(context)
        contactsAllowed = ContactLookup.hasPermission(context)
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = DialerRole.isHeld(context)
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsAllowed = granted
    }

    LightStatusBarIcons()

    Scaffold(
        containerColor = TiskzColors.Background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenDialer,
                shape = CircleShape,
                containerColor = Color.Transparent,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .size(64.dp)
                    .shadow(18.dp, CircleShape, spotColor = TiskzColors.Blue600),
            ) {
                Box(Modifier.fillMaxSize().background(TiskzBrushes.Brand), contentAlignment = Alignment.Center) {
                    Icon(TablerIcons.GridDots, contentDescription = "Marcar", tint = Color.White)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                ProtectionHero(
                    active = roleHeld,
                    demoMode = settings.demoMode,
                    automatic = settings.automaticConferenceEnabled,
                    onOpenSettings = onOpenSettings,
                    onActivate = { DialerRole.requestIntent(context)?.let(roleLauncher::launch) },
                )
            }
            item {
                StatsRow(
                    records = records,
                    modifier = Modifier.padding(horizontal = 20.dp).overlapUp(STATS_OVERLAP),
                )
            }
            item {
                Surface(color = TiskzColors.Blue50, shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Marin contesta por ti", style = MaterialTheme.typography.titleMedium)
                        Text(if (settings.automaticConferenceEnabled)
                            "Modo automático activado. La conexión depende de la SIM y la conferencia de tu operadora."
                            else "Prepara una vez los permisos, la SIM y el acceso. La protección está apagada hasta confirmar los costos de tu operadora.")
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Configurar protección automática") }
                    }
                }
            }
            if (!contactsAllowed) {
                item {
                    ContactsBanner(
                        onAllow = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    )
                }
            }
            item {
                Surface(
                    color = TiskzColors.Blue50,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Canal de voz en vivo", style = MaterialTheme.typography.titleMedium)
                        Text("Envía el micrófono a los agentes y recibe transcripción y alertas reales.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onOpenVoice, modifier = Modifier.fillMaxWidth()) { Text("Abrir canal de voz") }
                    }
                }
            }
            item {
                SectionTitle(
                    title = "Recientes",
                    subtitle = if (records.isEmpty()) "Sin llamadas todavía" else "${records.size} ${if (records.size == 1) "llamada" else "llamadas"}",
                )
            }
            item {
                if (records.isEmpty()) {
                    TiskzCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) { EmptyRecents() }
                } else {
                    PagedRecents(records, onOpenRecord)
                }
            }
        }
    }
}

/** La cabecera es oscura: los íconos de la barra de estado van en blanco solo en esta pantalla. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = true }
    }
}

// --- Cabecera ---

@Composable
private fun ProtectionHero(
    active: Boolean,
    demoMode: Boolean,
    automatic: Boolean,
    onOpenSettings: () -> Unit,
    onActivate: () -> Unit,
) {
    val brush = if (active) {
        TiskzBrushes.Brand
    } else {
        Brush.linearGradient(listOf(TiskzColors.Navy, TiskzColors.Blue900))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(brush)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 24.dp + STATS_OVERLAP),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TiskzLogo(size = 38.dp, onDark = true)
                Spacer(Modifier.size(10.dp))
                Text("Tiskz", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(TablerIcons.Settings, contentDescription = "Ajustes", tint = Color.White)
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(144.dp), contentAlignment = Alignment.Center) {
                    if (active) PulsingRings(Color.White, Modifier.size(144.dp))
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.12f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tiskz_shield),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(56.dp),
                            )
                        } else {
                            Icon(TablerIcons.ShieldOff, null, tint = Color.White, modifier = Modifier.size(44.dp))
                        }
                    }
                }
                Text(
                    text = when { active && automatic -> "Modo automático activado"; active -> "Tiskz es tu teléfono"; else -> "Protección desactivada" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    text = when { active && automatic -> "Intentará conectar a marin con tu SIM y operadora confirmadas"; active -> "Configura y activa la conferencia para que marin atienda"; else -> "Prepara Tiskz para atender con marin" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(14.dp))
                if (active) {
                    Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
                        Text(
                            text = if (demoMode) "Modo demo" else "Conectado a los agentes",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Button(
                        onClick = onActivate,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TiskzColors.Blue700),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Activar protección", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// --- Estadísticas ---

@Composable
private fun StatsRow(records: List<CallRecord>, modifier: Modifier = Modifier) {
    val scams = records.count { it.verdict == Verdict.SCAM }
    val safe = records.count { it.verdict == Verdict.SAFE }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(records.size, "Analizadas", TablerIcons.Activity, TiskzColors.Blue600, TiskzColors.Blue50, Modifier.weight(1f))
        StatCard(scams, "Estafas", TablerIcons.ShieldX, TiskzColors.Red, TiskzColors.RedContainer, Modifier.weight(1f))
        StatCard(safe, "Seguras", TablerIcons.ShieldCheck, TiskzColors.EmeraldDark, TiskzColors.EmeraldContainer, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(value: Int, label: String, icon: ImageVector, tint: Color, container: Color, modifier: Modifier) {
    TiskzCard(modifier, RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("$value", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TiskzColors.Ink)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
        }
    }
}

// --- Secciones ---

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TiskzColors.Ink, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
    }
}

@Composable
private fun ContactsBanner(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    TiskzCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(TiskzColors.Blue50), contentAlignment = Alignment.Center) {
                Icon(TablerIcons.Users, null, tint = TiskzColors.Blue600)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Ver nombres de contactos", style = MaterialTheme.typography.titleMedium, color = TiskzColors.Ink)
                Text("Muestra quién llama, no solo el número.", style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
            }
            Button(onClick = onAllow, shape = RoundedCornerShape(50)) { Text("Permitir") }
        }
    }
}

// --- Historial ---

private const val RECENTS_PAGE_SIZE = 5

/** Recientes de 5 en 5: se desliza a la derecha y las páginas numeradas van arriba de la lista. */
@Composable
private fun PagedRecents(records: List<CallRecord>, onOpenRecord: (String) -> Unit) {
    // Por fecha y hora, la más reciente primero, sin separar contactos de desconocidos.
    val pages = remember(records) { records.sortedByDescending { it.startedAt }.chunked(RECENTS_PAGE_SIZE) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val tabs = rememberLazyListState()
    LaunchedEffect(pager.currentPage) { tabs.animateScrollToItem((pager.currentPage - 2).coerceAtLeast(0)) }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = pager.currentPage > 0,
            ) { Icon(TablerIcons.ChevronLeft, contentDescription = "Página anterior") }
            LazyRow(
                state = tabs,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pages.size) { page ->
                    val selected = page == pager.currentPage
                    Surface(
                        onClick = { scope.launch { pager.animateScrollToPage(page) } },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) TiskzColors.Blue600 else TiskzColors.Blue50,
                        modifier = Modifier.size(width = 44.dp, height = 36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${page + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.White else TiskzColors.Blue700,
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                enabled = pager.currentPage < pages.lastIndex,
            ) { Icon(TablerIcons.ChevronRight, contentDescription = "Página siguiente") }
        }
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
        ) { page ->
            TiskzCard(Modifier.fillMaxWidth()) {
                Column {
                    pages[page].forEachIndexed { index, record ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 82.dp), color = TiskzColors.Line)
                        CallRecordRow(record, onClick = { onOpenRecord(record.id) })
                    }
                }
            }
        }
        Text(
            "Página ${pager.currentPage + 1} de ${pages.size} · desliza para ver más",
            style = MaterialTheme.typography.bodySmall,
            color = TiskzColors.Muted,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyRecents() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(TiskzColors.Blue50), contentAlignment = Alignment.Center) {
            Icon(TablerIcons.Phone, null, tint = TiskzColors.Blue600, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Aún no hay llamadas", style = MaterialTheme.typography.titleMedium, color = TiskzColors.Ink)
        Text("Prueba una simulación para ver a Tiskz en acción.", style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
    }
}

@Composable
private fun CallRecordRow(record: CallRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CallerAvatar(name = record.callerName, size = 50.dp)
            // Insignia del veredicto en la esquina del avatar.
            if (record.verdict != Verdict.NONE) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(record.verdict.containerColor()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when (record.verdict) {
                            Verdict.SCAM -> TablerIcons.ShieldX
                            Verdict.SUSPICIOUS -> TablerIcons.AlertTriangle
                            else -> TablerIcons.ShieldCheck
                        },
                        contentDescription = record.verdict.label,
                        tint = record.verdict.color(),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                record.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (record.verdict == Verdict.SCAM) TiskzColors.Red else TiskzColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (record.direction == CallDirection.INCOMING) TablerIcons.PhoneIncoming else TablerIcons.PhoneOutgoing,
                    contentDescription = null,
                    tint = TiskzColors.Muted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(4.dp))
                val detail = buildList {
                    if (record.blocked) add("Bloqueada")
                    add(formatWhen(record.startedAt))
                    if (record.callerName != null) add(PhoneNumbers.display(record.number))
                }.joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (record.verdict != Verdict.NONE) {
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${record.riskPercent}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = record.verdict.color())
                Text(record.verdict.label, style = MaterialTheme.typography.bodySmall, color = record.verdict.color())
            }
        }
    }
}
