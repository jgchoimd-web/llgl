package com.llgl.tube

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.llgl.tube.yt.PlayerPage

/**
 * A WebView holding YouTube's official IFrame player. The same view serves the wallpaper (on a
 * virtual display) and the in-app preview. JavaScript calls go in through [play], [pause], [next],
 * [setMuted], [setFit], [notice]; the page reports back through the `Bridge` interface.
 */
@SuppressLint("SetJavaScriptEnabled")
class PlayerView(context: Context, private val listener: Listener) : WebView(context) {
    interface Listener {
        fun onReady()
        fun onState(state: Int)
        fun onTitle(title: String)
        fun onError(code: Int)
    }

    init {
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        webChromeClient = WebChromeClient()
        webViewClient = WebViewClient()
        addJavascriptInterface(Bridge(), "Bridge")
    }

    fun load(listId: String, fit: String, muted: Boolean) {
        loadDataWithBaseURL(PlayerPage.ORIGIN, PlayerPage.html(listId, fit, muted), "text/html", "utf-8", null)
    }

    fun play() = js("play()")
    fun pause() = js("pause()")
    fun next() = js("next()")
    fun setMuted(muted: Boolean) = js("setMuted(${if (muted) "true" else "false"})")
    fun setFit(fit: String) = js("setFit('${if (fit == PlayerPage.FIT_CONTAIN) PlayerPage.FIT_CONTAIN else PlayerPage.FIT_COVER}')")
    fun notice(text: String) = js("notice(${jsString(text)})")

    private fun js(code: String) {
        evaluateJavascript(code, null)
    }

    private fun jsString(s: String): String = "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'"

    private inner class Bridge {
        @JavascriptInterface
        fun onReady(a: String) {
            post { listener.onReady() }
        }

        @JavascriptInterface
        fun onState(a: String) {
            val s = a.toIntOrNull() ?: return
            post { listener.onState(s) }
        }

        @JavascriptInterface
        fun onTitle(a: String) {
            post { listener.onTitle(a) }
        }

        @JavascriptInterface
        fun onError(a: String) {
            val c = a.toIntOrNull() ?: -1
            post { listener.onError(c) }
        }
    }

    companion object {
        const val STATE_PLAYING = 1
        const val STATE_PAUSED = 2
    }
}
