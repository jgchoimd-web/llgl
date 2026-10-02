package com.llgl.app.dial

import com.llgl.app.keyboard.Gesture
import com.llgl.app.keyboard.GestureClassifier
import com.llgl.app.keyboard.Pt
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
 */
class DialRecognizer(
    private val layout: DialLayout,
    private val pulseMode: Boolean,
    private val outerCount: Int,
    private val innerCounts: Map<Ring, Int> = emptyMap(),
    private val tickDegrees: Float = 9f,
    tapRadius: Float,
    longDistance: Float,
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
    private val hubPath = ArrayList<Pt>()
    private var phase = Phase.NONE
    private var startRing: Ring? = null
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

    /** Where the thumb entered the vowel ring it is on (radians), while a syllable is being dialled. */
    val vowelEntryAngle: Float?
        get() = if (phase == Phase.VOWEL) vowelEntry else null

    /** Where the thumb came back out onto the outer ring (radians), while a final is being dialled. */
    val finalEntryAngle: Float?
        get() = if (phase == Phase.VOWEL && finalActive) finalEntry else null

    fun begin(x: Float, y: Float) {
        reset()
        val (r, a) = layout.toPolar(x, y)
        when (val zone = layout.zone(r)) {
            Zone.HUB -> {
                phase = Phase.HUB
                hubPath += Pt(x, y)
            }
            Zone.OUTER, Zone.BEYOND -> {
                phase = Phase.OUTER
                startRing = Ring.OUTER
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
                    itemIndex = layout.index(ring, a, innerCounts.getValue(ring))
                }
            }
        }
    }

    fun move(x: Float, y: Float) {
        val (r, a) = layout.toPolar(x, y)
        val zone = layout.zone(r)
        when (phase) {
            Phase.NONE -> Unit
            Phase.HUB -> hubPath += Pt(x, y)
            Phase.OUTER -> when (zone) {
                Zone.OUTER, Zone.BEYOND -> {
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
                    }
                }
            }
            Phase.INNER_FIXED -> {
                val ring = startRing!!
                if (layout.ringOf(zone) == ring) {
                    itemIndex = layout.index(ring, a, innerCounts.getValue(ring))
                } else {
                    pushed = true
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
                        finalTicks = ticks(finalEntry, a)
                    }
                    if (zone == Zone.BEYOND) finalHardened = true
                }
                Zone.VOWEL -> {
                    finalActive = false
                    vowelTicks = ticks(vowelEntry, a)
                }
                Zone.DEEP, Zone.HUB -> {
                    finalActive = false
                    if (vowelSet == 0) {
                        vowelSet = 1
                        vowelEntry = a
                        vowelTicks = 0
                    } else {
                        vowelTicks = ticks(vowelEntry, a)
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

    /** Ticks turned clockwise on screen (toward the top end of the arc) since [entry]. */
    private fun ticks(entry: Float, angle: Float): Int =
        (Math.toDegrees((entry - angle).toDouble()) / tickDegrees).roundToInt()

    private fun reset() {
        phase = Phase.NONE
        hubPath.clear()
        startRing = null
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
