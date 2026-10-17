package com.tiskz.app.ui.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
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
import com.tiskz.app.ui.theme.TiskzColors

private val keys = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

/** Marcador mínimo: al ser la app de teléfono, Tiskz también tiene que poder llamar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(initialNumber: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf(initialNumber) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) placeCall(context, number)
    }

    Scaffold(
        containerColor = TiskzColors.White,
        topBar = {
            TopAppBar(
                title = { Text("Marcar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
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
            Spacer(Modifier.weight(1f))
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
                        Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Borrar", tint = TiskzColors.Muted)
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
                        placeCall(context, number)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    }
                },
                shape = CircleShape,
                color = TiskzColors.Blue600,
                modifier = Modifier.size(76.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Call, contentDescription = "Llamar", tint = TiskzColors.White, modifier = Modifier.size(34.dp))
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

private fun placeCall(context: Context, number: String) {
    try {
        context.getSystemService(TelecomManager::class.java)
            .placeCall(Uri.fromParts("tel", number, null), Bundle())
    } catch (e: SecurityException) {
        Toast.makeText(context, "Falta el permiso para llamar", Toast.LENGTH_SHORT).show()
    }
}
