package com.llgl.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SynthTest {

    @Test
    fun `wav wraps the samples with a 44 byte header`() {
        val pcm = ShortArray(100) { (it * 100).toShort() }
        val bytes = Synth.wav(pcm)
        assertEquals(44 + 200, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        // Little-endian data length.
        assertEquals(200, bytes[40].toInt() and 0xFF or ((bytes[41].toInt() and 0xFF) shl 8))
    }

    @Test
    fun `every clip has sound in it and stays below full scale`() {
        val clips = listOf(Synth.tink(0f), Synth.tink(1f), Synth.clack(), Synth.pop(), Synth.curl(), Synth.rumble(), Synth.chirp(), Synth.hissLoop(), Synth.sloshLoop())
        for (clip in clips) {
            assertTrue(clip.size > 400)
            var peak = 0
            for (s in clip) peak = maxOf(peak, abs(s.toInt()))
            assertTrue("peak $peak", peak in 20_000..26_300)
        }
    }

    @Test
    fun `a loop is shortened by its crossfade and joins quietly`() {
        val raw = Synth.render(1f) { t -> if (t < 0.5f) 0.9f else -0.9f }
        val looped = Synth.loop(raw)
        assertEquals(raw.size - raw.size / 10, looped.size)
        // The crossfaded start continues from the end, so repeating the clip does not jump.
        assertTrue(abs(looped[0].toInt() - looped[looped.size - 1].toInt()) < 2000)
    }
}
