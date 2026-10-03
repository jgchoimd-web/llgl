package com.llgl.vibe

import android.content.Context
import com.llgl.vibe.analysis.HapticTrack
import java.io.File

/** Analysed tracks on disk, so a song is decoded once. */
class AnalysisCache(context: Context) {
    private val dir = File(context.filesDir, "tracks").apply { mkdirs() }

    private fun file(key: String) = File(dir, "$key.vibe")

    fun get(key: String): HapticTrack? = file(key).takeIf { it.exists() }?.let { f ->
        try {
            HapticTrack.load(f.readBytes())
        } catch (_: Exception) {
            null
        }
    }

    fun put(key: String, track: HapticTrack) {
        try {
            file(key).writeBytes(track.save())
        } catch (_: Exception) {
        }
    }

    fun remove(key: String) {
        file(key).delete()
    }
}
