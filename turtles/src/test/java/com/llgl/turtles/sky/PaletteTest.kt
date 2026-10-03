package com.llgl.turtles.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PaletteTest {
    private fun maxChannelDiff(a: Int, b: Int): Int {
        var m = 0
        for (shift in intArrayOf(0, 8, 16, 24)) m = maxOf(m, abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF)))
        return m
    }

    @Test
    fun `colours change smoothly around the clock and wrap at midnight`() {
        var prev = Palette.at(0f)
        var h = 0.1f
        while (h <= 24f) {
            val c = Palette.at(h)
            assertTrue("jump at $h", maxChannelDiff(prev.top, c.top) <= 20 && maxChannelDiff(prev.bottom, c.bottom) <= 20)
            assertTrue(abs(prev.stars - c.stars) < 0.1f)
            prev = c
            h += 0.1f
        }
        assertEquals(Palette.at(0f).top, Palette.at(24f).top)
        assertEquals(Palette.at(0f).bottom, Palette.at(-0f).bottom)
        assertEquals(Palette.at(1f).top, Palette.at(25f).top)
    }

    @Test
    fun `stars come out at night and the day is bright`() {
        assertTrue(Palette.at(1f).stars > 0.9f)
        assertTrue(Palette.at(23f).stars > 0.8f)
        assertEquals(0f, Palette.at(12f).stars, 0f)
        assertEquals(0f, Palette.at(12f).night, 0f)
        assertTrue(Palette.at(6.5f).night in 0.1f..0.6f)
        assertEquals(0xFF808080.toInt(), Palette.lerpColor(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
    }
}
