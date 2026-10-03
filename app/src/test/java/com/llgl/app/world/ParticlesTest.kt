package com.llgl.app.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ParticlesTest {

    private fun box(seed: Long = 42L) = Terrarium(360f, 800f, seed)

    private fun Terrarium.run(seconds: Float) {
        repeat((seconds * 60f).toInt()) { step(1f / 60f) }
    }

    private fun Terrarium.assertAllInside() {
        val p = particles
        for (i in 0 until p.count) {
            val r = p.radius[i]
            assertFalse("particle $i has NaN", p.x[i].isNaN() || p.y[i].isNaN())
            assertTrue("particle $i x=${p.x[i]}", p.x[i] >= r - 0.01f && p.x[i] <= width - r + 0.01f)
            assertTrue("particle $i y=${p.y[i]}", p.y[i] >= r - 0.01f && p.y[i] <= height - r + 0.01f)
        }
    }

    @Test
    fun `everything stays inside the walls under any tilt`() {
        val tilts = listOf(0f to 0f, 1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f, 0.7f to 0.7f, -0.5f to 0.9f)
        for ((gx, gy) in tilts) {
            val world = box()
            world.gravityX = gx
            world.gravityY = gy
            world.run(10f)
            world.assertAllInside()
        }
    }

    @Test
    fun `a marble rolls to the low wall and reports the hit`() {
        val world = box()
        world.gravityX = 1f
        var wallHit = false
        for (i in 0 until 600) {
            world.step(1f / 60f)
            if (world.events.any { it is Event.Impact && it.wall }) wallHit = true
        }
        assertTrue("a marble should have hit the right wall", wallHit)
        val p = world.particles
        var nearRight = 0
        for (i in 0 until p.count) if (p.type[i] == Particles.MARBLE && p.x[i] > world.width - p.radius[i] - 40f) nearRight++
        assertTrue("marbles should gather at the right wall, got $nearRight", nearRight >= 3)
    }

    @Test
    fun `sand sleeps when the box lies flat`() {
        val world = box()
        world.run(6f)
        assertTrue("sand still moving at ${world.particles.sandMotion}", world.particles.sandMotion < 0.5f)
    }

    @Test
    fun `sand flows when the box is tilted`() {
        val world = box()
        world.gravityX = -1f
        world.run(5f)
        val p = world.particles
        var left = 0
        var sand = 0
        for (i in 0 until p.count) if (p.type[i] == Particles.SAND) {
            sand++
            if (p.x[i] < world.width * 0.4f) left++
        }
        assertTrue("most sand should have flowed left: $left of $sand", left > sand * 0.8f)
    }

    @Test
    fun `water droplets hold together`() {
        val world = box()
        world.run(4f)
        val p = world.particles
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        for (i in 0 until p.count) if (p.type[i] == Particles.WATER) {
            xs += p.x[i]
            ys += p.y[i]
        }
        var total = 0f
        var n = 0
        for (i in 0 until xs.size step 5) {
            var best = Float.MAX_VALUE
            for (j in xs.indices) {
                if (i == j) continue
                val d = hypot(xs[i] - xs[j], ys[i] - ys[j])
                if (d < best) best = d
            }
            total += best
            n++
        }
        val mean = total / n
        assertTrue("water spread out: mean nearest neighbour $mean", mean < 2.6f)
    }

    @Test
    fun `removing a particle moves the last one into its slot`() {
        val p = Particles(10, 100f, 100f)
        p.add(Particles.SAND, 10f, 10f, 1f, 0)
        p.add(Particles.SAND, 20f, 20f, 1f, 1)
        p.add(Particles.MARBLE, 30f, 30f, 4f, 2)
        p.remove(0)
        assertEquals(2, p.count)
        assertEquals(Particles.MARBLE, p.type[0])
        assertEquals(30f, p.x[0], 1e-6f)
        assertEquals(4f, p.radius[0], 1e-6f)
        assertEquals(Particles.SAND, p.type[1])
    }

    @Test
    fun `a shake kicks everything without losing anything`() {
        val world = box()
        world.shake(1f)
        assertTrue(world.events.any { it is Event.Shake })
        world.run(2f)
        world.assertAllInside()
    }
}
