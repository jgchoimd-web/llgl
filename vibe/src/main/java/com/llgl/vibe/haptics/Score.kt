package com.llgl.vibe.haptics

import kotlin.math.ln
import kotlin.math.roundToInt

/** How sound becomes vibration. */
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

/** The shared pieces of the sound-to-vibration mapping. Pure, so they are unit-tested. */
object Score {
    /** 80 Hz → 3 pulses/s, 800 Hz → 14 pulses/s, log scale between: melody on a motor without frequency control. */
    fun pulseRate(hz: Float): Float {
        val t = ((ln(hz / 80f) / ln(10f))).coerceIn(0f, 1f)
        return 3f + 11f * t
    }

    /** Applies the intensity knob (0..1.5) to a 0..255 strength; anything non-zero stays at least 1. */
    fun scaled(amp: Int, intensity: Float): Int = if (amp <= 0) 0 else (amp * intensity).roundToInt().coerceIn(1, 255)

    /** Brings any pitch into the motor's range by whole octaves. */
    fun foldToRange(hz: Float, minHz: Float, maxHz: Float): Float {
        if (hz <= 0f || minHz <= 0f || maxHz <= minHz) return hz
        var f = hz
        while (f > maxHz) f /= 2f
        while (f < minHz) f *= 2f
        return if (f > maxHz) maxHz else f
    }
}
