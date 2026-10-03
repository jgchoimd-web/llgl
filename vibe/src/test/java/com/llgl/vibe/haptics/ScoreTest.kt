package com.llgl.vibe.haptics

import com.llgl.vibe.analysis.HapticTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTest {
    /** 100 frames (2 s): beats at frames 10 and 60, bass high in frames 30..40, a 440 Hz note in frames 70..90. */
    private fun track(): HapticTrack {
        val loud = FloatArray(100) { 0.5f }
        val bass = FloatArray(100) { if (it in 30..40) 0.9f else 0.05f }
        val onset = FloatArray(100).also { it[10] = 1f; it[60] = 0.5f }
        val pitch = FloatArray(100) { if (it in 70..90) 440f else 0f }
        return HapticTrack(20f, loud, bass, onset, pitch, intArrayOf(10, 60), 120f)
    }

    @Test
    fun `rhythm pulses only around beats, strong beats longer`() {
        val a = Score.amplitudes(track(), Mode.RHYTHM)
        assertEquals(255, a[10])
        assertTrue(a[11] in 1..254)
        assertTrue(a[9] == 0 && a[20] == 0)
        val strong = (10..19).count { a[it] > 0 }
        val weak = (60..69).count { a[it] > 0 }
        assertTrue(strong > weak)
        assertTrue(a[60] in 180..190)
    }

    @Test
    fun `bass follows the low band with a gate and a short release`() {
        val a = Score.amplitudes(track(), Mode.BASS)
        assertEquals(0, a[5])
        assertTrue(a[35] > 200)
        assertTrue(a[41] in 1 until a[40])
        assertTrue(a[45] < a[41])
        val full = Score.amplitudes(track(), Mode.FULL)
        assertEquals(255, full[10])
        assertTrue(full[35] in 120..180)
    }

    @Test
    fun `melody pulses tick faster for higher notes`() {
        val low = track()
        val high = HapticTrack(20f, low.loud, low.bass, low.onset, FloatArray(100) { if (it in 70..90) 800f else 0f }, low.beats, low.bpm)
        val lowTicks = Score.melodyPulses(low).count { it > 0 }
        val highTicks = Score.melodyPulses(high).count { it > 0 }
        assertTrue("$lowTicks vs $highTicks", highTicks > lowTicks)
        assertTrue(Score.pulseRate(80f) < Score.pulseRate(400f))
        assertEquals(14f, Score.pulseRate(800f), 0.01f)
        assertEquals(0, Score.melodyPulses(low).take(69).count { it > 0 })
    }

    @Test
    fun `segments merge runs, cover exactly the span, and honour intensity`() {
        val amps = intArrayOf(0, 0, 100, 100, 100, 0, 200, 200)
        val s = Score.segments(amps, 20f, 0, 160, 1f)
        assertEquals(160L, s.totalMs)
        assertTrue(s.timings.contentEquals(longArrayOf(40, 60, 20, 40)))
        assertTrue(s.amplitudes.contentEquals(intArrayOf(0, 100, 0, 200)))

        val part = Score.segments(amps, 20f, 50, 130, 1f)
        assertEquals(80L, part.totalMs)
        assertTrue(part.timings.contentEquals(longArrayOf(50, 20, 10)))
        assertTrue(part.amplitudes.contentEquals(intArrayOf(100, 0, 200)))

        val loud = Score.segments(amps, 20f, 0, 160, 1.5f)
        assertEquals(255, loud.amplitudes.max())
        assertEquals(150, loud.amplitudes[1])
        val quiet = Score.segments(amps, 20f, 0, 160, 0.001f)
        assertTrue(quiet.amplitudes.contentEquals(intArrayOf(0, 1, 0, 1)))

        val beyond = Score.segments(amps, 20f, 500, 600, 1f)
        assertTrue(beyond.isSilent)
        assertEquals(100L, beyond.totalMs)
    }

    @Test
    fun `octave folding and envelopes`() {
        assertEquals(220f, Score.foldToRange(880f, 50f, 300f), 0.001f)
        assertEquals(100f, Score.foldToRange(50f, 100f, 300f), 0.001f)
        assertEquals(120f, Score.foldToRange(30f, 100f, 300f), 0.001f)
        assertEquals(150f, Score.foldToRange(150f, 100f, 300f), 0.001f)

        val t = track()
        val points = Score.envelope(t, 1200, 2000, 1f, 100f, 300f, 20L, 1000L, 16)
        assertTrue(points.isNotEmpty() && points.size <= 16)
        assertTrue(points.all { it.durationMs in 20L..1000L })
        val voiced = points.filter { it.amplitude > 0f }
        assertFalse(voiced.isEmpty())
        assertTrue(voiced.all { it.frequencyHz in 100f..300f })
        assertEquals(220f, voiced.first().frequencyHz, 0.5f)
        val silent = points.first()
        assertEquals(0f, silent.amplitude, 0f)

        val coarse = Score.envelope(t, 0, 2000, 1f, 100f, 300f, 20L, 500L, 4)
        assertTrue(coarse.size <= 4)
        assertTrue(Score.envelope(t, 5000, 6000, 1f, 100f, 300f, 20L, 500L, 4).isEmpty())
    }
}
