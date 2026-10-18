package com.tiskz.app.data

import android.content.Context
import com.tiskz.app.BuildConfig
import com.tiskz.app.call.CarrierDialPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DemoScenario(val label: String) {
    AUTO("Automático"),
    SCAM("Estafa"),
    SAFE("Segura"),
}

data class PhoneWardSettings(
    val onboardingDone: Boolean,
    /** true: las llamadas se analizan con guiones locales; false: con los agentes por WebSocket. */
    val demoMode: Boolean,
    val demoScenario: DemoScenario,
    val backendUrl: String,
    val wsUrl: String,
    /** Bajo este porcentaje de riesgo se destaca el botón "Contestar". */
    val answerThreshold: Int,
    val automaticConferenceEnabled: Boolean = false,
    val conferenceCostsConfirmed: Boolean = false,
    val conferenceAccount: String = "",
    val conferenceDestination: String = CarrierDialPlan.DEFAULT_AGENT,
    val conferenceConfirmedRoute: String = "",
    /** Guardar la conversación de cada llamada en el panel web. */
    val panelSync: Boolean = true,
    val phoneEvidenceConsent: Boolean = false,
    val panelUrl: String = BuildConfig.PANEL_URL,
    /** Organización del panel con la que se comparten las llamadas (X-PhoneWard-Org). */
    val panelOrg: String = BuildConfig.PANEL_ORG,
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("tiskz_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<PhoneWardSettings> = _settings.asStateFlow()
    val current: PhoneWardSettings get() = _settings.value

    fun update(transform: (PhoneWardSettings) -> PhoneWardSettings) {
        val proposed = transform(_settings.value)
        val sameRoute = proposed.conferenceAccount == current.conferenceAccount &&
            proposed.conferenceDestination == current.conferenceDestination
        val sameSetup = proposed.backendUrl == current.backendUrl && sameRoute
        val updated = proposed.copy(
            automaticConferenceEnabled = proposed.automaticConferenceEnabled && proposed.conferenceCostsConfirmed && proposed.conferenceConfirmedRoute.isNotBlank() &&
                proposed.conferenceAccount.isNotBlank() && sameSetup,
            conferenceCostsConfirmed = proposed.conferenceCostsConfirmed && proposed.conferenceAccount.isNotBlank() &&
                sameRoute,
            conferenceConfirmedRoute = if (sameRoute) proposed.conferenceConfirmedRoute else "",
        )
        _settings.value = updated
        prefs.edit()
            .putBoolean(KEY_ONBOARDING, updated.onboardingDone)
            .putBoolean(KEY_DEMO, updated.demoMode)
            .putString(KEY_SCENARIO, updated.demoScenario.name)
            .putString(KEY_BACKEND, updated.backendUrl)
            .putString(KEY_WS, updated.wsUrl)
            .putInt(KEY_THRESHOLD, updated.answerThreshold)
            .putBoolean("auto_conference_enabled", updated.automaticConferenceEnabled)
            .putBoolean("conference_costs_confirmed", updated.conferenceCostsConfirmed)
            .putString("conference_account", updated.conferenceAccount)
            .putString("conference_destination", updated.conferenceDestination)
            .putString("conference_confirmed_route", updated.conferenceConfirmedRoute)
            .putBoolean("phone_evidence_consent", updated.phoneEvidenceConsent)
            .putBoolean(KEY_PANEL_SYNC, updated.panelSync)
            .putString(KEY_PANEL_URL, updated.panelUrl)
            .putString(KEY_PANEL_ORG, updated.panelOrg)
            .apply()
    }

    private fun load() = PhoneWardSettings(
        onboardingDone = prefs.getBoolean(KEY_ONBOARDING, false),
        demoMode = prefs.getBoolean(KEY_DEMO, BuildConfig.DEMO_MODE_DEFAULT),
        demoScenario = prefs.getString(KEY_SCENARIO, null)
            ?.let { runCatching { DemoScenario.valueOf(it) }.getOrNull() }
            ?: DemoScenario.AUTO,
        backendUrl = prefs.getString(KEY_BACKEND, null) ?: BuildConfig.BACKEND_URL,
        wsUrl = prefs.getString(KEY_WS, null) ?: BuildConfig.WS_URL,
        answerThreshold = prefs.getInt(KEY_THRESHOLD, 40),
        automaticConferenceEnabled = prefs.getBoolean("auto_conference_enabled", false),
        conferenceCostsConfirmed = prefs.getBoolean("conference_costs_confirmed", false),
        conferenceAccount = prefs.getString("conference_account", "").orEmpty(),
        conferenceDestination = prefs.getString("conference_destination", null) ?: CarrierDialPlan.DEFAULT_AGENT,
        conferenceConfirmedRoute = prefs.getString("conference_confirmed_route", "").orEmpty(),
        panelSync = prefs.getBoolean(KEY_PANEL_SYNC, true),
        phoneEvidenceConsent = prefs.getBoolean("phone_evidence_consent", false),
        panelUrl = prefs.getString(KEY_PANEL_URL, null) ?: BuildConfig.PANEL_URL,
        panelOrg = prefs.getString(KEY_PANEL_ORG, null) ?: BuildConfig.PANEL_ORG,
    )

    private companion object {
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_DEMO = "demo_mode"
        const val KEY_SCENARIO = "demo_scenario"
        const val KEY_BACKEND = "backend_url"
        const val KEY_WS = "ws_url"
        const val KEY_THRESHOLD = "answer_threshold"
        const val KEY_PANEL_SYNC = "panel_sync"
        const val KEY_PANEL_URL = "panel_url"
        const val KEY_PANEL_ORG = "panel_org"
    }
}
