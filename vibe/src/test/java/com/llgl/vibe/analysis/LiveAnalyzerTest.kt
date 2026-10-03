package com.llgl.vibe.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LiveAnalyzerTest {
    private val rate = 16000

    /** Clicks every 500 ms on faint noise, a 60 Hz hum held from 2 s to 6 s. */
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
        Dsp.sine(rate, 60f, 4f, amplitude = 0.5f, into = x, offset = rate * 2)
        return x
    }

    /** A tone from [fromMs] to [toMs] with 5 ms ramps, added into [x]. */
    private fun tone(x: FloatArray, hz: Float, fromMs: Int, toMs: Int, amplitude: Float) {
        val from = fromMs * rate / 1000
        val to = toMs * rate / 1000
        val ramp = rate * 5 / 1000
        for (i in from until to) {
            val env = minOf(1f, (i - from).toFloat() / ramp, (to - i).toFloat() / ramp)
            x[i] += (amplitude * env * kotlin.math.sin(2.0 * Math.PI * hz * i / rate)).toFloat()
        }
    }

    private fun run(signal: FloatArray, suppress: Boolean = true): List<LiveAnalyzer.Frame> {
        val a = LiveAnalyzer(rate, 20)
        a.suppressBackground = suppress
        val frames = ArrayList<LiveAnalyzer.Frame>()
        var off = 0
        while (off + a.blockSize <= signal.size) {
            a.feed(signal, off)?.let { frames += it }
            off += a.blockSize
        }
        return frames
    }

    private fun mean(frames: List<LiveAnalyzer.Frame>, fromMs: Int, toMs: Int, pick: (LiveAnalyzer.Frame) -> Float): Float {
        var s = 0f
        for (f in fromMs / 20 until toMs / 20) s += pick(frames[f])
        return s / ((toMs - fromMs) / 20)
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
    fun `a hum is felt when it starts and sinks into the background after a couple of seconds`() {
        val frames = run(synthetic())
        assertTrue(mean(frames, 2100, 2600) { it.bass } > 0.6f)
        assertTrue(mean(frames, 5000, 6000) { it.bass } < 0.2f)
        // Without the suppression the hum keeps vibrating for as long as it lasts.
        val raw = run(synthetic(), suppress = false)
        assertTrue(mean(raw, 5000, 6000) { it.bass } > 0.6f)
        assertTrue(mean(raw, 7000, 7500) { it.bass } < 0.2f)
        val a = LiveAnalyzer(rate, 20)
        repeat(3) { a.feed(FloatArray(320)) }
        assertTrue(a.lastRms < 0.001f)
    }

    @Test
    fun `speech syllables stay in the foreground over a constant hum`() {
        val x = FloatArray(rate * 6)
        Dsp.sine(rate, 60f, 6f, amplitude = 0.3f, into = x)
        var at = 500
        while (at + 150 <= 6000) {
            tone(x, 1000f, at, at + 150, 0.3f)
            at += 250
        }
        val frames = run(x)
        var onSyllable = 0f
        var inGap = 0f
        for (k in 10 until 20) {
            val start = 500 + 250 * k
            onSyllable += frames[(start + 80) / 20].voice
            inGap += frames[(start + 200) / 20].voice
        }
        assertTrue("voice on syllables ${onSyllable / 10}", onSyllable / 10 > 0.5f)
        assertTrue("voice in gaps ${inGap / 10}", inGap / 10 < 0.2f)
        assertTrue(mean(frames, 4000, 5900) { it.bass } < 0.2f)
        assertTrue(mean(frames, 4000, 5900) { it.loud } > 0.25f)
    }

    @Test
    fun `steady noise fades out and never beats`() {
        val n = rate * 5
        val x = FloatArray(n)
        var seed = 4242
        for (i in 0 until n) {
            seed = seed * 1103515245 + 12345
            x[i] = ((seed ushr 16) and 0x7FFF) / 32768f * 0.4f - 0.2f
        }
        val frames = run(x)
        assertTrue(mean(frames, 2000, 4900) { it.loud } < 0.1f)
        assertTrue(mean(frames, 2000, 4900) { it.bass } < 0.1f)
        assertEquals(0, frames.drop(50).count { it.beat })
        val raw = run(x, suppress = false)
        assertTrue(mean(raw, 2000, 4900) { it.loud } > 0.5f)
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
