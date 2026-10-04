package com.llgl.vibe.haptics

import com.llgl.vibe.analysis.LiveAnalyzer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Turns frames that arrive one at a time into vibration strength, proportional to how loud the
 * sound is; keeps the pulse, bass, voice and tick state between calls.
 */
class LiveScore {
    var mode: Mode = Mode.FULL
    var intensity: Float = 1f

    private var pulseLeft = 0
    private var pulseLen = 1
    private var pulsePeak = 0f
    private var bassLevel = 0f
    private var voiceLevel = 0f
    private var phase = 0f

    /** Vibration strength 0..255 for this frame under the current mode and intensity. */
    fun amplitude(f: LiveAnalyzer.Frame, hopMs: Float): Int {
        // All generators advance every frame so switching modes never starts from stale state.
        if (f.beat) {
            pulseLen = 2 + (f.onset.coerceIn(0f, 1f) * 4f).roundToInt()
            pulseLeft = pulseLen
            // As strong as the sound is at the beat, a sharper hit a little stronger still.
            pulsePeak = 255f * f.loud.coerceIn(0f, 1f) * (0.6f + 0.4f * f.onset.coerceIn(0f, 1f))
        }
        val rhythm = if (pulseLeft > 0) {
            val k = pulseLen - pulseLeft
            pulseLeft--
            pulsePeak * (1f - 0.55f * k / pulseLen)
        } else {
            0f
        }
        val target = if (f.bass < 0.12f) 0f else f.bass.pow(0.8f)
        bassLevel = if (target >= bassLevel) target else max(target, bassLevel * 0.55f)
        val bass = 255f * bassLevel
        // Voice: instant attack with the rise added on top, so consonants snap and every syllable
        // starts with a distinct edge; release over about three frames so the gaps stay empty.
        val vTarget = if (f.voice < 0.1f) 0f else f.voice.pow(0.7f)
        val rise = max(0f, vTarget - voiceLevel)
        voiceLevel = if (vTarget >= voiceLevel) vTarget else max(vTarget, voiceLevel * 0.6f)
        val voice = 255f * min(1f, voiceLevel + 0.6f * rise)
        val melody: Float
        if (f.pitch <= 0f || f.loud < 0.08f) {
            phase = 0f
            melody = 0f
        } else {
            phase += Score.pulseRate(f.pitch) * hopMs / 1000f
            if (phase >= 1f) {
                phase -= 1f
                melody = 255f * f.loud.pow(0.7f)
            } else {
                melody = 0f
            }
        }
        val raw = when (mode) {
            Mode.RHYTHM -> rhythm
            Mode.BASS -> bass
            Mode.MELODY -> melody
            Mode.VOICE -> voice
            Mode.FULL -> max(rhythm, max(bass * 0.65f, voice * 0.6f))
        }
        return Score.scaled(raw.roundToInt(), intensity)
    }

    /** The waveform for one window of frames, equal neighbours merged; every window has the same length. */
    fun segments(amps: IntArray, hopMs: Long): Segments {
        if (amps.isEmpty()) return Segments(longArrayOf(hopMs), intArrayOf(0))
        val timings = ArrayList<Long>()
        val values = ArrayList<Int>()
        var run = amps[0]
        var len = 0L
        for (a in amps) {
            if (a != run) {
                timings += len
                values += run
                run = a
                len = 0L
            }
            len += hopMs
        }
        timings += len
        values += run
        return Segments(timings.toLongArray(), values.toIntArray())
    }

    /**
     * Frequency-envelope points for one window: frames grouped so each point lasts at least
     * [minPointMs], voiced frames carry the folded pitch and the loudness, the rest are silent.
     */
    fun envelope(frames: List<LiveAnalyzer.Frame>, hopMs: Long, minPointMs: Long, maxPoints: Int, minHz: Float, maxHz: Float): List<FreqPoint> {
        if (frames.isEmpty()) return emptyList()
        val group = ((minPointMs + hopMs - 1) / hopMs).toInt().coerceAtLeast(1)
        val rest = (minHz + maxHz) / 2f
        val out = ArrayList<FreqPoint>()
        var i = 0
        while (i < frames.size && out.size < maxPoints) {
            val end = minOf(frames.size, i + group)
            var loud = 0f
            var hz = 0f
            var voiced = 0
            for (k in i until end) {
                val f = frames[k]
                loud = max(loud, f.loud)
                if (f.pitch > 0f && f.loud >= 0.08f) {
                    hz += f.pitch
                    voiced++
                }
            }
            val freq = if (voiced > 0) Score.foldToRange(hz / voiced, minHz, maxHz) else rest
            val amp = if (voiced == 0) 0f else (loud.pow(0.7f) * intensity).coerceIn(0f, 1f)
            out += FreqPoint(amp, freq, (end - i) * hopMs)
            i = end
        }
        return out
    }
}
