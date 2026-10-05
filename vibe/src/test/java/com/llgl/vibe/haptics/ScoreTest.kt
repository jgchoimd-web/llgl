package com.llgl.vibe.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTest {
    @Test
    fun `pulse rate follows pitch on a log scale and clamps`() {
        assertEquals(3f, Score.pulseRate(80f), 0.01f)
        assertEquals(14f, Score.pulseRate(800f), 0.01f)
        assertTrue(Score.pulseRate(80f) < Score.pulseRate(400f))
        assertEquals(3f, Score.pulseRate(40f), 0.01f)
        assertEquals(14f, Score.pulseRate(2000f), 0.01f)
    }

    @Test
    fun `scaled honours intensity and clamps to the motor range`() {
        assertEquals(0, Score.scaled(0, 1.5f))
        assertEquals(150, Score.scaled(100, 1.5f))
        assertEquals(255, Score.scaled(200, 1.5f))
        assertEquals(1, Score.scaled(100, 0.001f))
    }

    @Test
    fun `octave folding brings any pitch into the motor range`() {
        assertEquals(220f, Score.foldToRange(880f, 50f, 300f), 0.001f)
        assertEquals(100f, Score.foldToRange(50f, 100f, 300f), 0.001f)
        assertEquals(120f, Score.foldToRange(30f, 100f, 300f), 0.001f)
        assertEquals(150f, Score.foldToRange(150f, 100f, 300f), 0.001f)
        assertEquals(0f, Score.foldToRange(0f, 100f, 300f), 0f)
    }
}
