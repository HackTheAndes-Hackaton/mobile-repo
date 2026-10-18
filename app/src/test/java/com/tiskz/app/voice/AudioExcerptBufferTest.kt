package com.tiskz.app.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class AudioExcerptBufferTest {
    @Test fun requiresOneSecondAndClearsOnEnd() {
        val buffer = AudioExcerptBuffer()
        assertNull(buffer.snapshot())
        buffer.accept(ByteArray(32000), 16000, 1)
        val wave = requireNotNull(buffer.snapshot())
        assertEquals(32044, wave.size)
        assertEquals("RIFF", wave.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(16000, ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN).getInt(24))
        buffer.clear()
        assertNull(buffer.snapshot())
    }

    @Test fun retainsLatestTenSecondsAcrossWrappingAndConverts48k() {
        val buffer = AudioExcerptBuffer()
        repeat(11) { second -> buffer.accept(ByteArray(96000) { if (it % 2 == 0) second.toByte() else 0 }, 48000, 1) }
        val wave = requireNotNull(buffer.snapshot())
        assertEquals(320044, wave.size)
        assertEquals(1.toByte(), wave[44])
        assertEquals(10.toByte(), wave[wave.lastIndex - 1])
    }
}
