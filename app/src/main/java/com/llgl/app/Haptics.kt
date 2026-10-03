package com.llgl.app

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Buzzes for what happens in the box, rate-limited so a rattling marble does not become a drone. */
class Haptics(context: Context) {
    var enabled = true

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private var lastAt = 0L

    /** A marble hitting something; [strength] 0..1. */
    fun impact(strength: Float) {
        if (!gate(45L)) return
        val s = strength.coerceIn(0f, 1f)
        vibrate((6f + 24f * s).toLong(), (40f + 215f * s).toInt())
    }

    fun tick() {
        if (!gate(45L)) return
        val v = vibrator ?: return
        if (!enabled) return
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                vibrate(8L, 90)
            }
        } catch (_: Exception) {
        }
    }

    fun rumble(millis: Long) {
        lastAt = SystemClock.uptimeMillis()
        vibrate(millis, 200)
    }

    private fun gate(minGap: Long): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < minGap) return false
        lastAt = now
        return true
    }

    private fun vibrate(millis: Long, amplitude: Int) {
        if (!enabled) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val amp = if (v.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
                v.vibrate(VibrationEffect.createOneShot(millis.coerceAtLeast(1L), amp))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(millis.coerceAtLeast(1L))
            }
        } catch (_: Exception) {
        }
    }
}
