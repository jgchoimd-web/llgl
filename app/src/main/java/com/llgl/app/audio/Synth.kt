package com.llgl.app.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The terrarium's sounds, synthesised from scratch: no audio assets. Everything is 22.05 kHz
 * 16-bit mono PCM; [wav] wraps a clip for the sound pool.
 */
object Synth {
    const val RATE = 22_050

    /** A marble on glass. [size] 0..1 (small marbles ring higher). */
    fun tink(size: Float = 0.5f): ShortArray {
        val f = 3400f - 1400f * size
        return render(0.16f) { t ->
            val env = exp(-t * 28f) * min(1f, t * 400f)
            (sin(TWO_PI * f * t) * 0.7f + sin(TWO_PI * f * 1.52f * t) * 0.3f) * env
        }
    }

    /** Marble against marble: a dull knock with a little grit. */
    fun clack(): ShortArray {
        val noise = Noise(7)
        var lp = 0f
        return render(0.07f) { t ->
            lp += (noise.next() - lp) * 0.35f
            val env = exp(-t * 60f)
            (lp * 0.6f + sin(TWO_PI * 900f * t) * 0.5f) * env
        }
    }

    /** A fingertip on the glass or a drop in the puddle: a short downward blip. */
    fun pop(): ShortArray = render(0.09f) { t ->
        val f = 260f - 170f * (t / 0.09f)
        sin(TWO_PI * f * t) * exp(-t * 40f) * 0.8f
    }

    /** An isopod snapping into a ball: two quick ticks. */
    fun curl(): ShortArray {
        val noise = Noise(11)
        return render(0.06f) { t ->
            val t2 = if (t < 0.03f) t else t - 0.03f
            noise.next() * exp(-t2 * 220f) * 0.7f
        }
    }

    /** The box being shaken: a low rumble. */
    fun rumble(): ShortArray {
        val noise = Noise(3)
        var lp = 0f
        return render(0.45f) { t ->
            lp += (noise.next() - lp) * 0.05f
            val env = min(1f, t * 20f) * exp(-t * 6f)
            lp * env * 4f
        }
    }

    /** A cricket: three modulated bursts. */
    fun chirp(): ShortArray = render(0.36f) { t ->
        val burst = (t / 0.12f).toInt()
        val local = t - burst * 0.12f
        if (local > 0.06f) 0f else {
            val env = sin(PI.toFloat() * local / 0.06f)
            val am = 0.5f + 0.5f * sin(TWO_PI * 60f * local)
            sin(TWO_PI * 4200f * t) * env * am * 0.35f
        }
    }

    /** Sand flowing: a second of soft hiss that loops seamlessly. */
    fun hissLoop(): ShortArray {
        val noise = Noise(5)
        var lp = 0f
        return loop(render(1.0f) { _ ->
            lp += (noise.next() - lp) * 0.3f
            lp * 1.2f
        })
    }

    /** Water moving: low, slowly wobbling noise that loops seamlessly. */
    fun sloshLoop(): ShortArray {
        val noise = Noise(9)
        var lp = 0f
        return loop(render(1.2f) { t ->
            lp += (noise.next() - lp) * 0.08f
            val wobble = 0.65f + 0.35f * sin(TWO_PI * 2.5f * t)
            lp * wobble * 5f
        })
    }

    /** Renders [seconds] of a signal in -1..1 to PCM, normalised to [peak]. */
    fun render(seconds: Float, peak: Float = 0.8f, signal: (Float) -> Float): ShortArray {
        val n = (seconds * RATE).toInt()
        val buffer = FloatArray(n)
        var max = 1e-6f
        for (i in 0 until n) {
            val v = signal(i.toFloat() / RATE)
            buffer[i] = v
            if (v > max) max = v
            if (-v > max) max = -v
        }
        val scale = peak / max
        val pcm = ShortArray(n)
        for (i in 0 until n) pcm[i] = (buffer[i] * scale * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        return pcm
    }

    /** Crossfades the last 10% into the start so the clip can repeat without a click. */
    fun loop(pcm: ShortArray): ShortArray {
        val fade = pcm.size / 10
        val out = pcm.copyOf(pcm.size - fade)
        for (i in 0 until fade) {
            val k = i.toFloat() / fade
            val a = pcm[i] * k
            val b = pcm[pcm.size - fade + i] * (1f - k)
            out[i] = (a + b).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    fun wav(pcm: ShortArray): ByteArray {
        val dataLength = pcm.size * 2
        val buffer = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataLength).put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            .putShort(1) // PCM
            .putShort(1) // mono
            .putInt(RATE)
            .putInt(RATE * 2) // byte rate
            .putShort(2) // block align
            .putShort(16) // bits per sample
        buffer.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataLength)
        for (s in pcm) buffer.putShort(s)
        return buffer.array()
    }

    /** White noise in -1..1 from a tiny LCG. */
    class Noise(seed: Int) {
        private var state = seed * 0x2545F491 + 1

        fun next(): Float {
            state = state * 1103515245 + 12345
            return ((state ushr 16) and 0x7FFF) / 32768f * 2f - 1f
        }
    }

    private const val TWO_PI = (2 * PI).toFloat()
}
