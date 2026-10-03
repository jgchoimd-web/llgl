package com.llgl.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.llgl.app.audio.Synth
import java.io.File

/**
 * The terrarium's sounds: synthesised once into WAV files in the cache directory and played
 * through a sound pool. Two clips loop quietly all the time (sand hiss, water slosh) and get
 * their volume from how much is moving.
 */
class SoundBank(context: Context) {
    enum class Clip(val file: String, val loop: Boolean = false) {
        TINK("tink"), CLACK("clack"), POP("pop"), CURL("curl"), RUMBLE("rumble"), CHIRP("chirp"),
        HISS("hiss", loop = true), SLOSH("slosh", loop = true),
    }

    var enabled = true
        set(value) {
            field = value
            if (!value) {
                pool?.setVolume(hissStream, 0f, 0f)
                pool?.setVolume(sloshStream, 0f, 0f)
            }
        }

    private var pool: SoundPool? = null
    private val ids = IntArray(Clip.entries.size)
    private val loaded = BooleanArray(Clip.entries.size)
    private val lastPlayed = LongArray(Clip.entries.size)
    private var hissStream = 0
    private var sloshStream = 0
    private var hissVolume = 0f
    private var sloshVolume = 0f
    private var paused = false

    init {
        try {
            val dir = context.cacheDir
            for (clip in Clip.entries) {
                val file = File(dir, "terrarium_${clip.file}.wav")
                if (!file.exists() || file.length() == 0L) file.writeBytes(Synth.wav(synthesise(clip)))
            }
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val p = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build()
            p.setOnLoadCompleteListener { _, sampleId, status ->
                if (status != 0) return@setOnLoadCompleteListener
                val index = ids.indexOf(sampleId)
                if (index < 0) return@setOnLoadCompleteListener
                loaded[index] = true
                when (Clip.entries[index]) {
                    Clip.HISS -> hissStream = p.play(sampleId, 0f, 0f, 0, -1, 1f)
                    Clip.SLOSH -> sloshStream = p.play(sampleId, 0f, 0f, 0, -1, 1f)
                    else -> Unit
                }
            }
            for (clip in Clip.entries) ids[clip.ordinal] = p.load(File(dir, "terrarium_${clip.file}.wav").absolutePath, 1)
            pool = p
        } catch (_: Exception) {
            pool = null
        }
    }

    private fun synthesise(clip: Clip): ShortArray = when (clip) {
        Clip.TINK -> Synth.tink()
        Clip.CLACK -> Synth.clack()
        Clip.POP -> Synth.pop()
        Clip.CURL -> Synth.curl()
        Clip.RUMBLE -> Synth.rumble()
        Clip.CHIRP -> Synth.chirp()
        Clip.HISS -> Synth.hissLoop()
        Clip.SLOSH -> Synth.sloshLoop()
    }

    fun play(clip: Clip, volume: Float, rate: Float = 1f) {
        if (!enabled || paused) return
        val p = pool ?: return
        if (!loaded[clip.ordinal]) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastPlayed[clip.ordinal] < MIN_GAP_MS) return
        lastPlayed[clip.ordinal] = now
        val v = volume.coerceIn(0f, 1f)
        try {
            p.play(ids[clip.ordinal], v, v, 1, 0, rate.coerceIn(0.5f, 2f))
        } catch (_: Exception) {
        }
    }

    /** Sets the two background loops from how much sand and water is moving (0..1 each). */
    fun setFlow(sand: Float, water: Float) {
        val p = pool ?: return
        if (!enabled || paused) return
        val hissTarget = sand.coerceIn(0f, 1f) * 0.5f
        val sloshTarget = water.coerceIn(0f, 1f) * 0.6f
        try {
            if (hissStream != 0 && kotlin.math.abs(hissTarget - hissVolume) > 0.02f) {
                hissVolume += (hissTarget - hissVolume) * 0.4f
                p.setVolume(hissStream, hissVolume, hissVolume)
            }
            if (sloshStream != 0 && kotlin.math.abs(sloshTarget - sloshVolume) > 0.02f) {
                sloshVolume += (sloshTarget - sloshVolume) * 0.4f
                p.setVolume(sloshStream, sloshVolume, sloshVolume)
            }
        } catch (_: Exception) {
        }
    }

    fun pause() {
        paused = true
        try {
            pool?.autoPause()
        } catch (_: Exception) {
        }
    }

    fun resume() {
        paused = false
        try {
            pool?.autoResume()
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            pool?.release()
        } catch (_: Exception) {
        }
        pool = null
    }

    private companion object {
        /** A marble rattling in a corner should click, not buzz. */
        const val MIN_GAP_MS = 40L
    }
}
