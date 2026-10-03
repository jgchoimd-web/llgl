package com.llgl.vibe.analysis

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns mono audio into a [HapticTrack]: per-frame loudness, bass, a three-band onset strength
 * with beat picking and a tempo estimate, and a pitch track from an 8 kHz copy.
 */
object Analyzer {
    fun analyze(samples: FloatArray, sampleRate: Int, hopMs: Float = 20f, progress: ((Float) -> Unit)? = null): HapticTrack {
        val hop = (sampleRate * hopMs / 1000f).roundToInt().coerceAtLeast(1)
        val frames = max(1, samples.size / hop)

        // --- band energies, streamed through the filters once ---
        val low1 = Biquad.lowPass(sampleRate, 150f)
        val low2 = Biquad.lowPass(sampleRate, 150f)
        val mid = Biquad.bandPass(sampleRate, 700f, 0.6f)
        val high = Biquad.highPass(sampleRate, 2500f)
        val eAll = FloatArray(frames)
        val eLow = FloatArray(frames)
        val eMid = FloatArray(frames)
        val eHigh = FloatArray(frames)
        for (f in 0 until frames) {
            var sa = 0f
            var sl = 0f
            var sm = 0f
            var sh = 0f
            val base = f * hop
            for (i in 0 until hop) {
                val x = samples[base + i]
                val l = low2.process(low1.process(x))
                val m = mid.process(x)
                val h = high.process(x)
                sa += x * x
                sl += l * l
                sm += m * m
                sh += h * h
            }
            eAll[f] = sa / hop
            eLow[f] = sl / hop
            eMid[f] = sm / hop
            eHigh[f] = sh / hop
            if (f % 500 == 0) progress?.invoke(0.6f * f / frames)
        }

        val loud = normalise(FloatArray(frames) { sqrt(eAll[it]) })
        val bass = normalise(FloatArray(frames) { sqrt(eLow[it]) })

        // --- onsets: positive change of log energy, weighted toward the bass ---
        val flux = FloatArray(frames)
        val bands = arrayOf(eLow, eMid, eHigh)
        val weights = floatArrayOf(1.0f, 0.8f, 0.6f)
        for ((b, e) in bands.withIndex()) {
            val ref = Dsp.percentile(e, 0.98f).coerceAtLeast(1e-9f)
            var prev = 0f
            for (f in 0 until frames) {
                val v = ln(1f + 50f * e[f] / ref)
                if (f > 0) flux[f] += weights[b] * max(0f, v - prev)
                prev = v
            }
        }
        val smooth = FloatArray(frames) { f ->
            var s = 0f
            var n = 0
            for (k in -1..1) {
                val j = f + k
                if (j in 0 until frames) {
                    s += flux[j]
                    n++
                }
            }
            s / n
        }
        val onset = normalise(smooth, 0.99f)
        val beats = pickBeats(onset, hopMs)
        val bpm = tempo(onset, hopMs)
        progress?.invoke(0.7f)

        // --- pitch on an 8 kHz copy, one estimate per frame ---
        val factor = max(1, (sampleRate / 8000f).roundToInt())
        val low = Dsp.decimate(samples, sampleRate, factor)
        val lowRate = sampleRate / factor
        val window = 512
        val pitch = FloatArray(frames)
        var last = 0f
        for (f in 0 until frames) {
            val centre = (f * hopMs / 1000f * lowRate).toInt()
            val start = centre - window / 2
            val hz = if (start >= 0 && start + window <= low.size) Pitch.estimate(low, start, window, lowRate) else 0f
            // Drop isolated octave jumps: keep a value only if it is within a semitone of its neighbour, else wait a frame.
            pitch[f] = if (hz > 0f && last > 0f && (hz / last > 2.06f || last / hz > 2.06f)) last else hz
            if (hz > 0f) last = hz else last = 0f
            if (f % 500 == 0) progress?.invoke(0.7f + 0.3f * f / frames)
        }
        progress?.invoke(1f)
        return HapticTrack(hopMs, loud, bass, onset, pitch, beats, bpm)
    }

    private fun normalise(values: FloatArray, fraction: Float = 0.98f): FloatArray {
        val ref = Dsp.percentile(values, fraction)
        if (ref <= 1e-9f) return FloatArray(values.size)
        return FloatArray(values.size) { (values[it] / ref).coerceIn(0f, 1f) }
    }

    /** Local maxima of the onset curve that stand clearly above their neighbourhood, at least 120 ms apart. */
    fun pickBeats(onset: FloatArray, hopMs: Float): IntArray {
        val frames = onset.size
        val minGap = (120f / hopMs).roundToInt().coerceAtLeast(1)
        val radius = (160f / hopMs).roundToInt().coerceAtLeast(1)
        val out = ArrayList<Int>()
        var lastBeat = -minGap
        for (f in 1 until frames - 1) {
            val v = onset[f]
            if (v < 0.08f || v < onset[f - 1] || v <= onset[f + 1]) continue
            var sum = 0f
            var n = 0
            for (k in -radius..radius) {
                val j = f + k
                if (j in 0 until frames) {
                    sum += onset[j]
                    n++
                }
            }
            val mean = sum / n
            if (v < mean * 1.4f + 0.04f) continue
            if (f - lastBeat < minGap) continue
            out += f
            lastBeat = f
        }
        return out.toIntArray()
    }

    /** Tempo from the autocorrelation of the onset curve between 55 and 200 BPM. */
    fun tempo(onset: FloatArray, hopMs: Float): Float {
        val frames = onset.size
        if (frames < 50) return 0f
        var mean = 0f
        for (v in onset) mean += v
        mean /= frames
        val lagMin = (60000f / (200f * hopMs)).roundToInt().coerceAtLeast(1)
        val lagMax = (60000f / (55f * hopMs)).roundToInt().coerceAtMost(frames / 2)
        if (lagMax <= lagMin) return 0f
        var bestLag = lagMin
        var best = Float.NEGATIVE_INFINITY
        for (lag in lagMin..lagMax) {
            var r = 0f
            for (f in 0 until frames - lag) r += (onset[f] - mean) * (onset[f + lag] - mean)
            r /= (frames - lag)
            if (r > best) {
                best = r
                bestLag = lag
            }
        }
        if (best <= 0f) return 0f
        return 60000f / (bestLag * hopMs)
    }
}
