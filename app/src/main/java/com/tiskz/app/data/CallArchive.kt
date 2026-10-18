package com.tiskz.app.data

import android.content.Context
import android.util.Log
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallRecord
import com.tiskz.app.model.Speaker
import com.tiskz.app.model.Verdict
import com.tiskz.app.util.PhoneNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Estado del envío de una llamada al panel. */
enum class ArchiveState { PENDING, SAVED, FAILED }

/**
 * Guarda en el panel web cada llamada con su conversación (asistente, quien llama y titular),
 * mediante PUT /calls/{id}. Si no hay conexión o falta la clave, queda en cola y se reintenta
 * al terminar otra llamada, al abrir la app o desde Ajustes. Reenviar la misma llamada no duplica.
 */
class CallArchive(
    context: Context,
    private val settings: SettingsRepository,
    private val credentials: ConferenceCredentials,
    private val client: OkHttpClient,
) {
    private val queue = context.getSharedPreferences("tiskz_panel_queue", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _states = MutableStateFlow(loadStates())
    /** Estado por id de llamada, para mostrarlo en Recientes. */
    val states: StateFlow<Map<String, ArchiveState>> = _states.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val pendingCount: Int get() = _states.value.count { it.value != ArchiveState.SAVED }

    /** Agrega la llamada a la cola y la envía. No hace nada si la persona desactivó el guardado. */
    fun upload(record: CallRecord) {
        if (!settings.current.panelSync) return
        queue.edit().putString(PAYLOAD + record.id, payload(record).toString()).putString(STATE + record.id, ArchiveState.PENDING.name).apply()
        _states.value = _states.value + (record.id to ArchiveState.PENDING)
        flush()
    }

    /** Reintenta todo lo pendiente. */
    fun flush() {
        scope.launch {
            mutex.withLock {
                val current = settings.current
                if (!current.panelSync) return@withLock
                val key = credentials.read(current.panelUrl)
                if (key == null) {
                    _lastError.value = "Falta la clave del panel en Ajustes."
                    return@withLock
                }
                pendingIds().forEach { id -> send(id, current.panelUrl, current.panelOrg, key) }
            }
        }
    }

    private fun send(id: String, baseUrl: String, org: String, key: String) {
        val body = queue.getString(PAYLOAD + id, null) ?: return
        val result = runCatching {
            val url = baseUrl.trimEnd('/').toHttpUrl().newBuilder().addPathSegment("calls").addPathSegment(id).build()
            val request = Request.Builder().url(url)
                .header("Authorization", "Bearer $key")
                .header("X-PhoneWard-Org", org)
                .header("X-Tiskz-Org", org) // panel anterior al cambio de nombre
                .put(body.toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { it.code to it.body?.string().orEmpty().take(300) }
        }
        val (code, detail) = result.getOrElse { e ->
            Log.w(TAG, "Sin conexión con el panel: ${e.javaClass.simpleName}")
            _lastError.value = "Sin conexión con el panel. Se reintentará."
            return
        }
        when {
            code in 200..299 -> {
                queue.edit().remove(PAYLOAD + id).putString(STATE + id, ArchiveState.SAVED.name).apply()
                _states.value = _states.value + (id to ArchiveState.SAVED)
                _lastError.value = null
            }
            // Clave, organización o datos rechazados: reintentar igual no sirve hasta corregir Ajustes.
            code in setOf(400, 401, 403, 404, 413, 422) -> {
                Log.w(TAG, "El panel rechazó la llamada ($code): $detail")
                queue.edit().putString(STATE + id, ArchiveState.FAILED.name).apply()
                _states.value = _states.value + (id to ArchiveState.FAILED)
                _lastError.value = when (code) {
                    401 -> "El panel rechazó la clave. Revísala en Ajustes."
                    400 -> "Revisa la organización del panel en Ajustes."
                    403 -> "La compartición de esta llamada fue revocada."
                    else -> "El panel no aceptó la llamada ($code)."
                }
            }
            else -> _lastError.value = "El panel no respondió ($code). Se reintentará."
        }
    }

    /** Lo rechazado se vuelve a intentar solo cuando la persona corrige Ajustes y pide reintentar. */
    fun retryFailed() {
        val failed = _states.value.filterValues { it == ArchiveState.FAILED }.keys
        queue.edit().apply { failed.forEach { putString(STATE + it, ArchiveState.PENDING.name) } }.apply()
        _states.value = _states.value + failed.associateWith { ArchiveState.PENDING }
        flush()
    }

    private fun pendingIds() = _states.value.filterValues { it == ArchiveState.PENDING }.keys
        .filter { queue.contains(PAYLOAD + it) }

    private fun loadStates(): Map<String, ArchiveState> = queue.all.keys
        .filter { it.startsWith(STATE) }
        .mapNotNull { k ->
            val state = runCatching { ArchiveState.valueOf(queue.getString(k, null).orEmpty()) }.getOrNull()
            state?.let { k.removePrefix(STATE) to it }
        }.toMap()

    companion object {
        private const val TAG = "PhoneWardArchive"
        private const val PAYLOAD = "payload:"
        private const val STATE = "state:"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Cuerpo de PUT /calls/{id}: el contrato está en el OpenAPI del panel (CallInput). */
        fun payload(record: CallRecord): JSONObject = JSONObject().apply {
            put("caller_number_raw", record.number.ifBlank { null } ?: JSONObject.NULL)
            put("caller_e164", record.number.takeIf { it.isNotBlank() }?.let(PhoneNumbers::e164) ?: JSONObject.NULL)
            put("caller_name", record.callerName ?: JSONObject.NULL)
            put("direction", if (record.direction == CallDirection.OUTGOING) "outgoing" else "incoming")
            put("call_status", if (record.answeredAt != null) "ended" else "missed")
            put("started_at", iso(record.startedAt))
            put("answered_at", record.answeredAt?.let(::iso) ?: JSONObject.NULL)
            put("ended_at", record.endedAt?.let(::iso) ?: JSONObject.NULL)
            put("talk_duration_s", record.durationSec)
            put("simulated", record.simulated)
            put("blocked", record.blocked)
            put("screened", record.screened)
            put("verdict", when (record.verdict) {
                Verdict.SAFE -> "safe"
                Verdict.SUSPICIOUS -> "suspicious"
                Verdict.SCAM -> "scam"
                Verdict.NONE -> "none"
            })
            put("risk_score", record.riskPercent.coerceIn(0, 100))
            put("risk_reasons", JSONArray(record.reasons.filter { it.isNotBlank() }.map { it.take(500) }.take(30)))
            put("scam_type", record.scamType?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("impersonating", record.impersonating?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("summary", record.summary ?: JSONObject.NULL)
            put("transcript", JSONArray().apply {
                record.transcript.filter { it.text.isNotBlank() }.forEach { line ->
                    put(JSONObject().apply {
                        put("id", line.id.take(200))
                        put("speaker", when (line.speaker) {
                            Speaker.AGENT -> "agent"
                            Speaker.USER -> "user"
                            Speaker.CALLER -> "caller"
                        })
                        put("text", line.text.trim().take(4000))
                        put("flagged", line.flagged)
                    })
                }
            })
        }

        private fun iso(millis: Long) = Instant.ofEpochMilli(millis).toString()
    }
}
