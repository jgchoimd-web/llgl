package com.llgl.vibe.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SequencerTest {

    @Test
    fun `an empty pattern is one silent bar`() {
        val p = Pattern()
        p.bpm = 120
        val s = Sequencer.render(p, 1f)
        assertTrue(s.isSilent)
        assertEquals(16 * 125L, s.totalMs)
        assertEquals(1, s.timings.size)
    }

    @Test
    fun `a kick on the first step starts strong and decays, the bar keeps its length`() {
        val p = Pattern()
        p.bpm = 120
        p.toggle(0, 0)
        p.toggle(2, 4)
        val s = Sequencer.render(p, 1f)
        assertEquals(16 * 125L, s.totalMs)
        assertEquals(255, s.amplitudes[0])
        assertTrue(s.timings[0] in 5L..70L)
        var t = 0L
        var kickLength = 0L
        for (i in s.timings.indices) {
            if (s.amplitudes[i] > 0 && t < 70) kickLength += s.timings[i]
            t += s.timings[i]
        }
        assertEquals(70L, kickLength)
        // the tick lands at step 4 = 500 ms
        var at = 0L
        var tickStart = -1L
        for (i in s.timings.indices) {
            if (at >= 500 && s.amplitudes[i] > 0) {
                tickStart = at
                break
            }
            at += s.timings[i]
        }
        assertEquals(500L, tickStart)
    }

    @Test
    fun `intensity scales and the save format round-trips`() {
        val p = Pattern()
        p.bpm = 90
        p.toggle(1, 3)
        val half = Sequencer.render(p, 0.5f)
        assertEquals(85, half.amplitudes.max())
        val saved = p.save()
        val back = Pattern.load(saved)!!
        assertEquals(90, back.bpm)
        assertTrue(back.grid[1][3])
        assertFalse(back.grid[0][3])
        assertEquals(saved, back.save())
        assertNull(Pattern.load("nonsense"))
        assertEquals(167L, p.stepMs)
    }
}
