package com.llgl.app.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class TerrariumTest {

    private fun box(seed: Long = 99L) = Terrarium(360f, 800f, seed)

    private fun Terrarium.run(seconds: Float) {
        repeat((seconds * 60f).toInt()) { step(1f / 60f) }
    }

    @Test
    fun `a shake quakes the box and curls every isopod`() {
        val world = box()
        world.shake(1f)
        assertTrue(world.quake > 0f)
        assertTrue(world.events.any { it is Event.Shake })
        assertTrue(world.creatures.filterIsInstance<Isopod>().all { it.state == Isopod.State.CURLED })
        world.run(1f)
        assertEquals(0f, world.quake, 1e-3f)
    }

    @Test
    fun `fireflies come out at night and leave by day`() {
        val world = box()
        assertEquals(0, world.fireflyCount())
        world.nightness = 1f
        world.run(10f)
        assertTrue("fireflies at night: ${world.fireflyCount()}", world.fireflyCount() >= 6)
        world.nightness = 0f
        world.run(12f)
        assertEquals(0, world.fireflyCount())
        assertTrue(world.creatures.none { it is Firefly })
    }

    @Test
    fun `crickets chirp only at night`() {
        val world = box()
        world.run(20f)
        var chirps = 0
        repeat(20 * 60) {
            world.step(1f / 60f)
            if (world.events.any { it == Event.Chirp }) chirps++
        }
        assertEquals(0, chirps)
        world.nightness = 1f
        repeat(40 * 60) {
            world.step(1f / 60f)
            if (world.events.any { it == Event.Chirp }) chirps++
        }
        assertTrue("chirps at night: $chirps", chirps >= 2)
    }

    @Test
    fun `a crumb is found and eaten`() {
        val world = box()
        val isopod = world.creatures.first { it is Isopod } as Isopod
        world.longPress(isopod.x + 30f, isopod.y)
        assertEquals(1, world.crumbs.size)
        world.run(25f)
        assertTrue("crumb should be eaten", world.crumbs.isEmpty())
    }

    @Test
    fun `a tap on water is reported as such`() {
        val world = box()
        val p = world.particles
        val w = (0 until p.count).first { p.type[it] == Particles.WATER }
        world.tap(p.x[w], p.y[w])
        assertTrue(world.events.filterIsInstance<Event.Tap>().last().onWater)
        world.step(1f / 60f)
        world.tap(5f, 5f)
        assertTrue(!world.events.filterIsInstance<Event.Tap>().last().onWater)
    }

    @Test
    fun `save and load round-trip`() {
        val world = box()
        world.gravityX = 0.6f
        world.run(3f)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { world.save(it) }

        val copy = box()
        assertTrue(copy.load(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))))
        assertEquals(world.particles.count, copy.particles.count)
        for (i in 0 until world.particles.count) {
            assertEquals(world.particles.type[i], copy.particles.type[i])
            assertEquals(world.particles.x[i], copy.particles.x[i], 1e-4f)
            assertEquals(world.particles.y[i], copy.particles.y[i], 1e-4f)
        }
        val a = world.creatures.filter { it !is Firefly }
        val b = copy.creatures.filter { it !is Firefly }
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].kind, b[i].kind)
            assertEquals(a[i].x, b[i].x, 1e-4f)
            assertEquals(a[i].y, b[i].y, 1e-4f)
        }
    }

    @Test
    fun `a curled isopod is saved walking, without its ball`() {
        val world = box()
        val isopod = world.creatures.first { it is Isopod } as Isopod
        isopod.curl(world)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { world.save(it) }
        val copy = box()
        assertTrue(copy.load(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))))
        assertEquals(0, (0 until copy.particles.count).count { copy.particles.type[it] == Particles.BALL })
        assertEquals(world.particles.count - 1, copy.particles.count)
    }

    @Test
    fun `garbage does not load`() {
        val world = box()
        val before = world.particles.count
        val garbage = DataInputStream(ByteArrayInputStream(ByteArray(64) { 0x7F }))
        assertTrue(!world.load(garbage))
        assertEquals(before, world.particles.count)
    }
}
