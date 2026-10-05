package com.llgl.xnl.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelSceneTest {
    private fun scene(lock: Boolean = false, seed: Int = 5): KernelScene = KernelScene(seed).apply {
        resize(1080, 2400)
        lockLayout = lock
        uptimeMs = 3L * 86400_000L + 4L * 3600_000L + 12L * 60_000L + 33_000L
    }

    private fun run(s: KernelScene, seconds: Float) {
        val steps = (seconds * 30).toInt()
        repeat(steps) {
            s.uptimeMs += 33L
            s.step(1f / 30f)
        }
    }

    @Test
    fun `the log boots, keeps talking with stamped lines, and stays capped`() {
        val s = scene()
        assertTrue(s.log.size >= 8)
        assertTrue(s.log.first().text.contains("XNL 0.1.0"))
        run(s, 120f)
        assertEquals(KernelScene.LOG_CAP, s.log.size)
        val stamps = s.log.map { it.text.substringAfter('[').substringBefore(']').trim().toDouble() }
        for (i in 1 until stamps.size) assertTrue("stamps go backwards at $i", stamps[i] >= stamps[i - 1])
        val kinds = s.log.map { it.text.substringAfter("] ").substringBefore(':') }.toSet()
        assertTrue("only $kinds", kinds.size >= 4)
        assertTrue(s.log.any { it.warn })
    }

    @Test
    fun `the page map stays partly used and the reclaim sweep runs`() {
        val s = scene()
        val total = s.pages.size
        assertEquals(45 * 100, total)
        run(s, 60f)
        val used = s.usedPages.toFloat() / total
        assertTrue("used $used", used in 0.12f..0.6f)
        assertEquals(s.pages.count { it != KernelScene.FREE }, s.usedPages)
        assertTrue(s.scans >= 1)
        assertTrue(s.heat.all { it in 0f..1f })
    }

    @Test
    fun `a tap on a core boosts it, anywhere else raises an interrupt that lands`() {
        val s = scene()
        val r = FloatArray(4)
        s.coreTile(0, r)
        val before = s.coreLoad[0]
        assertTrue(s.tap((r[0] + r[2]) / 2f, (r[1] + r[3]) / 2f))
        assertTrue(s.coreLoad[0] >= minOf(1f, before + 0.45f))
        assertTrue(s.log.last().text.contains("cpu0 boosted"))

        val sparksBefore = s.sparks.size
        assertTrue(s.tap(540f, 1500f))
        assertEquals(sparksBefore + 1, s.sparks.size)
        assertTrue(s.log.last().text.contains("user interrupt"))
        assertTrue(s.pages[(1500f / s.cellPx).toInt() * s.pageCols + (540f / s.cellPx).toInt()] == KernelScene.HOT)
        run(s, 2f)
        assertTrue(s.sparks.none { it.x0 == 540f && it.y0 == 1500f })
        assertTrue(s.log.any { it.text.contains("-> cpu") })
    }

    @Test
    fun `the lock layout keeps the dense parts below the clock`() {
        val lock = scene(lock = true)
        assertTrue(lock.coresTop >= 2400f * 0.40f)
        assertTrue(lock.wordmarkY >= 2400f * 0.5f)
        val home = scene()
        assertTrue(home.coresTop < 2400f * 0.2f)
        assertTrue(home.wordmarkY < 2400f * 0.5f)
        val r = FloatArray(4)
        home.coreTile(home.cores - 1, r)
        assertTrue(r[2] <= 1080f && r[0] >= 0f && r[2] > r[0])
    }

    @Test
    fun `core loads stay in range and keep moving`() {
        val s = scene()
        val start = s.coreLoad.copyOf()
        run(s, 10f)
        assertTrue(s.coreLoad.all { it in 0f..1f })
        assertTrue(s.coreLoad.indices.any { kotlin.math.abs(s.coreLoad[it] - start[it]) > 0.05f })
        s.cores = 4
        assertEquals(4, s.coreLoad.size)
        s.cores = 99
        assertEquals(16, s.cores)
    }

    @Test
    fun `resizing rebuilds the page map and the uptime reads like a kernel`() {
        val s = scene()
        s.resize(540, 1200)
        assertEquals(45, s.pageCols)
        assertEquals(100, s.pageRows)
        assertEquals(12f, s.cellPx, 0.01f)
        assertTrue(s.usedPages > 0)
        assertEquals("3d 04:12:33", Uptime.format(3L * 86400_000L + 4L * 3600_000L + 12L * 60_000L + 33_000L))
        assertEquals("00:00:59", Uptime.format(59_000L))
        assertEquals("00:00:00", Uptime.format(-5L))
    }
}
