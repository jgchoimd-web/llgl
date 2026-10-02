package com.llgl.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureClassifierTest {

    private val classifier = GestureClassifier(tapRadius = 12f, longDistance = 42f)

    /** A straight path from the origin of the given length, sampled every 2 px. */
    private fun line(dx: Float, dy: Float, length: Float, from: Pt = Pt(100f, 100f)): List<Pt> {
        val n = (length / 2f).toInt().coerceAtLeast(1)
        return (0..n).map { i -> Pt(from.x + dx * length * i / n, from.y + dy * length * i / n) }
    }

    @Test
    fun `a wobble under the tap radius is a tap`() {
        assertEquals(Gesture.Tap, classifier.classify(listOf(Pt(100f, 100f))))
        assertEquals(Gesture.Tap, classifier.classify(listOf(Pt(100f, 100f), Pt(104f, 97f), Pt(99f, 103f))))
    }

    @Test
    fun `all eight directions, short and long`() {
        val expected = mapOf(
            Pt(1f, 0f) to Dir.E, Pt(1f, -1f) to Dir.NE, Pt(0f, -1f) to Dir.N, Pt(-1f, -1f) to Dir.NW,
            Pt(-1f, 0f) to Dir.W, Pt(-1f, 1f) to Dir.SW, Pt(0f, 1f) to Dir.S, Pt(1f, 1f) to Dir.SE,
        )
        for ((unit, dir) in expected) {
            val norm = kotlin.math.hypot(unit.x, unit.y)
            assertEquals(Gesture.Flick(dir, long = false), classifier.classify(line(unit.x / norm, unit.y / norm, 24f)))
            assertEquals(Gesture.Flick(dir, long = true), classifier.classify(line(unit.x / norm, unit.y / norm, 60f)))
        }
    }

    @Test
    fun `an L-shaped path is a turn of its two legs`() {
        val up = line(0f, -1f, 30f)
        val thenRight = line(1f, 0f, 30f, from = up.last()).drop(1)
        assertEquals(Gesture.Turn(Dir.N, Dir.E), classifier.classify(up + thenRight))

        val down = line(0f, 1f, 30f)
        val thenDownRight = line(0.7f, 0.7f, 30f, from = down.last()).drop(1)
        assertEquals(Gesture.Turn(Dir.S, Dir.SE), classifier.classify(down + thenDownRight))

        val upLeft = line(-0.7f, -0.7f, 30f)
        val thenDownRight2 = line(0.7f, 0.7f, 20f, from = upLeft.last()).drop(1)
        assertEquals(Gesture.Turn(Dir.NW, Dir.SE), classifier.classify(upLeft + thenDownRight2))
    }

    @Test
    fun `a gently curving flick is not a turn`() {
        val path = (0..30).map { i -> val t = i / 30f; Pt(100f + 40f * t, 100f - 8f * t * t) }
        assertEquals(Gesture.Flick(Dir.E, long = false), classifier.classify(path))
    }

    @Test
    fun `a small retreat before lifting keeps the flick direction and length`() {
        val out = line(1f, 0f, 50f)
        val back = listOf(Pt(146f, 100f), Pt(143f, 101f))
        assertEquals(Gesture.Flick(Dir.E, long = true), classifier.classify(out + back))
    }

    @Test
    fun `direction sectors are centred on the compass points`() {
        assertEquals(Dir.E, Dir.fromAngle(20f))
        assertEquals(Dir.NE, Dir.fromAngle(25f))
        assertEquals(Dir.N, Dir.fromAngle(90f))
        assertEquals(Dir.W, Dir.fromAngle(-180f))
        assertEquals(Dir.SE, Dir.fromAngle(-30f))
        assertEquals(10f, GestureClassifier.angleDifference(5f, -5f), 1e-4f)
        assertEquals(180f, GestureClassifier.angleDifference(0f, 180f), 1e-4f)
    }
}
