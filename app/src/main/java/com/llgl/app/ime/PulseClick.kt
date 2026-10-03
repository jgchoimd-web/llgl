package com.llgl.app.ime

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * The dial's click: a short mechanical tick synthesised once into a tiny WAV in the cache directory
 * and replayed through a [SoundPool], which mixes rapid repeats without blocking the UI thread.
 */
class PulseClick(context: Context) {
    private var pool: SoundPool? = null
    private var soundId = 0

    @Volatile
    private var loaded = false

    init {
        try {
            val file = File(context.cacheDir, FILE)
            if (!file.exists() || file.length() == 0L) file.writeBytes(wav(synthesise()))
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
            p.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
            soundId = p.load(file.absolutePath, 1)
            pool = p
        } catch (_: Exception) {
            pool = null
        }
    }

    fun play() {
        if (!loaded) return
        try {
            pool?.play(soundId, VOLUME, VOLUME, 1, 0, 1f)
        } catch (_: Exception) {
            // No sound is better than a crash in the keyboard.
        }
    }

    fun release() {
        try {
            pool?.release()
        } catch (_: Exception) {
        }
        pool = null
        loaded = false
    }

    /** Nine milliseconds of a knock with a little noise, fading out fast. */
    private fun synthesise(): ShortArray {
        val samples = RATE * 9 / 1000
        val pcm = ShortArray(samples)
        var seed = 0x2545F491
        for (i in 0 until samples) {
            val t = i.toFloat() / samples
            seed = seed * 1103515245 + 12345
            val noise = ((seed ushr 16) and 0x7FFF) / 32768f * 2f - 1f
            val knock = sin(2.0 * PI * 240.0 * i / RATE).toFloat()
            val envelope = (1f - t) * (1f - t)
            pcm[i] = ((noise * 0.45f + knock * 0.55f) * envelope * 0.5f * Short.MAX_VALUE).toInt().toShort()
        }
        return pcm
    }

    private fun wav(pcm: ShortArray): ByteArray {
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

    private companion object {
        const val FILE = "pulse_click.wav"
        const val RATE = 22_050
        const val VOLUME = 0.7f
    }
}
