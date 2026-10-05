package com.llgl.tube

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import com.llgl.tube.yt.Channel

/**
 * The window on the wallpaper's virtual display: just the player, full screen. Plays only while the
 * engine says it is visible, and only on Wi-Fi when that setting is on; skips videos the embed refuses.
 */
class PlayerPresentation(private val appContext: Context, display: Display, private val prefs: Prefs) :
    Presentation(appContext, display, android.R.style.Theme_Material_NoActionBar_Fullscreen), PlayerView.Listener {

    private var view: PlayerView? = null
    private var visible = false
    private var playing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        val v = PlayerView(context, this)
        view = v
        setContentView(v)
        reload()
    }

    /** Loads the channel's uploads playlist, shuffled, into the official player. */
    fun reload() {
        val v = view ?: return
        val list = Channel.uploadsPlaylist(prefs.channelId)
        if (list == null) {
            prefs.status = "채널 ID가 잘못됐어요: ${prefs.channelId}"
            return
        }
        prefs.status = "플레이어 여는 중"
        v.load(list, prefs.fit, prefs.muted)
    }

    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        val v = view ?: return
        if (isVisible) {
            v.onResume()
            if (allowed()) {
                v.notice("")
                v.play()
            } else {
                v.pause()
                v.notice(if (Net.online(appContext)) "Wi-Fi가 아니라 멈춰 있어요 (설정에서 바꿀 수 있어요)" else "인터넷 연결이 없어요")
            }
        } else {
            v.pause()
            v.onPause()
        }
    }

    fun toggle() {
        val v = view ?: return
        if (playing) v.pause() else if (allowed()) v.play()
    }

    fun next() {
        view?.next()
    }

    fun applyPref(key: String?) {
        val v = view ?: return
        when (key) {
            Prefs.KEY_MUTED -> v.setMuted(prefs.muted)
            Prefs.KEY_FIT -> v.setFit(prefs.fit)
            Prefs.KEY_CHANNEL_ID -> reload()
            Prefs.KEY_WIFI_ONLY -> if (visible) setVisible(true)
        }
    }

    fun destroy() {
        val v = view
        view = null
        try {
            v?.pause()
            dismiss()
        } catch (_: Exception) {
        }
        v?.destroy()
    }

    private fun allowed(): Boolean = !prefs.wifiOnly || Net.onWifi(appContext)

    override fun onReady() {
        prefs.status = "플레이어 준비됨"
        if (visible && allowed()) view?.play()
    }

    override fun onState(state: Int) {
        playing = state == PlayerView.STATE_PLAYING
        if (playing) prefs.status = "재생 중"
    }

    override fun onTitle(title: String) {
        prefs.nowPlaying = title
    }

    override fun onError(code: Int) {
        prefs.status = "유튜브 오류 $code, 다음 영상으로"
    }
}
