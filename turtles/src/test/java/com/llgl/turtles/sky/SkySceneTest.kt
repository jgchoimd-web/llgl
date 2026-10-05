package com.llgl.turtles.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkySceneTest {
    private fun scene(lock: Boolean = false, seed: Int = 3): SkyScene = SkyScene(seed).apply {
        resize(1080, 2400)
        lockLayout = lock
        count = 8
    }

    private fun run(s: SkyScene, seconds: Float) {
        val steps = (seconds * 30).toInt()
        repeat(steps) { s.step(1f / 30f) }
    }

    @Test
    fun `turtles keep moving, wrap at the edges and stay in their lanes`() {
        val s = scene()
        run(s, 60f)
        assertTrue("no turtle wrapped in a minute", s.wraps > 0)
        for (t in s.turtles) {
            val size = s.turtleSize(t)
            assertTrue("x ${t.x}", t.x > -size * 2f && t.x < 1080f + size * 2f)
            assertTrue("y ${t.y}", t.y >= s.laneTop - size && t.y <= s.laneBottom + size)
            assertTrue("travelled ${t.travelled}", t.travelled > 100f)
            assertTrue(t.depth in 0.45f..1f)
        }
    }

    @Test
    fun `a tap rolls the nearest turtle, scatters sparkles, and the roll ends`() {
        val s = scene()
        val t = s.turtles.first()
        t.x = 540f
        t.baseY = 1200f
        t.y = 1200f
        assertTrue(s.tap(540f, 1200f))
        assertTrue(t.spin > 0f)
        assertTrue(s.sparkles.isNotEmpty())
        run(s, 1.5f)
        assertEquals(0f, t.spin, 0f)
        run(s, 2f)
        assertTrue(s.sparkles.isEmpty())
        assertFalse("nothing lives in the top corner", s.tap(10f, 10f))
    }

    @Test
    fun `the lock layout keeps the turtles below the clock`() {
        val s = scene(lock = true)
        run(s, 30f)
        for (t in s.turtles) assertTrue("baseY ${t.baseY}", t.baseY >= 2400f * 0.40f - 1f)
        s.lockLayout = false
        assertTrue(s.laneTop < 2400f * 0.40f)
        // Switching to the lock layout later eases the high flyers down instead of teleporting them.
        val home = scene()
        val high = home.turtles.first()
        high.baseY = 300f
        home.lockLayout = true
        home.step(1f / 30f)
        assertTrue(high.baseY > 300f && high.baseY < home.laneTop)
        run(home, 30f)
        assertTrue(high.baseY >= home.laneTop - 1f)
    }

    @Test
    fun `clouds drift and wrap`() {
        val s = scene()
        assertEquals(7, s.clouds.size)
        val xs = s.clouds.map { it.x }
        run(s, 300f)
        assertTrue(s.clouds.indices.any { s.clouds[it].x != xs[it] })
        for (c in s.clouds) assertTrue("cloud x ${c.x} width ${c.width}", c.x >= -c.width * 1.01f && c.x <= 1080f + c.width * 1.01f)
    }

    @Test
    fun `speed scales the distance travelled`() {
        val slow = scene(seed = 9)
        val fast = scene(seed = 9).apply { speedFactor = 2f }
        run(slow, 10f)
        run(fast, 10f)
        val d1 = slow.turtles.sumOf { it.travelled.toDouble() }
        val d2 = fast.turtles.sumOf { it.travelled.toDouble() }
        assertTrue("$d1 vs $d2", d2 > d1 * 1.5)
    }

    @Test
    fun `the count adds and removes turtles within limits`() {
        val s = scene()
        s.count = 3
        assertEquals(3, s.turtles.size)
        s.count = 12
        assertEquals(12, s.turtles.size)
        s.count = 0
        assertEquals(1, s.turtles.size)
        s.count = 99
        assertEquals(14, s.turtles.size)
    }

    @Test
    fun `resizing keeps everything proportionally in place`() {
        val s = scene()
        val t = s.turtles.first()
        val fx = t.x / 1080f
        s.resize(540, 1200)
        assertEquals(fx * 540f, t.x, 0.01f)
        assertTrue(s.turtleSize(t) < 0.11f * 540f + 0.01f)
        run(s, 5f)
        for (c in s.clouds) assertTrue(c.y <= 1200f)
    }
}
