package com.tiskz.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.ui.components.TiskzLogo
import com.tiskz.app.ui.theme.TiskzColors
import com.tiskz.app.util.DialerRole
import compose.icons.TablerIcons
import compose.icons.tablericons.Activity
import compose.icons.tablericons.Fingerprint
import compose.icons.tablericons.Headset

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalAppContainer.current.settings

    fun finish() {
        settings.update { it.copy(onboardingDone = true) }
        onFinished()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { finish() }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { permissionLauncher.launch(DialerRole.runtimePermissions) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TiskzColors.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.6f))
        TiskzLogo(size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text("PhoneWard", style = MaterialTheme.typography.displayMedium, color = TiskzColors.Blue700)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tu teléfono, con protección contra estafas.",
            style = MaterialTheme.typography.titleMedium,
            color = TiskzColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            Feature(TablerIcons.Headset, "Un asistente contesta primero", "Atiende a los números desconocidos antes que tú.")
            Feature(TablerIcons.Activity, "Alertas durante la llamada", "Te avisa si piden claves, códigos o dinero.")
            Feature(TablerIcons.Fingerprint, "Una configuración inicial", "Elige tu SIM y confirma los costos antes de activar el filtro.")
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                val roleIntent = DialerRole.requestIntent(context)
                if (roleIntent != null && !DialerRole.isHeld(context)) {
                    roleLauncher.launch(roleIntent)
                } else {
                    permissionLauncher.launch(DialerRole.runtimePermissions)
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text("Preparar PhoneWard", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = "Android te pedirá elegir PhoneWard como teléfono y conceder permisos. Esto todavía no activa las llamadas automáticas.",
            style = MaterialTheme.typography.bodySmall,
            color = TiskzColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = ::finish, modifier = Modifier.padding(top = 4.dp)) {
            Text("Probar sin activar")
        }
        Text(
            text = "La atención automática viene apagada.",
            style = MaterialTheme.typography.bodySmall,
            color = TiskzColors.Muted,
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).background(TiskzColors.Blue50, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = TiskzColors.Blue600)
        }
        Spacer(Modifier.size(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TiskzColors.Ink)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TiskzColors.Muted)
        }
    }
}
