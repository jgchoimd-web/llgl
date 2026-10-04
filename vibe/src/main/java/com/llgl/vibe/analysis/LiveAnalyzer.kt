package com.llgl.vibe.analysis

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The streaming analyser: one block at a time, no knowledge of the future.
 *
 * Each band's energy is measured above a noise floor that hugs the band's recent low points and
 * climbs slowly, so a sound that stays steady for more than a second (a hum, hiss, a fan, rumble,
 * a held drone) sinks into the floor and stops counting, while anything that comes and goes
 * (speech syllables, hits, notes) stays in the foreground. Levels are absolute, on a perceptual
 * curve of the real sound level, so the motor follows how loud the sound is instead of adapting
 * to it. A beat is confirmed one block late (the next block must not be higher), so each call
 * returns the frame for the block before it.
 */
class LiveAnalyzer(val sampleRate: Int, val blockMs: Int = 20) {
    /**
     * Foreground levels 0..1 of full scale (see [level]): [loud] of the whole signal (or of the
     * speech band, whichever is higher), [bass] of the band below 150 Hz, [voice] of the
     * 300–3000 Hz band where speech lives. [onset] is how much louder this block got relative to
     * recent onsets, [beat] a confirmed onset peak, [pitch] in Hz or 0 when nothing tonal is there.
     */
    data class Frame(val loud: Float, val bass: Float, val onset: Float, val beat: Boolean, val pitch: Float, val voice: Float = 0f)

    val blockSize: Int = (sampleRate * blockMs / 1000).coerceAtLeast(1)

    /** Whether steady sounds are subtracted before anything else is measured. */
    @Volatile
    var suppressBackground: Boolean = true

    /** Raw RMS of the last block, before the floor or any normalisation: tells silence from a quiet passage. */
    var lastRms: Float = 0f
        private set

    private val low1 = Biquad.lowPass(sampleRate, 150f)
    private val low2 = Biquad.lowPass(sampleRate, 150f)
    private val voiceHp = Biquad.highPass(sampleRate, 300f)
    private val voiceLp = Biquad.lowPass(sampleRate, 3000f)
    private val high = Biquad.highPass(sampleRate, 2500f)

    private val lowHistory = FloatArray(LOW_BLOCKS)
    private var lowPos = 0
    private val raw = FloatArray(4)
    private val floor = FloatArray(4)
    private val margins = floatArrayOf(2f, 2.5f, 2f, 2f)
    private val e = FloatArray(4)
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
            val m = voiceLp.process(voiceHp.process(x))
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
        lastRms = sqrt(sa / blockSize)
        // The band below 150 Hz holds only a few cycles per block, so its energy is averaged over
        // five blocks (100 ms) before anything judges it; the others are steady enough as they are.
        lowHistory[lowPos] = sl / blockSize
        lowPos = (lowPos + 1) % lowHistory.size
        var lowSum = 0f
        for (v in lowHistory) lowSum += v
        raw[0] = sa / blockSize
        raw[1] = lowSum / lowHistory.size
        raw[2] = sm / blockSize
        raw[3] = sh / blockSize

        for (b in 0 until 4) {
            // The floor never sits above the current level and climbs ~3 % a block (plus a sliver
            // of the level, so it catches up from silence in about a second and a half). Speech
            // pauses and the gaps between hits pull it straight back down.
            floor[b] = max(FLOOR_MIN, if (block == 0) raw[b] else min(raw[b], floor[b] * FLOOR_RISE + FLOOR_LEAK * raw[b]))
            e[b] = if (suppressBackground) max(0f, raw[b] - margins[b] * floor[b]) else raw[b]
            // The recent raw maximum only scales the onset flux below; levels are absolute.
            refs[b] = max(raw[b], max(FLOOR, refs[b] * DECAY))
        }
        val lvlAll = level(e[0])
        val bass = level(e[1])
        val voice = level(e[2])
        val loud = max(lvlAll, voice)

        var flux = 0f
        for (b in 0 until 3) {
            // Two-block energy average: a hit outlasts a block, jitter does not.
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
        pending = Frame(loud, bass, onset, false, pitch, voice)
        onset2 = onset1
        onset1 = onset
        block++
        return done
    }

    /**
     * Vibration-side level 0..1 for a foreground energy (mean square of full-scale samples): the
     * sound amplitude on a 0.6 power, roughly how loudness is perceived, so twice the amplitude
     * feels about half again as strong. Full at RMS 0.35 (-9 dBFS, a loud mix), nothing below
     * RMS 0.003 (-50 dBFS), where the motor could not be felt anyway.
     */
    private fun level(energy: Float): Float {
        if (energy <= 0f) return 0f
        val l = (sqrt(energy) / FULL_RMS).pow(0.6f)
        return if (l < LEVEL_GATE) 0f else min(1f, l)
    }

    private companion object {
        const val FULL_RMS = 0.35f
        const val LEVEL_GATE = 0.06f
        /** Energy floor for the onset reference: RMS 0.01, about -40 dBFS. */
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
        const val LOW_BLOCKS = 5
        const val FLOOR_MIN = 1e-7f
        const val FLOOR_RISE = 1.03f
        const val FLOOR_LEAK = 0.002f
    }
}
