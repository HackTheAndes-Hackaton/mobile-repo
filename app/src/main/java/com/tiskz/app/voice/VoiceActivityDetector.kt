package com.tiskz.app.voice

import kotlin.math.sqrt

/** Usa pausas, nunca cortes forzados durante una palabra. No conserva muestras. */
class VoiceActivityDetector {
    private var startedAt = 0L
    private var lastSpeech = 0L
    private var pending = false

    fun accept(rms: Double, nowMs: Long): Boolean {
        if (rms > 0.012) {
            if (!pending) startedAt = nowMs
            pending = true
            lastSpeech = nowMs
        }
        // Deja margen entre la captura local y la entrega RTP antes del commit por DataChannel.
        // Una pausa breve dentro de una frase larga no debe cortar su negación ni su última palabra.
        val commit = pending && nowMs - startedAt > 400 && nowMs - lastSpeech > 1200
        if (commit) pending = false
        return commit
    }

    companion object {
        fun pcm16Rms(bytes: ByteArray): Double {
            if (bytes.size < 2) return 0.0
            var sum = 0.0
            for (i in 0 until bytes.size - 1 step 2) {
                val sample = ((bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)).toShort().toDouble() / 32768.0
                sum += sample * sample
            }
            return sqrt(sum / (bytes.size / 2))
        }
    }
}
