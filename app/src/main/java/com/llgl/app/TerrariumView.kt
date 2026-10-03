package com.llgl.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.llgl.app.render.TerrariumRenderer
import com.llgl.app.world.Event
import com.llgl.app.world.Particles
import com.llgl.app.world.Terrarium
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The glass box, filling the whole view. Runs the world at a fixed step, draws it onto a small
 * bitmap and blows that up into crisp pixels, turns touches into pokes and pushes, and turns the
 * world's events into clicks and buzzes.
 */
class TerrariumView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Two fingers tapped the glass: show the little menu. */
    var onMenu: (() -> Unit)? = null

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

    private val prefs = Prefs(context)
    private val store = TerrariumStore(context)
    private val sounds = SoundBank(context)
    private val haptics = Haptics(context)

    var world: Terrarium? = null
        private set
    private var renderer: TerrariumRenderer? = null
    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private var scale = 3
    private val dst = Rect()
    private val pixelPaint = Paint().apply { isFilterBitmap = false; isDither = false }

    private var running = false
    private var attached = false
    private var lastFrameNanos = 0L
    private var accumulator = 0f
    private var stepCostMs = 0f
    private var flowTimer = 0f
    private var nightTimer = 0f
    private var tiltX = 0f
    private var tiltY = 0f
    private var lux = -1f
    private var nightTarget = 0f

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
        val w = world ?: return@Runnable
        longPressed = true
        w.longPress(downX / scale, downY / scale)
        haptics.tick()
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!running || !attached) return
            tick()
            invalidate()
            postOnAnimation(this)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        world?.let { store.save(it) }
        build(w, h)
    }

    private fun build(w: Int, h: Int) {
        scale = max(2, (w / 360f).roundToInt())
        val lrW = ceil(w / scale.toFloat()).toInt()
        val lrH = ceil(h / scale.toFloat()).toInt()
        val newWorld = Terrarium(lrW.toFloat(), lrH.toFloat(), prefs.seed, prefs.ageDays)
        store.load(newWorld)
        newWorld.gravityX = tiltX
        newWorld.gravityY = tiltY
        newWorld.nightness = nightTarget
        world = newWorld
        renderer = TerrariumRenderer(newWorld, prefs.seed)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(lrW, lrH, Bitmap.Config.ARGB_8888).also { bitmapCanvas = Canvas(it) }
        dst.set(0, 0, w, h)
        accumulator = 0f
    }

    /** Throws the contents away and plants a new box. */
    fun reset() {
        prefs.replant()
        store.delete()
        if (width > 0 && height > 0) {
            world = null
            build(width, height)
        }
    }

    fun resume() {
        running = true
        lastFrameNanos = 0L
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
        world?.let { store.save(it) }
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
        world?.let { store.save(it) }
        sounds.release()
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------------------------------
    // Phone state
    // ---------------------------------------------------------------------------------------

    fun setTilt(gx: Float, gy: Float) {
        tiltX = gx
        tiltY = gy
        world?.let {
            it.gravityX = gx
            it.gravityY = gy
        }
    }

    fun shake(strength: Float) {
        world?.shake(strength)
    }

    fun setLux(value: Float) {
        lux = value
    }

    private fun updateNight(dt: Float) {
        nightTimer -= dt
        if (nightTimer <= 0f) {
            nightTimer = 1f
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + Calendar.getInstance().get(Calendar.MINUTE) / 60f
            val byClock = when {
                hour >= 21f || hour < 5.5f -> 1f
                hour >= 19.5f -> (hour - 19.5f) / 1.5f
                hour < 7f -> 1f - (hour - 5.5f) / 1.5f
                else -> 0f
            }
            val byLight = if (lux < 0f) 0f else ((DARK_LUX - lux) / DARK_LUX).coerceIn(0f, 1f)
            nightTarget = max(byClock, byLight)
        }
        val w = world ?: return
        w.nightness += (nightTarget - w.nightness) * min(1f, dt * 2f)
    }

    // ---------------------------------------------------------------------------------------
    // Simulation
    // ---------------------------------------------------------------------------------------

    private fun tick() {
        val w = world ?: return
        val r = renderer ?: return
        val now = System.nanoTime()
        if (lastFrameNanos == 0L) lastFrameNanos = now
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
        lastFrameNanos = now
        accumulator += dt
        var steps = 0
        val before = System.nanoTime()
        while (accumulator >= STEP && steps < MAX_STEPS) {
            w.step(STEP)
            handleEvents(w, r)
            accumulator -= STEP
            steps++
        }
        if (steps == MAX_STEPS) accumulator = 0f
        if (steps > 0) {
            // Smooth the cost of one step; a slow phone gets a cheaper solver rather than a stalling one.
            val perStepMs = (System.nanoTime() - before) / 1_000_000f / steps
            stepCostMs += (perStepMs - stepCostMs) * 0.1f
            w.particles.iterations = if (stepCostMs > SLOW_STEP_MS) 1 else Particles.ITERATIONS
        }
        updateNight(dt)
        flowTimer -= dt
        if (flowTimer <= 0f) {
            flowTimer = 0.1f
            sounds.setFlow(min(1f, w.particles.sandMotion / 40f), min(1f, w.particles.waterMotion / 60f))
        }
    }

    private fun handleEvents(w: Terrarium, r: TerrariumRenderer) {
        for (e in w.events) {
            when (e) {
                is Event.Impact -> {
                    val strength = ((e.speed - 90f) / 400f).coerceIn(0.05f, 1f)
                    if (e.wall) {
                        val size = ((e.radius - 3f) / 3f).coerceIn(0f, 1f)
                        sounds.play(SoundBank.Clip.TINK, 0.25f + 0.75f * strength, rate = 1.3f - 0.5f * size)
                        haptics.impact(strength)
                    } else {
                        sounds.play(SoundBank.Clip.CLACK, 0.3f + 0.7f * strength)
                        haptics.impact(strength * 0.7f)
                    }
                    r.addImpact(e.x, e.y, e.wall, strength)
                }
                is Event.Tap -> {
                    sounds.play(SoundBank.Clip.POP, 0.7f, rate = if (e.onWater) 0.8f else 1.1f)
                    haptics.tick()
                    r.addRipple(e.x, e.y, e.onWater)
                }
                is Event.Curl -> {
                    sounds.play(SoundBank.Clip.CURL, 0.8f)
                    haptics.tick()
                }
                is Event.Shake -> {
                    sounds.play(SoundBank.Clip.RUMBLE, 0.9f)
                    haptics.rumble(200L)
                }
                Event.Chirp -> sounds.play(SoundBank.Clip.CHIRP, 0.35f, rate = 0.9f + 0.2f * (System.nanoTime() % 7) / 7f)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val w = world ?: return
        val r = renderer ?: return
        val bmp = bitmap ?: return
        val bc = bitmapCanvas ?: return
        r.draw(bc, w.nightness)
        canvas.drawBitmap(bmp, null, dst, pixelPaint)
    }

    // ---------------------------------------------------------------------------------------
    // Touch
    // ---------------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = world ?: return false
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
                w.fingerDown(event.x / scale, event.y / scale)
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
                w.fingerMove(x / scale, y / scale, dt)
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                w.fingerUp()
                if (menuGesture) {
                    onMenu?.invoke()
                } else if (!moved && !longPressed && event.eventTime - downTime < TAP_MS) {
                    w.tap(downX / scale, downY / scale)
                }
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                w.fingerUp()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val STEP = 1f / 60f
        const val MAX_STEPS = 3
        const val SLOW_STEP_MS = 7f
        const val LONG_PRESS_MS = 600L
        const val TAP_MS = 250L
        const val TAP_SLOP_DP = 8f
        const val DARK_LUX = 20f
    }
}
