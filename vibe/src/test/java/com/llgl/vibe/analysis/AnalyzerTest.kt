package com.llgl.vibe.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AnalyzerTest {
    private val rate = 16000

    /** 10 s of quiet noise with a sharp click every 500 ms (120 BPM), a 60 Hz bass hum in seconds 2-4. */
    private fun synthetic(): FloatArray {
        val n = rate * 10
        val x = FloatArray(n)
        var seed = 12345
        for (i in 0 until n) {
            seed = seed * 1103515245 + 12345
            x[i] = ((seed ushr 16) and 0x7FFF) / 32768f * 0.02f - 0.01f
        }
        for (click in 0 until 20) {
            val at = click * rate / 2
            for (k in 0 until rate / 100) {
                seed = seed * 1103515245 + 12345
                val noise = ((seed ushr 16) and 0x7FFF) / 32768f * 2f - 1f
                x[at + k] += noise * 0.8f * (1f - k / (rate / 100f))
            }
        }
        Dsp.sine(rate, 60f, 2f, amplitude = 0.5f, into = x, offset = rate * 2)
        return x
    }

    @Test
    fun `beats land on the clicks and the tempo is 120`() {
        val track = Analyzer.analyze(synthetic(), rate)
        assertEquals(500, track.frames)
        val beatsMs = track.beats.map { it * track.hopMs }
        assertTrue("found ${beatsMs.size} beats: $beatsMs", beatsMs.size in 18..22)
        for (b in beatsMs) {
            val nearest = (b / 500f).let { Math.round(it) * 500f }
            assertTrue("beat at $b ms is far from a click", abs(b - nearest) <= 40f)
        }
        assertEquals(120f, track.bpm, 4f)
    }

    @Test
    fun `bass follows the hum and loudness follows everything`() {
        val track = Analyzer.analyze(synthetic(), rate)
        fun mean(a: FloatArray, fromMs: Int, toMs: Int): Float {
            val f0 = track.frameAt(fromMs.toLong())
            val f1 = track.frameAt(toMs.toLong())
            var s = 0f
            for (f in f0 until f1) s += a[f]
            return s / (f1 - f0)
        }
        assertTrue(mean(track.bass, 2200, 3800) > 0.6f)
        assertTrue(mean(track.bass, 5200, 5400) < 0.15f)
        assertTrue(mean(track.loud, 2200, 3800) > mean(track.loud, 5100, 5450))
    }

    @Test
    fun `pitch finds a sine and stays quiet on silence`() {
        val x = FloatArray(rate * 3)
        Dsp.sine(rate, 220f, 1f, amplitude = 0.6f, into = x, offset = rate)
        val track = Analyzer.analyze(x, rate)
        val voiced = (track.frameAt(1100) until track.frameAt(1900)).map { track.pitch[it] }
        val near = voiced.count { abs(it - 220f) < 4f }
        assertTrue("only $near of ${voiced.size} frames near 220 Hz: $voiced", near >= voiced.size * 0.9)
        val silent = (track.frameAt(100) until track.frameAt(800)).map { track.pitch[it] }
        assertTrue(silent.all { it == 0f })
    }

    @Test
    fun `pitch estimator alone`() {
        val x = Dsp.sine(8000, 330f, 0.5f)
        assertEquals(330f, Pitch.estimate(x, 0, 512, 8000), 3f)
        assertEquals(0f, Pitch.estimate(FloatArray(1024), 0, 512, 8000), 0f)
        assertEquals(0f, Pitch.estimate(x, 3000, 2000, 8000), 0f) // out of range window
    }

    @Test
    fun `track survives a save and load`() {
        val track = Analyzer.analyze(synthetic(), rate)
        val back = HapticTrack.load(track.save())!!
        assertEquals(track.frames, back.frames)
        assertEquals(track.bpm, back.bpm, 0.001f)
        assertTrue(track.beats.contentEquals(back.beats))
        for (f in 0 until track.frames) {
            assertEquals(track.loud[f], back.loud[f], 0.001f)
            assertEquals(track.bass[f], back.bass[f], 0.001f)
            assertEquals(track.onset[f], back.onset[f], 0.001f)
            assertEquals(track.pitch[f], back.pitch[f], 0f)
        }
        assertEquals(null, HapticTrack.load(byteArrayOf(1, 2, 3)))
    }
}
