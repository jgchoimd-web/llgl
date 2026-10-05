package com.llgl.app.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class CreaturesTest {

    private fun box(seed: Long = 7L) = Terrarium(360f, 800f, seed)

    private fun Terrarium.run(seconds: Float, check: (() -> Unit)? = null) {
        repeat((seconds * 60f).toInt()) {
            step(1f / 60f)
            check?.invoke()
        }
    }

    @Test
    fun `a tapped isopod curls into a ball that rolls, then walks again`() {
        val world = box()
        val isopod = world.creatures.first { it is Isopod } as Isopod
        val before = world.particles.count
        world.tap(isopod.x, isopod.y)
        assertEquals(Isopod.State.CURLED, isopod.state)
        assertTrue(isopod.ballIndex >= 0)
        assertEquals(before + 1, world.particles.count)
        assertEquals(Particles.BALL, world.particles.type[isopod.ballIndex])
        assertTrue(world.events.any { it is Event.Curl })

        // Rolling: the ball follows gravity and the isopod follows the ball.
        world.gravityX = 1f
        val startX = isopod.x
        world.run(1f)
        assertTrue("curled isopod should roll right", isopod.x > startX + 20f)

        world.gravityX = 0f
        world.run(6f)
        assertTrue("isopod should have unrolled", isopod.state != Isopod.State.CURLED)
        assertEquals(-1, isopod.ballIndex)
        assertEquals(before, world.particles.count)
    }

    @Test
    fun `curled isopods keep pointing at their own balls while others unroll`() {
        val world = box()
        val isopods = world.creatures.filterIsInstance<Isopod>()
        assertEquals(3, isopods.size)
        for (i in isopods) i.curl(world)
        world.run(8f) {
            for (i in isopods) {
                if (i.state != Isopod.State.CURLED) continue
                val idx = i.ballIndex
                assertTrue(idx in 0 until world.particles.count)
                assertEquals(Particles.BALL, world.particles.type[idx])
                assertEquals(world.particles.x[idx], i.x, 1e-3f)
            }
        }
        assertTrue(isopods.none { it.state == Isopod.State.CURLED })
        assertEquals(0, (0 until world.particles.count).count { world.particles.type[it] == Particles.BALL })
    }

    @Test
    fun `a snail trail never exceeds its capacity`() {
        val world = box()
        val snail = world.creatures.first { it is Snail } as Snail
        repeat(7200) { snail.step(1f / 60f, world) }
        assertTrue(snail.trail.count <= Snail.TRAIL_POINTS)
        assertEquals(Snail.TRAIL_POINTS, snail.trail.count)
    }

    @Test
    fun `creatures flee from the thumb`() {
        val world = box()
        val isopod = world.creatures.first { it is Isopod } as Isopod
        val fx = isopod.x + 20f
        val fy = isopod.y
        world.fingerDown(fx, fy)
        val away = cos(isopod.heading) * (isopod.x - fx) + sin(isopod.heading) * (isopod.y - fy)
        assertTrue("heading should point away from the finger", away > 0f)
        val d0 = hypot(isopod.x - fx, isopod.y - fy)
        world.run(0.5f)
        assertTrue(hypot(isopod.x - fx, isopod.y - fy) > d0)
    }

    @Test
    fun `everyone stays inside the box`() {
        val world = box()
        world.nightness = 1f
        world.run(20f) {
            for (c in world.creatures) {
                assertTrue(c.x >= 0f && c.x <= world.width)
                assertTrue(c.y >= 0f && c.y <= world.height)
            }
        }
    }
}
