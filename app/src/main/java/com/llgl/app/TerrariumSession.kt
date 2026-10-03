package com.llgl.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.llgl.app.render.TerrariumRenderer
import com.llgl.app.world.Event
import com.llgl.app.world.Particles
import com.llgl.app.world.Terrarium
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One running terrarium: the world, its pixel renderer and the fixed-step clock, plus the phone
 * state feeding it. Shared by the full-screen view and the live wallpaper, which differ only in
 * where the frames go and whether touches arrive. Sounds and haptics are optional.
 */
class TerrariumSession(context: Context) {
    var sounds: SoundBank? = null
    var haptics: Haptics? = null

    private val prefs = Prefs(context)
    private val store = TerrariumStore(context)

    var world: Terrarium? = null
        private set
    private var renderer: TerrariumRenderer? = null
    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null

    /** Screen pixels per world pixel. */
    var scale = 3
        private set
    private val dst = Rect()
    private val pixelPaint = Paint().apply {
        isFilterBitmap = false
        isDither = false
    }

    private var lastFrameNanos = 0L
    private var accumulator = 0f
    private var stepCostMs = 0f
    private var flowTimer = 0f
    private var nightTimer = 0f
    private var tiltX = 0f
    private var tiltY = 0f
    private var lux = -1f
    private var nightTarget = 0f

    /** When the world was last loaded from or written to disk, to notice saves made elsewhere. */
    private var stateTime = 0L

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    /** Fits the box to a surface of [width]×[height] screen pixels, keeping the world if the size did not change. */
    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val newScale = max(2, (width / 360f).roundToInt())
        val lrW = ceil(width / newScale.toFloat()).toInt()
        val lrH = ceil(height / newScale.toFloat()).toInt()
        dst.set(0, 0, width, height)
        val current = world
        if (current != null && scale == newScale && current.width.toInt() == lrW && current.height.toInt() == lrH) return
        current?.let { store.save(it) }
        scale = newScale
        build(lrW, lrH)
    }

    private fun build(lrW: Int, lrH: Int) {
        val newWorld = Terrarium(lrW.toFloat(), lrH.toFloat(), prefs.seed, prefs.ageDays)
        store.load(newWorld)
        stateTime = System.currentTimeMillis()
        newWorld.gravityX = tiltX
        newWorld.gravityY = tiltY
        newWorld.nightness = nightTarget
        world = newWorld
        renderer = TerrariumRenderer(newWorld, prefs.seed)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(lrW, lrH, Bitmap.Config.ARGB_8888).also { bitmapCanvas = Canvas(it) }
        accumulator = 0f
        lastFrameNanos = 0L
    }

    /** Throws the contents away and plants a new box of the same size. */
    fun reset() {
        val w = world ?: return
        prefs.replant()
        store.deleteAll()
        world = null
        build(w.width.toInt(), w.height.toInt())
    }

    /** Call when frames start again after a pause so the first step is not a huge catch-up. */
    fun markResumed() {
        lastFrameNanos = 0L
        accumulator = 0f
    }

    fun save() {
        val w = world ?: return
        store.save(w)
        stateTime = System.currentTimeMillis()
    }

    /** Picks up a save written by the other side (app vs wallpaper) since this session last touched disk. */
    fun reloadIfNewer() {
        val w = world ?: return
        if (store.lastModified(w) > stateTime + 500L) {
            store.load(w)
            stateTime = System.currentTimeMillis()
        }
    }

    fun release() {
        world?.let { store.save(it) }
        bitmap?.recycle()
        bitmap = null
        bitmapCanvas = null
        sounds?.release()
        sounds = null
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
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f
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
    // Touch (the view only; the wallpaper never calls these)
    // ---------------------------------------------------------------------------------------

    fun fingerDown(x: Float, y: Float) = world?.fingerDown(x / scale, y / scale)

    fun fingerMove(x: Float, y: Float, dt: Float) = world?.fingerMove(x / scale, y / scale, dt)

    fun fingerUp() = world?.fingerUp()

    fun tap(x: Float, y: Float) = world?.tap(x / scale, y / scale)

    fun longPress(x: Float, y: Float) = world?.longPress(x / scale, y / scale)

    // ---------------------------------------------------------------------------------------
    // Simulation and drawing
    // ---------------------------------------------------------------------------------------

    /** Advances the world to [nowNanos] in fixed steps and turns its events into sound and touch feedback. */
    fun tick(nowNanos: Long) {
        val w = world ?: return
        val r = renderer ?: return
        if (lastFrameNanos == 0L) lastFrameNanos = nowNanos
        val dt = ((nowNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
        lastFrameNanos = nowNanos
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
            sounds?.setFlow(min(1f, w.particles.sandMotion / 40f), min(1f, w.particles.waterMotion / 60f))
        }
    }

    private fun handleEvents(w: Terrarium, r: TerrariumRenderer) {
        for (e in w.events) {
            when (e) {
                is Event.Impact -> {
                    val strength = ((e.speed - 90f) / 400f).coerceIn(0.05f, 1f)
                    if (e.wall) {
                        val size = ((e.radius - 3f) / 3f).coerceIn(0f, 1f)
                        sounds?.play(SoundBank.Clip.TINK, 0.25f + 0.75f * strength, rate = 1.3f - 0.5f * size)
                        haptics?.impact(strength)
                    } else {
                        sounds?.play(SoundBank.Clip.CLACK, 0.3f + 0.7f * strength)
                        haptics?.impact(strength * 0.7f)
                    }
                    r.addImpact(e.x, e.y, e.wall, strength)
                }
                is Event.Tap -> {
                    sounds?.play(SoundBank.Clip.POP, 0.7f, rate = if (e.onWater) 0.8f else 1.1f)
                    haptics?.tick()
                    r.addRipple(e.x, e.y, e.onWater)
                }
                is Event.Curl -> {
                    sounds?.play(SoundBank.Clip.CURL, 0.8f)
                    haptics?.tick()
                }
                is Event.Shake -> {
                    sounds?.play(SoundBank.Clip.RUMBLE, 0.9f)
                    haptics?.rumble(200L)
                }
                Event.Chirp -> sounds?.play(SoundBank.Clip.CHIRP, 0.35f, rate = 0.9f + 0.2f * (System.nanoTime() % 7) / 7f)
            }
        }
    }

    /** Draws the current frame onto [canvas], filling the surface given to [resize]. */
    fun draw(canvas: Canvas) {
        val w = world ?: return
        val r = renderer ?: return
        val bmp = bitmap ?: return
        val bc = bitmapCanvas ?: return
        r.draw(bc, w.nightness)
        canvas.drawBitmap(bmp, null, dst, pixelPaint)
    }

    private companion object {
        const val STEP = 1f / 60f
        const val MAX_STEPS = 3
        const val SLOW_STEP_MS = 7f
        const val DARK_LUX = 20f
    }
}
