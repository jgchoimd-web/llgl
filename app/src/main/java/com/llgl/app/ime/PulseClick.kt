package com.llgl.app.ime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** The dial's click: a short mechanical tick synthesised once and replayed for every pulse. */
class PulseClick {
    private val track: AudioTrack? = try {
        val rate = 22_050
        val samples = rate * 9 / 1000
        val pcm = ShortArray(samples)
        var seed = 0x2545F491
        for (i in 0 until samples) {
            val t = i.toFloat() / samples
            seed = seed * 1103515245 + 12345
            val noise = ((seed ushr 16) and 0x7FFF) / 32768f * 2f - 1f
            val knock = sin(2.0 * PI * 240.0 * i / rate).toFloat()
            val envelope = (1f - t) * (1f - t)
            pcm[i] = ((noise * 0.45f + knock * 0.55f) * envelope * 0.35f * Short.MAX_VALUE).toInt().toShort()
        }
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples * 2)
            .build()
            .also { it.write(pcm, 0, samples) }
    } catch (_: Exception) {
        null
    }

    fun play() {
        val t = track ?: return
        try {
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (_: Exception) {
            // No sound is better than a crash in the keyboard.
        }
    }

    fun release() {
        try {
            track?.release()
        } catch (_: Exception) {
        }
    }
}
