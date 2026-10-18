package com.tiskz.app.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Búfer volátil de 10 s. Se conserva únicamente si la sesión lo autorizó. */
internal class AudioExcerptBuffer {
    private val pcm = ByteArray(16000 * 2 * 10)
    private var position = 0
    private var size = 0

    @Synchronized fun accept(data: ByteArray, sampleRate: Int, channels: Int) {
        // WebRTC se configura a 48 kHz. También acepta 16/32 kHz sin inventar metadatos.
        if (sampleRate !in listOf(16000, 32000, 48000) || channels !in 1..2) return
        val stride = sampleRate / 16000 * channels * 2
        for (offset in 0..(data.size - stride) step stride) {
            // Decimación de la señal de micrófono para evidencia audible, no embedding.
            pcm[position] = data[offset]
            pcm[(position + 1) % pcm.size] = data[offset + 1]
            position = (position + 2) % pcm.size
            size = minOf(size + 2, pcm.size)
        }
    }

    @Synchronized fun snapshot(): ByteArray? {
        if (size < 32000) return null
        val wave = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
        wave.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(size)
        val start = if (size == pcm.size) position else 0
        for (index in 0 until size) wave.put(pcm[(start + index) % pcm.size])
        return wave.array()
    }

    @Synchronized fun clear() { pcm.fill(0); size = 0; position = 0 }
}
