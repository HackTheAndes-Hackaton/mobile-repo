package com.tiskz.app.data

import com.tiskz.app.model.NumberReputation
import com.tiskz.app.model.optStringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cuántas personas ya reportaron el número que está llamando (GET /reputation/{número} del panel).
 * Se consulta mientras suena: si tarda o falla, la llamada sigue igual, sin aviso.
 */
class CommunityReputation(
    private val settings: SettingsRepository,
    private val credentials: ConferenceCredentials,
    client: OkHttpClient,
) {
    private val client = client.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()

    suspend fun lookup(number: String): NumberReputation? = withContext(Dispatchers.IO) {
        if (number.count { it.isDigit() } < 6) return@withContext null
        val current = settings.current
        val key = credentials.read(current.panelUrl) ?: return@withContext null
        runCatching {
            val url = current.panelUrl.trimEnd('/').toHttpUrl().newBuilder()
                .addPathSegment("reputation").addPathSegment(number).build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $key").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val data = JSONObject(response.body?.string().orEmpty()).getJSONObject("data")
                NumberReputation(
                    reports = data.optInt("reports"),
                    scam = data.optInt("scam"),
                    mentionedIn = data.optInt("mentioned_in"),
                    scamType = data.optStringOrNull("scam_type"),
                    impersonating = data.optStringOrNull("impersonating"),
                )
            }
        }.getOrNull()
    }
}
