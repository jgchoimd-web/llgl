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
    fun `the hole grid is centred on the ring and snapping picks the nearest hole`() {
        val step = (9.0 * Math.PI / 180.0).toFloat()
        val (aMin, aMax) = layout.range(Ring.VOWEL)
        val mid = (aMin + aMax) / 2f
        val grid = layout.gridAngles(Ring.VOWEL, step)
        assertTrue(grid.any { Math.abs(it - mid) < 1e-5f })
        assertTrue("grid reaches past both ends", grid.first() < aMin && grid.last() > aMax)
        for (i in 1 until grid.size) assertEquals(step, grid[i] - grid[i - 1], 1e-5f)
        assertEquals(mid + step, layout.snapToGrid(Ring.VOWEL, mid + step * 0.6f, step), 1e-5f)
        assertEquals(mid, layout.snapToGrid(Ring.VOWEL, mid + step * 0.4f, step), 1e-5f)
        assertEquals(mid - 2 * step, layout.snapToGrid(Ring.VOWEL, mid - step * 1.7f, step), 1e-5f)
        assertEquals(layout.itemStep(Ring.OUTER, 16) * 16, aMaxMinusMin(Ring.OUTER), 1e-4f)
    }

    private fun aMaxMinusMin(ring: Ring): Float = layout.range(ring).let { (a, b) -> b - a }

    @Test
    fun `left-handed mode mirrors the dial`() {
        val mirrored = DialLayout(width = 1080f, height = 400f * density, density = density, leftHanded = true)
        val c = layout.itemCenter(Ring.OUTER, 16, 7)
        val (r, a) = mirrored.toPolar(layout.width - c.x, c.y)
        assertEquals(Zone.OUTER, mirrored.zone(r))
        assertEquals(7, mirrored.index(Ring.OUTER, a, 16))
    }
}
