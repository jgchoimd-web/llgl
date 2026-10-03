package com.llgl.vibe.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The vibration motor: what it can do, and one-shot effects for the live mode, which hands over a
 * fresh effect every window. On Android 16 with a capable motor a frequency envelope plays real
 * notes; elsewhere every mode is an amplitude waveform, and on very old phones an on/off pattern.
 * Safe to call from any thread.
 */
class VibeEngine(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    val available: Boolean = vibrator?.hasVibrator() == true
    val amplitudeControl: Boolean = available && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasAmplitudeControl() == true

    val envelopes: Boolean
    val minHz: Float
    val maxHz: Float
    val resonantHz: Float
    val envMaxSize: Int
    val envMinPointMs: Long
    val envMaxPointMs: Long
    val envMaxDurationMs: Long

    init {
        var env = false
        var lo = 0f
        var hi = 0f
        var res = 0f
        var maxSize = 0
        var minPoint = 20L
        var maxPoint = 1000L
        var maxDuration = 15_000L
        if (available && Build.VERSION.SDK_INT >= 36) {
            try {
                val v = vibrator!!
                env = v.areEnvelopeEffectsSupported()
                if (env) {
                    val profile = v.frequencyProfile
                    val info = v.envelopeEffectInfo
                    if (profile != null && info != null && info.maxSize > 1) {
                        lo = profile.minFrequencyHz
                        hi = profile.maxFrequencyHz
                        maxSize = info.maxSize
                        minPoint = info.minControlPointDurationMillis.coerceAtLeast(1L)
                        maxPoint = info.maxControlPointDurationMillis.coerceAtLeast(minPoint)
                        maxDuration = info.maxDurationMillis.coerceAtLeast(minPoint)
                    } else {
                        env = false
                    }
                }
                res = v.resonantFrequency
            } catch (_: Throwable) {
                env = false
            }
        }
        envelopes = env && hi > lo && lo > 0f
        minHz = lo
        maxHz = hi
        resonantHz = if (res.isNaN()) 0f else res
        envMaxSize = maxSize
        envMinPointMs = minPoint
        envMaxPointMs = maxPoint
        envMaxDurationMs = maxDuration
    }

    /** One line for the screen. */
    val description: String = when {
        !available -> "이 폰에는 진동 모터가 없어요"
        envelopes -> "진폭 제어 ✓ · 주파수 제어 ✓ (${minHz.toInt()}–${maxHz.toInt()} Hz)"
        amplitudeControl -> "진폭 제어 ✓ · 주파수 제어 ✗ (Android 16과 지원 모터 필요)"
        else -> "켜기/끄기만 가능한 모터 (세기 조절 없음)"
    }

    /** Cancels whatever is playing. */
    fun stop() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    /** A short double buzz so the user can feel [intensity]. */
    fun preview(intensity: Float) {
        if (!available) return
        val amp = Score.scaled(200, intensity)
        vibrate(waveform(longArrayOf(60L, 60L, 90L), intArrayOf(amp, 0, amp)))
    }

    /** Plays [segments] right now, once; the live mode calls this every window. */
    fun playSegments(segments: Segments) {
        if (!available) return
        if (segments.isSilent) {
            stop()
            return
        }
        vibrate(waveform(segments.timings, segments.amplitudes))
    }

    /** Plays a frequency envelope right now; false when the device cannot, so the caller falls back. */
    fun playEnvelope(points: List<FreqPoint>): Boolean {
        if (!envelopes || Build.VERSION.SDK_INT < 36 || points.isEmpty()) return false
        if (points.all { it.amplitude <= 0f }) {
            stop()
            return true
        }
        return try {
            val b = VibrationEffect.WaveformEnvelopeBuilder()
            b.setInitialFrequencyHz(points.first().frequencyHz)
            for (p in points) b.addControlPoint(p.amplitude, p.frequencyHz, p.durationMs.coerceIn(envMinPointMs, envMaxPointMs))
            vibrate(b.build())
            true
        } catch (_: Exception) {
            false
        }
    }

    /** A one-shot waveform effect, or on very old phones the on/off pattern that stands in for it. */
    private fun waveform(timings: LongArray, amplitudes: IntArray): Any {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amps = if (amplitudeControl) amplitudes else IntArray(amplitudes.size) { if (amplitudes[it] >= 90) 255 else 0 }
            return VibrationEffect.createWaveform(timings, amps, -1)
        }
        // Legacy: off/on pairs. Build [off, on, off, on, ...] from the steps.
        val pattern = ArrayList<Long>()
        var on = false
        var acc = 0L
        pattern += 0L
        for (i in timings.indices) {
            val isOn = amplitudes[i] >= 90
            if (isOn == on) {
                acc += timings[i]
            } else {
                pattern += acc
                acc = timings[i]
                on = isOn
            }
        }
        pattern += acc
        return LegacyPattern(pattern.toLongArray())
    }

    private class LegacyPattern(val pattern: LongArray)

    private fun vibrate(effect: Any) {
        val v = vibrator ?: return
        try {
            when {
                effect is LegacyPattern -> @Suppress("DEPRECATION") v.vibrate(effect.pattern, -1)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    v.vibrate(effect as VibrationEffect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> v.vibrate(effect as VibrationEffect)
            }
        } catch (_: Exception) {
        }
    }
}
