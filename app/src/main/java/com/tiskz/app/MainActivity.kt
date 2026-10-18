package com.tiskz.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tiskz.app.call.CallManager
import com.tiskz.app.call.CallNotifications
import com.tiskz.app.data.DemoScenario
import com.tiskz.app.ui.LocalAppContainer
import com.tiskz.app.ui.TiskzNavHost
import com.tiskz.app.ui.theme.TiskzTheme

class MainActivity : ComponentActivity() {

    private var pendingDial by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        if (savedInstanceState == null) {
            pendingDial = intent.dialNumber()
            handleDebugSimulation(intent)
        }

        val container = (application as TiskzApp).container
        setContent {
            TiskzTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    TiskzNavHost(pendingDial = pendingDial, onDialConsumed = { pendingDial = null })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CallNotifications.cancelMissed(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dialNumber()?.let { pendingDial = it }
        handleDebugSimulation(intent)
    }

    /**
     * Solo en builds de depuración: dispara una llamada simulada desde la PC, útil en la demo.
     *   adb shell am start -n com.tiskz.app/.MainActivity --es simulate scam   (o safe)
     */
    private fun handleDebugSimulation(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        when (intent.getStringExtra("simulate")) {
            "scam" -> CallManager.simulateIncoming("+56987654321", DemoScenario.SCAM)
            "safe" -> CallManager.simulateIncoming("+56987654320", DemoScenario.SAFE)
        }
    }

    /** Número de un intent DIAL/VIEW tel:, o null si la app se abrió normalmente. */
    private fun Intent.dialNumber(): String? = when (action) {
        Intent.ACTION_DIAL, Intent.ACTION_VIEW -> data?.schemeSpecificPart.orEmpty()
        else -> null
    }
}
