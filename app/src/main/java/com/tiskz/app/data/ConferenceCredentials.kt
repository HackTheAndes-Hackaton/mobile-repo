package com.tiskz.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Acceso configurado una vez por el equipo. Cifrado con una clave no exportable de Android.
 * Con otro [prefsName] y [alias] guarda otra credencial (por ejemplo, la API key del panel).
 */
class ConferenceCredentials(
    context: Context,
    prefsName: String = "tiskz_conference_access",
    private val alias: String = ALIAS,
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _configured = MutableStateFlow(read(prefs.getString("origin", "").orEmpty()) != null)
    val configured = _configured.asStateFlow()

    fun save(baseUrl: String, accessCode: String) {
        val origin = validOrigin(baseUrl)
        require(accessCode.length in 12..512) { "Introduce el código privado del equipo." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(origin.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(accessCode.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("origin", origin)
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("value", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
        _configured.value = true
    }

    fun read(baseUrl: String): String? = runCatching {
        val origin = validOrigin(baseUrl)
        if (prefs.getString("origin", null) != origin) return null
        val iv = Base64.decode(prefs.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val value = Base64.decode(prefs.getString("value", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(origin.toByteArray(Charsets.UTF_8))
        String(cipher.doFinal(value), Charsets.UTF_8).takeIf { it.length in 12..512 }
    }.getOrNull()

    fun clear() {
        prefs.edit().clear().apply()
        _configured.value = false
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun validOrigin(baseUrl: String): String = baseUrl.trim().toHttpUrl().also {
        require(it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null)
    }.toString().trimEnd('/')

    private companion object { const val ALIAS = "tiskz-conference-access-v1" }
}
