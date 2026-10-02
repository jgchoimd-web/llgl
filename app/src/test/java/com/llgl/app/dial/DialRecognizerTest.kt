package com.llgl.app.dial

import com.llgl.app.keyboard.Dir
import com.llgl.app.keyboard.Gesture
import com.llgl.app.keyboard.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class DialRecognizerTest {

    private val density = 2.75f
    private val layout = DialLayout(width = 1080f, height = 400f * density, density = density)
    private val tick = 9f

    private fun pulse() = DialRecognizer(layout, pulseMode = true, outerCount = 16, tickDegrees = tick, tapRadius = 12f * density, longDistance = 42f * density)
    private fun fixed() = DialRecognizer(layout, pulseMode = false, outerCount = 16, innerCounts = mapOf(Ring.VOWEL to 12, Ring.DEEP to 6), tickDegrees = tick, tapRadius = 12f * density, longDistance = 42f * density)

    private fun ringMid(ring: Ring): Float = layout.ringRadii(ring).let { (a, b) -> (a + b) / 2f }
    private fun angleOf(ring: Ring, count: Int, index: Int): Float = layout.itemAngles(ring, count, index).let { (a, b) -> (a + b) / 2f }
    private fun deg(d: Float): Float = (d * PI / 180.0).toFloat()

    /** Moves the recognizer along a straight polar path in small steps. */
    private fun DialRecognizer.glide(r0: Float, a0: Float, r1: Float, a1: Float, steps: Int = 40) {
        for (i in 1..steps) {
            val r = r0 + (r1 - r0) * i / steps
            val a = a0 + (a1 - a0) * i / steps
            val p = layout.toScreen(r, a)
            move(p.x, p.y)
        }
    }

    @Test
    fun `landing on the outer ring and lifting is that item`() {
        val rec = pulse()
        val p = layout.itemCenter(Ring.OUTER, 16, 7)
        rec.begin(p.x, p.y)
        assertEquals(DialRecognizer.Result.Item(Ring.OUTER, 7, pushed = false), rec.end())
    }

    @Test
    fun `turning along the outer ring changes the item like a dial`() {
        val rec = pulse()
        val r = ringMid(Ring.OUTER)
        val a7 = angleOf(Ring.OUTER, 16, 7)
        val a9 = angleOf(Ring.OUTER, 16, 9)
        val p = layout.toScreen(r, a7)
        rec.begin(p.x, p.y)
        rec.glide(r, a7, r, a9)
        assertEquals(DialRecognizer.Result.Item(Ring.OUTER, 9, pushed = false), rec.end())
    }

    @Test
    fun `push in gives the zero-tick vowel and turning counts pulses`() {
        val rec = pulse()
        val rOuter = ringMid(Ring.OUTER)
        val rVowel = ringMid(Ring.VOWEL)
        val a = angleOf(Ring.OUTER, 16, 8)
        val p = layout.toScreen(rOuter, a)
        rec.begin(p.x, p.y)
        rec.glide(rOuter, a, rVowel, a)
        assertEquals(DialRecognizer.Result.Syllable(8, false, 0, 0, null, false), rec.result())
        // Two ticks clockwise (toward the top of the screen = smaller angle).
        rec.glide(rVowel, a, rVowel, a - deg(2 * tick))
        assertEquals(2, (rec.result() as DialRecognizer.Result.Syllable).vowelTicks)
        // Back and one tick the other way.
        rec.glide(rVowel, a - deg(2 * tick), rVowel, a + deg(tick))
        assertEquals(-1, (rec.end() as DialRecognizer.Result.Syllable).vowelTicks)
    }

    @Test
    fun `pushing deeper switches to the second vowel set and restarts the count`() {
        val rec = pulse()
        val rOuter = ringMid(Ring.OUTER)
        val rVowel = ringMid(Ring.VOWEL)
        val rDeep = ringMid(Ring.DEEP)
        val a = angleOf(Ring.OUTER, 16, 8)
        val p = layout.toScreen(rOuter, a)
        rec.begin(p.x, p.y)
        rec.glide(rOuter, a, rVowel, a)
        rec.glide(rVowel, a, rVowel, a - deg(3 * tick))
        rec.glide(rVowel, a - deg(3 * tick), rDeep, a - deg(3 * tick))
        val deep = rec.result() as DialRecognizer.Result.Syllable
        assertEquals(1, deep.vowelSet)
        assertEquals(0, deep.vowelTicks)
        rec.glide(rDeep, a - deg(3 * tick), rDeep, a - deg(4 * tick))
        assertEquals(1, (rec.end() as DialRecognizer.Result.Syllable).vowelTicks)
    }

    @Test
    fun `push back out selects a final by pulses, and past the edge hardens it`() {
        val rec = pulse()
        val rOuter = ringMid(Ring.OUTER)
        val rVowel = ringMid(Ring.VOWEL)
        val a = angleOf(Ring.OUTER, 16, 8)
        val p = layout.toScreen(rOuter, a)
        rec.begin(p.x, p.y)
        rec.glide(rOuter, a, rVowel, a)
        rec.glide(rVowel, a, rOuter, a)
        var s = rec.result() as DialRecognizer.Result.Syllable
        assertEquals(0, s.finalTicks)
        rec.glide(rOuter, a, rOuter, a + deg(tick))
        s = rec.result() as DialRecognizer.Result.Syllable
        assertEquals(-1, s.finalTicks)
        assertEquals(false, s.finalHardened)
        val beyond = layout.radii[3] + layout.beyondMargin + 20f
        rec.glide(rOuter, a + deg(tick), beyond, a + deg(tick))
        s = rec.end() as DialRecognizer.Result.Syllable
        assertEquals(-1, s.finalTicks)
        assertTrue(s.finalHardened)
        assertEquals(0, s.vowelTicks)
    }

    @Test
    fun `going back in cancels the final`() {
        val rec = pulse()
        val rOuter = ringMid(Ring.OUTER)
        val rVowel = ringMid(Ring.VOWEL)
        val a = angleOf(Ring.OUTER, 16, 8)
        val p = layout.toScreen(rOuter, a)
        rec.begin(p.x, p.y)
        rec.glide(rOuter, a, rVowel, a)
        rec.glide(rVowel, a, rOuter, a)
        rec.glide(rOuter, a, rVowel, a)
        assertNull((rec.end() as DialRecognizer.Result.Syllable).finalTicks)
    }

    @Test
    fun `pushing past the outer edge before the vowel hardens the initial`() {
        val rec = pulse()
        val rOuter = ringMid(Ring.OUTER)
        val rVowel = ringMid(Ring.VOWEL)
        val a = angleOf(Ring.OUTER, 16, 8)
        val beyond = layout.radii[3] + layout.beyondMargin + 20f
        val p = layout.toScreen(rOuter, a)
        rec.begin(p.x, p.y)
        rec.glide(rOuter, a, beyond, a)
        rec.glide(beyond, a, rVowel, a)
        val s = rec.end() as DialRecognizer.Result.Syllable
        assertTrue(s.initialHardened)
        assertEquals(8, s.initialIndex)
    }

    @Test
    fun `landing on an inner ring is a syllable with a silent initial`() {
        val rec = pulse()
        val rVowel = ringMid(Ring.VOWEL)
        val a = deg(135f)
        val p = layout.toScreen(rVowel, a)
        rec.begin(p.x, p.y)
        rec.glide(rVowel, a, rVowel, a - deg(tick))
        assertEquals(DialRecognizer.Result.Syllable(null, false, 0, 1, null, false), rec.end())
    }

    @Test
    fun `hub taps and flicks`() {
        val rec = pulse()
        val hub = layout.toScreen(layout.radii[0] * 0.6f, deg(135f))
        rec.begin(hub.x, hub.y)
        assertEquals(DialRecognizer.Result.Hub(Gesture.Tap), rec.end())

        rec.begin(hub.x, hub.y)
        for (i in 1..10) rec.move(hub.x - 6f * density * i, hub.y)
        assertEquals(DialRecognizer.Result.Hub(Gesture.Flick(Dir.W, long = true)), rec.end())
    }

    @Test
    fun `fixed mode reads inner rings as items and a crossing as a push`() {
        val rec = fixed()
        val p = layout.itemCenter(Ring.VOWEL, 12, 5)
        rec.begin(p.x, p.y)
        assertEquals(DialRecognizer.Result.Item(Ring.VOWEL, 5, pushed = false), rec.result())
        val (r, a) = layout.toPolar(p.x, p.y)
        rec.glide(r, a, ringMid(Ring.OUTER), a)
        assertEquals(DialRecognizer.Result.Item(Ring.VOWEL, 5, pushed = true), rec.end())
    }
}
