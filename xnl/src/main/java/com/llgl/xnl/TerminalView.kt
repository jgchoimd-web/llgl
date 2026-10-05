package com.llgl.xnl

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View

/** The same terminal inside the app, for the preview: its own session, a tap starts the next command. */
class TerminalView(context: Context) : View(context) {
    private val prefs = Prefs(context)
    private var session: TermSession? = null
    private var lockPreview = true

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, if (session?.animating == true) FRAME_MS else IDLE_MS)
        }
    }

    fun configure(lockPreview: Boolean) {
        this.lockPreview = lockPreview
        session?.lockFade = lockPreview
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        session?.resize(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val s = TermSession(context, prefs)
        s.lockFade = lockPreview
        if (width > 0 && height > 0) s.resize(width, height)
        session = s
        removeCallbacks(tick)
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        session?.close()
        session = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        session?.frame(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            session?.tap()
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val FRAME_MS = 33L
        const val IDLE_MS = 100L
    }
}
