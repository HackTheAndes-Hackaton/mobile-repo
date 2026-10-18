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

data class TiskzSettings(
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
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("tiskz_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<TiskzSettings> = _settings.asStateFlow()
    val current: TiskzSettings get() = _settings.value

    fun update(transform: (TiskzSettings) -> TiskzSettings) {
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
            .apply()
    }

    private fun load() = TiskzSettings(
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
    )

    private companion object {
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_DEMO = "demo_mode"
        const val KEY_SCENARIO = "demo_scenario"
        const val KEY_BACKEND = "backend_url"
        const val KEY_WS = "ws_url"
        const val KEY_THRESHOLD = "answer_threshold"
    }
}
