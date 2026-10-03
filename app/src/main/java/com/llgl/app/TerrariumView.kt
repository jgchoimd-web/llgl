package com.llgl.app

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.llgl.app.world.Terrarium
import kotlin.math.hypot

/**
 * The glass box filling the whole view, with touch: tap to poke, drag to push, long press to drop
 * a crumb, two fingers for the menu. Everything else lives in [TerrariumSession].
 */
class TerrariumView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Two fingers tapped the glass: show the little menu. */
    var onMenu: (() -> Unit)? = null

    private val sounds = SoundBank(context)
    private val haptics = Haptics(context)
    private val session = TerrariumSession(context).also {
        it.sounds = sounds
        it.haptics = haptics
    }

    var soundEnabled: Boolean
        get() = sounds.enabled
        set(value) {
            sounds.enabled = value
        }

    var hapticsEnabled: Boolean
        get() = haptics.enabled
        set(value) {
            haptics.enabled = value
        }

    val world: Terrarium? get() = session.world

    private var running = false
    private var attached = false

    private var pointerId = -1
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastMoveTime = 0L
    private var moved = false
    private var longPressed = false
    private var menuGesture = false
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        longPressed = true
        session.longPress(downX, downY)
        haptics.tick()
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!running || !attached) return
            session.tick(System.nanoTime())
            invalidate()
            postOnAnimation(this)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        session.resize(w, h)
    }

    /** Throws the contents away and plants a new box. */
    fun reset() = session.reset()

    fun resume() {
        running = true
        session.reloadIfNewer()
        session.markResumed()
        sounds.resume()
        if (attached) {
            removeCallbacks(frame)
            postOnAnimation(frame)
        }
    }

    fun pause() {
        running = false
        removeCallbacks(frame)
        sounds.pause()
        session.save()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        if (running) postOnAnimation(frame)
    }

    override fun onDetachedFromWindow() {
        attached = false
        removeCallbacks(frame)
        handler.removeCallbacksAndMessages(null)
        session.release()
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------------------------------
    // Phone state
    // ---------------------------------------------------------------------------------------

    fun setTilt(gx: Float, gy: Float) = session.setTilt(gx, gy)

    fun shake(strength: Float) = session.shake(strength)

    fun setLux(value: Float) = session.setLux(value)

    // ---------------------------------------------------------------------------------------
    // Drawing and touch
    // ---------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        session.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (session.world == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                lastMoveTime = event.eventTime
                moved = false
                longPressed = false
                menuGesture = false
                session.fingerDown(event.x, event.y)
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                menuGesture = true
                handler.removeCallbacks(longPressRunnable)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return true
                val x = event.getX(index)
                val y = event.getY(index)
                if (!moved && hypot(x - downX, y - downY) > TAP_SLOP_DP * resources.displayMetrics.density) {
                    moved = true
                    handler.removeCallbacks(longPressRunnable)
                }
                val dt = (event.eventTime - lastMoveTime) / 1000f
                lastMoveTime = event.eventTime
                session.fingerMove(x, y, dt)
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                session.fingerUp()
                if (menuGesture) {
                    onMenu?.invoke()
                } else if (!moved && !longPressed && event.eventTime - downTime < TAP_MS) {
                    session.tap(downX, downY)
                }
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                session.fingerUp()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val LONG_PRESS_MS = 600L
        const val TAP_MS = 250L
        const val TAP_SLOP_DP = 8f
    }
}
