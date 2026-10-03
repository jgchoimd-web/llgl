package com.llgl.vibe.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LiveAnalyzerTest {
    private val rate = 16000

    /** The same signal as AnalyzerTest: clicks every 500 ms, a 60 Hz hum in seconds 2-4. */
    private fun synthetic(): FloatArray {
        val n = rate * 10
        val x = FloatArray(n)
        var seed = 777
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

    private fun run(signal: FloatArray): List<LiveAnalyzer.Frame> {
        val a = LiveAnalyzer(rate, 20)
        val frames = ArrayList<LiveAnalyzer.Frame>()
        var off = 0
        while (off + a.blockSize <= signal.size) {
            a.feed(signal, off)?.let { frames += it }
            off += a.blockSize
        }
        return frames
    }

    @Test
    fun `streams out one frame per block, one block late`() {
        val a = LiveAnalyzer(rate, 20)
        assertEquals(320, a.blockSize)
        assertEquals(null, a.feed(FloatArray(320)))
        assertTrue(a.feed(FloatArray(320)) != null)
    }

    @Test
    fun `beats land on the clicks without seeing the future`() {
        val frames = run(synthetic())
        val beatsMs = frames.withIndex().filter { it.value.beat }.map { it.index * 20f }
        assertTrue("found ${beatsMs.size} beats: $beatsMs", beatsMs.size in 18..22)
        for (b in beatsMs) {
            val nearest = Math.round(b / 500f) * 500f
            assertTrue("beat at $b ms is far from a click", abs(b - nearest) <= 60f)
        }
    }

    @Test
    fun `bass follows the hum and silence reads as silence`() {
        val frames = run(synthetic())
        fun mean(from: Int, to: Int, pick: (LiveAnalyzer.Frame) -> Float): Float {
            var s = 0f
            for (f in from / 20 until to / 20) s += pick(frames[f])
            return s / ((to - from) / 20)
        }
        assertTrue(mean(2300, 3800) { it.bass } > 0.6f)
        assertTrue(mean(5200, 5400) { it.bass } < 0.2f)
        val a = LiveAnalyzer(rate, 20)
        repeat(3) { a.feed(FloatArray(320)) }
        assertTrue(a.lastRms < 0.001f)
    }

    @Test
    fun `pitch of a sine comes through`() {
        val x = FloatArray(rate * 3)
        Dsp.sine(rate, 220f, 1f, amplitude = 0.6f, into = x, offset = rate)
        val frames = run(x)
        val voiced = (1200 / 20 until 1900 / 20).map { frames[it].pitch }
        val near = voiced.count { abs(it - 220f) < 4f }
        assertTrue("only $near of ${voiced.size} near 220: $voiced", near >= voiced.size * 0.9)
        assertTrue((5 until 40).all { frames[it].pitch == 0f })
    }
}
