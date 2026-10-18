package com.tiskz.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.tiskz.app.BuildConfig
import com.tiskz.app.call.CallManager
import com.tiskz.app.call.CarrierDialPlan
import com.tiskz.app.call.ConferenceTelecom
import com.tiskz.app.model.CallPhase
import com.tiskz.app.util.DialerRole

/**
 * Configuración del equipo fijada al compilar (local.properties, fuera de Git): canal, código,
 * panel y número del agente. El usuario final no la ve ni la cambia; Ajustes y Voz en vivo
 * muestran solo lo que le corresponde. Sin tiskz.preprovisioned=true, todo sigue como antes.
 */
object TeamProvisioning {
    val enabled: Boolean get() = BuildConfig.PREPROVISIONED

    /** Al arrancar: valores fijos y credenciales cifradas en el teléfono. */
    fun apply(container: AppContainer) {
        if (!enabled) return
        container.settings.update {
            it.copy(
                demoMode = false,
                backendUrl = BuildConfig.BACKEND_URL,
                panelUrl = BuildConfig.PANEL_URL,
                panelOrg = BuildConfig.PANEL_ORG,
                panelSync = true,
                phoneEvidenceConsent = true,
                conferenceDestination = CarrierDialPlan.DEFAULT_AGENT,
            )
        }
        val code = BuildConfig.VOICE_ACCESS_CODE
        if (code.length in 12..512 && container.conferenceCredentials.read(BuildConfig.BACKEND_URL) != code) {
            runCatching { container.conferenceCredentials.save(BuildConfig.BACKEND_URL, code) }
        }
        val key = BuildConfig.PANEL_API_KEY
        if (key.length in 12..512 && container.panelCredentials.read(BuildConfig.PANEL_URL) != key) {
            runCatching { container.panelCredentials.save(BuildConfig.PANEL_URL, key) }
        }
    }

    /**
     * Con PhoneWard como teléfono y permisos concedidos: elige la SIM, confirma la ruta al agente
     * y activa la protección automática. Devuelve un texto para el usuario si algo falta.
     */
    fun ensureProtection(context: Context, container: AppContainer, enable: Boolean? = null): String? {
        if (!enabled) return null
        val prefs = context.getSharedPreferences("tiskz_team", Context.MODE_PRIVATE)
        // La primera vez se activa sola; después se respeta si el usuario la apagó.
        val turnOn = enable ?: !prefs.getBoolean("protection_initialized", false)
        if (CallManager.activeCall.value?.phase?.let { it != CallPhase.DISCONNECTED } == true) return null
        if (!DialerRole.isHeld(context)) return "Elige PhoneWard como app de teléfono para activar la protección."
        val permitted = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!permitted) return "Permite a PhoneWard gestionar llamadas para activar la protección."
        val repo = container.settings
        val accounts = runCatching {
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom.callCapablePhoneAccounts.filter {
                telecom.getPhoneAccount(it)?.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) == true
            }
        }.getOrDefault(emptyList())
        val handle = accounts.firstOrNull { ConferenceTelecom.accountKey(it) == repo.current.conferenceAccount } ?: accounts.firstOrNull()
            ?: return "No se encontró una SIM activa."
        val plan = ConferenceTelecom.dialPlan(context, handle, CarrierDialPlan.DEFAULT_AGENT)
            ?: return "Tu operadora o red todavía no es compatible con la protección automática."
        repo.update { it.copy(conferenceAccount = ConferenceTelecom.accountKey(handle), conferenceDestination = CarrierDialPlan.DEFAULT_AGENT) }
        repo.update { it.copy(conferenceCostsConfirmed = true, conferenceConfirmedRoute = plan.confirmationKey) }
        if (container.conferenceCredentials.read(repo.current.backendUrl) == null) return "Falta el acceso del equipo en esta versión de la app."
        if (turnOn) {
            repo.update { it.copy(automaticConferenceEnabled = true) }
            prefs.edit().putBoolean("protection_initialized", true).apply()
        }
        return null
    }
}
