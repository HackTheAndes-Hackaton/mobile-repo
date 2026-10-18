package com.tiskz.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.tiskz.app.analysis.AnalysisSession
import com.tiskz.app.analysis.MockAnalysisSession
import com.tiskz.app.analysis.WebSocketAnalysisSession
import com.tiskz.app.util.PhoneNumbers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(private val context: Context) {

    val settings = SettingsRepository(context)
    val conferenceCredentials = ConferenceCredentials(context)
    val history = HistoryRepository(context)
    val blocklist = BlocklistRepository(context)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    /** API key del panel web: se ingresa una vez en Ajustes y queda cifrada, nunca en el APK. */
    val panelCredentials = ConferenceCredentials(context, "tiskz_panel_access", "tiskz-panel-access-v1")
    val archive by lazy { CallArchive(context, settings, panelCredentials, httpClient) }

    @get:SuppressLint("HardwareIds")
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    }

    fun openVoiceSession(baseUrl: String, accessCode: String, assistant: Boolean = false, evidenceConsent: Boolean = false) =
        com.tiskz.app.voice.WebRtcVoiceSession(context.applicationContext, httpClient, baseUrl, accessCode, assistant, evidenceConsent)

    fun openPhoneAlerts(baseUrl: String, accessCode: String) =
        com.tiskz.app.voice.PhoneAlertSession(httpClient, baseUrl, accessCode)

    /**
     * Abre el análisis de una llamada entrante.
     * [forcedScenario] lo usan los botones "Simular llamada": siempre van por la demo.
     */
    fun openAnalysisSession(callerNumber: String, forcedScenario: DemoScenario? = null): AnalysisSession {
        val current = settings.current
        return if (forcedScenario != null || current.demoMode) {
            MockAnalysisSession(resolveScenario(forcedScenario ?: current.demoScenario, callerNumber))
        } else {
            WebSocketAnalysisSession(httpClient, current.wsUrl, callerNumber, deviceId)
        }
    }

    /**
     * En modo automático: un contacto guardado es una llamada segura; si no,
     * último dígito par = segura, impar = estafa.
     */
    private fun resolveScenario(scenario: DemoScenario, number: String): DemoScenario {
        if (scenario != DemoScenario.AUTO) return scenario
        // Consulta corta a Contactos en el hilo principal: aceptable para el modo demo.
        if (ContactLookup.findName(context, number) != null) return DemoScenario.SAFE
        val lastDigit = PhoneNumbers.digits(number).lastOrNull()?.digitToInt() ?: 1
        return if (lastDigit % 2 == 0) DemoScenario.SAFE else DemoScenario.SCAM
    }
}
