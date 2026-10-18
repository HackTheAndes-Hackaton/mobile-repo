package com.tiskz.app.ui.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tiskz.app.call.Dialing
import com.tiskz.app.data.ContactLookup
import com.tiskz.app.data.ContactsBook
import com.tiskz.app.data.PhoneContact
import com.tiskz.app.data.normalizeQuery
import com.tiskz.app.ui.components.CallerAvatar
import com.tiskz.app.ui.components.TiskzCard
import com.tiskz.app.ui.theme.TiskzBrushes
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.util.PhoneNumbers
import compose.icons.TablerIcons
import compose.icons.tablericons.Users
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ExternalLink
import compose.icons.tablericons.GridDots
import compose.icons.tablericons.Phone
import compose.icons.tablericons.Search
import compose.icons.tablericons.Star
import compose.icons.tablericons.UserPlus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Contactos del teléfono: buscar, ver sus números y llamar, como en cualquier app de teléfono. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(onBack: () -> Unit, onOpenDialer: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContactLookup.hasPermission(context)) }
    var contacts by remember { mutableStateOf<List<PhoneContact>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingNumber by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingNumber?.takeIf { granted }?.let { Dialing.placeCall(context, it) }
        pendingNumber = null
    }
    val call: (String) -> Unit = { number ->
        if (Dialing.canCall(context)) Dialing.placeCall(context, number)
        else { pendingNumber = number; callPermission.launch(Manifest.permission.CALL_PHONE) }
    }

    // Al volver de la app de contactos (nuevo o editado) se recarga la lista.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = ContactLookup.hasPermission(context)
        reload++
    }
    // ON_RESUME también llega al abrir la pantalla: con reload > 0 se carga una sola vez, no dos.
    LaunchedEffect(allowed, reload) {
        if (allowed && reload > 0) contacts = withContext(Dispatchers.IO) { ContactsBook.load(context) }
    }

    Scaffold(
        containerColor = TiskzColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Contactos") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Volver") }
                },
                actions = {
                    IconButton(onClick = { context.startActivity(ContactsBook.insertIntent()) }) {
                        Icon(TablerIcons.UserPlus, contentDescription = "Nuevo contacto", tint = TiskzColors.Blue600)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TiskzColors.Background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenDialer,
                shape = CircleShape,
                containerColor = Color.Transparent,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                modifier = Modifier.size(64.dp).shadow(18.dp, CircleShape, spotColor = TiskzColors.Blue600),
            ) {
                Box(Modifier.fillMaxSize().background(TiskzBrushes.Brand), contentAlignment = Alignment.Center) {
                    Icon(TablerIcons.GridDots, contentDescription = "Marcar", tint = Color.White)
                }
            }
        },
    ) { padding ->
        if (!allowed) {
            PermissionPrompt(Modifier.padding(padding)) { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }
            return@Scaffold
        }
        val list = contacts
        val filtered = remember(list, query) {
            val q = normalizeQuery(query)
            val digits = PhoneNumbers.digits(query)
            list.orEmpty().filter { it.matches(q, digits) }
        }
        val grouped = remember(filtered) { filtered.groupBy { it.initial } }
        val favorites = remember(list) { list.orEmpty().filter { it.starred } }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar por nombre o número") },
                    leadingIcon = { Icon(TablerIcons.Search, contentDescription = null, tint = TiskzColors.Muted) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = TiskzColors.White,
                        focusedContainerColor = TiskzColors.White,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (query.isBlank() && favorites.isNotEmpty()) {
                item { Header("Favoritos") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(favorites, key = { it.id }) { contact -> Favorite(contact) { call(contact.phones.first().number) } }
                    }
                }
            }
            when {
                list == null -> item { Hint("Cargando contactos…") }
                filtered.isEmpty() && query.isNotBlank() -> item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ningún contacto coincide con «$query».", color = TiskzColors.Muted)
                        if (PhoneNumbers.digits(query).length >= 6) {
                            Spacer(Modifier.size(12.dp))
                            Button(onClick = { call(query.trim()) }) { Text("Llamar a ${query.trim()}") }
                        }
                    }
                }
                filtered.isEmpty() -> item { Hint("No tienes contactos guardados todavía.") }
                else -> grouped.forEach { (letter, group) ->
                    item(key = "h-$letter") { Header(letter) }
                    items(group, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            expanded = expanded == contact.id,
                            onToggle = { expanded = if (expanded == contact.id) null else contact.id },
                            onCall = call,
                            onOpen = { context.startActivity(contact.viewIntent()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: PhoneContact,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCall: (String) -> Unit,
    onOpen: () -> Unit,
) {
    TiskzCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), shape = RoundedCornerShape(20.dp)) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallerAvatar(contact.name, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(contact.name, style = MaterialTheme.typography.titleMedium, color = TiskzColors.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (contact.starred) {
                            Spacer(Modifier.width(4.dp))
                            Icon(TablerIcons.Star, contentDescription = "Favorito", tint = TiskzColors.Amber, modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(
                        text = if (contact.phones.size > 1) "${contact.phones.size} números" else PhoneNumbers.display(contact.phones.first().number),
                        style = MaterialTheme.typography.bodySmall,
                        color = TiskzColors.Muted,
                    )
                }
                CallButton { if (contact.phones.size == 1) onCall(contact.phones.first().number) else onToggle() }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 70.dp, end = 14.dp, bottom = 10.dp)) {
                    contact.phones.forEach { phone ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onCall(phone.number) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(PhoneNumbers.display(phone.number), style = MaterialTheme.typography.bodyLarge, color = TiskzColors.Ink)
                                Text(phone.label, style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
                            }
                            Icon(TablerIcons.Phone, contentDescription = "Llamar", tint = TiskzColors.Blue600, modifier = Modifier.size(20.dp))
                        }
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(TablerIcons.ExternalLink, contentDescription = null, tint = TiskzColors.Blue600, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ver o editar en Contactos", style = MaterialTheme.typography.labelLarge, color = TiskzColors.Blue600)
                    }
                }
            }
        }
    }
}

@Composable
private fun Favorite(contact: PhoneContact, onCall: () -> Unit) {
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onCall).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CallerAvatar(contact.name, size = 56.dp, background = TiskzBrushes.Brand, content = Color.White)
        Spacer(Modifier.size(6.dp))
        Text(contact.name.substringBefore(' '), style = MaterialTheme.typography.labelMedium, color = TiskzColors.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CallButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = TiskzColors.Blue50, modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(TablerIcons.Phone, contentDescription = "Llamar", tint = TiskzColors.Blue600, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = TiskzColors.Blue700,
        modifier = Modifier.padding(start = 28.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = TiskzColors.Muted, modifier = Modifier.fillMaxWidth().padding(24.dp))
}

@Composable
private fun PermissionPrompt(modifier: Modifier, onAllow: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(TablerIcons.Users, contentDescription = null, tint = TiskzColors.Blue600, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(12.dp))
        Text("Permite el acceso a tus contactos", style = MaterialTheme.typography.titleMedium, color = TiskzColors.Ink)
        Text(
            "Así puedes buscarlos y llamarlos desde PhoneWard, y sus llamadas suenan normal, sin el asistente.",
            style = MaterialTheme.typography.bodyMedium,
            color = TiskzColors.Muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        Button(onClick = onAllow) { Text("Permitir contactos") }
    }
}
