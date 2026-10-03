package com.llgl.sandbox.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class GridTest {

    private fun grid(w: Int = 60, h: Int = 60) = Grid(w, h, seed = 7)

    private fun Grid.run(steps: Int) = repeat(steps) { step() }

    private fun Grid.fill(x0: Int, y0: Int, x1: Int, y1: Int, e: Byte) {
        for (y in y0..y1) for (x in x0..x1) cells[y * width + x] = e
    }

    private fun Grid.meanY(e: Byte): Double {
        var sum = 0.0
        var n = 0
        for (i in 0 until size) if (cells[i] == e) {
            sum += i / width
            n++
        }
        return if (n == 0) Double.NaN else sum / n
    }

    @Test
    fun `sand falls and piles on the floor without being lost`() {
        val g = grid()
        g.fill(20, 0, 39, 9, Elements.SAND)
        val before = g.count(Elements.SAND)
        g.run(300)
        assertEquals(before, g.count(Elements.SAND))
        for (i in 0 until g.size) if (g.cells[i] == Elements.SAND) assertTrue("sand still high at row ${i / g.width}", i / g.width >= g.height - 20)
        assertTrue(g.meanY(Elements.SAND) > g.height - 8)
    }

    @Test
    fun `water spreads out and levels`() {
        val g = grid()
        g.fill(25, 30, 34, 44, Elements.WATER)
        val before = g.count(Elements.WATER)
        g.run(600)
        assertEquals(before, g.count(Elements.WATER))
        var top = g.height
        for (i in 0 until g.size) if (g.cells[i] == Elements.WATER) top = minOf(top, i / g.width)
        assertTrue("water column still ${g.height - top} high", g.height - top <= 5)
    }

    @Test
    fun `oil floats on water and sand sinks through it`() {
        val g = grid()
        g.fill(10, 40, 49, 49, Elements.OIL)
        g.fill(10, 50, 49, 59, Elements.WATER)
        g.fill(28, 0, 31, 3, Elements.SAND)
        g.run(400)
        assertTrue(g.meanY(Elements.OIL) < g.meanY(Elements.WATER))
        assertTrue("sand should sink to the floor", g.meanY(Elements.SAND) > g.height - 4)
    }

    @Test
    fun `fire burns wood away and water puts fire out`() {
        val g = grid()
        g.fill(10, 50, 30, 59, Elements.WOOD)
        g.paint(31, 55, 2, Elements.FIRE)
        val wood = g.count(Elements.WOOD)
        g.run(900)
        assertTrue("wood left ${g.count(Elements.WOOD)} of $wood", g.count(Elements.WOOD) < wood / 2)

        val wet = grid()
        wet.fill(0, 30, 59, 59, Elements.WATER)
        wet.fill(20, 40, 25, 45, Elements.FIRE)
        for (i in 0 until wet.size) if (wet.cells[i] == Elements.FIRE) wet.life[i] = 60
        // Water creeps into the fire block as the steam at its edges rises away: a row or two per tick,
        // and well before the 60-tick life would let the fire die on its own.
        wet.run(40)
        assertEquals(0, wet.count(Elements.FIRE))
        assertTrue(wet.count(Elements.STEAM) > 0)
    }

    @Test
    fun `lava and water make stone and steam`() {
        val g = grid()
        g.fill(0, 50, 59, 59, Elements.WATER)
        g.paint(30, 40, 3, Elements.LAVA)
        val lava = g.count(Elements.LAVA)
        assertTrue(lava > 0)
        g.run(200)
        assertTrue(g.count(Elements.STONE) > 0)
        assertTrue(g.count(Elements.STEAM) + g.count(Elements.WATER) > 0)
        assertTrue("lava should have set into stone, ${g.count(Elements.LAVA)} of $lava left", g.count(Elements.LAVA) < lava)
    }

    @Test
    fun `a battery lights a lamp through a wire, and an open switch stops it`() {
        val g = grid()
        g.cells[5 * g.width + 5] = Elements.BATTERY
        for (x in 6..15) g.cells[5 * g.width + x] = Elements.WIRE
        val lamp = 5 * g.width + 16
        g.cells[lamp] = Elements.LAMP
        var lit = false
        var firstLitTick = -1L
        for (t in 0 until 60) {
            g.step()
            if (g.charge[lamp] > 0) {
                lit = true
                if (firstLitTick < 0) firstLitTick = g.tick
            }
        }
        assertTrue("lamp never lit", lit)
        assertTrue("pulse should walk one cell per tick: lit at $firstLitTick", firstLitTick in 10..14)

        val open = grid()
        open.cells[5 * open.width + 5] = Elements.BATTERY
        for (x in 6..15) open.cells[5 * open.width + x] = Elements.WIRE
        open.cells[5 * open.width + 10] = Elements.SWITCH_OFF
        open.cells[lamp] = Elements.LAMP
        var everLit = false
        for (t in 0 until 60) {
            open.step()
            if (open.charge[lamp] > 0) everLit = true
        }
        assertFalse(everLit)
        open.toggle(10, 5, 0)
        assertEquals(Elements.SWITCH_ON, open.cells[5 * open.width + 10])
        for (t in 0 until 60) {
            open.step()
            if (open.charge[lamp] > 0) everLit = true
        }
        assertTrue(everLit)
    }

    private fun Grid.charged(): Int {
        var n = 0
        for (i in 0 until size) if (charge[i] > 0) n++
        return n
    }

    /** A rectangular ring of wire, corners (10,10) and (30,20): 60 cells round. */
    private fun Grid.wireLoop() {
        for (x in 10..30) {
            cells[10 * width + x] = Elements.WIRE
            cells[20 * width + x] = Elements.WIRE
        }
        for (y in 10..20) {
            cells[y * width + 10] = Elements.WIRE
            cells[y * width + 30] = Elements.WIRE
        }
    }

    @Test
    fun `a battery keeps pulses running round a wire loop, while a lone spark's two pulses meet and cancel`() {
        val fed = grid()
        fed.wireLoop()
        fed.cells[10 * fed.width + 9] = Elements.BATTERY
        for (t in 0 until 200) {
            fed.step()
            assertTrue("loop went quiet at tick ${fed.tick}", fed.charged() > 0)
        }

        val lone = grid()
        lone.wireLoop()
        lone.spark(10, 10, 0)
        lone.run(5)
        assertTrue(lone.charged() > 0)
        lone.run(200)
        assertEquals("the two pulses should have met on the far side and cancelled", 0, lone.charged())
    }

    @Test
    fun `a powered heater boils water and lightning charges wires`() {
        val g = grid()
        g.cells[30 * g.width + 10] = Elements.BATTERY
        for (x in 11..19) g.cells[30 * g.width + x] = Elements.WIRE
        g.cells[30 * g.width + 20] = Elements.HEATER
        // A tub whose left wall is the heater: floor, far wall, and a lip above the heater.
        g.fill(20, 31, 26, 31, Elements.WALL)
        g.fill(26, 26, 26, 30, Elements.WALL)
        g.fill(20, 26, 20, 29, Elements.WALL)
        g.fill(21, 27, 25, 30, Elements.WATER)
        val water = g.count(Elements.WATER)
        var sawSteam = false
        for (t in 0 until 400) {
            g.step()
            if (g.count(Elements.STEAM) > 0) sawSteam = true
        }
        assertTrue("the heater never boiled anything", sawSteam)
        assertTrue(g.count(Elements.WATER) <= water)

        val w = grid()
        for (x in 10..20) w.cells[40 * w.width + x] = Elements.WIRE
        w.spark(15, 40, 0)
        assertTrue(w.charge[40 * w.width + 15] > 0)
        w.step()
        assertTrue(w.charge[40 * w.width + 14] > 0 && w.charge[40 * w.width + 16] > 0)
    }

    @Test
    fun `acid eats wood, gunpowder explodes, plants drink water`() {
        val acid = grid()
        acid.fill(20, 50, 39, 59, Elements.WOOD)
        acid.fill(25, 45, 34, 49, Elements.ACID)
        val wood = acid.count(Elements.WOOD)
        acid.run(400)
        assertTrue(acid.count(Elements.WOOD) < wood)

        val boom = grid()
        boom.fill(20, 50, 39, 59, Elements.GUNPOWDER)
        boom.paint(30, 48, 1, Elements.FIRE)
        boom.run(60)
        assertTrue(boom.count(Elements.GUNPOWDER) < 100)

        val garden = grid()
        garden.fill(0, 50, 59, 59, Elements.WATER)
        garden.fill(29, 49, 30, 49, Elements.PLANT)
        garden.run(600)
        assertTrue(garden.count(Elements.PLANT) > 10)
    }

    @Test
    fun `steam condenses back into water`() {
        val g = grid()
        g.fill(10, 20, 49, 29, Elements.STEAM)
        for (i in 0 until g.size) if (g.cells[i] == Elements.STEAM) g.life[i] = 40
        g.run(400)
        assertEquals(0, g.count(Elements.STEAM))
        assertTrue(g.count(Elements.WATER) > 0)
    }

    @Test
    fun `painting builds solids over anything but only pours into empty cells`() {
        val g = grid()
        g.paint(10, 10, 2, Elements.SAND)
        val sand = g.count(Elements.SAND)
        g.paint(10, 10, 2, Elements.WATER)
        assertEquals(sand, g.count(Elements.SAND))
        assertEquals(0, g.count(Elements.WATER))
        g.paint(10, 10, 2, Elements.WALL)
        assertEquals(0, g.count(Elements.SAND))
        g.erase(10, 10, 3)
        assertEquals(0, g.count(Elements.WALL))
    }

    @Test
    fun `save and load round-trip`() {
        val g = grid()
        for (e in Elements.palette) g.paint((e * 3 + 5) % g.width, (e * 7 + 10) % g.height, 2, e)
        g.run(50)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { g.save(it) }
        val copy = grid()
        assertTrue(copy.load(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))))
        assertTrue(g.cells.contentEquals(copy.cells))
        assertTrue(g.life.contentEquals(copy.life))
        assertTrue(g.charge.contentEquals(copy.charge))
        assertEquals(g.tick, copy.tick)
        assertFalse(Grid(10, 10).load(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))))
    }

    @Test
    fun `a chaotic grid keeps stepping without errors`() {
        val g = grid(80, 80)
        for (k in 0 until 200) {
            val e = Elements.palette[k % Elements.palette.size]
            g.paint((k * 37) % g.width, (k * 53) % g.height, 2 + k % 3, e)
        }
        g.spark(40, 40, 5)
        g.run(500)
        for (i in 0 until g.size) assertTrue(g.cells[i] >= 0 && g.cells[i] < Elements.COUNT)
        assertEquals(500L, g.tick)
    }
}
