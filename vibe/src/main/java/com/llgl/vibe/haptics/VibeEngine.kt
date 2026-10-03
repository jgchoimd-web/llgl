package com.llgl.vibe.haptics

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.llgl.vibe.analysis.HapticTrack
import kotlin.math.min

/**
 * Drives the vibration motor from a [HapticTrack] in step with a clock the caller provides
 * (the audio position). Issues the pattern in 15-second chunks and re-reads the clock at every
 * chunk, so audio and motor cannot drift apart. On Android 16 with a capable motor, MELODY mode
 * sets the motor's frequency note by note; elsewhere every mode is an amplitude pattern.
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

    var intensity: Float = 1f
    var mode: Mode = Mode.FULL

    private val handler = Handler(Looper.getMainLooper())
    private var track: HapticTrack? = null
    private var clock: (() -> Int)? = null
    private var ampsFor: Mode? = null
    private var amps: IntArray? = null
    private val next = Runnable { issue() }

    val running: Boolean get() = track != null

    /** Starts following [clock] (ms into [track]); call again after a seek, or [resync]. */
    fun play(track: HapticTrack, clock: () -> Int) {
        this.track = track
        this.clock = clock
        amps = null
        issue()
    }

    fun resync() {
        if (track != null) issue()
    }

    fun stop() {
        handler.removeCallbacks(next)
        track = null
        clock = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    /** A short buzz so the user can feel the current intensity. */
    fun preview() {
        if (!available) return
        val amp = Score.scaled(200, intensity)
        vibrate(waveform(longArrayOf(60L, 60L, 90L), intArrayOf(amp, 0, amp), -1))
    }

    /** Plays [segments] right now, once; the live mode calls this every window. Safe from any thread. */
    fun playSegments(segments: Segments) {
        if (!available) return
        if (segments.isSilent) {
            try {
                vibrator?.cancel()
            } catch (_: Exception) {
            }
            return
        }
        vibrate(waveform(segments.timings, segments.amplitudes, -1))
    }

    /** Plays a frequency envelope right now; false when the device cannot, so the caller falls back. */
    fun playEnvelope(points: List<FreqPoint>): Boolean {
        if (!envelopes || Build.VERSION.SDK_INT < 36 || points.isEmpty()) return false
        if (points.all { it.amplitude <= 0f }) {
            try {
                vibrator?.cancel()
            } catch (_: Exception) {
            }
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

    /** Loops [segments] until [stop]. */
    fun loop(segments: Segments) {
        handler.removeCallbacks(next)
        track = null
        if (!available || segments.isSilent) {
            try {
                vibrator?.cancel()
            } catch (_: Exception) {
            }
            return
        }
        vibrate(waveform(segments.timings, segments.amplitudes, 0))
    }

    private fun issue() {
        handler.removeCallbacks(next)
        val t = track ?: return
        val from = (clock?.invoke() ?: return).toLong().coerceAtLeast(0L)
        val chunk = if (mode == Mode.MELODY && envelopes) min(CHUNK_MS, envMaxDurationMs) else CHUNK_MS
        val to = min(t.durationMs, from + chunk)
        if (to <= from) {
            try {
                vibrator?.cancel()
            } catch (_: Exception) {
            }
            return
        }
        val effect = if (mode == Mode.MELODY && envelopes) envelope(t, from, to) else amplitudeEffect(t, from, to)
        if (effect == null) {
            try {
                vibrator?.cancel()
            } catch (_: Exception) {
            }
        } else {
            vibrate(effect)
        }
        handler.postDelayed(next, (to - from - LEAD_MS).coerceAtLeast(200L))
    }

    private fun amplitudeEffect(t: HapticTrack, from: Long, to: Long): Any? {
        if (ampsFor != mode || amps == null) {
            amps = Score.amplitudes(t, mode)
            ampsFor = mode
        }
        val s = Score.segments(amps!!, t.hopMs, from, to, intensity)
        if (s.isSilent) return null
        return waveform(s.timings, s.amplitudes, -1)
    }

    private fun envelope(t: HapticTrack, from: Long, to: Long): Any? {
        if (Build.VERSION.SDK_INT < 36) return null
        val points = Score.envelope(t, from, to, intensity, minHz, maxHz, envMinPointMs, envMaxPointMs, envMaxSize)
        if (points.isEmpty() || points.all { it.amplitude <= 0f }) return null
        return try {
            val b = VibrationEffect.WaveformEnvelopeBuilder()
            b.setInitialFrequencyHz(points.first().frequencyHz)
            for (p in points) b.addControlPoint(p.amplitude, p.frequencyHz, p.durationMs)
            b.build()
        } catch (_: Exception) {
            amplitudeEffect(t, from, to)
        }
    }

    /** A waveform effect, or on very old phones the on/off pattern that stands in for it. */
    private fun waveform(timings: LongArray, amplitudes: IntArray, repeat: Int): Any {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amps = if (amplitudeControl) amplitudes else IntArray(amplitudes.size) { if (amplitudes[it] >= 90) 255 else 0 }
            return VibrationEffect.createWaveform(timings, amps, repeat)
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
        return LegacyPattern(pattern.toLongArray(), repeat)
    }

    private class LegacyPattern(val pattern: LongArray, val repeat: Int)

    private fun vibrate(effect: Any) {
        val v = vibrator ?: return
        try {
            when {
                effect is LegacyPattern -> @Suppress("DEPRECATION") v.vibrate(effect.pattern, effect.repeat)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    v.vibrate(effect as VibrationEffect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> v.vibrate(effect as VibrationEffect)
            }
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val CHUNK_MS = 15_000L
        const val LEAD_MS = 250L
    }
}
