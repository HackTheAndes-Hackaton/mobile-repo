package com.tiskz.app.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VoiceRoom(val id: String, val producerToken: String) {
    override fun toString() = "VoiceRoom(id=$id)"
}

/** Señalización del canal. Nunca almacena credenciales ni incluye tickets en mensajes de error. */
class VoiceBackendApi(baseUrl: String, client: OkHttpClient) {
    // El proxy no debe reenviar el código privado a otro destino mediante una redirección.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val base: HttpUrl = baseUrl.trim().toHttpUrl().also {
        require(it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null) {
            "El canal de voz requiere una URL HTTPS sin credenciales ni parámetros."
        }
    }.let { it.newBuilder().encodedPath(it.encodedPath.trimEnd('/') + "/").build() }
    private val calls = ConcurrentHashMap.newKeySet<Call>()

    suspend fun createRoom(accessCode: String, consent: Boolean, assistant: Boolean = false, evidenceConsent: Boolean = false): VoiceRoom {
        require(consent) { "Se requiere autorización para analizar el audio." }
        require(accessCode.length in 12..512) { "Introduce el código privado del equipo." }
        val body = JSONObject().put("consentimiento_analisis", true)
        if (evidenceConsent) body.put("consentimiento_conservacion", true)
        if (assistant) body.put("mode", "assistant").put("consentimiento_voz_ia", true)
        val json = post("v1/sessions", accessCode, body)
        val id = json.getString("session_id")
        UUID.fromString(id)
        val token = json.getString("producer_token")
        check(token.length in 12..256) { "Ticket de voz inválido." }
        return VoiceRoom(id, token)
    }

    suspend fun offer(room: VoiceRoom, sdp: String): String =
        post("v1/sessions/${room.id}/offer", room.producerToken, JSONObject().put("sdp", sdp))
            .getString("sdp").also { check(it.startsWith("v=")) { "Respuesta de audio inválida." } }

    suspend fun end(room: VoiceRoom) {
        post("v1/sessions/${room.id}/end", room.producerToken, JSONObject())
    }

    suspend fun saveExcerpt(room: VoiceRoom, audio: ByteArray, requestId: String): String =
        post("v1/sessions/${room.id}/evidence", room.producerToken, JSONObject()
            .put("request_id", requestId).put("audio_base64", java.util.Base64.getEncoder().encodeToString(audio)))
            .getJSONObject("data").getString("id").also {
                check(it.matches(Regex("clip_[a-f0-9]{48}"))) { "Identificador de audio inválido." }
            }

    suspend fun loadExcerpt(room: VoiceRoom, id: String): ByteArray = suspendCancellableCoroutine { continuation ->
        require(id.matches(Regex("clip_[a-f0-9]{48}")))
        val request = Request.Builder().url(base.newBuilder().addPathSegments("v1/sessions/${room.id}/evidence/$id").build())
            .header("X-Voice-Token", room.producerToken).build()
        val call = client.newCall(request)
        calls.add(call)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                calls.remove(call)
                if (continuation.isActive) continuation.resumeWithException(IOException("No se pudo recuperar el audio."))
            }
            override fun onResponse(call: Call, response: Response) {
                calls.remove(call)
                try {
                    val bytes = response.use {
                        check(it.isSuccessful && it.header("Content-Type")?.startsWith("audio/wav") == true) { "Fragmento no disponible." }
                        val body = requireNotNull(it.body)
                        val source = body.source()
                        check(!source.request(480045)) { "Fragmento demasiado grande." }
                        source.readByteArray().also { data ->
                            check(data.size in 32044..480044 && data.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF") { "Audio inválido." }
                        }
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("No se pudo recuperar el fragmento autorizado."))
                }
            }
        })
    }

    suspend fun phoneEventsUrl(accessCode: String): String {
        val ticket = post("v1/phone/monitor", accessCode, JSONObject().put("consentimiento_analisis", true)).getString("ticket")
        check(ticket.length in 12..256) { "Ticket de alertas inválido." }
        return base.newBuilder().addPathSegments("ws/calls").addQueryParameter("channel", "phone")
            .addQueryParameter("ticket", ticket).build().toString().replaceFirst("https://", "wss://")
    }

    fun eventsUrl(room: VoiceRoom): String = base.newBuilder().addPathSegments("ws/calls")
        .addQueryParameter("session", room.id).addQueryParameter("ticket", room.producerToken)
        .build().toString().replaceFirst("https://", "wss://")

    fun cancelPending() = calls.toList().forEach { it.cancel() }

    private suspend fun post(path: String, token: String, body: JSONObject): JSONObject = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(base.newBuilder().addPathSegments(path).build())
            // Maritime reserva Authorization para su propia autenticación.
            .header("X-Voice-Token", token)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val call = client.newCall(request)
        calls.add(call)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                calls.remove(call)
                if (continuation.isActive) continuation.resumeWithException(IOException("No se pudo conectar con el canal de voz."))
            }
            override fun onResponse(call: Call, response: Response) {
                calls.remove(call)
                try {
                    val result = response.use {
                        if (!it.isSuccessful) throw IOException(when (it.code) {
                            401 -> "Código de acceso incorrecto o sesión vencida."
                            429 -> "Hay demasiadas sesiones o intentos. Termina una sesión y vuelve a intentar."
                            else -> "El servicio de voz devolvió HTTP ${it.code}."
                        })
                        val text = it.body?.string().orEmpty()
                        check(text.length <= 131072) { "Respuesta del canal demasiado grande." }
                        JSONObject(text)
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
