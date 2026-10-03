package com.llgl.vibe.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/** The audio half of the player: a MediaPlayer that can be muted while still keeping time. */
class Playback(private val context: Context) {
    private var player: MediaPlayer? = null
    var onCompletion: (() -> Unit)? = null

    var muted: Boolean = false
        set(value) {
            field = value
            applyVolume()
        }

    val isPlaying: Boolean get() = player?.isPlaying == true
    val position: Int get() = try { player?.currentPosition ?: 0 } catch (_: Exception) { 0 }
    val duration: Int get() = try { player?.duration ?: 0 } catch (_: Exception) { 0 }
    val loaded: Boolean get() = player != null

    /** Prepares [uri] synchronously (local files are quick); false when the player refuses it. */
    fun load(uri: Uri): Boolean {
        release()
        return try {
            val p = MediaPlayer()
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            p.setDataSource(context, uri)
            p.setOnCompletionListener { onCompletion?.invoke() }
            p.prepare()
            player = p
            applyVolume()
            true
        } catch (_: Exception) {
            player?.release()
            player = null
            false
        }
    }

    fun play() {
        try {
            player?.start()
        } catch (_: Exception) {
        }
    }

    fun pause() {
        try {
            if (player?.isPlaying == true) player?.pause()
        } catch (_: Exception) {
        }
    }

    fun seekTo(ms: Int) {
        try {
            player?.seekTo(ms)
        } catch (_: Exception) {
        }
    }

    private fun applyVolume() {
        val v = if (muted) 0f else 1f
        try {
            player?.setVolume(v, v)
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }
}
