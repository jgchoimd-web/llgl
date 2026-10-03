package com.llgl.turtles

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.llgl.turtles.render.SkyRenderer
import com.llgl.turtles.sky.SkyScene

/** The same sky inside the app, for the preview: its own scene, a tap rolls a turtle, tilt when wanted. */
class TurtleView(context: Context) : View(context) {
    private val scene = SkyScene((System.nanoTime() and 0x7FFFFFFF).toInt())
    private val renderer = SkyRenderer()
    private var tilt: TiltSensor? = null
    private var useTilt = true
    private var lastNanos = 0L
    private var clockFrames = 0

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 33L)
        }
    }

    fun configure(count: Int, speed: Float, tilt: Boolean, lockLayout: Boolean) {
        scene.count = count
        scene.speedFactor = speed
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
        scene.hour = Prefs.currentHour()
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
        if (++clockFrames >= 60) {
            clockFrames = 0
            scene.hour = Prefs.currentHour()
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
