package com.llgl.vibe.haptics

import com.llgl.vibe.analysis.LiveAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveScoreTest {
    private fun frame(loud: Float = 0.5f, bass: Float = 0f, onset: Float = 0f, beat: Boolean = false, pitch: Float = 0f, voice: Float = 0f) =
        LiveAnalyzer.Frame(loud, bass, onset, beat, pitch, voice)

    @Test
    fun `a beat starts a decaying pulse in rhythm mode, as strong as the sound`() {
        val s = LiveScore().apply { mode = Mode.RHYTHM }
        assertEquals(0, s.amplitude(frame(), 20f))
        val first = s.amplitude(frame(loud = 1f, onset = 1f, beat = true), 20f)
        assertEquals(255, first)
        val second = s.amplitude(frame(), 20f)
        assertTrue(second in 1 until first)
        var zeroAfter = -1
        for (i in 0 until 10) if (s.amplitude(frame(), 20f) == 0) {
            zeroAfter = i
            break
        }
        assertTrue(zeroAfter in 2..6)
        // The same hit at half the level is about half as strong; a softer onset a little less.
        val quiet = LiveScore().apply { mode = Mode.RHYTHM }
        val half = quiet.amplitude(frame(loud = 0.5f, onset = 1f, beat = true), 20f)
        assertTrue("$half", half in 120..135)
        val soft = LiveScore().apply { mode = Mode.RHYTHM }
        assertTrue(soft.amplitude(frame(loud = 0.5f, onset = 0.2f, beat = true), 20f) < half)
    }

    @Test
    fun `bass mode gates, holds and releases`() {
        val s = LiveScore().apply { mode = Mode.BASS }
        assertEquals(0, s.amplitude(frame(bass = 0.05f), 20f))
        val held = s.amplitude(frame(bass = 0.9f), 20f)
        assertTrue(held > 200)
        assertEquals(held, s.amplitude(frame(bass = 0.9f), 20f))
        val release = s.amplitude(frame(bass = 0f), 20f)
        assertTrue(release in 1 until held)
        s.intensity = 0.5f
        assertTrue(s.amplitude(frame(bass = 0.9f), 20f) <= 128)
    }

    @Test
    fun `voice mode snaps on each syllable and lets go in the gaps`() {
        val s = LiveScore().apply { mode = Mode.VOICE }
        assertEquals(0, s.amplitude(frame(voice = 0.05f), 20f))
        val first = s.amplitude(frame(voice = 0.9f), 20f)
        assertEquals(255, first)
        val held = (0 until 6).map { s.amplitude(frame(voice = 0.9f), 20f) }
        assertTrue("$held", held.all { it in 220..245 })
        val release = (0 until 3).map { s.amplitude(frame(voice = 0f), 20f) }
        assertTrue("$release", release[0] in 100..160 && release[2] < 60)
        // A softer syllable after a loud one still gets its own edge.
        s.amplitude(frame(voice = 0f), 20f)
        assertTrue(s.amplitude(frame(voice = 0.5f), 20f) > 150)
    }

    @Test
    fun `full mode is the loudest of rhythm, scaled bass and scaled voice, melody ticks with pitch`() {
        val s = LiveScore().apply { mode = Mode.FULL }
        val beat = s.amplitude(frame(loud = 1f, bass = 0.9f, onset = 1f, beat = true), 20f)
        assertEquals(255, beat)
        val later = (0 until 8).map { s.amplitude(frame(bass = 0.9f), 20f) }.last()
        assertTrue("$later", later in 120..180)
        val v = LiveScore().apply { mode = Mode.FULL }
        assertTrue(v.amplitude(frame(voice = 0.9f), 20f) in 140..160)

        val m = LiveScore().apply { mode = Mode.MELODY }
        var ticks = 0
        var peak = 0
        repeat(50) {
            val a = m.amplitude(frame(loud = 0.8f, pitch = 440f), 20f)
            if (a > 0) ticks++
            if (a > peak) peak = a
        }
        assertTrue("$peak", peak in 210..225) // 255 × 0.8^0.7
        // One second of frames ticks at the pulse rate for that pitch (about 11 Hz for 440 Hz).
        val expected = (Score.pulseRate(440f) * 50 * 0.02f).toInt()
        assertTrue("$ticks ticks, expected about $expected", ticks in expected - 1..expected + 1)
        assertEquals(0, m.amplitude(frame(loud = 0.8f, pitch = 0f), 20f))
    }

    @Test
    fun `windows become fixed-length waveforms and envelopes`() {
        val s = LiveScore()
        val seg = s.segments(intArrayOf(0, 0, 200, 200, 0), 20L)
        assertEquals(100L, seg.totalMs)
        assertTrue(seg.timings.contentEquals(longArrayOf(40L, 40L, 20L)))
        assertTrue(seg.amplitudes.contentEquals(intArrayOf(0, 200, 0)))
        assertEquals(100L, s.segments(IntArray(5), 20L).totalMs)

        val frames = listOf(frame(pitch = 440f, loud = 0.9f), frame(pitch = 440f, loud = 0.9f), frame(), frame(), frame(pitch = 880f, loud = 0.5f))
        val points = s.envelope(frames, 20L, minPointMs = 40L, maxPoints = 8, minHz = 100f, maxHz = 300f)
        assertEquals(3, points.size)
        assertEquals(40L, points[0].durationMs)
        assertEquals(220f, points[0].frequencyHz, 0.01f)
        assertTrue(points[0].amplitude > 0.8f)
        assertEquals(0f, points[1].amplitude, 0f)
        assertEquals(220f, points[2].frequencyHz, 0.01f)
        assertEquals(20L, points[2].durationMs)
        assertTrue(s.envelope(emptyList(), 20L, 20L, 8, 100f, 300f).isEmpty())
    }
}
