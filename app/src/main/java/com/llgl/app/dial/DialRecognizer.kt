package com.llgl.app.dial

import com.llgl.app.keyboard.Gesture
import com.llgl.app.keyboard.GestureClassifier
import com.llgl.app.keyboard.Pt
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Follows one touch around the dial and says what it means so far.
 *
 * Pulse mode (Hangul): land on the outer ring for the initial consonant, push in for the vowel
 * (ticks turned = which vowel; deeper = second vowel set), push back out for the final (ticks =
 * which final; past the edge = hardened), lift. Landing on an inner ring means a silent ㅇ.
 *
 * Fixed mode (English, symbols): every ring has fixed items picked by angle; crossing a ring
 * edge "pushes" the item (capital letter, alternate symbol).
 *
 * The hub works the same everywhere: tap = space, flicks = editing keys.
 *
 * Like a rotary phone, the plate under the thumb turns with it: [grabbedRing] and [rotation] say
 * which plate and how far, so the view can draw the dial moving and spring it back on release.
 */
class DialRecognizer(
    private val layout: DialLayout,
    private val pulseMode: Boolean,
    private val outerCount: Int,
    private val innerCounts: Map<Ring, Int> = emptyMap(),
    private val tickDegrees: Float = 9f,
    tapRadius: Float,
    longDistance: Float,
    /** How far past a ring edge the thumb must go before its zone changes, so edges do not flutter. */
    private val hysteresis: Float = 0f,
    /** One pulse while dialling a final on the outer ring; the view passes the outer ring's hole spacing. */
    private val finalTickDegrees: Float = tickDegrees,
) {
    sealed interface Result {
        data object None : Result
        data class Hub(val gesture: Gesture) : Result

        /** Fixed-item modes and the outer ring: the item under the thumb, and whether it crossed a ring edge. */
        data class Item(val ring: Ring, val index: Int, val pushed: Boolean) : Result

        /**
         * Pulse mode syllable. [initialIndex] is the outer-ring item, or null when the thumb landed on an
         * inner ring (silent ㅇ). [vowelSet] 0 = vowel ring, 1 = deep ring. [finalTicks] is null without a final.
         */
        data class Syllable(
            val initialIndex: Int?,
            val initialHardened: Boolean,
            val vowelSet: Int,
            val vowelTicks: Int,
            val finalTicks: Int?,
            val finalHardened: Boolean,
        ) : Result
    }

    private enum class Phase { NONE, HUB, OUTER, INNER_FIXED, VOWEL }

    private val classifier = GestureClassifier(tapRadius, longDistance)
    private val edges = floatArrayOf(layout.radii[0], layout.radii[1], layout.radii[2], layout.radii[3] + layout.beyondMargin)
    private val hubPath = ArrayList<Pt>()
    private var phase = Phase.NONE
    private var lastZone: Zone? = null
    private var angle = 0f
    private var startRing: Ring? = null
    private var onRing = true
    private var ringEntry = 0f
    private var itemIndex = 0
    private var pushed = false
    private var initialIndex: Int? = null
    private var initialHardened = false
    private var vowelSet = 0
    private var vowelEntry = 0f
    private var vowelTicks = 0
    private var finalActive = false
    private var finalEntry = 0f
    private var finalTicks = 0
    private var finalHardened = false

    /** The plate turning with the thumb right now, or null in the hub or off every plate. */
    val grabbedRing: Ring?
        get() = when (phase) {
            Phase.OUTER -> if (onRing) Ring.OUTER else null
            Phase.INNER_FIXED -> if (onRing) startRing else null
            Phase.VOWEL -> when {
                finalActive -> Ring.OUTER
                vowelSet == 0 -> Ring.VOWEL
                else -> Ring.DEEP
            }
            else -> null
        }

    /** How far the grabbed plate has turned with the thumb since it was grabbed (radians, dial frame). */
    val rotation: Float
        get() = when (phase) {
            Phase.OUTER, Phase.INNER_FIXED -> if (onRing) angle - ringEntry else 0f
            Phase.VOWEL -> angle - (if (finalActive) finalEntry else vowelEntry)
            else -> 0f
        }

    /** Where the thumb entered the vowel ring it is on (radians), while a syllable is being dialled. */
    val vowelEntryAngle: Float?
        get() = if (phase == Phase.VOWEL) vowelEntry else null

    /** Where the thumb came back out onto the outer ring (radians), while a final is being dialled. */
    val finalEntryAngle: Float?
        get() = if (phase == Phase.VOWEL && finalActive) finalEntry else null

    fun begin(x: Float, y: Float) {
        reset()
        val (r, a) = layout.toPolar(x, y)
        angle = a
        when (val zone = stableZone(r)) {
            Zone.HUB -> {
                phase = Phase.HUB
                hubPath += Pt(x, y)
            }
            Zone.OUTER, Zone.BEYOND -> {
                phase = Phase.OUTER
                startRing = Ring.OUTER
                ringEntry = a
                itemIndex = layout.index(Ring.OUTER, a, outerCount)
                initialIndex = itemIndex
            }
            Zone.DEEP, Zone.VOWEL -> {
                val ring = layout.ringOf(zone)!!
                startRing = ring
                if (pulseMode) {
                    phase = Phase.VOWEL
                    initialIndex = null
                    vowelSet = if (ring == Ring.DEEP) 1 else 0
                    vowelEntry = a
                } else {
                    phase = Phase.INNER_FIXED
                    ringEntry = a
                    itemIndex = layout.index(ring, a, innerCounts.getValue(ring))
                }
            }
        }
    }

    fun move(x: Float, y: Float) {
        val (r, a) = layout.toPolar(x, y)
        angle = a
        val zone = stableZone(r)
        when (phase) {
            Phase.NONE -> Unit
            Phase.HUB -> hubPath += Pt(x, y)
            Phase.OUTER -> when (zone) {
                Zone.OUTER, Zone.BEYOND -> {
                    if (!onRing) {
                        onRing = true
                        ringEntry = a
                    }
                    itemIndex = layout.index(Ring.OUTER, a, outerCount)
                    initialIndex = itemIndex
                    if (zone == Zone.BEYOND) {
                        pushed = true
                        initialHardened = true
                    }
                }
                Zone.VOWEL, Zone.DEEP, Zone.HUB -> {
                    pushed = true
                    if (pulseMode) {
                        phase = Phase.VOWEL
                        vowelSet = if (zone == Zone.VOWEL) 0 else 1
                        vowelEntry = a
                        vowelTicks = 0
                    } else {
                        onRing = false
                    }
                }
            }
            Phase.INNER_FIXED -> {
                val ring = startRing!!
                if (layout.ringOf(zone) == ring) {
                    if (!onRing) {
                        onRing = true
                        ringEntry = a
                    }
                    itemIndex = layout.index(ring, a, innerCounts.getValue(ring))
                } else {
                    pushed = true
                    onRing = false
                }
            }
            Phase.VOWEL -> when (zone) {
                Zone.OUTER, Zone.BEYOND -> {
                    if (!finalActive) {
                        finalActive = true
                        finalEntry = a
                        finalTicks = 0
                        finalHardened = false
                    } else {
                        finalTicks = ticks(finalEntry, a, finalTickDegrees, finalTicks)
                    }
                    if (zone == Zone.BEYOND) finalHardened = true
                }
                Zone.VOWEL -> {
                    finalActive = false
                    vowelTicks = ticks(vowelEntry, a, tickDegrees, vowelTicks)
                }
                Zone.DEEP, Zone.HUB -> {
                    finalActive = false
                    if (vowelSet == 0) {
                        vowelSet = 1
                        vowelEntry = a
                        vowelTicks = 0
                    } else {
                        vowelTicks = ticks(vowelEntry, a, tickDegrees, vowelTicks)
                    }
                }
            }
        }
    }

    /** The reading so far; also the final result once the thumb lifts. */
    fun result(): Result = when (phase) {
        Phase.NONE -> Result.None
        Phase.HUB -> Result.Hub(classifier.classify(hubPath))
        Phase.OUTER -> Result.Item(Ring.OUTER, itemIndex, pushed)
        Phase.INNER_FIXED -> Result.Item(startRing!!, itemIndex, pushed)
        Phase.VOWEL -> Result.Syllable(
            initialIndex = initialIndex,
            initialHardened = initialHardened,
            vowelSet = vowelSet,
            vowelTicks = vowelTicks,
            finalTicks = if (finalActive) finalTicks else null,
            finalHardened = finalActive && finalHardened,
        )
    }

    fun end(): Result {
        val out = result()
        reset()
        return out
    }

    /**
     * Ticks turned clockwise on screen (toward the top end of the arc) since [entry], one tick per [stepDegrees].
     * The count only changes once the thumb is clearly past the halfway point, so it does not flutter there.
     */
    private fun ticks(entry: Float, angle: Float, stepDegrees: Float, previous: Int): Int {
        val exact = Math.toDegrees((entry - angle).toDouble()) / stepDegrees
        return if (abs(exact - previous) < TICK_HYSTERESIS) previous else exact.roundToInt()
    }

    /** The zone at radius [r], except that a ring edge only counts once crossed by more than [hysteresis]. */
    private fun stableZone(r: Float): Zone {
        val zone = layout.zone(r)
        val last = lastZone
        if (last == null || zone == last || hysteresis <= 0f) {
            lastZone = zone
            return zone
        }
        var nearestEdge = edges[0]
        for (edge in edges) if (abs(edge - r) < abs(nearestEdge - r)) nearestEdge = edge
        if (abs(r - nearestEdge) < hysteresis) return last
        lastZone = zone
        return zone
    }

    private companion object {
        /** In ticks: how far past the halfway point the thumb must turn before the count moves. */
        const val TICK_HYSTERESIS = 0.65
    }

    private fun reset() {
        phase = Phase.NONE
        lastZone = null
        angle = 0f
        hubPath.clear()
        startRing = null
        onRing = true
        ringEntry = 0f
        itemIndex = 0
        pushed = false
        initialIndex = null
        initialHardened = false
        vowelSet = 0
        vowelEntry = 0f
        vowelTicks = 0
        finalActive = false
        finalEntry = 0f
        finalTicks = 0
        finalHardened = false
    }
}
