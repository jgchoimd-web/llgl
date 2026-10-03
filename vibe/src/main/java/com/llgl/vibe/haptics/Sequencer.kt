package com.llgl.vibe.haptics

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A 16-step, three-lane vibration pattern: 쿵 (heavy, long), 탁 (medium), 틱 (light, short).
 * Rendered to a waveform that loops bar after bar.
 */
class Pattern(val steps: Int = 16) {
    val lanes = 3
    val grid = Array(lanes) { BooleanArray(steps) }
    var bpm = 100

    fun toggle(lane: Int, step: Int) {
        grid[lane][step] = !grid[lane][step]
    }

    fun clear() {
        for (l in grid) l.fill(false)
    }

    val isEmpty: Boolean get() = grid.all { lane -> lane.none { it } }

    /** Milliseconds per 16th-note step. */
    val stepMs: Long get() = max(40L, (60000f / bpm / 4f).roundToInt().toLong())

    fun save(): String = "$bpm|" + grid.joinToString("|") { lane -> lane.joinToString("") { if (it) "1" else "0" } }

    companion object {
        val LANE_NAMES = listOf("쿵", "탁", "틱")

        /** Strength and length of a hit per lane. */
        val LANE_AMP = intArrayOf(255, 170, 110)
        val LANE_MS = longArrayOf(70L, 45L, 25L)

        fun load(s: String, steps: Int = 16): Pattern? {
            val parts = s.split('|')
            if (parts.size != 4) return null
            val p = Pattern(steps)
            p.bpm = parts[0].toIntOrNull()?.coerceIn(40, 240) ?: return null
            for (l in 0 until 3) {
                val bits = parts[l + 1]
                if (bits.length != steps) return null
                for (i in 0 until steps) p.grid[l][i] = bits[i] == '1'
            }
            return p
        }
    }
}

object Sequencer {
    private const val RESOLUTION_MS = 5L

    /** One bar of the pattern as a waveform, at [intensity]; empty steps are explicit silence so the loop keeps time. */
    fun render(pattern: Pattern, intensity: Float): Segments {
        val stepMs = pattern.stepMs
        val totalMs = stepMs * pattern.steps
        val slots = (totalMs / RESOLUTION_MS).toInt()
        val level = IntArray(slots)
        for (lane in 0 until pattern.lanes) {
            for (step in 0 until pattern.steps) {
                if (!pattern.grid[lane][step]) continue
                val at = (step * stepMs / RESOLUTION_MS).toInt()
                val len = (Pattern.LANE_MS[lane] / RESOLUTION_MS).toInt().coerceAtLeast(1)
                val amp = Pattern.LANE_AMP[lane]
                for (k in 0 until len) {
                    val i = at + k
                    if (i >= slots) break
                    val a = (amp * (1f - 0.5f * k / len)).roundToInt()
                    if (a > level[i]) level[i] = a
                }
            }
        }
        val timings = ArrayList<Long>()
        val amps = ArrayList<Int>()
        var runStart = 0
        var runValue = Score.scaled(level[0], intensity)
        for (i in 1..slots) {
            val v = if (i < slots) Score.scaled(level[i], intensity) else -1
            if (v != runValue) {
                timings += (i - runStart) * RESOLUTION_MS
                amps += runValue
                runStart = i
                runValue = v
            }
        }
        return Segments(timings.toLongArray(), amps.toIntArray())
    }
}
