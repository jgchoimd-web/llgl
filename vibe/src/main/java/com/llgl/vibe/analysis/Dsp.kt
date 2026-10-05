package com.llgl.vibe.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A second-order IIR section (RBJ cookbook), direct form I. */
class Biquad private constructor(private val b0: Float, private val b1: Float, private val b2: Float, private val a1: Float, private val a2: Float) {
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }

    companion object {
        fun lowPass(sampleRate: Int, cutoffHz: Float, q: Float = 0.7071f): Biquad {
            val w0 = (2.0 * PI * cutoffHz / sampleRate)
            val c = cos(w0).toFloat()
            val alpha = (sin(w0) / (2 * q)).toFloat()
            val a0 = 1 + alpha
            return Biquad(((1 - c) / 2) / a0, (1 - c) / a0, ((1 - c) / 2) / a0, (-2 * c) / a0, (1 - alpha) / a0)
        }

        fun highPass(sampleRate: Int, cutoffHz: Float, q: Float = 0.7071f): Biquad {
            val w0 = (2.0 * PI * cutoffHz / sampleRate)
            val c = cos(w0).toFloat()
            val alpha = (sin(w0) / (2 * q)).toFloat()
            val a0 = 1 + alpha
            return Biquad(((1 + c) / 2) / a0, -(1 + c) / a0, ((1 + c) / 2) / a0, (-2 * c) / a0, (1 - alpha) / a0)
        }

        /** Constant-peak band-pass; [q] sets the width (centre / bandwidth). */
        fun bandPass(sampleRate: Int, centreHz: Float, q: Float): Biquad {
            val w0 = (2.0 * PI * centreHz / sampleRate)
            val c = cos(w0).toFloat()
            val alpha = (sin(w0) / (2 * q)).toFloat()
            val a0 = 1 + alpha
            return Biquad(alpha / a0, 0f, -alpha / a0, (-2 * c) / a0, (1 - alpha) / a0)
        }
    }
}

object Dsp {
    fun rms(buf: FloatArray, from: Int = 0, length: Int = buf.size - from): Float {
        if (length <= 0) return 0f
        var s = 0.0
        for (i in from until from + length) s += (buf[i] * buf[i]).toDouble()
        return sqrt(s / length).toFloat()
    }

    /** A normalised sine, for tests and demo signals. */
    fun sine(sampleRate: Int, hz: Float, seconds: Float, amplitude: Float = 1f, into: FloatArray? = null, offset: Int = 0): FloatArray {
        val n = (sampleRate * seconds).toInt()
        val out = into ?: FloatArray(n)
        for (i in 0 until n) if (offset + i < out.size) out[offset + i] += (amplitude * sin(2.0 * PI * hz * i / sampleRate)).toFloat()
        return out
    }
}
