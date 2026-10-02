package com.llgl.app.dial

import com.llgl.app.keyboard.Pt
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The dial's rings, from the thumb's joint outward. */
enum class Ring { DEEP, VOWEL, OUTER }

/** Where a touch point is: the hub around the joint, one of the rings, or past the outer edge. */
enum class Zone { HUB, DEEP, VOWEL, OUTER, BEYOND }

/**
 * A rotary dial centred on the thumb's joint, which sits just outside the bottom-right corner of
 * the keyboard (bottom-left when [leftHanded]). Three concentric rings and a hub; every ring is
 * clipped to the angular range that fits inside the keyboard rectangle, and radii are scaled
 * down when the keyboard is too short. Inputs are pixels; screen y points down; angles are
 * radians counter-clockwise from +x with y up (so 90° points to the top of the screen).
 */
class DialLayout(
    val width: Float,
    val height: Float,
    density: Float,
    val leftHanded: Boolean = false,
    pivotOffsetDp: Float = 16f,
    radiiDp: FloatArray = DEFAULT_RADII_DP,
) {
    val pivotX: Float
    val pivotY: Float

    /** Edge radii in pixels: hub edge, deep/vowel edge, vowel/outer edge, outer edge. */
    val radii: FloatArray
    val beyondMargin: Float = 6f * density
    private val ranges: Map<Ring, Pair<Float, Float>>

    init {
        require(radiiDp.size == 4)
        val offset = pivotOffsetDp * density
        val maxOuter = height + offset - 2f * density
        val scale = min(1f, maxOuter / (radiiDp.last() * density))
        radii = FloatArray(4) { radiiDp[it] * density * scale }
        pivotX = width + offset
        pivotY = height + offset
        ranges = Ring.entries.associateWith { ring ->
            val (rIn, rOut) = ringRadii(ring)
            angularRange(rIn, rOut, offset)
        }
    }

    fun ringRadii(ring: Ring): Pair<Float, Float> = when (ring) {
        Ring.DEEP -> radii[0] to radii[1]
        Ring.VOWEL -> radii[1] to radii[2]
        Ring.OUTER -> radii[2] to radii[3]
    }

    /** The widest angle range whose sectors stay inside the keyboard rectangle. */
    private fun angularRange(rIn: Float, rOut: Float, offset: Float): Pair<Float, Float> {
        val pi = PI.toFloat()
        var aMin = acos((-offset / rIn).coerceIn(-1f, 1f))
        val topLimit = (height + offset) / rOut
        if (topLimit < 1f) aMin = max(aMin, pi - asin(topLimit))
        var aMax = pi - asin((offset / rIn).coerceIn(-1f, 1f))
        val leftLimit = (width + offset) / rOut
        if (leftLimit < 1f) aMax = min(aMax, acos(-leftLimit))
        return aMin to aMax
    }

    fun range(ring: Ring): Pair<Float, Float> = ranges.getValue(ring)

    fun toPolar(x: Float, y: Float): Pair<Float, Float> {
        val px = if (leftHanded) width - x else x
        val dx = px - pivotX
        val dy = pivotY - y
        return hypot(dx, dy) to atan2(dy, dx)
    }

    fun toScreen(r: Float, a: Float): Pt {
        val x = pivotX + r * cos(a)
        val y = pivotY - r * sin(a)
        return Pt(if (leftHanded) width - x else x, y)
    }

    fun zone(r: Float): Zone = when {
        r < radii[0] -> Zone.HUB
        r < radii[1] -> Zone.DEEP
        r < radii[2] -> Zone.VOWEL
        r <= radii[3] + beyondMargin -> Zone.OUTER
        else -> Zone.BEYOND
    }

    fun ringOf(zone: Zone): Ring? = when (zone) {
        Zone.DEEP -> Ring.DEEP
        Zone.VOWEL -> Ring.VOWEL
        Zone.OUTER, Zone.BEYOND -> Ring.OUTER
        Zone.HUB -> null
    }

    /** Which of [count] evenly spaced items on [ring] an angle falls on (clamped to the ring's range). */
    fun index(ring: Ring, angle: Float, count: Int): Int {
        val (aMin, aMax) = range(ring)
        val step = (aMax - aMin) / count
        val clamped = angle.coerceIn(aMin, aMax - 1e-4f)
        return ((clamped - aMin) / step).toInt().coerceIn(0, count - 1)
    }

    fun itemAngles(ring: Ring, count: Int, index: Int): Pair<Float, Float> {
        val (aMin, aMax) = range(ring)
        val step = (aMax - aMin) / count
        return (aMin + index * step) to (aMin + (index + 1) * step)
    }

    fun itemCenter(ring: Ring, count: Int, index: Int): Pt {
        val (a0, a1) = itemAngles(ring, count, index)
        val (rIn, rOut) = ringRadii(ring)
        return toScreen((rIn + rOut) / 2f, (a0 + a1) / 2f)
    }

    companion object {
        /** Hub to 21 mm, deep ring to 36 mm, vowel ring to 50 mm, outer ring to 65 mm from the joint. */
        val DEFAULT_RADII_DP = floatArrayOf(136f, 226f, 316f, 408f)
    }
}
