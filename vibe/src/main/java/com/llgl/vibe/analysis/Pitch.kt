package com.llgl.vibe.analysis

/** A cut-down YIN: difference function, cumulative mean normalisation, first dip under a threshold. */
object Pitch {
    /**
     * Fundamental of [buf] from [start] over [n] samples at [sampleRate], within [fMin, fMax] Hz;
     * 0 when nothing periodic enough is there.
     */
    fun estimate(buf: FloatArray, start: Int, n: Int, sampleRate: Int, fMin: Float = 60f, fMax: Float = 900f, threshold: Float = 0.15f): Float {
        if (start < 0 || start + n > buf.size) return 0f
        val tauMin = (sampleRate / fMax).toInt().coerceAtLeast(2)
        val tauMax = (sampleRate / fMin).toInt().coerceAtMost(n / 2)
        if (tauMax <= tauMin) return 0f
        val window = n - tauMax
        var energy = 0f
        for (j in 0 until window) energy += buf[start + j] * buf[start + j]
        if (energy / window < 1e-6f) return 0f

        val d = FloatArray(tauMax + 1)
        for (tau in 1..tauMax) {
            var s = 0f
            val base = start
            for (j in 0 until window) {
                val diff = buf[base + j] - buf[base + j + tau]
                s += diff * diff
            }
            d[tau] = s
        }
        val cm = FloatArray(tauMax + 1)
        var running = 0f
        cm[0] = 1f
        for (tau in 1..tauMax) {
            running += d[tau]
            cm[tau] = if (running > 0f) d[tau] * tau / running else 1f
        }
        var tau = -1
        var t = tauMin
        while (t <= tauMax) {
            if (cm[t] < threshold) {
                while (t + 1 <= tauMax && cm[t + 1] < cm[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau < 0) {
            var best = tauMin
            for (k in tauMin..tauMax) if (cm[k] < cm[best]) best = k
            if (cm[best] > 0.4f) return 0f
            tau = best
        }
        // Parabolic refinement on the raw difference function.
        var refined = tau.toFloat()
        if (tau in 2 until tauMax) {
            val s0 = d[tau - 1]
            val s1 = d[tau]
            val s2 = d[tau + 1]
            val denom = 2f * (2f * s1 - s0 - s2)
            if (denom != 0f) refined += (s0 - s2) / denom * -1f
        }
        return sampleRate / refined
    }
}
