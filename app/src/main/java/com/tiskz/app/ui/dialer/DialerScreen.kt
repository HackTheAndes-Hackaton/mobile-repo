package com.tiskz.app.ui.dialer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tiskz.app.call.Dialing
import com.tiskz.app.data.ContactsBook
import com.tiskz.app.data.PhoneContact
import com.tiskz.app.ui.components.CallerAvatar
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.util.PhoneNumbers
import compose.icons.TablerIcons
import compose.icons.tablericons.Users
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Backspace
import compose.icons.tablericons.Phone
import compose.icons.tablericons.UserPlus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val keys = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

/** Marcador mínimo: al ser la app de teléfono, Tiskz también tiene que poder llamar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(initialNumber: String, onBack: () -> Unit, onOpenContacts: () -> Unit = {}) {
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf(initialNumber) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Dialing.placeCall(context, number)
    }
    // Contactos que coinciden con lo marcado, para elegirlos sin escribir el número completo.
    var contacts by remember { mutableStateOf(emptyList<PhoneContact>()) }
    LaunchedEffect(Unit) { contacts = withContext(Dispatchers.IO) { ContactsBook.load(context) } }
    val suggestions = remember(number, contacts) {
        val typed = PhoneNumbers.digits(number)
        if (typed.length < 3) emptyList()
        else contacts.asSequence()
            .flatMap { c -> c.phones.asSequence().zip(c.phoneDigits.asSequence()).map { (phone, digits) -> Triple(c, phone, digits) } }
            .filter { (_, _, digits) -> digits.contains(typed) }
            .map { (c, phone, _) -> c to phone }
            .take(3)
            .toList()
    }

    Scaffold(
        containerColor = TiskzColors.White,
        topBar = {
            TopAppBar(
                title = { Text("Marcar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (number.isNotBlank()) {
                        IconButton(onClick = { context.startActivity(ContactsBook.insertIntent(number)) }) {
                            Icon(TablerIcons.UserPlus, contentDescription = "Guardar contacto", tint = TiskzColors.Blue600)
                        }
                    }
                    IconButton(onClick = onOpenContacts) {
                        Icon(TablerIcons.Users, contentDescription = "Contactos", tint = TiskzColors.Blue600)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TiskzColors.White),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
                suggestions.forEach { (contact, phone) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { number = phone.number }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CallerAvatar(contact.name, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.titleSmall, color = TiskzColors.Ink, maxLines = 1)
                            Text("${PhoneNumbers.display(phone.number)} · ${phone.label}", style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted, maxLines = 1)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = number,
                    fontSize = 34.sp,
                    color = TiskzColors.Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (number.isNotEmpty()) {
                    IconButton(onClick = { number = number.dropLast(1) }) {
                        Icon(TablerIcons.Backspace, contentDescription = "Borrar", tint = TiskzColors.Muted)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            keys.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (digit, letters) ->
                        DialKey(digit, letters) { number += digit }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = {
                    if (number.isBlank()) return@Surface
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        Dialing.placeCall(context, number)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    }
                },
                shape = CircleShape,
                color = TiskzColors.Blue600,
                modifier = Modifier.size(76.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(TablerIcons.Phone, contentDescription = "Llamar", tint = TiskzColors.White, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DialKey(digit: String, letters: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = TiskzColors.Blue50, modifier = Modifier.size(76.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(digit, fontSize = 28.sp, color = TiskzColors.Ink)
            if (letters.isNotEmpty()) {
                Text(letters, style = MaterialTheme.typography.bodySmall, color = TiskzColors.Muted)
            }
        }
    }
}
