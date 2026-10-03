package com.llgl.vibe.analysis

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * What the analyser knows about a song, one value per frame ([hopMs] apart): loudness, bass
 * energy and onset strength in 0..1, pitch in Hz (0 = none), plus the beat frames and tempo.
 * Small enough to cache per song and to turn into vibration on the fly.
 */
class HapticTrack(
    val hopMs: Float,
    val loud: FloatArray,
    val bass: FloatArray,
    val onset: FloatArray,
    val pitch: FloatArray,
    val beats: IntArray,
    val bpm: Float,
) {
    val frames: Int get() = loud.size
    val durationMs: Long get() = (frames * hopMs).toLong()

    fun frameAt(ms: Long): Int = ((ms / hopMs).toInt()).coerceIn(0, (frames - 1).coerceAtLeast(0))

    fun save(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeFloat(hopMs)
            out.writeInt(frames)
            for (a in arrayOf(loud, bass, onset)) for (v in a) out.writeShort((v.coerceIn(0f, 1f) * 65535f).toInt())
            for (v in pitch) out.writeFloat(v)
            out.writeInt(beats.size)
            for (b in beats) out.writeInt(b)
            out.writeFloat(bpm)
        }
        return bytes.toByteArray()
    }

    companion object {
        private const val MAGIC = 0x56494245 // VIBE
        private const val VERSION = 1

        fun load(bytes: ByteArray): HapticTrack? = try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                val hop = input.readFloat()
                val frames = input.readInt()
                if (frames < 0 || frames > 10_000_000) return null
                fun shorts() = FloatArray(frames) { (input.readUnsignedShort()) / 65535f }
                val loud = shorts()
                val bass = shorts()
                val onset = shorts()
                val pitch = FloatArray(frames) { input.readFloat() }
                val beats = IntArray(input.readInt()) { input.readInt() }
                val bpm = input.readFloat()
                HapticTrack(hop, loud, bass, onset, pitch, beats, bpm)
            }
        } catch (_: Exception) {
            null
        }
    }
}
