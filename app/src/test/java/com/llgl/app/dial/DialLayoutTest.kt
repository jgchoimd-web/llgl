package com.llgl.app.dial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialLayoutTest {

    private val density = 2.75f
    private val layout = DialLayout(width = 1080f, height = 400f * density, density = density)

    @Test
    fun `zones follow the radii`() {
        assertEquals(Zone.HUB, layout.zone(layout.radii[0] - 1f))
        assertEquals(Zone.DEEP, layout.zone(layout.radii[0] + 1f))
        assertEquals(Zone.VOWEL, layout.zone(layout.radii[1] + 1f))
        assertEquals(Zone.OUTER, layout.zone(layout.radii[2] + 1f))
        assertEquals(Zone.OUTER, layout.zone(layout.radii[3] + layout.beyondMargin - 1f))
        assertEquals(Zone.BEYOND, layout.zone(layout.radii[3] + layout.beyondMargin + 1f))
    }

    @Test
    fun `every item centre on every ring is inside the keyboard and maps back to its index`() {
        for (ring in Ring.entries) {
            val count = if (ring == Ring.OUTER) 16 else 12
            for (i in 0 until count) {
                val c = layout.itemCenter(ring, count, i)
                assertTrue("$ring $i at $c", c.x in 0f..layout.width && c.y in 0f..layout.height)
                val (r, a) = layout.toPolar(c.x, c.y)
                assertEquals(ring, layout.ringOf(layout.zone(r)))
                assertEquals(i, layout.index(ring, a, count))
            }
        }
    }

    @Test
    fun `outer items are wide enough for a thumb`() {
        val (aMin, aMax) = layout.range(Ring.OUTER)
        val (rIn, rOut) = layout.ringRadii(Ring.OUTER)
        val arcDp = (rIn + rOut) / 2f * (aMax - aMin) / 16 / density
        assertTrue("outer item arc is $arcDp dp", arcDp >= 30f)
        assertTrue((rOut - rIn) / density >= 60f)
    }

    @Test
    fun `a short keyboard scales the dial to fit`() {
        val short = DialLayout(width = 1080f, height = 300f * density, density = density)
        assertTrue(short.radii.last() <= short.height + 16f * density)
    }

    @Test
    fun `left-handed mode mirrors the dial`() {
        val mirrored = DialLayout(width = 1080f, height = 400f * density, density = density, leftHanded = true)
        val c = layout.itemCenter(Ring.OUTER, 16, 7)
        val (r, a) = mirrored.toPolar(layout.width - c.x, c.y)
        assertEquals(Zone.OUTER, mirrored.zone(r))
        assertEquals(7, mirrored.index(Ring.OUTER, a, 16))
    }
}
