package com.llgl.xnl

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.llgl.xnl.kernel.KernelScene
import com.llgl.xnl.render.KernelRenderer

/** The same kernel space inside the app, for the preview: its own scene, a tap raises an interrupt, tilt when wanted. */
class KernelView(context: Context) : View(context) {
    private val scene = KernelScene((System.nanoTime() and 0x7FFFFFFF).toInt())
    private val renderer = KernelRenderer()
    private val facts = SystemFacts(context)
    private var tilt: TiltSensor? = null
    private var useTilt = true
    private var lastNanos = 0L
    private var factFrames = 0

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 33L)
        }
    }

    fun configure(accent: Int, wordmark: Boolean, logSpeed: Float, tilt: Boolean, lockLayout: Boolean) {
        scene.accent = accent
        scene.showWordmark = wordmark
        scene.logSpeed = logSpeed
        scene.lockLayout = lockLayout
        useTilt = tilt
        syncTilt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scene.resize(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastNanos = 0L
        facts.refresh(scene)
        removeCallbacks(tick)
        post(tick)
        syncTilt()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        tilt?.stop()
        super.onDetachedFromWindow()
    }

    private fun syncTilt() {
        if (!isAttachedToWindow) return
        if (useTilt) {
            if (tilt == null) {
                tilt = TiltSensor(context) { x, y ->
                    scene.tiltX = x
                    scene.tiltY = y
                }
            }
            tilt?.start()
        } else {
            tilt?.stop()
            scene.tiltX = 0f
            scene.tiltY = 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.033f else (now - lastNanos) / 1e9f
        lastNanos = now
        facts.tick(scene)
        if (++factFrames >= 90) {
            factFrames = 0
            facts.refresh(scene)
        }
        scene.step(dt)
        renderer.draw(canvas, scene, 0f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            scene.tap(event.x, event.y)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
