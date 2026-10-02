package com.llgl.app.keyboard

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Eight flick directions in screen terms (N is up). */
enum class Dir {
    E, NE, N, NW, W, SW, S, SE;

    companion object {
        /** [degrees] is a mathematical angle: 0 = right, 90 = up, counter-clockwise. */
        fun fromAngle(degrees: Float): Dir {
            val a = ((degrees % 360f) + 360f) % 360f
            return entries[((a + 22.5f) / 45f).toInt() % 8]
        }
    }
}

sealed interface Gesture {
    data object Tap : Gesture
    data class Flick(val dir: Dir, val long: Boolean) : Gesture
    data class Turn(val first: Dir, val second: Dir) : Gesture
}

data class Pt(val x: Float, val y: Float)

/**
 * Turns a touch path (screen pixels, y down) into a [Gesture].
 *
 * - Nothing moved further than [tapRadius] from the start: [Gesture.Tap].
 * - The path bends by at least [turnAngleDeg] at a corner that sticks out from the start-to-end
 *   chord: [Gesture.Turn] of the two legs.
 * - Otherwise a [Gesture.Flick] toward the farthest point, "long" from [longDistance] on.
 */
class GestureClassifier(
    private val tapRadius: Float,
    private val longDistance: Float,
    private val turnAngleDeg: Float = 40f,
) {
    fun classify(path: List<Pt>): Gesture {
        if (path.size < 2) return Gesture.Tap
        val start = path.first()
        var farthest = start
        var farthestDistance = 0f
        for (p in path) {
            val d = distance(start, p)
            if (d > farthestDistance) {
                farthestDistance = d
                farthest = p
            }
        }
        if (farthestDistance < tapRadius) return Gesture.Tap

        val end = path.last()
        // An L: the corner sticks out from the start-to-end chord.
        val chord = distance(start, end)
        if (chord > 1e-3f) {
            var corner: Pt? = null
            var cornerDeviation = 0f
            for (i in 1 until path.size - 1) {
                val deviation = perpendicularDistance(start, end, path[i])
                if (deviation > cornerDeviation) {
                    cornerDeviation = deviation
                    corner = path[i]
                }
            }
            if (corner != null && cornerDeviation >= tapRadius * 0.8f) turnAt(start, corner, end)?.let { return it }
        }
        // A V: the path comes back toward the start, so the corner is the farthest point.
        turnAt(start, farthest, end)?.let { return it }
        return Gesture.Flick(Dir.fromAngle(angle(start, farthest)), long = farthestDistance >= longDistance)
    }

    private fun turnAt(start: Pt, corner: Pt, end: Pt): Gesture.Turn? {
        val minLeg = tapRadius * 0.8f
        if (distance(start, corner) < minLeg || distance(corner, end) < minLeg) return null
        val a1 = angle(start, corner)
        val a2 = angle(corner, end)
        if (angleDifference(a1, a2) < turnAngleDeg) return null
        return Gesture.Turn(Dir.fromAngle(a1), Dir.fromAngle(a2))
    }

    companion object {
        fun distance(a: Pt, b: Pt): Float = hypot(b.x - a.x, b.y - a.y)

        /** Mathematical angle in degrees from [a] to [b] (screen y points down, so it is flipped). */
        fun angle(a: Pt, b: Pt): Float = Math.toDegrees(atan2((a.y - b.y).toDouble(), (b.x - a.x).toDouble())).toFloat()

        fun angleDifference(a: Float, b: Float): Float {
            val d = abs(((a - b) % 360f + 540f) % 360f - 180f)
            return d
        }

        private fun perpendicularDistance(a: Pt, b: Pt, p: Pt): Float {
            val vx = b.x - a.x
            val vy = b.y - a.y
            val length = hypot(vx, vy)
            if (length < 1e-3f) return distance(a, p)
            return abs(vx * (p.y - a.y) - vy * (p.x - a.x)) / length
        }
    }
}
