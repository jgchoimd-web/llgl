package com.llgl.gameforge.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Runs one generated game. JavaScript errors from the page are handed to [onError] so the player
 * can send them back to the model.
 */
@SuppressLint("SetJavaScriptEnabled")
class GameWebView(context: Context, private val onError: (String) -> Unit) : WebView(context) {
    private var shownKey = Int.MIN_VALUE

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        setBackgroundColor(Color.BLACK)
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            setWebContentsDebuggingEnabled(true)
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    onError("${message.message()} (줄 ${message.lineNumber()})")
                }
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            // A game is one page; links it might contain stay inside the app instead of opening anything.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
    }

    /** Loads [html] unless this exact [key] is already showing (so recompositions do not restart the game). */
    fun show(html: String, key: Int) {
        if (shownKey == key) return
        shownKey = key
        loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)
    }

    companion object {
        /** A secure-context origin so localStorage and friends work for the game. */
        const val BASE_URL = "https://gameforge.local/"
    }
}
