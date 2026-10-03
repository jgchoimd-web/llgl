package com.llgl.app.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PlantsTest {

    @Test
    fun `plants keep their distance from each other and from the walls`() {
        val plants = Plants.generate(Rng(3L), 360f, 800f, 0f)
        assertTrue(plants.size >= 7)
        for (p in plants) {
            assertTrue(p.x in 18f..342f)
            assertTrue(p.y in 18f..782f)
        }
        for (i in plants.indices) for (j in i + 1 until plants.size) {
            val a = plants[i]
            val b = plants[j]
            assertTrue(hypot(a.x - b.x, a.y - b.y) >= a.footprint + b.footprint)
        }
    }

    @Test
    fun `plants grow over the first week and sprouts appear daily`() {
        assertEquals(0.6f, Plants.growth(0f), 1e-6f)
        assertEquals(0.8f, Plants.growth(3.5f), 1e-6f)
        assertEquals(1f, Plants.growth(7f), 1e-6f)
        assertEquals(1f, Plants.growth(30f), 1e-6f)
        val young = Plants.generate(Rng(5L), 360f, 800f, 0f)
        val older = Plants.generate(Rng(5L), 360f, 800f, 3f)
        assertEquals(young.size + 3, older.size)
    }

    @Test
    fun `ferns and mushrooms block marbles, moss does not`() {
        val plants = Plants.generate(Rng(11L), 360f, 800f, 7f)
        for (p in plants) {
            when (p.kind) {
                Plant.Kind.MOSS -> assertTrue(p.obstacle == null)
                else -> assertTrue(p.obstacle != null && p.obstacle.radius > 0f)
            }
        }
    }
}
