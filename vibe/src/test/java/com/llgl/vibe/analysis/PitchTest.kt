package com.llgl.vibe.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchTest {
    @Test
    fun `finds a sine's pitch and stays quiet on silence or a bad window`() {
        val x = Dsp.sine(8000, 330f, 0.5f)
        assertEquals(330f, Pitch.estimate(x, 0, 512, 8000), 3f)
        assertEquals(0f, Pitch.estimate(FloatArray(1024), 0, 512, 8000), 0f)
        assertEquals(0f, Pitch.estimate(x, 3000, 2000, 8000), 0f) // window runs past the end
    }
}
