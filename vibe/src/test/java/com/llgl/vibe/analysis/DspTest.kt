package com.llgl.vibe.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DspTest {
    private val rate = 16000

    private fun filtered(filter: Biquad, hz: Float): Float {
        val x = Dsp.sine(rate, hz, 1f)
        val y = FloatArray(x.size) { filter.process(x[it]) }
        return Dsp.rms(y, rate / 2) // skip the transient
    }

    @Test
    fun `rms of a sine is amplitude over root two`() {
        val x = Dsp.sine(rate, 440f, 1f, amplitude = 0.5f)
        assertEquals(0.5f / sqrt(2f), Dsp.rms(x), 0.01f)
        assertEquals(0f, Dsp.rms(FloatArray(0)), 0f)
    }

    @Test
    fun `low-pass keeps bass and drops treble`() {
        assertTrue(filtered(Biquad.lowPass(rate, 150f), 50f) > 0.6f)
        assertTrue(filtered(Biquad.lowPass(rate, 150f), 2000f) < 0.05f)
    }

    @Test
    fun `high-pass keeps treble and drops bass`() {
        assertTrue(filtered(Biquad.highPass(rate, 2500f), 5000f) > 0.6f)
        assertTrue(filtered(Biquad.highPass(rate, 2500f), 60f) < 0.02f)
    }

    @Test
    fun `band-pass peaks at its centre`() {
        val centre = filtered(Biquad.bandPass(rate, 700f, 0.6f), 700f)
        assertTrue(centre > 0.6f)
        assertTrue(filtered(Biquad.bandPass(rate, 700f, 0.6f), 60f) < centre * 0.3f)
        assertTrue(filtered(Biquad.bandPass(rate, 700f, 0.6f), 6000f) < centre * 0.3f)
    }

    @Test
    fun `decimate shortens by the factor and keeps a slow wave`() {
        val x = Dsp.sine(rate, 100f, 1f)
        val y = Dsp.decimate(x, rate, 2)
        assertEquals(x.size / 2, y.size)
        assertEquals(Dsp.rms(x, 1000), Dsp.rms(y, 500), 0.05f)
        assertTrue(Dsp.decimate(x, rate, 1) === x)
    }

    @Test
    fun `percentile is a robust maximum`() {
        val v = FloatArray(100) { it / 99f }
        assertEquals(0.98f, Dsp.percentile(v, 0.98f), 0.02f)
        assertEquals(0f, Dsp.percentile(FloatArray(0), 0.5f), 0f)
    }
}
