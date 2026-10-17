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
        val commit = pending && nowMs - startedAt > 400 &&
            (nowMs - lastSpeech > 600 || (nowMs - startedAt > 6000 && nowMs - lastSpeech > 180))
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
