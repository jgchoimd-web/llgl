package com.llgl.vibe.haptics

import com.llgl.vibe.analysis.HapticTrack
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Mode(val label: String, val blurb: String) {
    RHYTHM("리듬", "비트마다 한 번씩 툭"),
    BASS("베이스", "저음 크기를 따라 웅웅"),
    MELODY("멜로디", "음높이를 따라 (주파수 제어 폰은 실제 음으로)"),
    FULL("전부", "비트 + 베이스"),
}

/** Vibration on/strength steps, as `Vibrator.vibrate(createWaveform)` wants them. */
class Segments(val timings: LongArray, val amplitudes: IntArray) {
    val totalMs: Long get() = timings.sum()
    val isSilent: Boolean get() = amplitudes.all { it == 0 }
}

/** One control point of a frequency envelope (Android 16+). */
data class FreqPoint(val amplitude: Float, val frequencyHz: Float, val durationMs: Long)

/** Turns a [HapticTrack] into vibration. Pure, so the mapping is unit-tested. */
object Score {
    /** Vibration strength 0..255 per frame for [mode], before the intensity knob. */
    fun amplitudes(track: HapticTrack, mode: Mode): IntArray = when (mode) {
        Mode.RHYTHM -> rhythm(track)
        Mode.BASS -> bass(track)
        Mode.MELODY -> melodyPulses(track)
        Mode.FULL -> {
            val r = rhythm(track)
            val b = bass(track)
            IntArray(track.frames) { max(r[it], (b[it] * 0.65f).roundToInt()) }
        }
    }

    private fun rhythm(track: HapticTrack): IntArray {
        val out = IntArray(track.frames)
        for (b in track.beats) {
            val s = track.onset[b].coerceIn(0f, 1f)
            val length = 2 + (s * 4f).roundToInt() // 40..120 ms at 20 ms hops
            val peak = 120f + 135f * s
            for (k in 0 until length) {
                val f = b + k
                if (f >= track.frames) break
                val a = (peak * (1f - 0.55f * k / length)).roundToInt()
                if (a > out[f]) out[f] = a
            }
        }
        return out
    }

    private fun bass(track: HapticTrack): IntArray {
        val out = IntArray(track.frames)
        var level = 0f
        for (f in 0 until track.frames) {
            val target = if (track.bass[f] < 0.12f) 0f else track.bass[f].pow(0.8f)
            // Fast attack, held while the bass holds, a quick release toward it afterwards.
            level = if (target >= level) target else max(target, level * 0.55f)
            out[f] = (255f * level).roundToInt().coerceIn(0, 255)
        }
        return out
    }

    /**
     * Melody without frequency control: a pulse train whose rate follows the pitch (higher note,
     * faster ticks) and whose strength follows loudness. Reads as "melody" on any phone.
     */
    fun melodyPulses(track: HapticTrack): IntArray {
        val out = IntArray(track.frames)
        var phase = 0f
        for (f in 0 until track.frames) {
            val hz = track.pitch[f]
            if (hz <= 0f || track.loud[f] < 0.08f) {
                phase = 0f
                continue
            }
            val rate = pulseRate(hz)
            phase += rate * track.hopMs / 1000f
            if (phase >= 1f) {
                phase -= 1f
                out[f] = (90f + 165f * track.loud[f].pow(0.7f)).roundToInt()
                if (f + 1 < track.frames) out[f + 1] = max(out[f + 1], (out[f] * 0.5f).roundToInt())
            }
        }
        return out
    }

    /** 80 Hz → 3 pulses/s, 800 Hz → 14 pulses/s, log scale between. */
    fun pulseRate(hz: Float): Float {
        val t = ((ln(hz / 80f) / ln(10f))).coerceIn(0f, 1f)
        return 3f + 11f * t
    }

    /**
     * The waveform for [fromMs, toMs): equal neighbouring frames merged into one step, strength
     * scaled by [intensity] (0..1.5, clamped to 255). The first step starts exactly at [fromMs].
     */
    fun segments(amps: IntArray, hopMs: Float, fromMs: Long, toMs: Long, intensity: Float): Segments {
        val timings = ArrayList<Long>()
        val values = ArrayList<Int>()
        val start = max(0, (fromMs / hopMs).toInt())
        val end = min(amps.size, ((toMs + hopMs - 1) / hopMs).toInt())
        if (end <= start) return Segments(longArrayOf(max(1L, toMs - fromMs)), intArrayOf(0))
        var runStart = start
        var runValue = scaled(amps[start], intensity)
        for (f in start + 1..end) {
            val v = if (f < end) scaled(amps[f], intensity) else -1
            if (v != runValue) {
                val t0 = max(fromMs, (runStart * hopMs).toLong())
                val t1 = min(toMs, (f * hopMs).toLong())
                if (t1 > t0) {
                    timings += t1 - t0
                    values += runValue
                }
                runStart = f
                runValue = v
            }
        }
        if (timings.isEmpty()) return Segments(longArrayOf(max(1L, toMs - fromMs)), intArrayOf(0))
        return Segments(timings.toLongArray(), values.toIntArray())
    }

    fun scaled(amp: Int, intensity: Float): Int = if (amp <= 0) 0 else (amp * intensity).roundToInt().coerceIn(1, 255)

    /** Brings any pitch into the motor's range by whole octaves. */
    fun foldToRange(hz: Float, minHz: Float, maxHz: Float): Float {
        if (hz <= 0f || minHz <= 0f || maxHz <= minHz) return hz
        var f = hz
        while (f > maxHz) f /= 2f
        while (f < minHz) f *= 2f
        return if (f > maxHz) maxHz else f
    }

    /**
     * Frequency-envelope points for [fromMs, toMs): pitch folded into [minHz, maxHz], strength from
     * loudness (times [intensity]), merged while the note holds, and coarsened until there are at
     * most [maxPoints] points each between [minPointMs] and [maxPointMs] long. Silence is a point
     * at amplitude 0, so timing is kept.
     */
    fun envelope(
        track: HapticTrack,
        fromMs: Long,
        toMs: Long,
        intensity: Float,
        minHz: Float,
        maxHz: Float,
        minPointMs: Long,
        maxPointMs: Long,
        maxPoints: Int,
    ): List<FreqPoint> {
        val start = max(0, (fromMs / track.hopMs).toInt())
        val end = min(track.frames, ((toMs + track.hopMs - 1) / track.hopMs).toInt())
        if (end <= start) return emptyList()
        val restHz = ((minHz + maxHz) / 2f)
        var step = 1
        while (true) {
            val points = ArrayList<FreqPoint>()
            var f = start
            while (f < end) {
                val f2 = min(end, f + step)
                var loud = 0f
                var hz = 0f
                var voiced = 0
                for (k in f until f2) {
                    loud = max(loud, track.loud[k])
                    if (track.pitch[k] > 0f) {
                        hz += track.pitch[k]
                        voiced++
                    }
                }
                val freq = if (voiced > 0) foldToRange(hz / voiced, minHz, maxHz) else restHz
                val amp = if (voiced == 0 || loud < 0.08f) 0f else (loud.pow(0.7f) * intensity).coerceIn(0f, 1f)
                val dur = ((f2 - f) * track.hopMs).toLong().coerceAtLeast(1L)
                val last = points.lastOrNull()
                if (last != null && last.amplitude == amp && sameNote(last.frequencyHz, freq) && last.durationMs + dur <= maxPointMs) {
                    points[points.size - 1] = last.copy(durationMs = last.durationMs + dur)
                } else {
                    points += FreqPoint(amp, freq, dur)
                }
                f = f2
            }
            val fixed = points.map { it.copy(durationMs = it.durationMs.coerceIn(minPointMs, maxPointMs)) }
            if (fixed.size <= maxPoints || step * track.hopMs >= maxPointMs) return fixed.take(maxPoints)
            step++
        }
    }

    private fun sameNote(a: Float, b: Float): Boolean = if (a <= 0f || b <= 0f) a == b else (a / b) in 0.97f..1.03f
}
