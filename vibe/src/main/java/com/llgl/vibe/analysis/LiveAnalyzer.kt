package com.llgl.vibe.analysis

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The streaming twin of [Analyzer]: one block at a time, no knowledge of the future. Levels are
 * normalised against a slowly decaying maximum instead of a percentile, and a beat is confirmed
 * one block late (the next block must not be higher), so each call returns the frame for the
 * block before it.
 */
class LiveAnalyzer(val sampleRate: Int, val blockMs: Int = 20) {
    data class Frame(val loud: Float, val bass: Float, val onset: Float, val beat: Boolean, val pitch: Float)

    val blockSize: Int = (sampleRate * blockMs / 1000).coerceAtLeast(1)

    /** Raw RMS of the last block, before any normalisation: tells silence from a quiet passage. */
    var lastRms: Float = 0f
        private set

    private val low1 = Biquad.lowPass(sampleRate, 150f)
    private val low2 = Biquad.lowPass(sampleRate, 150f)
    private val mid = Biquad.bandPass(sampleRate, 700f, 0.6f)
    private val high = Biquad.highPass(sampleRate, 2500f)
    private val refs = FloatArray(4) { FLOOR }
    private val prevLog = FloatArray(3)
    private val ePrev = FloatArray(3)
    private val weights = floatArrayOf(1.0f, 0.8f, 0.6f)
    private var refFlux = FLUX_FLOOR
    private val history = FloatArray(16)
    private var historyPos = 0
    private var onset1 = 0f
    private var onset2 = 0f
    private var pending: Frame? = null
    private var block = 0
    private var lastBeat = -100

    private val decim = max(1, sampleRate / 8000)
    private val pitchRate = sampleRate / decim
    private val pitchLow = Biquad.lowPass(sampleRate, pitchRate / 2f * 0.8f)
    private val ring = FloatArray(1024)
    private var ringPos = 0
    private var ringFilled = 0
    private var decimAcc = 0f
    private var decimK = 0
    private val window = FloatArray(512)

    /** Feeds one block of [blockSize] samples; returns the finished frame for the previous block, or null on the first call. */
    fun feed(samples: FloatArray, offset: Int = 0): Frame? {
        var sa = 0f
        var sl = 0f
        var sm = 0f
        var sh = 0f
        for (i in 0 until blockSize) {
            val x = samples[offset + i]
            val l = low2.process(low1.process(x))
            val m = mid.process(x)
            val h = high.process(x)
            sa += x * x
            sl += l * l
            sm += m * m
            sh += h * h
            decimAcc += pitchLow.process(x)
            if (++decimK == decim) {
                ring[ringPos] = decimAcc / decim
                ringPos = (ringPos + 1) % ring.size
                ringFilled = min(ring.size, ringFilled + 1)
                decimAcc = 0f
                decimK = 0
            }
        }
        val e = floatArrayOf(sa / blockSize, sl / blockSize, sm / blockSize, sh / blockSize)
        lastRms = sqrt(e[0])
        for (b in 0 until 4) refs[b] = max(e[b], max(FLOOR, refs[b] * DECAY))
        val loud = sqrt(e[0] / refs[0]).coerceIn(0f, 1f)
        val bass = sqrt(e[1] / refs[1]).coerceIn(0f, 1f)

        var flux = 0f
        for (b in 0 until 3) {
            // Two-block energy average: the bass band holds only a few cycles per block, so its
            // single-block estimate jitters by half even on a steady sound; a hit outlasts a block.
            val es = 0.5f * (e[b + 1] + ePrev[b])
            ePrev[b] = e[b + 1]
            val v = ln(1f + 50f * es / refs[b + 1])
            if (block > 0) flux += weights[b] * max(0f, v - prevLog[b])
            prevLog[b] = v
        }
        refFlux = max(flux, max(FLUX_FLOOR, refFlux * DECAY_FLUX))
        val onset = (flux / refFlux).coerceIn(0f, 1f)

        // Beat for block n-1: a local peak against its neighbours and the recent mean.
        var beat = false
        if (block >= 2) {
            var mean = 0f
            for (v in history) mean += v
            mean /= history.size
            if (onset1 >= 0.08f && onset1 > onset2 && onset1 >= onset && onset1 > mean * 1.4f + 0.04f && (block - 1) - lastBeat >= MIN_GAP_BLOCKS) {
                beat = true
                lastBeat = block - 1
            }
        }
        history[historyPos] = onset
        historyPos = (historyPos + 1) % history.size

        var pitch = 0f
        if (ringFilled >= window.size) {
            var p = (ringPos - window.size + ring.size) % ring.size
            for (i in window.indices) {
                window[i] = ring[p]
                p = (p + 1) % ring.size
            }
            pitch = Pitch.estimate(window, 0, window.size, pitchRate)
        }

        val done = pending?.copy(beat = beat)
        pending = Frame(loud, bass, onset, false, pitch)
        onset2 = onset1
        onset1 = onset
        block++
        return done
    }

    private companion object {
        /** Energy below which nothing is "loud": RMS 0.01, about -40 dBFS. */
        const val FLOOR = 1e-4f
        const val DECAY = 0.9954f // halves in ~3 s of 20 ms blocks
        const val DECAY_FLUX = 0.993f // halves in ~2 s
        /**
         * The smallest flux that counts as a full-strength onset, so a beat needs a flux of at
         * least 0.08 times this. A real hit raises the log energy by 0.5..4 per band; the
         * block-to-block jitter of steady noise sitting well below the band's recent maximum
         * reaches ~0.15 and must stay under that threshold.
         */
        const val FLUX_FLOOR = 3f
        const val MIN_GAP_BLOCKS = 6
    }
}
